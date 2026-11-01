package com.aleb.aletracr.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.aleb.aletracr.data.Repository
import com.aleb.aletracr.data.UserSettings
import com.aleb.aletracr.remote.ClaudeNutritionClient
import com.aleb.aletracr.ui.NumberField
import com.aleb.aletracr.util.toDecimal
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(repo: Repository, onEnableNotification: () -> Unit = {}) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var loaded by remember { mutableStateOf<UserSettings?>(null) }
    LaunchedEffect(Unit) { loaded = repo.settingsRepo.settings.first() }
    val s = loaded
    val live by repo.settingsRepo.settings.collectAsState(null)

    Scaffold(
        topBar = { TopAppBar(title = { Text("Einstellungen") }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (s == null) return@Scaffold
        var kcal by remember { mutableStateOf(s.kcalGoal.toString()) }
        var protein by remember { mutableStateOf(s.proteinPct.toString()) }
        var carbs by remember { mutableStateOf(s.carbsPct.toString()) }
        var fat by remember { mutableStateOf(s.fatPct.toString()) }
        var water by remember { mutableStateOf(s.waterGoalMl.toString()) }
        var apiKey by remember { mutableStateOf(s.apiKey) }
        var model by remember { mutableStateOf(s.model) }
        var showKey by remember { mutableStateOf(false) }
        var showCalc by remember { mutableStateOf(false) }

        val pctSum = (protein.toIntOrNull() ?: 0) + (carbs.toIntOrNull() ?: 0) + (fat.toIntOrNull() ?: 0)

        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Ziele", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                NumberField(kcal, { kcal = it.filter(Char::isDigit) }, "Kalorienziel", Modifier.weight(1f), suffix = "kcal")
                TextButton(onClick = { showCalc = true }) { Text("Berechnen") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(protein, { protein = it.filter(Char::isDigit) }, "Eiweiß", Modifier.weight(1f), suffix = "%")
                NumberField(carbs, { carbs = it.filter(Char::isDigit) }, "KH", Modifier.weight(1f), suffix = "%")
                NumberField(fat, { fat = it.filter(Char::isDigit) }, "Fett", Modifier.weight(1f), suffix = "%")
            }
            if (pctSum != 100) {
                Text("Makros ergeben $pctSum % (sollten 100 % sein)", color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall)
            }
            NumberField(water, { water = it.filter(Char::isDigit) }, "Wasserziel", Modifier.fillMaxWidth(), suffix = "ml")

            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Dauerhafte Benachrichtigung", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Zeigt übrige Kalorien an und erlaubt Schnelleinträge direkt aus der Benachrichtigung.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = live?.notificationEnabled ?: s.notificationEnabled,
                    onCheckedChange = { on ->
                        scope.launch {
                            repo.settingsRepo.setNotificationEnabled(on)
                            if (on) onEnableNotification()
                        }
                    },
                )
            }

            HorizontalDivider()
            Text("KI (Claude von Anthropic)", style = MaterialTheme.typography.titleMedium)
            Text(
                "Für Foto- und Text-Erkennung brauchst du einen API-Key von console.anthropic.com. " +
                    "Der Key wird nur lokal auf diesem Gerät gespeichert. Abgerechnet wird pro Anfrage über dein Anthropic-Konto.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it.trim() },
                label = { Text("Anthropic API-Key") },
                singleLine = true,
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showKey = !showKey }) {
                        Icon(if (showKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, "Anzeigen")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            ClaudeNutritionClient.MODELS.forEach { (id, label) ->
                Row(
                    Modifier.fillMaxWidth().selectable(selected = model == id, onClick = { model = id }),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = model == id, onClick = { model = id })
                    Text(label)
                }
            }

            Button(
                onClick = {
                    scope.launch {
                        repo.settingsRepo.save(
                            UserSettings(
                                kcalGoal = kcal.toIntOrNull() ?: s.kcalGoal,
                                proteinPct = protein.toIntOrNull() ?: s.proteinPct,
                                carbsPct = carbs.toIntOrNull() ?: s.carbsPct,
                                fatPct = fat.toIntOrNull() ?: s.fatPct,
                                waterGoalMl = water.toIntOrNull() ?: s.waterGoalMl,
                                apiKey = apiKey,
                                model = model,
                                notificationEnabled = repo.settingsRepo.settings.first().notificationEnabled,
                            )
                        )
                        snackbar.showSnackbar("Gespeichert")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Speichern") }
            Text(
                "Produktdaten: Open Food Facts (openfoodfacts.org), lizenziert unter ODbL.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (showCalc) {
            GoalCalculatorDialog(onDismiss = { showCalc = false }, onResult = { kcal = it.toString(); showCalc = false })
        }
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
