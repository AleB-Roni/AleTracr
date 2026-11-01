package com.aleb.aletracr.notify

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.aleb.aletracr.AleTracrApp
import java.util.concurrent.TimeUnit

/**
 * Keeps the notification correct when the app is not running,
 * e.g. resets it to the full goal after midnight.
 */
class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as AleTracrApp
        CalorieNotification.refresh(applicationContext, app.repository)
        return Result.success()
    }

    companion object {
        private const val NAME = "kcal-notification-refresh"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<RefreshWorker>(30, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun runNow(context: Context) {
            WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<RefreshWorker>().build())
        }
    }
}
