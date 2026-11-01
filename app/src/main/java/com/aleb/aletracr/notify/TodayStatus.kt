package com.aleb.aletracr.notify

import android.content.Context
import com.aleb.aletracr.data.Repository
import com.aleb.aletracr.health.HealthConnectManager
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/** Today's numbers, shared by the notification and the homescreen widget. */
data class TodayStatus(
    val goal: Int,
    val baseGoal: Int,
    val activeKcal: Int,
    val eaten: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val proteinGoal: Double,
    val carbsGoal: Double,
    val fatGoal: Double,
) {
    val remaining get() = goal - eaten

    companion object {
        suspend fun load(context: Context, repo: Repository): TodayStatus {
            val s = repo.settingsRepo.settings.first()
            val entries = repo.diary.between(LocalDate.now().toString(), LocalDate.now().toString())
            val active = if (s.healthConnect) HealthConnectManager.today(context)?.activeKcal?.toInt() ?: 0 else 0
            return TodayStatus(
                goal = s.kcalGoal + active, baseGoal = s.kcalGoal, activeKcal = active,
                eaten = entries.sumOf { it.kcal },
                protein = entries.sumOf { it.protein }, carbs = entries.sumOf { it.carbs }, fat = entries.sumOf { it.fat },
                proteinGoal = s.proteinGoalG, carbsGoal = s.carbsGoalG, fatGoal = s.fatGoalG,
            )
        }
    }
}
