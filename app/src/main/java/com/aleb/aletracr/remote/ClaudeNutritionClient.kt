package com.aleb.aletracr.remote

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.BadRequestException
import com.anthropic.errors.PermissionDeniedException
import com.anthropic.errors.RateLimitException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.models.messages.Base64ImageSource
import com.anthropic.models.messages.ContentBlockParam
import com.anthropic.models.messages.JsonOutputFormat
import com.anthropic.models.messages.Message
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.StopReason
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Base64

/**
 * Claude (Anthropic) implementation. Paid per request, very accurate.
 * Meal/label answers are constrained to a JSON schema (structured outputs).
 */
class ClaudeNutritionClient(
    apiKey: String,
    private val model: String = DEFAULT_MODEL,
) : NutritionAi {
    private val client: AnthropicClient = AnthropicOkHttpClient.builder()
        .apiKey(apiKey)
        .build()

    override suspend fun estimateMeal(description: String?, imageJpeg: ByteArray?): AiEstimate {
        require(!description.isNullOrBlank() || imageJpeg != null) { "Beschreibung oder Foto erforderlich" }
        val content = buildList {
            imageJpeg?.let { add(image(it)) }
            add(ContentBlockParam.ofText(AiPrompts.mealUserText(description, imageJpeg != null)))
        }
        return AiPrompts.parseEstimate(request(AiPrompts.MEAL_SYSTEM, content, AiPrompts.MEAL_SCHEMA))
    }

    override suspend fun readLabel(imageJpeg: ByteArray): FoodProduct {
        val content = listOf(image(imageJpeg), ContentBlockParam.ofText("Lies diese Nährwerttabelle ab."))
        return AiPrompts.parseLabel(request(AiPrompts.LABEL_SYSTEM, content, AiPrompts.LABEL_SCHEMA))
    }

    override suspend fun coach(summary: String): String =
        request(AiPrompts.COACH_SYSTEM, listOf(ContentBlockParam.ofText(summary)), schema = null)

    private fun image(jpeg: ByteArray) = ContentBlockParam.ofImage(
        Base64ImageSource.builder()
            .mediaType(Base64ImageSource.MediaType.IMAGE_JPEG)
            .data(Base64.getEncoder().encodeToString(jpeg))
            .build()
    )

    private suspend fun request(
        system: String,
        content: List<ContentBlockParam>,
        schema: Map<String, Any>?,
    ): String = withContext(Dispatchers.IO) {
        val output = OutputConfig.builder().effort(OutputConfig.Effort.MEDIUM)
        if (schema != null) output.format(JsonOutputFormat.builder().schema(toSchema(schema)).build())

        val builder = MessageCreateParams.builder()
            .model(model)
            .maxTokens(16000L)
            .system(system)
            .addUserMessageOfContentBlockParams(content)
            .outputConfig(output.build())

        // Server-side fallback: if a safety classifier declines, the API
        // retries on a suitable model instead of returning a refusal.
        if (model in FALLBACK_MODELS) {
            builder.putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
            builder.putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
        }

        val response: Message = try {
            client.messages().create(builder.build())
        } catch (e: UnauthorizedException) {
            throw AiException("Ungültiger Claude API-Key. Bitte in den Einstellungen prüfen.", e)
        } catch (e: PermissionDeniedException) {
            throw AiException("Der API-Key hat keinen Zugriff auf das Modell $model.", e)
        } catch (e: RateLimitException) {
            throw AiException("Zu viele Anfragen – bitte kurz warten und erneut versuchen.", e)
        } catch (e: BadRequestException) {
            throw AiException("Anfrage abgelehnt: ${e.message}", e)
        } catch (e: AnthropicServiceException) {
            throw AiException("KI-Dienst nicht erreichbar (HTTP ${e.statusCode()}).", e)
        } catch (e: AnthropicIoException) {
            throw AiException("Keine Verbindung zur KI. Internet prüfen.", e)
        }

        when (response.stopReason().orElse(null)) {
            StopReason.REFUSAL -> throw AiException("Die KI konnte diese Anfrage nicht bearbeiten.")
            StopReason.MAX_TOKENS -> throw AiException("Antwort der KI war unvollständig – bitte erneut versuchen.")
            else -> Unit
        }
        val text = response.content().mapNotNull { it.text().orElse(null)?.text() }.joinToString("")
        if (text.isBlank()) throw AiException("Leere Antwort der KI.")
        text
    }

    private fun toSchema(schema: Map<String, Any>): JsonOutputFormat.Schema {
        val b = JsonOutputFormat.Schema.builder()
        schema.forEach { (k, v) -> b.putAdditionalProperty(k, JsonValue.from(v)) }
        return b.build()
    }

    companion object {
        const val DEFAULT_MODEL = "claude-opus-5-5"

        /** Models offered in the settings screen (id to label). */
        val MODELS = listOf(
            "claude-opus-5-5" to "Claude Opus 5.5 – genaueste Schätzung",
            "claude-sonnet-5-5" to "Claude Sonnet 5.5 – schnell & günstiger",
            "claude-haiku-5-5" to "Claude Haiku 5.5 – am günstigsten",
        )

        private val FALLBACK_MODELS = setOf("claude-opus-5-5", "claude-sonnet-5-5")
    }
}
