package com.aleb.aletracr.data

import com.aleb.aletracr.food.CommonFood
import com.aleb.aletracr.food.CommonFoods
import com.aleb.aletracr.food.FoodMatch
import com.aleb.aletracr.food.FoodUnit
import com.aleb.aletracr.food.ParsedFood
import com.aleb.aletracr.food.SpeechFoodParser
import com.aleb.aletracr.remote.AiFoodItem
import com.aleb.aletracr.remote.AiProvider
import com.aleb.aletracr.remote.ClaudeNutritionClient
import com.aleb.aletracr.remote.FoodProduct
import com.aleb.aletracr.remote.GeminiNutritionClient
import com.aleb.aletracr.remote.NutritionAi
import com.aleb.aletracr.remote.OpenFoodFactsClient
import androidx.room.withTransaction
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate
import kotlin.math.roundToInt

/** Everything that goes into a backup file. */
@Serializable
data class Backup(
    val version: Int = 1,
    val diary: List<DiaryEntry>,
    val foods: List<FoodItem>,
    val water: List<WaterEntry>,
    val weight: List<WeightEntry>,
    val recipes: List<Recipe>,
    val recipeItems: List<RecipeItem>,
)

/** Central place for everything the screens need. */
class Repository(
    private val db: AppDatabase,
    val settingsRepo: SettingsRepository,
    private val off: OpenFoodFactsClient = OpenFoodFactsClient(),
) {
    val diary = db.diaryDao()
    val foods = db.foodDao()
    val body = db.bodyDao()
    val recipes = db.recipeDao()

    private var cachedAi: Pair<String, NutritionAi>? = null
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    // --- Food database -------------------------------------------------------

    suspend fun searchOnline(query: String): List<FoodProduct> = off.search(query)

    /** Barcode lookup: local cache first, then Open Food Facts. */
    suspend fun lookupBarcode(barcode: String): FoodProduct? {
        foods.byBarcode(barcode)?.let { return it.toProduct() }
        return off.byBarcode(barcode)
    }

    /** Own foods first, then matching entries from the built-in table (offline, instant). */
    suspend fun searchLocal(query: String): List<FoodProduct> {
        val own = foods.searchLocal(query).map { it.toProduct() }
        val q = SpeechFoodParser.norm(query)
        val builtIn = CommonFoods.all.filter { f ->
            (listOf(f.name) + f.aliases).any { a ->
                val n = SpeechFoodParser.norm(a)
                n.startsWith(q) || " $n ".contains(" $q") || (q.length >= 4 && q.contains(n))
            }
        }.filterNot { it.isWater }.map { it.toProduct() }
        return (own + builtIn).distinctBy { it.name.lowercase() + it.brand }
    }

    /** Log a portion of a product and remember the product for "Zuletzt". */
    suspend fun logProduct(date: String, meal: Meal, product: FoodProduct, grams: Double, source: EntrySource) {
        val food = rememberFood(product, custom = source == EntrySource.AI_LABEL)
        val f = grams / 100.0
        diary.insert(
            DiaryEntry(
                date = date, meal = meal, name = product.name, brand = product.brand,
                amountGrams = grams,
                kcal = product.kcalPer100 * f, protein = product.proteinPer100 * f,
                carbs = product.carbsPer100 * f, fat = product.fatPer100 * f,
                sugar = (product.sugarPer100 ?: 0.0) * f, fiber = (product.fiberPer100 ?: 0.0) * f,
                source = source, foodId = food.id,
            )
        )
    }

    private suspend fun rememberFood(p: FoodProduct, custom: Boolean = false): FoodItem {
        val existing = p.barcode?.let { foods.byBarcode(it) } ?: foods.byNameAndBrand(p.name, p.brand)
        val now = System.currentTimeMillis()
        return if (existing != null) {
            val updated = existing.copy(useCount = existing.useCount + 1, lastUsed = now)
            foods.update(updated)
            updated
        } else {
            val item = p.toFoodItem().copy(useCount = 1, lastUsed = now, isCustom = custom)
            item.copy(id = foods.insert(item))
        }
    }

    suspend fun toggleFavorite(product: FoodProduct) {
        val existing = product.barcode?.let { foods.byBarcode(it) }
            ?: foods.byNameAndBrand(product.name, product.brand)
        if (existing != null) {
            foods.update(existing.copy(isFavorite = !existing.isFavorite))
        } else {
            foods.insert(product.toFoodItem().copy(isFavorite = true))
        }
    }

    suspend fun isFavorite(product: FoodProduct): Boolean {
        val existing = product.barcode?.let { foods.byBarcode(it) }
            ?: foods.byNameAndBrand(product.name, product.brand)
        return existing?.isFavorite == true
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

    /** Returns the ids of the new entries (for "Rückgängig"). */
    suspend fun logAiItems(date: String, meal: Meal, items: List<AiFoodItem>, fromPhoto: Boolean): List<Long> =
        diary.insertAll(items.map {
            DiaryEntry(
                date = date, meal = meal, name = it.name, amountGrams = it.amountGrams,
                kcal = it.kcal, protein = it.protein, carbs = it.carbs, fat = it.fat,
                sugar = it.sugar, fiber = it.fiber,
                source = if (fromPhoto) EntrySource.AI_PHOTO else EntrySource.AI_TEXT,
            )
        })

    /** Copies all entries of a meal from one day to another (e.g. "wie gestern"). */
    suspend fun copyMeal(fromDate: String, toDate: String, meal: Meal): Int {
        val src = diary.entriesFor(fromDate, meal)
        diary.insertAll(src.map {
            it.copy(id = 0, date = toDate, source = EntrySource.COPY, createdAt = System.currentTimeMillis())
        })
        return src.size
    }

    // --- Spracheingabe ohne KI -------------------------------------------------

    /** Parser that knows the user's own foods (history first) plus the built-in table. */
    suspend fun speechParser(): SpeechFoodParser {
        val own = foods.all().map { it to SpeechFoodParser.norm(it.name) }
        return SpeechFoodParser(history = { phrase ->
            val p = SpeechFoodParser.norm(phrase)
            own.filter { (_, n) -> n == p || n.startsWith("$p ") || " $n ".contains(" $p ") }
                .maxByOrNull { (f, n) -> (if (n == p) 1000 else 0) + f.useCount }
                ?.first?.let { f ->
                    FoodMatch(
                        name = f.name, brand = f.brand, kcalPer100 = f.kcalPer100, proteinPer100 = f.proteinPer100,
                        carbsPer100 = f.carbsPer100, fatPer100 = f.fatPer100, sugarPer100 = f.sugarPer100 ?: 0.0,
                        fiberPer100 = f.fiberPer100 ?: 0.0,
                        unitGrams = buildMap {
                            f.servingGrams?.let { put(FoodUnit.PORTION, it); put(FoodUnit.PIECE, it) }
                        },
                        defaultUnit = if (f.servingGrams != null) FoodUnit.PORTION else FoodUnit.GRAM,
                        foodId = f.id,
                    )
                }
        })
    }

    /** Saves parsed speech items; water goes to the water tracker. Returns (diary ids, water ids). */
    suspend fun logParsed(date: String, meal: Meal, items: List<ParsedFood>): Pair<List<Long>, List<Long>> {
        val waterIds = items.filter { it.match.isWater }.map { body.addWater(WaterEntry(date = date, ml = it.grams.toInt())) }
        val diaryIds = diary.insertAll(items.filterNot { it.match.isWater }.map {
            DiaryEntry(
                date = date, meal = meal, name = it.match.name, brand = it.match.brand, amountGrams = it.grams,
                kcal = it.kcal, protein = it.protein, carbs = it.carbs, fat = it.fat, sugar = it.sugar, fiber = it.fiber,
                source = EntrySource.VOICE, foodId = it.match.foodId,
            )
        })
        return diaryIds to waterIds
    }

    // --- Recipes ----------------------------------------------------------------

    /** Saves the entries of a meal as a reusable recipe. Returns the number of items. */
    suspend fun saveMealAsRecipe(date: String, meal: Meal, name: String): Int {
        val entries = diary.entriesFor(date, meal)
        if (entries.isEmpty()) return 0
        db.withTransaction {
            val id = recipes.insert(Recipe(name = name.trim()))
            recipes.insertItems(entries.map {
                RecipeItem(
                    recipeId = id, name = it.name, brand = it.brand, amountGrams = it.amountGrams,
                    kcal = it.kcal, protein = it.protein, carbs = it.carbs, fat = it.fat,
                    sugar = it.sugar, fiber = it.fiber,
                )
            })
        }
        return entries.size
    }

    suspend fun logRecipe(date: String, meal: Meal, recipeId: Long, portion: Double = 1.0) {
        diary.insertAll(recipes.items(recipeId).map {
            DiaryEntry(
                date = date, meal = meal, name = it.name, brand = it.brand,
                amountGrams = it.amountGrams?.times(portion),
                kcal = it.kcal * portion, protein = it.protein * portion, carbs = it.carbs * portion,
                fat = it.fat * portion, sugar = it.sugar * portion, fiber = it.fiber * portion,
                source = EntrySource.RECIPE,
            )
        })
    }

    /** Creates (id = 0) or replaces a dish with the given ingredients. Returns its id. */
    suspend fun saveDish(id: Long, name: String, items: List<RecipeItem>): Long = db.withTransaction {
        val dishId = if (id == 0L) {
            recipes.insert(Recipe(name = name.trim()))
        } else {
            recipes.rename(id, name.trim())
            recipes.deleteItems(id)
            id
        }
        recipes.insertItems(items.map { it.copy(id = 0, recipeId = dishId) })
        dishId
    }

    suspend fun deleteRecipe(id: Long) = db.withTransaction {
        recipes.deleteItems(id)
        recipes.deleteRecipe(id)
    }

    // --- AI ------------------------------------------------------------------

    suspend fun aiClient(): NutritionAi? {
        val s = settingsRepo.settings.first()
        val key = s.activeKey
        if (key.isBlank()) return null
        val model = if (s.aiProvider == AiProvider.GEMINI) s.geminiModel else s.claudeModel
        val cacheKey = "${s.aiProvider}|$key|$model"
        cachedAi?.let { if (it.first == cacheKey) return it.second }
        val client: NutritionAi = when (s.aiProvider) {
            AiProvider.GEMINI -> GeminiNutritionClient(key, model)
            AiProvider.CLAUDE -> ClaudeNutritionClient(key, model)
        }
        cachedAi = cacheKey to client
        return client
    }

    /** Text summary of the last 7 days for the AI coach. */
    suspend fun coachSummary(): String {
        val s = settingsRepo.settings.first()
        val today = LocalDate.now()
        val from = today.minusDays(6)
        val entries = diary.between(from.toString(), today.toString())
        val byDay = entries.groupBy { it.date }
        return buildString {
            appendLine("Ziele pro Tag: ${s.kcalGoal} kcal, Eiweiß ${s.proteinGoalG.roundToInt()} g, " +
                "Kohlenhydrate ${s.carbsGoalG.roundToInt()} g, Fett ${s.fatGoalG.roundToInt()} g, " +
                "Zucker max. ${s.sugarGoalG} g, Ballaststoffe ${s.fiberGoalG} g, Wasser ${s.waterGoalMl} ml.")
            appendLine("Letzte 7 Tage:")
            for (i in 0..6) {
                val d = from.plusDays(i.toLong()).toString()
                val e = byDay[d].orEmpty()
                val water = body.waterOn(d)
                if (e.isEmpty()) {
                    appendLine("- $d: nichts eingetragen")
                } else {
                    appendLine("- $d: ${e.sumOf { it.kcal }.roundToInt()} kcal, E ${e.sumOf { it.protein }.roundToInt()} g, " +
                        "K ${e.sumOf { it.carbs }.roundToInt()} g, F ${e.sumOf { it.fat }.roundToInt()} g, " +
                        "Zucker ${e.sumOf { it.sugar }.roundToInt()} g, Ballaststoffe ${e.sumOf { it.fiber }.roundToInt()} g, Wasser $water ml")
                }
            }
            val top = entries.groupingBy { it.name }.eachCount().entries.sortedByDescending { it.value }.take(8)
            if (top.isNotEmpty()) appendLine("Häufigste Lebensmittel: " + top.joinToString { "${it.key} (${it.value}×)" })
            body.allWeights().takeLast(5).takeIf { it.isNotEmpty() }?.let { w ->
                appendLine("Gewicht: " + w.joinToString { "${it.date}: ${it.kg} kg" })
            }
        }
    }

    // --- Backup & export -------------------------------------------------------

    suspend fun exportCsv(): String {
        val sb = StringBuilder("Datum;Mahlzeit;Name;Marke;Menge (g);kcal;Eiweiß (g);Kohlenhydrate (g);Fett (g);Zucker (g);Ballaststoffe (g);Notiz\n")
        fun esc(v: String?) = "\"" + (v ?: "").replace("\"", "\"\"") + "\""
        fun num(v: Double?) = v?.let { "%.1f".format(java.util.Locale.GERMAN, it) } ?: ""
        diary.all().forEach {
            sb.append(listOf(
                it.date, it.meal.label, esc(it.name), esc(it.brand), num(it.amountGrams), num(it.kcal),
                num(it.protein), num(it.carbs), num(it.fat), num(it.sugar), num(it.fiber), esc(it.note),
            ).joinToString(";")).append('\n')
        }
        return sb.toString()
    }

    suspend fun exportBackup(): String = json.encodeToString(
        Backup(
            diary = diary.all(), foods = foods.all(), water = body.allWater(), weight = body.allWeights(),
            recipes = recipes.allRecipes(), recipeItems = recipes.allItems(),
        )
    )

    /** Replaces all data with the backup. Returns the number of diary entries restored. */
    suspend fun restoreBackup(text: String): Int {
        val b = json.decodeFromString<Backup>(text)
        val dao = db.backupDao()
        db.withTransaction {
            dao.clearDiary(); dao.clearFoods(); dao.clearWater(); dao.clearWeight()
            dao.clearRecipeItems(); dao.clearRecipes()
            dao.insertFoods(b.foods); dao.insertDiary(b.diary); dao.insertWater(b.water)
            dao.insertWeight(b.weight); dao.insertRecipes(b.recipes); dao.insertRecipeItems(b.recipeItems)
        }
        return b.diary.size
    }
}

fun FoodItem.toProduct() = FoodProduct(
    name = name, brand = brand, barcode = barcode,
    kcalPer100 = kcalPer100, proteinPer100 = proteinPer100,
    carbsPer100 = carbsPer100, fatPer100 = fatPer100,
    sugarPer100 = sugarPer100, fiberPer100 = fiberPer100,
    servingGrams = servingGrams, servingLabel = servingLabel, imageUrl = imageUrl,
)

fun FoodProduct.toFoodItem() = FoodItem(
    name = name, brand = brand, barcode = barcode,
    kcalPer100 = kcalPer100, proteinPer100 = proteinPer100,
    carbsPer100 = carbsPer100, fatPer100 = fatPer100,
    sugarPer100 = sugarPer100, fiberPer100 = fiberPer100,
    servingGrams = servingGrams, servingLabel = servingLabel, imageUrl = imageUrl,
)

/** Built-in food as a product (values per 100 g, typical piece/slice as serving). */
fun CommonFood.toProduct(): FoodProduct {
    val serving = units[FoodUnit.PIECE] ?: units[FoodUnit.SLICE] ?: units[defaultUnit]
    val servingLabel = when {
        units[FoodUnit.PIECE] != null -> "1 Stück"
        units[FoodUnit.SLICE] != null -> "1 Scheibe"
        serving != null -> "1 " + defaultUnit.words.first().replaceFirstChar { it.uppercase() }
        else -> null
    }
    return FoodProduct(
        name = name, brand = "Standardwert", barcode = null,
        kcalPer100 = kcal, proteinPer100 = protein, carbsPer100 = carbs, fatPer100 = fat,
        sugarPer100 = sugar, fiberPer100 = fiber,
        servingGrams = serving, servingLabel = servingLabel,
    )
}
