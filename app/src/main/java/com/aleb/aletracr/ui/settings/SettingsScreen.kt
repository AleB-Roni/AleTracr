package com.aleb.aletracr.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.aleb.aletracr.data.Reminder
import com.aleb.aletracr.data.Repository
import com.aleb.aletracr.data.UserSettings
import com.aleb.aletracr.health.HealthConnectManager
import com.aleb.aletracr.remote.AiProvider
import com.aleb.aletracr.remote.ClaudeNutritionClient
import com.aleb.aletracr.ui.IconBadge
import com.aleb.aletracr.ui.NumberField
import com.aleb.aletracr.ui.SoftCard
import com.aleb.aletracr.ui.Staggered
import com.aleb.aletracr.util.toDecimal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import kotlin.math.roundToInt

/** All changes are saved immediately. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(repo: Repository, onEnableNotification: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val live by repo.settingsRepo.settings.collectAsState(null)
    val s = live ?: run {
        Scaffold { }
        return
    }
    fun update(transform: (UserSettings) -> UserSettings) {
        scope.launch { repo.settingsRepo.save(transform(s)) }
    }
    fun toast(msg: String) { scope.launch { snackbar.showSnackbar(msg) } }

    // --- File export / import (Storage Access Framework) ---
    fun writeTo(uri: Uri?, content: suspend () -> String, done: String) {
        if (uri == null) return
        scope.launch {
            try {
                val text = content()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
                }
                toast(done)
            } catch (e: Exception) {
                toast("Fehler: ${e.message}")
            }
        }
    }
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) {
        writeTo(it, repo::exportCsv, "CSV exportiert")
    }
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) {
        writeTo(it, repo::exportBackup, "Backup gespeichert")
    }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { restoreUri = it }

    val healthLauncher = rememberLauncherForActivityResult(HealthConnectManager.permissionContract()) { granted ->
        val ok = granted.containsAll(HealthConnectManager.PERMISSIONS)
        update { it.copy(healthConnect = ok) }
        toast(if (ok) "Health Connect verbunden" else "Keine Berechtigung erteilt")
    }

    var showCalc by remember { mutableStateOf(false) }
    var editingReminder by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Einstellungen") }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // ------------------------------------------------------------ Goals
            Staggered(0) {
                Section("Ziele", Icons.Filled.Flag) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IntField(s.kcalGoal, "Kalorienziel", "kcal", Modifier.weight(1f)) { v -> update { it.copy(kcalGoal = v) } }
                        TextButton(onClick = { showCalc = true }) { Text("Berechnen") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IntField(s.proteinPct, "Eiweiß", "%", Modifier.weight(1f)) { v -> update { it.copy(proteinPct = v) } }
                        IntField(s.carbsPct, "KH", "%", Modifier.weight(1f)) { v -> update { it.copy(carbsPct = v) } }
                        IntField(s.fatPct, "Fett", "%", Modifier.weight(1f)) { v -> update { it.copy(fatPct = v) } }
                    }
                    val pct = s.proteinPct + s.carbsPct + s.fatPct
                    AnimatedVisibility(pct != 100) {
                        Text("Makros ergeben $pct % (sollten 100 % sein)", color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IntField(s.sugarGoalG, "Zucker max.", "g", Modifier.weight(1f)) { v -> update { it.copy(sugarGoalG = v) } }
                        IntField(s.fiberGoalG, "Ballaststoffe", "g", Modifier.weight(1f)) { v -> update { it.copy(fiberGoalG = v) } }
                    }
                    IntField(s.waterGoalMl, "Wasserziel", "ml", Modifier.fillMaxWidth()) { v -> update { it.copy(waterGoalMl = v) } }
                }
            }

            // ------------------------------------------------------------ AI
            Staggered(1) {
                Section("KI", Icons.Filled.AutoAwesome) {
                    AiProvider.entries.forEach { p ->
                        Row(
                            Modifier.fillMaxWidth().selectable(selected = s.aiProvider == p, onClick = { update { it.copy(aiProvider = p) } }),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = s.aiProvider == p, onClick = { update { it.copy(aiProvider = p) } })
                            Text(p.label)
                        }
                    }
                    if (s.aiProvider == AiProvider.GEMINI) {
                        Text(
                            "Kostenlos: Melde dich mit deinem Google-Konto bei Google AI Studio an, erstelle einen API-Key " +
                                "und füge ihn hier ein. Im Gratis-Tarif gibt es ein Tageslimit; Google kann kostenlose " +
                                "Anfragen zur Verbesserung seiner Dienste verwenden.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        AssistChip(
                            onClick = {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/apikey"))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            },
                            label = { Text("Gemini-Key holen (aistudio.google.com)") },
                        )
                        KeyField(s.geminiKey, "Gemini API-Key") { v -> update { it.copy(geminiKey = v) } }
                        TextSetting(s.geminiModel, "Modell (z. B. gemini-3.8-flash)") { v -> update { it.copy(geminiModel = v) } }
                    } else {
                        Text(
                            "Claude ist sehr genau, aber kostenpflichtig (Abrechnung pro Anfrage über console.anthropic.com).",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        KeyField(s.claudeKey, "Anthropic API-Key") { v -> update { it.copy(claudeKey = v) } }
                        ClaudeNutritionClient.MODELS.forEach { (id, label) ->
                            Row(
                                Modifier.fillMaxWidth().selectable(selected = s.claudeModel == id, onClick = { update { it.copy(claudeModel = id) } }),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = s.claudeModel == id, onClick = { update { it.copy(claudeModel = id) } })
                                Text(label, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    Text("Der Key wird nur lokal auf diesem Gerät gespeichert.", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // ------------------------------------------------------------ Notifications
            Staggered(2) {
                Section("Benachrichtigungen & Erinnerungen", Icons.Filled.Notifications) {
                    SwitchRow(
                        "Dauerhafte Kalorien-Anzeige",
                        "Übrige Kalorien + Schnelleintrag direkt in der Benachrichtigung",
                        s.notificationEnabled,
                    ) { on ->
                        scope.launch {
                            repo.settingsRepo.setNotificationEnabled(on)
                            if (on) onEnableNotification()
                        }
                    }
                    ReminderRow("Frühstück", s.breakfastReminder, { r -> update { it.copy(breakfastReminder = r) }; if (r.enabled) onEnableNotification() }) { editingReminder = "breakfast" }
                    ReminderRow("Mittagessen", s.lunchReminder, { r -> update { it.copy(lunchReminder = r) }; if (r.enabled) onEnableNotification() }) { editingReminder = "lunch" }
                    ReminderRow("Abendessen", s.dinnerReminder, { r -> update { it.copy(dinnerReminder = r) }; if (r.enabled) onEnableNotification() }) { editingReminder = "dinner" }
                    Text("Erinnert nur, wenn die Mahlzeit noch nicht eingetragen ist.", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    SwitchRow("Wasser-Erinnerung", "Alle 2 Stunden, wenn du hinter deinem Ziel bist", s.waterReminder) { on ->
                        update { it.copy(waterReminder = on) }
                        if (on) onEnableNotification()
                    }
                }
            }

            // ------------------------------------------------------------ Health Connect
            Staggered(3) {
                Section("Aktivität (Health Connect)", Icons.Filled.DirectionsRun) {
                    val available = remember { HealthConnectManager.isAvailable(context) }
                    if (!available) {
                        Text(
                            "Health Connect ist auf diesem Gerät nicht verfügbar. Installiere „Health Connect“ aus dem Play Store " +
                                "(ab Android 14 ist es eingebaut).",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    } else {
                        SwitchRow(
                            "Aktive Kalorien gutschreiben",
                            "Schritte & Training (z. B. von Google Fit, Samsung Health, Garmin) erhöhen dein Tagesbudget",
                            s.healthConnect,
                        ) { on ->
                            if (on) healthLauncher.launch(HealthConnectManager.PERMISSIONS)
                            else update { it.copy(healthConnect = false) }
                        }
                    }
                }
            }

            // ------------------------------------------------------------ Data
            Staggered(4) {
                Section("Daten & Backup", Icons.Filled.Backup) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { backupLauncher.launch("AleTracr-Backup-${LocalDate.now()}.json") }, modifier = Modifier.weight(1f)) {
                            Text("Backup erstellen")
                        }
                        OutlinedButton(onClick = { restoreLauncher.launch(arrayOf("application/json", "*/*")) }, modifier = Modifier.weight(1f)) {
                            Text("Wiederherstellen")
                        }
                    }
                    OutlinedButton(onClick = { csvLauncher.launch("AleTracr-Tagebuch-${LocalDate.now()}.csv") }, modifier = Modifier.fillMaxWidth()) {
                        Text("Tagebuch als CSV exportieren (Excel)")
                    }
                    Text("Tipp: Speichere Backups in Google Drive, um sie auf ein neues Handy mitzunehmen.",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Text(
                "Produktdaten: © Open Food Facts contributors (ODbL).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp),
            )
        }
    }

    if (showCalc) {
        GoalCalculatorDialog(onDismiss = { showCalc = false }, onResult = { kcal ->
            showCalc = false
            update { it.copy(kcalGoal = kcal) }
        })
    }

    editingReminder?.let { which ->
        val current = when (which) {
            "breakfast" -> s.breakfastReminder
            "lunch" -> s.lunchReminder
            else -> s.dinnerReminder
        }
        val state = rememberTimePickerState(current.hour, current.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { editingReminder = null },
            title = { Text("Uhrzeit wählen") },
            text = { TimePicker(state) },
            confirmButton = {
                TextButton(onClick = {
                    val r = Reminder(true, state.hour * 60 + state.minute)
                    update {
                        when (which) {
                            "breakfast" -> it.copy(breakfastReminder = r)
                            "lunch" -> it.copy(lunchReminder = r)
                            else -> it.copy(dinnerReminder = r)
                        }
                    }
                    editingReminder = null
                    onEnableNotification()
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { editingReminder = null }) { Text("Abbrechen") } },
        )
    }

    restoreUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { restoreUri = null },
            title = { Text("Backup wiederherstellen?") },
            text = { Text("Alle aktuellen Einträge werden durch das Backup ersetzt.") },
            confirmButton = {
                TextButton(onClick = {
                    restoreUri = null
                    scope.launch {
                        try {
                            val text = withContext(Dispatchers.IO) {
                                context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                            } ?: error("Datei konnte nicht gelesen werden")
                            val n = repo.restoreBackup(text)
                            toast("$n Einträge wiederhergestellt")
                        } catch (e: Exception) {
                            toast("Wiederherstellen fehlgeschlagen: ${e.message}")
                        }
                    }
                }) { Text("Ersetzen", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { restoreUri = null }) { Text("Abbrechen") } },
        )
    }
}

