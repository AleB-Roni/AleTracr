package com.aleb.aletracr.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.app.RemoteInput
import com.aleb.aletracr.AleTracrApp
import com.aleb.aletracr.data.Meal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Handles the notification buttons ("Kalorien eintragen", "Ausblenden") and device reboot. */
class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as AleTracrApp
        val repo = app.repository
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    CalorieNotification.ACTION_QUICK_ADD -> {
                        val input = RemoteInput.getResultsFromIntent(intent)
                            ?.getCharSequence(CalorieNotification.KEY_QUICK_INPUT)?.toString()
                        val parsed = input?.let(::parseQuickInput)
                        if (parsed != null) {
                            val meal = Meal.forTime()
                            repo.quickAdd(
                                LocalDate.now().toString(), meal, parsed.second, parsed.first,
                                0.0, 0.0, 0.0, note = null,
                            )
                            toast(context, "${parsed.first.toInt()} kcal zu ${meal.label} hinzugefügt")
                        } else {
                            toast(context, "Bitte eine Zahl eingeben, z. B. 250 oder 250 Apfel")
                        }
                    }
                    CalorieNotification.ACTION_DISABLE -> {
                        repo.settingsRepo.setNotificationEnabled(false)
                        toast(context, "Benachrichtigung aus – in den Einstellungen wieder aktivierbar")
                    }
                }
                // Re-post (or cancel) so the inline-reply spinner disappears and numbers update.
                CalorieNotification.refresh(context, repo)
            } finally {
                pending.finish()
            }
        }
    }

    private fun toast(context: Context, msg: String) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        /** "250" -> (250, "Schnelleintrag"), "250 Apfel" -> (250, "Apfel"), "Apfel 95" -> (95, "Apfel") */
        fun parseQuickInput(raw: String): Pair<Double, String>? {
            val match = Regex("""\d+(?:[.,]\d+)?""").find(raw) ?: return null
            val kcal = match.value.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 && it < 10000 } ?: return null
            val name = raw.removeRange(match.range).replace(Regex("""\bkcal\b""", RegexOption.IGNORE_CASE), "").trim()
            return kcal to name.ifBlank { "Schnelleintrag" }
        }
    }
}
