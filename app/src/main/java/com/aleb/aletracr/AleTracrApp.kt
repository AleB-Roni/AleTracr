package com.aleb.aletracr

import android.app.Application
import com.aleb.aletracr.data.AppDatabase
import com.aleb.aletracr.data.Repository
import com.aleb.aletracr.data.SettingsRepository
import com.aleb.aletracr.notify.CalorieNotification
import com.aleb.aletracr.notify.RefreshWorker
import com.aleb.aletracr.notify.Reminders
import com.aleb.aletracr.widget.CalorieWidget
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import java.time.LocalDate

class AleTracrApp : Application() {
    lateinit var repository: Repository
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun onCreate() {
        super.onCreate()
        repository = Repository(AppDatabase.create(this), SettingsRepository(this))

        CalorieNotification.createChannel(this)
        Reminders.createChannel(this)
        RefreshWorker.schedule(this)

        // While the process is alive, update the notification on every diary/settings change
        // and when the date changes at midnight.
        val today = flow {
            while (true) {
                emit(LocalDate.now().toString())
                delay(60_000)
            }
        }.distinctUntilChanged()
        appScope.launch {
            combine(
                repository.settingsRepo.settings,
                today.flatMapLatest { repository.diary.entriesFor(it) },
            ) { _, _ -> }.collect {
                CalorieNotification.refresh(this@AleTracrApp, repository)
                CalorieWidget.refresh(this@AleTracrApp)
            }
        }
        // Re-schedule reminders whenever their settings change.
        appScope.launch {
            repository.settingsRepo.settings
                .map { listOf(it.breakfastReminder, it.lunchReminder, it.dinnerReminder, it.waterReminder, it.fastingStart, it.fastingHours) }
                .distinctUntilChanged()
                .collect { Reminders.scheduleAll(this@AleTracrApp, repository.settingsRepo.settings.first()) }
        }
    }

    /** Called after the user grants the notification permission. */
    fun refreshNotification() {
        appScope.launch {
            CalorieNotification.refresh(this@AleTracrApp, repository)
            CalorieWidget.refresh(this@AleTracrApp)
        }
    }
}
