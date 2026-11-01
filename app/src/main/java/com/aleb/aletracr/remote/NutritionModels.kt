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
    val confidence: String,
)

data class AiEstimate(
    val items: List<AiFoodItem>,
    val notes: String,
)

class AiException(message: String, cause: Throwable? = null) : Exception(message, cause)
