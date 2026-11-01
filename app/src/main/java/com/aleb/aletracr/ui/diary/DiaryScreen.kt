package com.aleb.aletracr.ui.diary

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aleb.aletracr.data.DiaryEntry
import com.aleb.aletracr.data.EntrySource
import com.aleb.aletracr.data.Meal
import com.aleb.aletracr.data.Repository
import com.aleb.aletracr.data.UserSettings
import com.aleb.aletracr.ui.CalorieRing
import com.aleb.aletracr.ui.MacroBar
import com.aleb.aletracr.ui.NumberField
import com.aleb.aletracr.ui.theme.MacroColors
import com.aleb.aletracr.util.gramText
import com.aleb.aletracr.util.label
import com.aleb.aletracr.util.toDecimal
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.roundToInt

@OptIn(ExperimentalCoroutinesApi::class)
class DiaryViewModel(private val repo: Repository) : ViewModel() {
    val date = MutableStateFlow(LocalDate.now())

    val entries = date.flatMapLatest { repo.diary.entriesFor(it.toString()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val water = date.flatMapLatest { repo.body.waterFor(it.toString()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val settings = repo.settingsRepo.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserSettings())

    fun shiftDay(days: Long) { date.value = date.value.plusDays(days) }
    fun today() { date.value = LocalDate.now() }

    fun addWater(ml: Int) = viewModelScope.launch {
        repo.body.addWater(com.aleb.aletracr.data.WaterEntry(date = date.value.toString(), ml = ml))
    }
    fun undoWater() = viewModelScope.launch { repo.body.undoLastWater(date.value.toString()) }

    fun delete(e: DiaryEntry) = viewModelScope.launch { repo.diary.delete(e) }

    /** Changing the amount scales kcal and macros proportionally. */
    fun updateAmount(e: DiaryEntry, grams: Double?, kcal: Double?) = viewModelScope.launch {
        val updated = when {
            grams != null && e.amountGrams != null && e.amountGrams > 0 -> {
                val f = grams / e.amountGrams
                e.copy(amountGrams = grams, kcal = e.kcal * f, protein = e.protein * f, carbs = e.carbs * f, fat = e.fat * f)
            }
            kcal != null -> e.copy(kcal = kcal)
            else -> e
        }
        repo.diary.update(updated)
    }

    suspend fun copyFromYesterday(meal: Meal): Int {
        val d = date.value
        return repo.copyMeal(d.minusDays(1).toString(), d.toString(), meal)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaryScreen(repo: Repository, onAddFood: (date: String, meal: Meal) -> Unit) {
    val vm: DiaryViewModel = viewModel(factory = viewModelFactory { initializer { DiaryViewModel(repo) } })
    val date by vm.date.collectAsState()
    val entries by vm.entries.collectAsState()
    val water by vm.water.collectAsState()
    val settings by vm.settings.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<DiaryEntry?>(null) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(date.label(), modifier = Modifier.clickable { vm.today() }) },
                navigationIcon = {
                    IconButton(onClick = { vm.shiftDay(-1) }) { Icon(Icons.Filled.ChevronLeft, "Vorheriger Tag") }
                },
                actions = {
                    IconButton(onClick = { vm.shiftDay(1) }) { Icon(Icons.Filled.ChevronRight, "Nächster Tag") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { SummaryCard(entries, settings) }
            item { WaterCard(water, settings.waterGoalMl, vm::addWater, vm::undoWater) }
            Meal.entries.forEach { meal ->
                item(key = meal.name) {
                    MealCard(
                        meal = meal,
                        entries = entries.filter { it.meal == meal },
                        onAdd = { onAddFood(date.toString(), meal) },
                        onEntryClick = { editing = it },
                        onCopyYesterday = {
                            scope.launch {
                                val n = vm.copyFromYesterday(meal)
                                snackbar.showSnackbar(
                                    if (n == 0) "Gestern war bei ${meal.label} nichts eingetragen"
                                    else "$n Einträge von gestern kopiert"
                                )
                            }
                        },
                    )
                }
            }
        }
    }

    editing?.let { e ->
        EditEntryDialog(
            entry = e,
            onDismiss = { editing = null },
            onDelete = { vm.delete(e); editing = null },
            onSave = { grams, kcal -> vm.updateAmount(e, grams, kcal); editing = null },
        )
    }
}

@Composable
private fun SummaryCard(entries: List<DiaryEntry>, s: UserSettings) {
    val kcal = entries.sumOf { it.kcal }
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            CalorieRing(kcal, s.kcalGoal)
            Spacer(Modifier.width(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Ziel ${s.kcalGoal} · Gegessen ${kcal.roundToInt()}", style = MaterialTheme.typography.bodySmall)
                MacroBar("Eiweiß", entries.sumOf { it.protein }, s.proteinGoalG, MacroColors.protein)
                MacroBar("Kohlenhydrate", entries.sumOf { it.carbs }, s.carbsGoalG, MacroColors.carbs)
                MacroBar("Fett", entries.sumOf { it.fat }, s.fatGoalG, MacroColors.fat)
            }
        }
    }
}

@Composable
private fun WaterCard(ml: Int, goal: Int, onAdd: (Int) -> Unit, onUndo: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.WaterDrop, null, tint = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.width(8.dp))
            Text("Wasser: ${"%.2f".format(ml / 1000.0)} / ${"%.1f".format(goal / 1000.0)} l", Modifier.weight(1f))
            IconButton(onClick = onUndo, enabled = ml > 0) { Icon(Icons.AutoMirrored.Filled.Undo, "Rückgängig") }
            TextButton(onClick = { onAdd(250) }) { Text("+250 ml") }
        }
    }
}

