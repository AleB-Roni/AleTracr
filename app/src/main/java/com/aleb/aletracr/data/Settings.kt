package com.aleb.aletracr.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aleb.aletracr.remote.ClaudeNutritionClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class UserSettings(
    val kcalGoal: Int = 2000,
    /** Macro split in percent of calories */
    val proteinPct: Int = 30,
    val carbsPct: Int = 40,
    val fatPct: Int = 30,
    val waterGoalMl: Int = 2500,
    val apiKey: String = "",
    val model: String = ClaudeNutritionClient.DEFAULT_MODEL,
    /** Persistent "kcal left" notification */
    val notificationEnabled: Boolean = true,
) {
    val proteinGoalG get() = kcalGoal * proteinPct / 100.0 / 4.0
    val carbsGoalG get() = kcalGoal * carbsPct / 100.0 / 4.0
    val fatGoalG get() = kcalGoal * fatPct / 100.0 / 9.0
}

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private object Keys {
        val kcal = intPreferencesKey("kcal_goal")
        val protein = intPreferencesKey("protein_pct")
        val carbs = intPreferencesKey("carbs_pct")
        val fat = intPreferencesKey("fat_pct")
        val water = intPreferencesKey("water_goal")
        val apiKey = stringPreferencesKey("anthropic_api_key")
        val model = stringPreferencesKey("model")
        val notification = booleanPreferencesKey("notification_enabled")
    }

    val settings: Flow<UserSettings> = context.dataStore.data.map { p: Preferences ->
        val d = UserSettings()
        UserSettings(
            kcalGoal = p[Keys.kcal] ?: d.kcalGoal,
            proteinPct = p[Keys.protein] ?: d.proteinPct,
            carbsPct = p[Keys.carbs] ?: d.carbsPct,
            fatPct = p[Keys.fat] ?: d.fatPct,
            waterGoalMl = p[Keys.water] ?: d.waterGoalMl,
            apiKey = p[Keys.apiKey] ?: d.apiKey,
            model = p[Keys.model] ?: d.model,
            notificationEnabled = p[Keys.notification] ?: d.notificationEnabled,
        )
    }

    suspend fun save(s: UserSettings) {
        context.dataStore.edit { p ->
            p[Keys.kcal] = s.kcalGoal
            p[Keys.protein] = s.proteinPct
            p[Keys.carbs] = s.carbsPct
            p[Keys.fat] = s.fatPct
            p[Keys.water] = s.waterGoalMl
            p[Keys.apiKey] = s.apiKey.trim()
            p[Keys.model] = s.model
            p[Keys.notification] = s.notificationEnabled
        }
    }

    suspend fun setNotificationEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.notification] = enabled }
    }
}
