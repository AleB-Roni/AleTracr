package com.aleb.aletracr

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableIntStateOf
import com.aleb.aletracr.notify.CalorieNotification
import com.aleb.aletracr.ui.AppNav
import com.aleb.aletracr.ui.theme.AleTracrTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {

    /** Incremented each time the notification's "Mehr…" button opens the app. */
    private val quickAddRequests = mutableIntStateOf(0)

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) (application as AleTracrApp).refreshNotification()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as AleTracrApp
        handleIntent(intent)
        maybeAskNotificationPermission(app)
        setContent {
            AleTracrTheme {
                AppNav(app.repository, quickAddRequests.intValue, ::askNotificationPermission)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(CalorieNotification.EXTRA_OPEN_QUICK_ADD, false) == true) {
            intent.removeExtra(CalorieNotification.EXTRA_OPEN_QUICK_ADD)
            quickAddRequests.intValue++
        }
    }

    private fun maybeAskNotificationPermission(app: AleTracrApp) {
        val enabled = runBlocking { app.repository.settingsRepo.settings.first().notificationEnabled }
        if (enabled && !CalorieNotification.canPost(this)) askNotificationPermission()
    }

    fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !CalorieNotification.canPost(this)) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            (application as AleTracrApp).refreshNotification()
        }
    }
}