@Composable
private fun Section(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    SoftCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(icon, MaterialTheme.colorScheme.primary, size = 36.dp)
                Spacer(Modifier.width(12.dp))
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            content()
        }
    }
}

/** Number field that keeps its own text and reports valid values. */
@Composable
private fun IntField(value: Int, label: String, suffix: String, modifier: Modifier, onValid: (Int) -> Unit) {
    var text by remember { mutableStateOf(value.toString()) }
    NumberField(text, { t ->
        text = t.filter(Char::isDigit)
        text.toIntOrNull()?.takeIf { it > 0 }?.let(onValid)
    }, label, modifier, suffix = suffix)
}

@Composable
private fun TextSetting(value: String, label: String, onChange: (String) -> Unit) {
    var text by remember { mutableStateOf(value) }
    OutlinedTextField(
        value = text, onValueChange = { text = it.trim(); onChange(text) },
        label = { Text(label) }, singleLine = true, shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun KeyField(value: String, label: String, onChange: (String) -> Unit) {
    var text by remember { mutableStateOf(value) }
    var show by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it.trim(); onChange(text) },
        label = { Text(label) },
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { show = !show }) {
                Icon(if (show) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, "Anzeigen")
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { onChange(!checked) }) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ReminderRow(label: String, r: Reminder, onChange: (Reminder) -> Unit, onPickTime: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        AssistChip(onClick = onPickTime, label = { Text(r.label) }, enabled = r.enabled)
        Spacer(Modifier.width(8.dp))
        Switch(checked = r.enabled, onCheckedChange = { onChange(r.copy(enabled = it)) })
    }
}

