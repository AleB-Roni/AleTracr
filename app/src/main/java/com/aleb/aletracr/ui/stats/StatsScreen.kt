package com.aleb.aletracr.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aleb.aletracr.data.DayTotal
import com.aleb.aletracr.data.Repository
import com.aleb.aletracr.data.UserSettings
import com.aleb.aletracr.data.WeightEntry
import com.aleb.aletracr.ui.NumberField
import com.aleb.aletracr.util.toDecimal
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

private val shortDate = DateTimeFormatter.ofPattern("d.M.yyyy")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(repo: Repository) {
    val today = remember { LocalDate.now() }
    val from = today.minusDays(6)
    val totals by remember { repo.diary.totalsBetween(from.toString(), today.toString()) }.collectAsState(emptyList())
    val settings by repo.settingsRepo.settings.collectAsState(UserSettings())
    val weights by repo.body.weights().collectAsState(emptyList())
    val loggedDays by repo.diary.loggedDays().collectAsState(0)
    val scope = rememberCoroutineScope()
    var weightInput by remember { mutableStateOf("") }

    val week = (0..6).map { i ->
        val d = from.plusDays(i.toLong())
        d to (totals.firstOrNull { it.date == d.toString() } ?: DayTotal(d.toString(), 0.0, 0.0, 0.0, 0.0))
    }
    val logged = week.filter { it.second.kcal > 0 }
    val avg = if (logged.isEmpty()) 0.0 else logged.sumOf { it.second.kcal } / logged.size

    Scaffold(topBar = { TopAppBar(title = { Text("Fortschritt") }) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Letzte 7 Tage", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Ø ${avg.roundToInt()} kcal/Tag · Ziel ${settings.kcalGoal} · $loggedDays Tage protokolliert",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(Modifier.height(12.dp))
                        WeekChart(week.map { it.second.kcal }, settings.kcalGoal)
                        Row(Modifier.fillMaxWidth()) {
                            week.forEach { (d, _) ->
                                Text(
                                    d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.GERMAN),
                                    Modifier.weight(1f), textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Gewicht", style = MaterialTheme.typography.titleMedium)
                        if (weights.size >= 2) {
                            WeightChart(weights)
                            val diff = weights.last().kg - weights.first().kg
                            Text(
                                "Seit Beginn: ${if (diff > 0) "+" else ""}${"%.1f".format(Locale.GERMAN, diff)} kg",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            NumberField(weightInput, { weightInput = it }, "Heutiges Gewicht", Modifier.weight(1f), suffix = "kg")
                            Spacer(Modifier.width(8.dp))
                            Button(
                                enabled = weightInput.toDecimal() != null,
                                onClick = {
                                    val kg = weightInput.toDecimal() ?: return@Button
                                    scope.launch { repo.body.upsertWeight(WeightEntry(today.toString(), kg)) }
                                    weightInput = ""
                                },
                            ) { Text("Speichern") }
                        }
                    }
                }
            }
            items(weights.reversed(), key = { it.date }) { w ->
                ListItem(
                    headlineContent = { Text("${"%.1f".format(Locale.GERMAN, w.kg)} kg") },
                    supportingContent = { Text(LocalDate.parse(w.date).format(shortDate)) },
                    trailingContent = {
                        IconButton(onClick = { scope.launch { repo.body.deleteWeight(w) } }) {
                            Icon(Icons.Filled.Delete, "Löschen")
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun WeekChart(values: List<Double>, goal: Int) {
    val bar = MaterialTheme.colorScheme.primary
    val over = MaterialTheme.colorScheme.error
    val line = MaterialTheme.colorScheme.outline
    Canvas(Modifier.fillMaxWidth().height(140.dp)) {
        val maxV = maxOf(values.maxOrNull() ?: 0.0, goal * 1.2).toFloat().coerceAtLeast(1f)
        val slot = size.width / values.size
        val w = slot * 0.6f
        values.forEachIndexed { i, v ->
            val h = (v.toFloat() / maxV) * size.height
            drawRoundRect(
                color = if (v > goal) over else bar,
                topLeft = Offset(i * slot + (slot - w) / 2, size.height - h),
                size = Size(w, h),
                cornerRadius = CornerRadius(6f, 6f),
            )
        }
        val gy = size.height - goal / maxV * size.height
        drawLine(line, Offset(0f, gy), Offset(size.width, gy), strokeWidth = 2f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f)))
    }
}

@Composable
private fun WeightChart(weights: List<WeightEntry>) {
    val color = MaterialTheme.colorScheme.secondary
    Canvas(Modifier.fillMaxWidth().height(120.dp)) {
        val min = weights.minOf { it.kg } - 0.5
        val max = weights.maxOf { it.kg } + 0.5
        val stepX = size.width / (weights.size - 1)
        val path = Path()
        weights.forEachIndexed { i, w ->
            val x = i * stepX
            val y = size.height - ((w.kg - min) / (max - min)).toFloat() * size.height
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            drawCircle(color, 5f, Offset(x, y))
        }
        drawPath(path, color, style = Stroke(4f))
    }
}
