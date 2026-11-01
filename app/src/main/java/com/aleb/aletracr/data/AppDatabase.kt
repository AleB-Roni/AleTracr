package com.aleb.aletracr.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        DiaryEntry::class, FoodItem::class, WaterEntry::class, WeightEntry::class,
        Recipe::class, RecipeItem::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun diaryDao(): DiaryDao
    abstract fun foodDao(): FoodDao
    abstract fun bodyDao(): BodyDao
    abstract fun recipeDao(): RecipeDao
    abstract fun backupDao(): BackupDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "aletracr.db").build()
    }
}