/** Mifflin-St Jeor BMR × activity factor, adjusted for the user's goal. */
@Composable
private fun GoalCalculatorDialog(onDismiss: () -> Unit, onResult: (Int) -> Unit) {
    var male by remember { mutableStateOf(true) }
    var age by remember { mutableStateOf("30") }
    var height by remember { mutableStateOf("175") }
    var weight by remember { mutableStateOf("75") }
    var activity by remember { mutableStateOf(1) }
    var goal by remember { mutableStateOf(0) }
    val activities = listOf("Wenig Bewegung" to 1.2, "Leicht aktiv" to 1.375, "Aktiv" to 1.55, "Sehr aktiv" to 1.725)
    val goals = listOf("Abnehmen" to -500, "Halten" to 0, "Zunehmen" to 300)

    val a = age.toDecimal(); val h = height.toDecimal(); val w = weight.toDecimal()
    val result = if (a != null && h != null && w != null) {
        val bmr = 10 * w + 6.25 * h - 5 * a + if (male) 5 else -161
        (bmr * activities[activity].second + goals[goal].second).roundToInt()
    } else null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Kalorienbedarf berechnen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(male, { male = true }, { Text("Mann") })
                    FilterChip(!male, { male = false }, { Text("Frau") })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(age, { age = it }, "Alter", Modifier.weight(1f))
                    NumberField(height, { height = it }, "Größe", Modifier.weight(1f), suffix = "cm")
                    NumberField(weight, { weight = it }, "Gewicht", Modifier.weight(1f), suffix = "kg")
                }
                activities.forEachIndexed { i, (label, _) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(activity == i, { activity = i }); Text(label)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    goals.forEachIndexed { i, (label, _) -> FilterChip(goal == i, { goal = i }, { Text(label) }) }
                }
                result?.let { Text("≈ $it kcal pro Tag", style = MaterialTheme.typography.titleMedium) }
            }
        },
        confirmButton = { TextButton(enabled = result != null, onClick = { result?.let(onResult) }) { Text("Übernehmen") } },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}