@Composable
private fun MealCard(
    meal: Meal,
    entries: List<DiaryEntry>,
    onAdd: () -> Unit,
    onEntryClick: (DiaryEntry) -> Unit,
    onCopyYesterday: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Row(Modifier.padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(meal.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text("${entries.sumOf { it.kcal }.roundToInt()} kcal", fontWeight = FontWeight.SemiBold)
                Column {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Mehr") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Von gestern kopieren") },
                            leadingIcon = { Icon(Icons.Filled.ContentCopy, null) },
                            onClick = { menu = false; onCopyYesterday() },
                        )
                    }
                }
            }
            if (entries.isNotEmpty()) HorizontalDivider()
            entries.forEach { e ->
                Row(
                    Modifier.fillMaxWidth().clickable { onEntryClick(e) }.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (e.source == EntrySource.AI_PHOTO || e.source == EntrySource.AI_TEXT) {
                                Icon(
                                    Icons.Filled.AutoAwesome, "KI-Schätzung",
                                    Modifier.padding(end = 4.dp).width(14.dp),
                                    tint = MaterialTheme.colorScheme.tertiary,
                                )
                            }
                            Text(e.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        val details = listOfNotNull(e.brand, e.amountGrams?.gramText(), e.note).joinToString(" · ")
                        if (details.isNotEmpty()) {
                            Text(details, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Text("${e.kcal.roundToInt()}")
                }
            }
            TextButton(onClick = onAdd, modifier = Modifier.padding(start = 4.dp)) {
                Icon(Icons.Filled.Add, null)
                Spacer(Modifier.width(4.dp))
                Text("Hinzufügen")
            }
        }
    }
}

@Composable
private fun EditEntryDialog(
    entry: DiaryEntry,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onSave: (grams: Double?, kcal: Double?) -> Unit,
) {
    val hasGrams = entry.amountGrams != null
    var text by remember {
        mutableStateOf(if (hasGrams) entry.amountGrams!!.roundToInt().toString() else entry.kcal.roundToInt().toString())
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(entry.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "${entry.kcal.roundToInt()} kcal · E ${entry.protein.gramText()} · K ${entry.carbs.gramText()} · F ${entry.fat.gramText()}",
                    style = MaterialTheme.typography.bodySmall,
                )
                NumberField(text, { text = it }, if (hasGrams) "Menge" else "Kalorien", suffix = if (hasGrams) "g" else "kcal")
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val v = text.toDecimal()
                if (hasGrams) onSave(v, null) else onSave(null, v)
            }) { Text("Speichern") }
        },
        dismissButton = {
            TextButton(onClick = onDelete) { Text("Löschen", color = MaterialTheme.colorScheme.error) }
        },
    )
}
