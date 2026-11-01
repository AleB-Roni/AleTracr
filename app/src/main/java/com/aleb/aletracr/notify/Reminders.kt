package com.aleb.aletracr.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.aleb.aletracr.AleTracrApp
import com.aleb.aletracr.MainActivity
import com.aleb.aletracr.R
import com.aleb.aletracr.data.Meal
import com.aleb.aletracr.data.Reminder
import com.aleb.aletracr.data.UserSettings
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * Smart reminders: a meal reminder only appears if nothing has been logged for
 * that meal yet; the water reminder only if you are behind your water goal.
 * Each reminder is a one-time work request that re-schedules itself for the next day.
 */
object Reminders {
    const val CHANNEL_ID = "reminders"
    private const val KIND = "kind"
    private const val KIND_WATER = "WATER"
    private const val KIND_FASTING = "FASTING"
    private val WATER_SLOTS = listOf(10, 12, 14, 16, 18, 20)

    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL_ID, "Erinnerungen", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Erinnerungen an Mahlzeiten, Wasser und Fasten"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** (Re-)schedules all reminders according to the settings. */
    fun scheduleAll(context: Context, s: UserSettings) {
        scheduleMeal(context, Meal.BREAKFAST, s.breakfastReminder)
        scheduleMeal(context, Meal.LUNCH, s.lunchReminder)
        scheduleMeal(context, Meal.DINNER, s.dinnerReminder)
        if (s.waterReminder) scheduleWater(context) else WorkManager.getInstance(context).cancelUniqueWork("reminder-water")
        scheduleFasting(context, s)
    }

    private fun scheduleMeal(context: Context, meal: Meal, r: Reminder) {
        val name = "reminder-${meal.name}"
        val wm = WorkManager.getInstance(context)
        if (!r.enabled) { wm.cancelUniqueWork(name); return }
        val delay = delayUntil(LocalTime.of(r.hour, r.minute))
        wm.enqueueUniqueWork(
            name, ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
                .setInputData(workDataOf(KIND to meal.name))
                .build(),
        )
    }

    private fun scheduleWater(context: Context) {
        val now = LocalTime.now()
        val next = WATER_SLOTS.map { LocalTime.of(it, 0) }.firstOrNull { it.isAfter(now) } ?: LocalTime.of(WATER_SLOTS.first(), 0)
        WorkManager.getInstance(context).enqueueUniqueWork(
            "reminder-water", ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInitialDelay(delayUntil(next).toMillis(), TimeUnit.MILLISECONDS)
                .setInputData(workDataOf(KIND to KIND_WATER))
                .build(),
        )
    }

    fun scheduleFasting(context: Context, s: UserSettings) {
        val wm = WorkManager.getInstance(context)
        if (s.fastingStart == 0L) { wm.cancelUniqueWork("reminder-fasting"); return }
        val end = s.fastingStart + s.fastingHours * 3_600_000L
        val delay = (end - System.currentTimeMillis()).coerceAtLeast(0)
        wm.enqueueUniqueWork(
            "reminder-fasting", ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(workDataOf(KIND to KIND_FASTING))
                .build(),
        )
    }

    private fun delayUntil(time: LocalTime): Duration {
        val now = LocalDateTime.now()
        var target = LocalDate.now().atTime(time)
        if (!target.isAfter(now)) target = target.plusDays(1)
        return Duration.between(now, target)
    }

    class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            val app = applicationContext as AleTracrApp
            val repo = app.repository
            val s = repo.settingsRepo.settings.first()
            val today = LocalDate.now().toString()
            when (val kind = inputData.getString(KIND)) {
                KIND_WATER -> {
                    if (!s.waterReminder) return Result.success()
                    val hour = LocalTime.now().hour
                    // Expected share of the goal by now (09:00–21:00 linear).
                    val expected = s.waterGoalMl * ((hour - 9).coerceIn(0, 12) / 12.0)
                    val drunk = repo.body.waterOn(today)
                    if (drunk < expected - 200) {
                        notify(applicationContext, 2000, "Zeit für ein Glas Wasser 💧",
                            "Du hast heute ${drunk} ml von ${s.waterGoalMl} ml getrunken.")
                    }
                    scheduleWater(applicationContext)
                }
                KIND_FASTING -> {
                    if (s.fastingStart != 0L) {
                        notify(applicationContext, 2001, "Fastenziel erreicht 🎉",
                            "${s.fastingHours} Stunden geschafft – du kannst dein Fasten jetzt beenden.")
                    }
                }
                else -> {
                    val meal = runCatching { Meal.valueOf(kind ?: "") }.getOrNull() ?: return Result.success()
                    if (repo.diary.countFor(today, meal) == 0) {
                        notify(applicationContext, 2100 + meal.ordinal, "${meal.label} schon eingetragen?",
                            "Tippe hier, um dein ${meal.label} schnell hinzuzufügen.", openQuickAdd = true)
                    }
                    val r = when (meal) {
                        Meal.BREAKFAST -> s.breakfastReminder
                        Meal.LUNCH -> s.lunchReminder
                        else -> s.dinnerReminder
                    }
                    scheduleMeal(applicationContext, meal, r)
                }
            }
            return Result.success()
        }
    }

    private fun notify(context: Context, id: Int, title: String, text: String, openQuickAdd: Boolean = false) {
        if (!CalorieNotification.canPost(context)) return
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (openQuickAdd) intent.putExtra(CalorieNotification.EXTRA_OPEN_QUICK_ADD, true)
        val pi = PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_kcal)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, n)
        } catch (_: SecurityException) {
        }
    }
}
