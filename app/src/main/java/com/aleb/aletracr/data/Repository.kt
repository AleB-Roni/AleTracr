package com.aleb.aletracr.data

import com.aleb.aletracr.remote.AiFoodItem
import com.aleb.aletracr.remote.ClaudeNutritionClient
import com.aleb.aletracr.remote.FoodProduct
import com.aleb.aletracr.remote.OpenFoodFactsClient
import kotlinx.coroutines.flow.first

/** Central place for everything the screens need. */
class Repository(
    private val db: AppDatabase,
    val settingsRepo: SettingsRepository,
    private val off: OpenFoodFactsClient = OpenFoodFactsClient(),
) {
    val diary = db.diaryDao()
    val foods = db.foodDao()
    val body = db.bodyDao()

    private var cachedAi: Pair<String, ClaudeNutritionClient>? = null

    // --- Food database -------------------------------------------------------

    suspend fun searchOnline(query: String): List<FoodProduct> = off.search(query)

    /** Barcode lookup: local cache first, then Open Food Facts. */
    suspend fun lookupBarcode(barcode: String): FoodProduct? {
        foods.byBarcode(barcode)?.let { return it.toProduct() }
        return off.byBarcode(barcode)
    }

    suspend fun searchLocal(query: String): List<FoodProduct> =
        foods.searchLocal(query).map { it.toProduct() }

    /** Log a portion of a product and remember the product for "Zuletzt". */
    suspend fun logProduct(date: String, meal: Meal, product: FoodProduct, grams: Double, source: EntrySource) {
        val food = rememberFood(product)
        val f = grams / 100.0
        diary.insert(
            DiaryEntry(
                date = date, meal = meal, name = product.name, brand = product.brand,
                amountGrams = grams,
                kcal = product.kcalPer100 * f, protein = product.proteinPer100 * f,
                carbs = product.carbsPer100 * f, fat = product.fatPer100 * f,
                source = source, foodId = food.id,
            )
        )
    }

    private suspend fun rememberFood(p: FoodProduct): FoodItem {
        val existing = p.barcode?.let { foods.byBarcode(it) } ?: foods.byNameAndBrand(p.name, p.brand)
        val now = System.currentTimeMillis()
        return if (existing != null) {
            val updated = existing.copy(useCount = existing.useCount + 1, lastUsed = now)
            foods.update(updated)
            updated
        } else {
            val item = FoodItem(
                name = p.name, brand = p.brand, barcode = p.barcode,
                kcalPer100 = p.kcalPer100, proteinPer100 = p.proteinPer100,
                carbsPer100 = p.carbsPer100, fatPer100 = p.fatPer100,
                servingGrams = p.servingGrams, servingLabel = p.servingLabel,
                imageUrl = p.imageUrl, useCount = 1, lastUsed = now,
            )
            item.copy(id = foods.insert(item))
        }
    }


    suspend fun toggleFavorite(product: FoodProduct) {
        val existing = product.barcode?.let { foods.byBarcode(it) }
            ?: foods.byNameAndBrand(product.name, product.brand)
        if (existing != null) {
            foods.update(existing.copy(isFavorite = !existing.isFavorite))
        } else {
            foods.insert(
                FoodItem(
                    name = product.name, brand = product.brand, barcode = product.barcode,
                    kcalPer100 = product.kcalPer100, proteinPer100 = product.proteinPer100,
                    carbsPer100 = product.carbsPer100, fatPer100 = product.fatPer100,
                    servingGrams = product.servingGrams, servingLabel = product.servingLabel,
                    imageUrl = product.imageUrl, isFavorite = true,
                )
            )
        }
    }

    // --- Diary ----------------------------------------------------------------

    suspend fun quickAdd(
        date: String, meal: Meal, name: String, kcal: Double,
        protein: Double, carbs: Double, fat: Double, note: String?,
    ) {
        diary.insert(
            DiaryEntry(
                date = date, meal = meal, name = name.ifBlank { "Schnelleintrag" },
                kcal = kcal, protein = protein, carbs = carbs, fat = fat,
                source = EntrySource.QUICK, note = note?.takeIf(String::isNotBlank),
            )
        )
    }

    suspend fun logAiItems(date: String, meal: Meal, items: List<AiFoodItem>, fromPhoto: Boolean) {
        diary.insertAll(items.map {
            DiaryEntry(
                date = date, meal = meal, name = it.name, amountGrams = it.amountGrams,
                kcal = it.kcal, protein = it.protein, carbs = it.carbs, fat = it.fat,
                source = if (fromPhoto) EntrySource.AI_PHOTO else EntrySource.AI_TEXT,
            )
        })
    }

    /** Copies all entries of a meal from one day to another (e.g. "wie gestern"). */
    suspend fun copyMeal(fromDate: String, toDate: String, meal: Meal): Int {
        val src = diary.entriesFor(fromDate, meal)
        diary.insertAll(src.map {
            it.copy(id = 0, date = toDate, source = EntrySource.COPY, createdAt = System.currentTimeMillis())
        })
        return src.size
    }

    // --- AI ------------------------------------------------------------------

    suspend fun aiClient(): ClaudeNutritionClient? {
        val s = settingsRepo.settings.first()
        if (s.apiKey.isBlank()) return null
        val cacheKey = s.apiKey + "|" + s.model
        cachedAi?.let { if (it.first == cacheKey) return it.second }
        return ClaudeNutritionClient(s.apiKey, s.model).also { cachedAi = cacheKey to it }
    }
}

fun FoodItem.toProduct() = FoodProduct(
    name = name, brand = brand, barcode = barcode,
    kcalPer100 = kcalPer100, proteinPer100 = proteinPer100,
    carbsPer100 = carbsPer100, fatPer100 = fatPer100,
    servingGrams = servingGrams, servingLabel = servingLabel, imageUrl = imageUrl,
)
