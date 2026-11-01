package com.aleb.aletracr.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface DiaryDao {
    @Query("SELECT * FROM diary_entries WHERE date = :date ORDER BY createdAt")
    fun entriesFor(date: String): Flow<List<DiaryEntry>>

    @Query("SELECT * FROM diary_entries WHERE date = :date AND meal = :meal ORDER BY createdAt")
    suspend fun entriesFor(date: String, meal: Meal): List<DiaryEntry>

    @Query(
        """SELECT date, SUM(kcal) AS kcal, SUM(protein) AS protein, SUM(carbs) AS carbs, SUM(fat) AS fat
           FROM diary_entries WHERE date BETWEEN :from AND :to GROUP BY date ORDER BY date"""
    )
    fun totalsBetween(from: String, to: String): Flow<List<DayTotal>>

    @Query("SELECT * FROM diary_entries ORDER BY date, createdAt")
    suspend fun all(): List<DiaryEntry>

    @Query("SELECT * FROM diary_entries WHERE date BETWEEN :from AND :to")
    suspend fun between(from: String, to: String): List<DiaryEntry>

    @Query("SELECT COUNT(*) FROM diary_entries WHERE date = :date AND meal = :meal")
    suspend fun countFor(date: String, meal: Meal): Int

    @Query("SELECT COUNT(DISTINCT date) FROM diary_entries")
    fun loggedDays(): Flow<Int>

    @Insert
    suspend fun insert(entry: DiaryEntry): Long

    @Insert
    suspend fun insertAll(entries: List<DiaryEntry>): List<Long>

    @Query("DELETE FROM diary_entries WHERE id IN (:ids)")
    suspend fun deleteIds(ids: List<Long>)

    @Update
    suspend fun update(entry: DiaryEntry)

    @Delete
    suspend fun delete(entry: DiaryEntry)
}

@Dao
interface FoodDao {
    @Query("SELECT * FROM foods WHERE useCount > 0 ORDER BY lastUsed DESC LIMIT :limit")
    fun recent(limit: Int = 50): Flow<List<FoodItem>>

    @Query("SELECT * FROM foods WHERE isFavorite = 1 ORDER BY name")
    fun favorites(): Flow<List<FoodItem>>


    @Query("SELECT * FROM foods WHERE name LIKE '%' || :q || '%' OR brand LIKE '%' || :q || '%' ORDER BY useCount DESC LIMIT 20")
    suspend fun searchLocal(q: String): List<FoodItem>

    @Query("SELECT * FROM foods WHERE barcode = :barcode LIMIT 1")
    suspend fun byBarcode(barcode: String): FoodItem?

    @Query("SELECT * FROM foods WHERE name = :name AND ((brand IS NULL AND :brand IS NULL) OR brand = :brand) LIMIT 1")
    suspend fun byNameAndBrand(name: String, brand: String?): FoodItem?

    @Query("SELECT * FROM foods")
    suspend fun all(): List<FoodItem>

    @Query("SELECT * FROM foods WHERE id = :id")
    suspend fun byId(id: Long): FoodItem?

    @Insert
    suspend fun insert(food: FoodItem): Long

    @Update
    suspend fun update(food: FoodItem)

    @Delete
    suspend fun delete(food: FoodItem)
}

@Dao
interface BodyDao {
    @Query("SELECT COALESCE(SUM(ml), 0) FROM water WHERE date = :date")
    fun waterFor(date: String): Flow<Int>

    @Insert
    suspend fun addWater(entry: WaterEntry)

    @Query("DELETE FROM water WHERE id = (SELECT id FROM water WHERE date = :date ORDER BY id DESC LIMIT 1)")
    suspend fun undoLastWater(date: String)

    @Query("SELECT COALESCE(SUM(ml), 0) FROM water WHERE date = :date")
    suspend fun waterOn(date: String): Int

    @Query("SELECT * FROM water")
    suspend fun allWater(): List<WaterEntry>

    @Query("SELECT * FROM weight ORDER BY date")
    suspend fun allWeights(): List<WeightEntry>

    @Query("SELECT * FROM weight ORDER BY date")
    fun weights(): Flow<List<WeightEntry>>

    @Upsert
    suspend fun upsertWeight(entry: WeightEntry)

    @Delete
    suspend fun deleteWeight(entry: WeightEntry)
}

@Dao
interface RecipeDao {
    @Query(
        """SELECT r.id AS id, r.name AS name, COUNT(i.id) AS itemCount, COALESCE(SUM(i.kcal), 0) AS kcal
           FROM recipes r LEFT JOIN recipe_items i ON i.recipeId = r.id
           GROUP BY r.id ORDER BY r.name"""
    )
    fun recipes(): Flow<List<RecipeWithTotals>>

    @Query("SELECT * FROM recipe_items WHERE recipeId = :recipeId")
    suspend fun items(recipeId: Long): List<RecipeItem>

    @Query("SELECT * FROM recipes")
    suspend fun allRecipes(): List<Recipe>

    @Query("SELECT * FROM recipe_items")
    suspend fun allItems(): List<RecipeItem>

    @Insert
    suspend fun insert(recipe: Recipe): Long

    @Insert
    suspend fun insertItems(items: List<RecipeItem>)

    @Query("DELETE FROM recipe_items WHERE recipeId = :id")
    suspend fun deleteItems(id: Long)

    @Query("DELETE FROM recipes WHERE id = :id")
    suspend fun deleteRecipe(id: Long)
}

/** Used to restore a backup in one go. */
@Dao
interface BackupDao {
    @Query("DELETE FROM diary_entries") suspend fun clearDiary()
    @Query("DELETE FROM foods") suspend fun clearFoods()
    @Query("DELETE FROM water") suspend fun clearWater()
    @Query("DELETE FROM weight") suspend fun clearWeight()
    @Query("DELETE FROM recipes") suspend fun clearRecipes()
    @Query("DELETE FROM recipe_items") suspend fun clearRecipeItems()

    @Insert suspend fun insertDiary(items: List<DiaryEntry>)
    @Insert suspend fun insertFoods(items: List<FoodItem>)
    @Insert suspend fun insertWater(items: List<WaterEntry>)
    @Insert suspend fun insertWeight(items: List<WeightEntry>)
    @Insert suspend fun insertRecipes(items: List<Recipe>)
    @Insert suspend fun insertRecipeItems(items: List<RecipeItem>)
}
