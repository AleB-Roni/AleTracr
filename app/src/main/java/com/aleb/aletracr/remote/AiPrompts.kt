package com.aleb.aletracr.remote

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Prompts, JSON schemas and parsing shared by all AI providers. */
internal object AiPrompts {
    val json = Json { ignoreUnknownKeys = true; isLenient = true }

    val MEAL_SYSTEM = """
        Du bist ein Ernährungsexperte in einer Kalorien-Tracking-App.
        Der Nutzer zeigt dir ein Foto seiner Mahlzeit und/oder beschreibt, was er gegessen hat.
        Zerlege die Mahlzeit in einzelne Lebensmittel und schätze für jedes die tatsächlich
        gegessene Menge in Gramm sowie Kalorien, Eiweiß, Kohlenhydrate, Fett, Zucker und Ballaststoffe
        für genau diese Menge.

        - Übernimm Mengen aus der Beschreibung, wenn angegeben ("2 Scheiben", "300 g"), sonst schätze
          anhand von Tellergröße, Besteck und typischen Portionen in Deutschland.
        - Denke an versteckte Kalorien wie Öl, Butter, Soßen, Dressing und Zucker in Getränken
          und führe sie als eigene Position auf, wenn sie relevant sind.
        - Verwende Nährwerte gängiger deutscher Nährwerttabellen (z. B. BLS) bzw. typischer Produkte.
        - Setze confidence auf "niedrig", wenn die Menge oder Zubereitung schwer erkennbar ist.
        - Wenn auf dem Bild kein Essen zu sehen ist, gib eine leere items-Liste zurück und erkläre es in notes.
        Antworte ausschließlich auf Deutsch.
    """.trimIndent()

    val LABEL_SYSTEM = """
        Du liest Nährwerttabellen von Lebensmittelverpackungen.
        Lies die Werte pro 100 g bzw. 100 ml ab. Falls nur Werte pro Portion angegeben sind,
        rechne sie auf 100 g um. Ist eine Portionsgröße angegeben, trage sie in serving_grams ein, sonst 0.
        Erkennst du Produktname oder Marke auf dem Bild, trage sie ein, sonst einen kurzen beschreibenden Namen
        und einen leeren String als Marke. Fehlende Werte setzt du auf 0.
    """.trimIndent()

    val COACH_SYSTEM = """
        Du bist ein freundlicher, motivierender Ernährungscoach in einer Kalorien-Tracking-App.
        Du bekommst eine Zusammenfassung der letzten Tage. Gib eine kurze Analyse auf Deutsch
        (höchstens 150 Wörter): was gut lief, 2–3 konkrete, alltagstaugliche Tipps und eine kurze Motivation.
        Keine medizinischen Diagnosen. Verwende einfache Aufzählungspunkte mit "•".
    """.trimIndent()

    private val number = mapOf("type" to "number")

    /** JSON Schema (draft subset understood by Claude and convertible for Gemini). */
    val MEAL_SCHEMA: Map<String, Any> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "items" to mapOf(
                "type" to "array",
                "items" to mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "name" to mapOf("type" to "string", "description" to "Kurzer deutscher Name des Lebensmittels"),
                        "amount_grams" to mapOf("type" to "number", "description" to "Geschätzte gegessene Menge in Gramm (bzw. ml)"),
                        "kcal" to number,
                        "protein_g" to number,
                        "carbs_g" to number,
                        "fat_g" to number,
                        "sugar_g" to number,
                        "fiber_g" to number,
                        "confidence" to mapOf("type" to "string", "enum" to listOf("hoch", "mittel", "niedrig")),
                    ),
                    "required" to listOf("name", "amount_grams", "kcal", "protein_g", "carbs_g", "fat_g", "sugar_g", "fiber_g", "confidence"),
                    "additionalProperties" to false,
                ),
            ),
            "notes" to mapOf("type" to "string", "description" to "Kurzer Hinweis zur Schätzung, max. 2 Sätze"),
        ),
        "required" to listOf("items", "notes"),
        "additionalProperties" to false,
    )

    val LABEL_SCHEMA: Map<String, Any> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "name" to mapOf("type" to "string"),
            "brand" to mapOf("type" to "string"),
            "kcal_100g" to number,
            "protein_100g" to number,
            "carbs_100g" to number,
            "fat_100g" to number,
            "sugar_100g" to number,
            "fiber_100g" to number,
            "serving_grams" to number,
        ),
        "required" to listOf("name", "brand", "kcal_100g", "protein_100g", "carbs_100g", "fat_100g", "sugar_100g", "fiber_100g", "serving_grams"),
        "additionalProperties" to false,
    )

    @Serializable
    private data class WireItem(
        val name: String,
        val amount_grams: Double,
        val kcal: Double,
        val protein_g: Double = 0.0,
        val carbs_g: Double = 0.0,
        val fat_g: Double = 0.0,
        val sugar_g: Double = 0.0,
        val fiber_g: Double = 0.0,
        val confidence: String = "mittel",
    )

    @Serializable
    private data class WireEstimate(val items: List<WireItem> = emptyList(), val notes: String = "")

    @Serializable
    private data class WireLabel(
        val name: String,
        val brand: String = "",
        val kcal_100g: Double,
        val protein_100g: Double = 0.0,
        val carbs_100g: Double = 0.0,
        val fat_100g: Double = 0.0,
        val sugar_100g: Double = 0.0,
        val fiber_100g: Double = 0.0,
        val serving_grams: Double = 0.0,
    )

    fun mealUserText(description: String?, hasImage: Boolean): String = buildString {
        if (hasImage) append("Analysiere das Foto meiner Mahlzeit. ")
        if (!description.isNullOrBlank()) {
            append("Meine Beschreibung: \"")
            append(description.trim())
            append("\"")
        }
    }

    fun parseEstimate(text: String): AiEstimate {
        val w = try {
            json.decodeFromString<WireEstimate>(stripFences(text))
        } catch (e: Exception) {
            throw AiException("Antwort der KI konnte nicht gelesen werden.", e)
        }
        return AiEstimate(
            items = w.items.map {
                AiFoodItem(
                    name = it.name, amountGrams = it.amount_grams, kcal = it.kcal,
                    protein = it.protein_g, carbs = it.carbs_g, fat = it.fat_g,
                    sugar = it.sugar_g, fiber = it.fiber_g, confidence = it.confidence,
                )
            },
            notes = w.notes,
        )
    }

    fun parseLabel(text: String): FoodProduct {
        val w = try {
            json.decodeFromString<WireLabel>(stripFences(text))
        } catch (e: Exception) {
            throw AiException("Nährwerttabelle konnte nicht gelesen werden.", e)
        }
        if (w.kcal_100g <= 0) throw AiException("Auf dem Foto wurde keine Nährwerttabelle erkannt.")
        return FoodProduct(
            name = w.name.ifBlank { "Eigenes Produkt" },
            brand = w.brand.takeIf(String::isNotBlank),
            barcode = null,
            kcalPer100 = w.kcal_100g, proteinPer100 = w.protein_100g,
            carbsPer100 = w.carbs_100g, fatPer100 = w.fat_100g,
            sugarPer100 = w.sugar_100g, fiberPer100 = w.fiber_100g,
            servingGrams = w.serving_grams.takeIf { it > 0 },
        )
    }

    /** Some models wrap JSON in ```json fences – remove them. */
    private fun stripFences(text: String): String =
        text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
}
