package com.aleb.aletracr.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Re-schedules the refresh worker after a reboot (the app process starts and does the rest). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            RefreshWorker.schedule(context)
            RefreshWorker.runNow(context)
        }
    }
}
