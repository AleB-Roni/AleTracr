package com.aleb.aletracr.remote

/** A product from a food database, nutrition values per 100 g / 100 ml. */
data class FoodProduct(
    val name: String,
    val brand: String?,
    val barcode: String?,
    val kcalPer100: Double,
    val proteinPer100: Double,
    val carbsPer100: Double,
    val fatPer100: Double,
    val sugarPer100: Double? = null,
    val fiberPer100: Double? = null,
    val saltPer100: Double? = null,
    val servingGrams: Double? = null,
    val servingLabel: String? = null,
    val imageUrl: String? = null,
)

/** One food item the AI recognised, values already scaled to the eaten amount. */
data class AiFoodItem(
    val name: String,
    val amountGrams: Double,
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val sugar: Double = 0.0,
    val fiber: Double = 0.0,
    val confidence: String,
)

data class AiEstimate(
    val items: List<AiFoodItem>,
    val notes: String,
    /** Short summary of the whole meal, e.g. "Spaghetti mit Tomatensoße und Parmesan, ca. 640 kcal". */
    val summary: String = "",
)

class AiException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Common interface so the app can switch between AI providers (Gemini = free, Claude = paid). */
interface NutritionAi {
    /** Meal photo and/or description -> list of foods with nutrition for the eaten amount. */
    suspend fun estimateMeal(description: String?, imageJpeg: ByteArray?): AiEstimate

    /** Photo of a nutrition facts table -> product with values per 100 g. */
    suspend fun readLabel(imageJpeg: ByteArray): FoodProduct

    /** Short, friendly coaching text based on a summary of the last days. */
    suspend fun coach(summary: String): String
}

enum class AiProvider(val label: String) {
    GEMINI("Google Gemini – kostenlos (Free Tier)"),
    CLAUDE("Anthropic Claude – kostenpflichtig, sehr genau"),
}
