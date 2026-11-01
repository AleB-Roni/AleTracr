package com.aleb.aletracr

import android.app.Application
import com.aleb.aletracr.data.AppDatabase
import com.aleb.aletracr.data.Repository
import com.aleb.aletracr.data.SettingsRepository
import com.aleb.aletracr.notify.CalorieNotification
import com.aleb.aletracr.notify.RefreshWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
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
            }
        }
    }

    /** Called after the user grants the notification permission. */
    fun refreshNotification() {
        appScope.launch { CalorieNotification.refresh(this@AleTracrApp, repository) }
    }
}
