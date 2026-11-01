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

    @Query("SELECT COALESCE(SUM(kcal), 0) FROM diary_entries WHERE date = :date")
    suspend fun kcalFor(date: String): Double

    @Query("SELECT COUNT(DISTINCT date) FROM diary_entries")
    fun loggedDays(): Flow<Int>

    @Insert
    suspend fun insert(entry: DiaryEntry): Long

    @Insert
    suspend fun insertAll(entries: List<DiaryEntry>)

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

    @Query("SELECT * FROM weight ORDER BY date")
    fun weights(): Flow<List<WeightEntry>>

    @Upsert
    suspend fun upsertWeight(entry: WeightEntry)

    @Delete
    suspend fun deleteWeight(entry: WeightEntry)
}
