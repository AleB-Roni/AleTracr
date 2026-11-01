package com.aleb.aletracr.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import com.aleb.aletracr.MainActivity
import com.aleb.aletracr.R
import com.aleb.aletracr.data.Repository
import kotlinx.coroutines.flow.first
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Persistent, silent notification showing today's remaining calories, with
 * actions to quick-add calories inline and to switch the notification off.
 */
object CalorieNotification {
    const val CHANNEL_ID = "kcal_status"
    const val NOTIFICATION_ID = 1001
    const val KEY_QUICK_INPUT = "quick_kcal_input"
    const val ACTION_QUICK_ADD = "com.aleb.aletracr.action.QUICK_ADD"
    const val ACTION_DISABLE = "com.aleb.aletracr.action.DISABLE_NOTIFICATION"
    const val EXTRA_OPEN_QUICK_ADD = "open_quick_add"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL_ID, "Kalorien heute", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Zeigt dauerhaft an, wie viele Kalorien heute noch übrig sind"
            setShowBadge(false)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun cancel(context: Context) = NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)

    /** Reads today's data and shows (or removes) the notification. */
    suspend fun refresh(context: Context, repo: Repository) {
        val settings = repo.settingsRepo.settings.first()
        if (!settings.notificationEnabled) {
            cancel(context)
            return
        }
        if (!canPost(context)) return
        show(context, TodayStatus.load(context, repo))
    }

    private fun show(context: Context, status: TodayStatus) {
        val eaten = status.eaten
        val goal = status.goal
        val remaining = status.remaining.roundToInt()
        val title = if (remaining >= 0) "Noch $remaining kcal übrig" else "${abs(remaining)} kcal über dem Ziel"
        val text = "Gegessen ${eaten.roundToInt()} von $goal kcal" +
            (if (status.activeKcal > 0) " (inkl. +${status.activeKcal} aktiv)" else "")

        val openApp = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val openQuickAdd = PendingIntent.getActivity(
            context, 1,
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_OPEN_QUICK_ADD, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // Inline reply: type "250" or "250 Apfel" directly in the notification.
        val remoteInput = RemoteInput.Builder(KEY_QUICK_INPUT)
            .setLabel("kcal eingeben, z. B. 250 Apfel")
            .build()
        val quickAddIntent = PendingIntent.getBroadcast(
            context, 2,
            Intent(context, NotificationActionReceiver::class.java).setAction(ACTION_QUICK_ADD),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        val quickAddAction = NotificationCompat.Action.Builder(R.drawable.ic_stat_kcal, "Kalorien eintragen", quickAddIntent)
            .addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(false)
            .build()

        val disableIntent = PendingIntent.getBroadcast(
            context, 3,
            Intent(context, NotificationActionReceiver::class.java).setAction(ACTION_DISABLE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_kcal)
            .setContentTitle(title)
            .setContentText(text)
            .setProgress(goal.coerceAtLeast(1), eaten.roundToInt().coerceIn(0, goal.coerceAtLeast(1)), false)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(quickAddAction)
            .addAction(0, "Mehr…", openQuickAdd)
            .addAction(0, "Ausblenden", disableIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Permission revoked in the meantime – nothing to do.
        }
    }
}
