package com.aleb.aletracr.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

enum class Meal(val label: String) {
    BREAKFAST("Frühstück"),
    LUNCH("Mittagessen"),
    DINNER("Abendessen"),
    SNACK("Snacks");

    companion object {
        /** Guesses the meal from the time of day (used for quick-add from the notification). */
        fun forTime(time: java.time.LocalTime = java.time.LocalTime.now()): Meal = when {
            time.hour < 4 -> SNACK
            time < java.time.LocalTime.of(10, 30) -> BREAKFAST
            time < java.time.LocalTime.of(14, 30) -> LUNCH
            time < java.time.LocalTime.of(17, 30) -> SNACK
            time < java.time.LocalTime.of(21, 30) -> DINNER
            else -> SNACK
        }
    }
}

enum class EntrySource { SEARCH, BARCODE, AI_PHOTO, AI_TEXT, AI_LABEL, QUICK, CUSTOM, COPY, RECIPE, VOICE }

/** One logged food in the diary. Nutrition values are for the logged amount. */
@Serializable
@Entity(tableName = "diary_entries", indices = [Index("date")])
data class DiaryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** ISO date, e.g. 2026-11-01 */
    val date: String,
    val meal: Meal,
    val name: String,
    val brand: String? = null,
    /** null for quick-add entries without a weight */
    val amountGrams: Double? = null,
    val kcal: Double,
    val protein: Double = 0.0,
    val carbs: Double = 0.0,
    val fat: Double = 0.0,
    val sugar: Double = 0.0,
    val fiber: Double = 0.0,
    val source: EntrySource,
    val foodId: Long? = null,
    val note: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

/** A food the user has used before (from search/barcode/AI) or created. Values per 100 g. */
@Serializable
@Entity(tableName = "foods", indices = [Index("barcode")])
data class FoodItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val brand: String? = null,
    val barcode: String? = null,
    val kcalPer100: Double,
    val proteinPer100: Double,
    val carbsPer100: Double,
    val fatPer100: Double,
    val sugarPer100: Double? = null,
    val fiberPer100: Double? = null,
    val servingGrams: Double? = null,
    val servingLabel: String? = null,
    val imageUrl: String? = null,
    val isFavorite: Boolean = false,
    val isCustom: Boolean = false,
    val useCount: Int = 0,
    val lastUsed: Long = 0,
)

@Serializable
@Entity(tableName = "water", indices = [Index("date")])
data class WaterEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val ml: Int,
)

@Serializable
@Entity(tableName = "weight")
data class WeightEntry(
    @PrimaryKey val date: String,
    val kg: Double,
)

/** A saved combination of foods, e.g. "Mein Frühstücksmüsli". */
@Serializable
@Entity(tableName = "recipes")
data class Recipe(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
@Entity(tableName = "recipe_items", indices = [Index("recipeId")])
data class RecipeItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recipeId: Long,
    val name: String,
    val brand: String? = null,
    val amountGrams: Double? = null,
    val kcal: Double,
    val protein: Double = 0.0,
    val carbs: Double = 0.0,
    val fat: Double = 0.0,
    val sugar: Double = 0.0,
    val fiber: Double = 0.0,
)

data class RecipeWithTotals(
    val id: Long,
    val name: String,
    val itemCount: Int,
    val kcal: Double,
)

/** Aggregated totals for a day, used by the stats screen. */
data class DayTotal(
    val date: String,
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
)
