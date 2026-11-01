package com.aleb.aletracr.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.Base64
import java.util.concurrent.TimeUnit

/**
 * Google Gemini implementation via the Gemini Developer API (REST).
 * A key from Google AI Studio (aistudio.google.com) can be used for free
 * within the free-tier limits – no credit card needed.
 */
class GeminiNutritionClient(
    private val apiKey: String,
    private val model: String = DEFAULT_MODEL,
    private val http: OkHttpClient = defaultHttp,
) : NutritionAi {

    override suspend fun estimateMeal(description: String?, imageJpeg: ByteArray?): AiEstimate {
        require(!description.isNullOrBlank() || imageJpeg != null) { "Beschreibung oder Foto erforderlich" }
        val text = generate(
            AiPrompts.MEAL_SYSTEM, AiPrompts.mealUserText(description, imageJpeg != null), imageJpeg, AiPrompts.MEAL_SCHEMA,
        )
        return AiPrompts.parseEstimate(text)
    }

    override suspend fun readLabel(imageJpeg: ByteArray): FoodProduct =
        AiPrompts.parseLabel(generate(AiPrompts.LABEL_SYSTEM, "Lies diese Nährwerttabelle ab.", imageJpeg, AiPrompts.LABEL_SCHEMA))

    override suspend fun coach(summary: String): String = generate(AiPrompts.COACH_SYSTEM, summary, null, null)

    private suspend fun generate(
        system: String,
        userText: String,
        imageJpeg: ByteArray?,
        schema: Map<String, Any>?,
    ): String = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            putJsonObject("systemInstruction") {
                putJsonArray("parts") { add(buildJsonObject { put("text", system) }) }
            }
            putJsonArray("contents") {
                add(buildJsonObject {
                    put("role", "user")
                    putJsonArray("parts") {
                        if (imageJpeg != null) {
                            add(buildJsonObject {
                                putJsonObject("inlineData") {
                                    put("mimeType", "image/jpeg")
                                    put("data", Base64.getEncoder().encodeToString(imageJpeg))
                                }
                            })
                        }
                        add(buildJsonObject { put("text", userText) })
                    }
                })
            }
            if (schema != null) {
                putJsonObject("generationConfig") {
                    put("responseMimeType", "application/json")
                    put("responseSchema", toGeminiSchema(schema))
                }
            }
        }

        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
            .header("x-goog-api-key", apiKey)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val raw = try {
            http.newCall(request).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) throw httpError(resp.code, text)
                text
            }
        } catch (e: IOException) {
            throw AiException("Keine Verbindung zur KI. Internet prüfen.", e)
        }

        val root = AiPrompts.json.parseToJsonElement(raw).jsonObject
        val candidate = (root["candidates"] as? JsonArray)?.firstOrNull()?.jsonObject
        if (candidate == null) {
            val reason = (root["promptFeedback"] as? JsonObject)?.get("blockReason")?.jsonPrimitive?.contentOrNull
            throw AiException(if (reason != null) "Anfrage von Gemini blockiert ($reason)." else "Leere Antwort der KI.")
        }
        when (candidate["finishReason"]?.jsonPrimitive?.contentOrNull) {
            "MAX_TOKENS" -> throw AiException("Antwort der KI war unvollständig – bitte erneut versuchen.")
            "SAFETY", "PROHIBITED_CONTENT", "BLOCKLIST" -> throw AiException("Die KI konnte diese Anfrage nicht bearbeiten.")
        }
        val parts = (candidate["content"] as? JsonObject)?.get("parts")?.jsonArray.orEmpty()
        val text = parts.mapNotNull { p ->
            val o = p.jsonObject
            // Skip "thought" parts of thinking models.
            if ((o["thought"] as? JsonPrimitive)?.contentOrNull == "true") null
            else (o["text"] as? JsonPrimitive)?.contentOrNull
        }.joinToString("")
        if (text.isBlank()) throw AiException("Leere Antwort der KI.")
        text
    }

    private fun httpError(code: Int, body: String): AiException {
        val msg = runCatching {
            AiPrompts.json.parseToJsonElement(body).jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
        }.getOrNull()
        return when {
            code == 400 && msg?.contains("API key", ignoreCase = true) == true ->
                AiException("Ungültiger Gemini API-Key. Bitte in den Einstellungen prüfen.")
            code == 403 -> AiException("Gemini-Key hat keinen Zugriff (${msg ?: "403"}).")
            code == 404 -> AiException("Gemini-Modell \"$model\" nicht gefunden. Bitte in den Einstellungen ein anderes Modell eintragen.")
            code == 429 -> AiException("Kostenloses Gemini-Kontingent für den Moment aufgebraucht – bitte später erneut versuchen.")
            else -> AiException("Gemini-Fehler (HTTP $code): ${msg ?: "unbekannt"}")
        }
    }

    companion object {
        /** Current free-tier Flash model; editable in the settings in case Google renames it. */
        const val DEFAULT_MODEL = "gemini-3.8-flash"

        private val defaultHttp by lazy {
            OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .build()
        }

        /**
         * Gemini's responseSchema uses an OpenAPI subset: upper-case types and
         * no "additionalProperties".
         */
        internal fun toGeminiSchema(value: Any?): JsonElement = when (value) {
            is Map<*, *> -> buildJsonObject {
                value.forEach { (k, v) ->
                    val key = k.toString()
                    when (key) {
                        "additionalProperties" -> Unit
                        "type" -> put("type", v.toString().uppercase())
                        else -> put(key, toGeminiSchema(v))
                    }
                }
                if (value["type"] == "object") {
                    (value["properties"] as? Map<*, *>)?.keys?.let { keys ->
                        putJsonArray("propertyOrdering") { keys.forEach { add(JsonPrimitive(it.toString())) } }
                    }
                }
            }
            is List<*> -> buildJsonArray { value.forEach { add(toGeminiSchema(it)) } }
            is String -> JsonPrimitive(value)
            is Number -> JsonPrimitive(value)
            is Boolean -> JsonPrimitive(value)
            null -> JsonNull
            else -> JsonPrimitive(value.toString())
        }
    }
}
