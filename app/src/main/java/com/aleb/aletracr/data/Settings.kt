package com.aleb.aletracr.data

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aleb.aletracr.remote.AiProvider
import com.aleb.aletracr.remote.ClaudeNutritionClient
import com.aleb.aletracr.remote.GeminiNutritionClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** A daily reminder at a fixed time (minutes since midnight). */
data class Reminder(val enabled: Boolean, val minuteOfDay: Int) {
    val hour get() = minuteOfDay / 60
    val minute get() = minuteOfDay % 60
    val label get() = "%02d:%02d".format(hour, minute)
}

data class UserSettings(
    val kcalGoal: Int = 2000,
    /** Macro split in percent of calories */
    val proteinPct: Int = 30,
    val carbsPct: Int = 40,
    val fatPct: Int = 30,
    val sugarGoalG: Int = 50,
    val fiberGoalG: Int = 30,
    val waterGoalMl: Int = 2500,

    val aiProvider: AiProvider = AiProvider.GEMINI,
    val geminiKey: String = "",
    val geminiModel: String = GeminiNutritionClient.DEFAULT_MODEL,
    val claudeKey: String = "",
    val claudeModel: String = ClaudeNutritionClient.DEFAULT_MODEL,

    /** Persistent "kcal left" notification */
    val notificationEnabled: Boolean = true,
    val breakfastReminder: Reminder = Reminder(true, 9 * 60 + 30),
    val lunchReminder: Reminder = Reminder(true, 13 * 60 + 30),
    val dinnerReminder: Reminder = Reminder(true, 19 * 60 + 30),
    val waterReminder: Boolean = false,

    /** Interval fasting: start time in epoch millis, 0 = not fasting */
    val fastingStart: Long = 0,
    val fastingHours: Int = 16,

    /** Add active calories from Health Connect to the daily budget */
    val healthConnect: Boolean = false,
) {
    val proteinGoalG get() = kcalGoal * proteinPct / 100.0 / 4.0
    val carbsGoalG get() = kcalGoal * carbsPct / 100.0 / 4.0
    val fatGoalG get() = kcalGoal * fatPct / 100.0 / 9.0
    val activeKey get() = if (aiProvider == AiProvider.GEMINI) geminiKey else claudeKey
}

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private object Keys {
        val kcal = intPreferencesKey("kcal_goal")
        val protein = intPreferencesKey("protein_pct")
        val carbs = intPreferencesKey("carbs_pct")
        val fat = intPreferencesKey("fat_pct")
        val sugar = intPreferencesKey("sugar_goal")
        val fiber = intPreferencesKey("fiber_goal")
        val water = intPreferencesKey("water_goal")
        val provider = stringPreferencesKey("ai_provider")
        val geminiKey = stringPreferencesKey("gemini_api_key")
        val geminiModel = stringPreferencesKey("gemini_model")
        val claudeKey = stringPreferencesKey("anthropic_api_key")
        val claudeModel = stringPreferencesKey("model")
        val notification = booleanPreferencesKey("notification_enabled")
        val waterReminder = booleanPreferencesKey("water_reminder")
        val fastingStart = longPreferencesKey("fasting_start")
        val fastingHours = intPreferencesKey("fasting_hours")
        val healthConnect = booleanPreferencesKey("health_connect")
        fun reminderOn(name: String) = booleanPreferencesKey("reminder_${name}_on")
        fun reminderTime(name: String) = intPreferencesKey("reminder_${name}_time")
    }

    val settings: Flow<UserSettings> = context.dataStore.data.map { p: Preferences ->
        val d = UserSettings()
        fun reminder(name: String, def: Reminder) =
            Reminder(p[Keys.reminderOn(name)] ?: def.enabled, p[Keys.reminderTime(name)] ?: def.minuteOfDay)
        UserSettings(
            kcalGoal = p[Keys.kcal] ?: d.kcalGoal,
            proteinPct = p[Keys.protein] ?: d.proteinPct,
            carbsPct = p[Keys.carbs] ?: d.carbsPct,
            fatPct = p[Keys.fat] ?: d.fatPct,
            sugarGoalG = p[Keys.sugar] ?: d.sugarGoalG,
            fiberGoalG = p[Keys.fiber] ?: d.fiberGoalG,
            waterGoalMl = p[Keys.water] ?: d.waterGoalMl,
            aiProvider = p[Keys.provider]?.let { runCatching { AiProvider.valueOf(it) }.getOrNull() } ?: d.aiProvider,
            geminiKey = p[Keys.geminiKey] ?: d.geminiKey,
            geminiModel = p[Keys.geminiModel]?.takeIf(String::isNotBlank) ?: d.geminiModel,
            claudeKey = p[Keys.claudeKey] ?: d.claudeKey,
            claudeModel = p[Keys.claudeModel] ?: d.claudeModel,
            notificationEnabled = p[Keys.notification] ?: d.notificationEnabled,
            breakfastReminder = reminder("breakfast", d.breakfastReminder),
            lunchReminder = reminder("lunch", d.lunchReminder),
            dinnerReminder = reminder("dinner", d.dinnerReminder),
            waterReminder = p[Keys.waterReminder] ?: d.waterReminder,
            fastingStart = p[Keys.fastingStart] ?: d.fastingStart,
            fastingHours = p[Keys.fastingHours] ?: d.fastingHours,
            healthConnect = p[Keys.healthConnect] ?: d.healthConnect,
        )
    }

    suspend fun save(s: UserSettings) {
        context.dataStore.edit { p ->
            p[Keys.kcal] = s.kcalGoal
            p[Keys.protein] = s.proteinPct
            p[Keys.carbs] = s.carbsPct
            p[Keys.fat] = s.fatPct
            p[Keys.sugar] = s.sugarGoalG
            p[Keys.fiber] = s.fiberGoalG
            p[Keys.water] = s.waterGoalMl
            p[Keys.provider] = s.aiProvider.name
            p[Keys.geminiKey] = s.geminiKey.trim()
            p[Keys.geminiModel] = s.geminiModel.trim()
            p[Keys.claudeKey] = s.claudeKey.trim()
            p[Keys.claudeModel] = s.claudeModel
            p[Keys.notification] = s.notificationEnabled
            p[Keys.waterReminder] = s.waterReminder
            p[Keys.healthConnect] = s.healthConnect
            p.putReminder("breakfast", s.breakfastReminder)
            p.putReminder("lunch", s.lunchReminder)
            p.putReminder("dinner", s.dinnerReminder)
        }
    }

    private fun MutablePreferences.putReminder(name: String, r: Reminder) {
        this[Keys.reminderOn(name)] = r.enabled
        this[Keys.reminderTime(name)] = r.minuteOfDay
    }

    suspend fun setNotificationEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.notification] = enabled }
    }

    suspend fun setFasting(start: Long, hours: Int) {
        context.dataStore.edit {
            it[Keys.fastingStart] = start
            it[Keys.fastingHours] = hours
        }
    }
}
