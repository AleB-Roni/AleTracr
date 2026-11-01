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
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.StopReason
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Base64

/**
 * Estimates calories and macros from a meal photo and/or a free-text
 * description ("2 Brötchen mit Nutella und ein Cappuccino") using Claude.
 *
 * The answer is constrained to a JSON schema (structured outputs), so it can
 * be parsed reliably and shown as an editable list before saving.
 */
class ClaudeNutritionClient(
    apiKey: String,
    private val model: String = DEFAULT_MODEL,
) {
    private val client: AnthropicClient = AnthropicOkHttpClient.builder()
        .apiKey(apiKey)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun estimate(description: String?, imageJpeg: ByteArray?): AiEstimate =
        withContext(Dispatchers.IO) {
            require(!description.isNullOrBlank() || imageJpeg != null) {
                "Beschreibung oder Foto erforderlich"
            }

            val content = buildList {
                if (imageJpeg != null) {
                    add(
                        ContentBlockParam.ofImage(
                            Base64ImageSource.builder()
                                .mediaType(Base64ImageSource.MediaType.IMAGE_JPEG)
                                .data(Base64.getEncoder().encodeToString(imageJpeg))
                                .build()
                        )
                    )
                }
                val text = buildString {
                    if (imageJpeg != null) append("Analysiere das Foto meiner Mahlzeit. ")
                    if (!description.isNullOrBlank()) {
                        append("Meine Beschreibung: \"")
                        append(description.trim())
                        append("\"")
                    }
                }
                add(ContentBlockParam.ofText(text))
            }

            val builder = MessageCreateParams.builder()
                .model(model)
                .maxTokens(16000L)
                .system(SYSTEM_PROMPT)
                .addUserMessageOfContentBlockParams(content)
                .outputConfig(
                    OutputConfig.builder()
                        .effort(OutputConfig.Effort.MEDIUM)
                        .format(JsonOutputFormat.builder().schema(schema()).build())
                        .build()
                )

            // Server-side fallback: if a safety classifier declines, the API
            // retries on a suitable model instead of returning a refusal.
            if (model in FALLBACK_MODELS) {
                builder.putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
                builder.putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
            }

            val response = try {
                client.messages().create(builder.build())
            } catch (e: UnauthorizedException) {
                throw AiException("Ungültiger API-Key. Bitte in den Einstellungen prüfen.", e)
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

            val stop = response.stopReason().orElse(null)
            if (stop == StopReason.REFUSAL) {
                throw AiException("Die KI konnte diese Anfrage nicht bearbeiten.")
            }
            if (stop == StopReason.MAX_TOKENS) {
                throw AiException("Antwort der KI war unvollständig – bitte erneut versuchen.")
            }

            val text = response.content()
                .mapNotNull { block -> block.text().orElse(null)?.text() }
                .joinToString("")
            if (text.isBlank()) throw AiException("Leere Antwort der KI.")

            val parsed = try {
                json.decodeFromString<WireEstimate>(text)
            } catch (e: Exception) {
                throw AiException("Antwort der KI konnte nicht gelesen werden.", e)
            }
            AiEstimate(
                items = parsed.items.map {
                    AiFoodItem(
                        name = it.name,
                        amountGrams = it.amount_grams,
                        kcal = it.kcal,
                        protein = it.protein_g,
                        carbs = it.carbs_g,
                        fat = it.fat_g,
                        confidence = it.confidence,
                    )
                },
                notes = parsed.notes,
            )
        }

    @Serializable
    private data class WireItem(
        val name: String,
        val amount_grams: Double,
        val kcal: Double,
        val protein_g: Double,
        val carbs_g: Double,
        val fat_g: Double,
        val confidence: String,
    )

    @Serializable
    private data class WireEstimate(
        val items: List<WireItem>,
        val notes: String = "",
    )

    private fun schema(): JsonOutputFormat.Schema {
        val number = mapOf("type" to "number")
        val item = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "name" to mapOf("type" to "string", "description" to "Kurzer deutscher Name des Lebensmittels"),
                "amount_grams" to mapOf("type" to "number", "description" to "Geschätzte gegessene Menge in Gramm (bzw. ml)"),
                "kcal" to number,
                "protein_g" to number,
                "carbs_g" to number,
                "fat_g" to number,
                "confidence" to mapOf("type" to "string", "enum" to listOf("hoch", "mittel", "niedrig")),
            ),
            "required" to listOf("name", "amount_grams", "kcal", "protein_g", "carbs_g", "fat_g", "confidence"),
            "additionalProperties" to false,
        )
        return JsonOutputFormat.Schema.builder()
            .putAdditionalProperty("type", JsonValue.from("object"))
            .putAdditionalProperty(
                "properties",
                JsonValue.from(
                    mapOf(
                        "items" to mapOf("type" to "array", "items" to item),
                        "notes" to mapOf("type" to "string", "description" to "Kurzer Hinweis zur Schätzung, max. 2 Sätze"),
                    )
                )
            )
            .putAdditionalProperty("required", JsonValue.from(listOf("items", "notes")))
            .putAdditionalProperty("additionalProperties", JsonValue.from(false))
            .build()
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

        private val SYSTEM_PROMPT = """
            Du bist ein Ernährungsexperte in einer Kalorien-Tracking-App.
            Der Nutzer zeigt dir ein Foto seiner Mahlzeit und/oder beschreibt, was er gegessen hat.
            Zerlege die Mahlzeit in einzelne Lebensmittel und schätze für jedes die tatsächlich
            gegessene Menge in Gramm sowie Kalorien, Eiweiß, Kohlenhydrate und Fett für genau diese Menge.

            - Übernimm Mengen aus der Beschreibung, wenn angegeben ("2 Scheiben", "300 g"), sonst schätze
              anhand von Tellergröße, Besteck und typischen Portionen in Deutschland.
            - Denke an versteckte Kalorien wie Öl, Butter, Soßen, Dressing und Zucker in Getränken
              und führe sie als eigene Position auf, wenn sie relevant sind.
            - Verwende Nährwerte gängiger deutscher Nährwerttabellen (z. B. BLS) bzw. typischer Produkte.
            - Setze confidence auf "niedrig", wenn die Menge oder Zubereitung schwer erkennbar ist.
            - Wenn auf dem Bild kein Essen zu sehen ist, gib eine leere items-Liste zurück und erkläre es in notes.
            Antworte ausschließlich auf Deutsch.
        """.trimIndent()
    }
}
