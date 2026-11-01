package com.aleb.aletracr.ui.diary

import android.app.Application
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.aleb.aletracr.remote.AiEstimate
import com.aleb.aletracr.remote.AiException
import com.aleb.aletracr.remote.AiFoodItem
import com.aleb.aletracr.ui.rememberPhotoCapture
import com.aleb.aletracr.ui.rememberSpeechInput
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Cookie
import androidx.compose.material.icons.filled.DinnerDining
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.FreeBreakfast
import androidx.compose.material.icons.filled.LunchDining
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
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
import com.aleb.aletracr.data.WaterEntry
import com.aleb.aletracr.health.Activity
import com.aleb.aletracr.health.HealthConnectManager
import com.aleb.aletracr.ui.CalorieRing
import com.aleb.aletracr.ui.IconBadge
import com.aleb.aletracr.ui.MacroBar
import com.aleb.aletracr.ui.NumberField
import com.aleb.aletracr.ui.SoftCard
import com.aleb.aletracr.ui.Staggered
import com.aleb.aletracr.ui.theme.Gradients
import com.aleb.aletracr.ui.theme.MacroColors
import com.aleb.aletracr.util.gramText
import com.aleb.aletracr.util.label
import com.aleb.aletracr.util.toDecimal
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.roundToInt

@OptIn(ExperimentalCoroutinesApi::class)
class DiaryViewModel(private val repo: Repository, private val app: Application) : ViewModel() {
    val date = MutableStateFlow(LocalDate.now())

    val entries = date.flatMapLatest { repo.diary.entriesFor(it.toString()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val water = date.flatMapLatest { repo.body.waterFor(it.toString()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val settings = repo.settingsRepo.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserSettings())

    /** Steps/active calories from Health Connect (today only). */
    val activity = MutableStateFlow<Activity?>(null)

    fun loadActivity() = viewModelScope.launch {
        activity.value = if (settings.value.healthConnect && date.value == LocalDate.now()) {
            HealthConnectManager.today(app)
        } else null
    }

    fun shiftDay(days: Long) { date.value = date.value.plusDays(days); loadActivity() }
    fun today() { date.value = LocalDate.now(); loadActivity() }

    fun addWater(ml: Int) = viewModelScope.launch {
        repo.body.addWater(WaterEntry(date = date.value.toString(), ml = ml))
    }
    fun undoWater() = viewModelScope.launch { repo.body.undoLastWater(date.value.toString()) }

    fun delete(e: DiaryEntry) = viewModelScope.launch { repo.diary.delete(e) }
    fun restore(e: DiaryEntry) = viewModelScope.launch { repo.diary.insert(e.copy(id = 0)) }

    /** Changing the amount scales kcal and all nutrients proportionally. */
    fun updateAmount(e: DiaryEntry, grams: Double?, kcal: Double?) = viewModelScope.launch {
        val updated = when {
            grams != null && e.amountGrams != null && e.amountGrams > 0 -> {
                val f = grams / e.amountGrams
                e.copy(
                    amountGrams = grams, kcal = e.kcal * f, protein = e.protein * f, carbs = e.carbs * f,
                    fat = e.fat * f, sugar = e.sugar * f, fiber = e.fiber * f,
                )
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

    suspend fun saveRecipe(meal: Meal, name: String) = repo.saveMealAsRecipe(date.value.toString(), meal, name)

    fun startFasting(hours: Int) = viewModelScope.launch { repo.settingsRepo.setFasting(System.currentTimeMillis(), hours) }
    fun stopFasting() = viewModelScope.launch { repo.settingsRepo.setFasting(0, settings.value.fastingHours) }

    // --- KI direkt aus dem Tagebuch (Foto / Spracheingabe) ---------------------

    /** Meal that the AI is currently working on (shows a spinner on that card). */
    val aiBusy = MutableStateFlow<Meal?>(null)
    val aiResult = MutableStateFlow<QuickAiResult?>(null)
    val aiError = MutableStateFlow<String?>(null)

    /**
     * Voice input is added automatically (undo possible); a photo estimate is shown
     * as a summary first and only added after confirmation.
     */
    fun quickAi(meal: Meal, text: String?, photo: ByteArray?) = viewModelScope.launch {
        val client = repo.aiClient()
        if (client == null) {
            aiError.value = "Bitte zuerst in den Einstellungen einen KI-Key hinterlegen (Gemini ist kostenlos)."
            return@launch
        }
        aiBusy.value = meal
        try {
            val estimate = client.estimateMeal(text, photo)
            if (estimate.items.isEmpty()) {
                aiError.value = estimate.notes.ifBlank { "Kein Essen erkannt." }
            } else if (photo == null) {
                val ids = repo.logAiItems(date.value.toString(), meal, estimate.items, fromPhoto = false)
                aiResult.value = QuickAiResult(meal, text, estimate, addedIds = ids)
            } else {
                aiResult.value = QuickAiResult(meal, text, estimate, addedIds = emptyList(), photo = photo)
            }
        } catch (e: AiException) {
            aiError.value = e.message
        } catch (e: Exception) {
            aiError.value = "Fehler: ${e.message}"
        } finally {
            aiBusy.value = null
        }
    }

    fun confirmAi(r: QuickAiResult, selected: List<AiFoodItem>) = viewModelScope.launch {
        if (selected.isNotEmpty()) repo.logAiItems(date.value.toString(), r.meal, selected, fromPhoto = r.photo != null)
        aiResult.value = null
    }

    fun undoAi(r: QuickAiResult) = viewModelScope.launch {
        repo.diary.deleteIds(r.addedIds)
        aiResult.value = null
    }
}

/** Result of a quick AI entry; [addedIds] is non-empty if the items were already saved (voice). */
class QuickAiResult(
    val meal: Meal,
    val spokenText: String?,
    val estimate: AiEstimate,
    val addedIds: List<Long>,
    val photo: ByteArray? = null,
) {
    val alreadyAdded get() = addedIds.isNotEmpty()
}

private fun Meal.icon(): ImageVector = when (this) {
    Meal.BREAKFAST -> Icons.Filled.FreeBreakfast
    Meal.LUNCH -> Icons.Filled.LunchDining
    Meal.DINNER -> Icons.Filled.DinnerDining
    Meal.SNACK -> Icons.Filled.Cookie
}

private fun Meal.color(): Color = when (this) {
    Meal.BREAKFAST -> Color(0xFFFFA928)
    Meal.LUNCH -> Color(0xFF2BB673)
    Meal.DINNER -> Color(0xFF4F7CFF)
    Meal.SNACK -> Color(0xFFFF4F8B)
}

@Composable
fun DiaryScreen(repo: Repository, onAddFood: (date: String, meal: Meal) -> Unit) {
    val app = LocalContext.current.applicationContext as Application
    val vm: DiaryViewModel = viewModel(factory = viewModelFactory { initializer { DiaryViewModel(repo, app) } })
    val date by vm.date.collectAsState()
    val entries by vm.entries.collectAsState()
    val water by vm.water.collectAsState()
    val settings by vm.settings.collectAsState()
    val activity by vm.activity.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<DiaryEntry?>(null) }
    var savingRecipe by remember { mutableStateOf<Meal?>(null) }
    val aiBusy by vm.aiBusy.collectAsState()
    val aiResult by vm.aiResult.collectAsState()
    val aiError by vm.aiError.collectAsState()
    var captureMeal by remember { mutableStateOf(Meal.forTime()) }
    val speak = rememberSpeechInput(
        prompt = "Was hast du gegessen? z. B. \"zwei Scheiben Vollkornbrot mit Käse\"",
        onError = { vm.aiError.value = it },
    ) { text -> vm.quickAi(captureMeal, text, null) }
    val photo = rememberPhotoCapture { bytes -> vm.quickAi(captureMeal, null, bytes) }

    LaunchedEffect(aiError) {
        aiError?.let { snackbar.showSnackbar(it); vm.aiError.value = null }
    }

    LaunchedEffect(settings.healthConnect) { vm.loadActivity() }

    // After midnight, jump from "yesterday" to the new day (unless the user browsed to another day).
    LaunchedEffect(Unit) {
        var lastToday = LocalDate.now()
        while (true) {
            delay(30_000)
            val now = LocalDate.now()
            if (now != lastToday) {
                if (vm.date.value == lastToday) vm.today()
                lastToday = now
            }
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding()),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "hero") {
                HeroHeader(
                    date = date, entries = entries, settings = settings, activity = activity,
                    onPrev = { vm.shiftDay(-1) }, onNext = { vm.shiftDay(1) }, onToday = vm::today,
                )
            }
            item(key = "water") {
                Staggered(1) {
                    WaterCard(water, settings.waterGoalMl, vm::addWater, vm::undoWater, Modifier.padding(horizontal = 16.dp))
                }
            }
            item(key = "fasting") {
                Staggered(2) {
                    FastingCard(settings, vm::startFasting, vm::stopFasting, Modifier.padding(horizontal = 16.dp))
                }
            }
            Meal.entries.forEachIndexed { i, meal ->
                item(key = meal.name) {
                    Staggered(3 + i) {
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
                            onSaveRecipe = { savingRecipe = meal },
                            aiBusy = aiBusy == meal,
                            onPhoto = { captureMeal = meal; photo() },
                            onVoice = { captureMeal = meal; speak() },
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                }
            }
        }
    }

    editing?.let { e ->
        EditEntryDialog(
            entry = e,
            onDismiss = { editing = null },
            onDelete = {
                vm.delete(e)
                editing = null
                scope.launch {
                    val r = snackbar.showSnackbar("${e.name} gelöscht", actionLabel = "Rückgängig")
                    if (r == androidx.compose.material3.SnackbarResult.ActionPerformed) vm.restore(e)
                }
            },
            onSave = { grams, kcal -> vm.updateAmount(e, grams, kcal); editing = null },
        )
    }

    aiResult?.let { r ->
        QuickAiDialog(
            result = r,
            onConfirm = { items -> vm.confirmAi(r, items) },
            onUndo = { vm.undoAi(r) },
            onClose = { vm.aiResult.value = null },
        )
    }

    savingRecipe?.let { meal ->
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { savingRecipe = null },
            title = { Text("Als Rezept speichern") },
            text = {
                OutlinedTextField(name, { name = it }, label = { Text("Name, z. B. Mein Müsli") }, singleLine = true)
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    savingRecipe = null
                    scope.launch {
                        val n = vm.saveRecipe(meal, name)
                        snackbar.showSnackbar(if (n == 0) "${meal.label} ist leer" else "Rezept \"$name\" mit $n Zutaten gespeichert")
                    }
                }) { Text("Speichern") }
            },
            dismissButton = { TextButton(onClick = { savingRecipe = null }) { Text("Abbrechen") } },
        )
    }
}

@Composable
private fun HeroHeader(
    date: LocalDate,
    entries: List<DiaryEntry>,
    settings: UserSettings,
    activity: Activity?,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
) {
    val active = activity?.activeKcal?.roundToInt() ?: 0
    val goal = settings.kcalGoal + active
    val kcal = entries.sumOf { it.kcal }
    val over = kcal > goal
    val onHero = Color.White
    val track = Color.White.copy(alpha = 0.22f)

    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 36.dp, bottomEnd = 36.dp))
            .background(if (over) Gradients.over else Gradients.hero)
            .statusBarsPadding()
            .padding(start = 8.dp, end = 8.dp, bottom = 20.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = onPrev) { Icon(Icons.Filled.ChevronLeft, "Vorheriger Tag", tint = onHero) }
                AnimatedContent(
                    targetState = date,
                    transitionSpec = {
                        val dir = if (targetState > initialState) 1 else -1
                        (slideInHorizontally { it * dir / 2 } + fadeIn()) togetherWith (slideOutHorizontally { -it * dir / 2 } + fadeOut())
                    },
                    modifier = Modifier.weight(1f),
                    label = "date",
                ) { d ->
                    Text(
                        d.label(), color = onHero, style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.fillMaxWidth().clickable(onClick = onToday),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
                IconButton(onClick = onNext) { Icon(Icons.Filled.ChevronRight, "Nächster Tag", tint = onHero) }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp)) {
                CalorieRing(kcal, goal, size = 150.dp, stroke = 14.dp, trackColor = track, barColor = onHero, textColor = onHero)
                Spacer(Modifier.width(20.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                    StatLine("Ziel", "$goal kcal", onHero)
                    StatLine("Gegessen", "${kcal.roundToInt()} kcal", onHero)
                    if (activity != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.DirectionsWalk, null, tint = onHero, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("${activity.steps} Schritte · +$active kcal", color = onHero, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MacroBar("Eiweiß", entries.sumOf { it.protein }, settings.proteinGoalG, onHero, Modifier.weight(1f), track, onHero)
                    MacroBar("Kohlenh.", entries.sumOf { it.carbs }, settings.carbsGoalG, onHero, Modifier.weight(1f), track, onHero)
                    MacroBar("Fett", entries.sumOf { it.fat }, settings.fatGoalG, onHero, Modifier.weight(1f), track, onHero)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MacroBar("Zucker", entries.sumOf { it.sugar }, settings.sugarGoalG.toDouble(), onHero, Modifier.weight(1f), track, onHero)
                    MacroBar("Ballaststoffe", entries.sumOf { it.fiber }, settings.fiberGoalG.toDouble(), onHero, Modifier.weight(1f), track, onHero)
                }
            }
        }
    }
}

@Composable
private fun StatLine(label: String, value: String, color: Color) {
    Column {
        Text(label, color = color.copy(alpha = 0.75f), style = MaterialTheme.typography.labelMedium)
        Text(value, color = color, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun WaterCard(ml: Int, goal: Int, onAdd: (Int) -> Unit, onUndo: () -> Unit, modifier: Modifier) {
    val glasses = (goal / 250).coerceIn(1, 12)
    val filled = ml / 250
    SoftCard(modifier) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Filled.WaterDrop, MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Wasser", style = MaterialTheme.typography.titleMedium)
                    Text("${"%.2f".format(ml / 1000.0)} von ${"%.1f".format(goal / 1000.0)} l", style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = onUndo, enabled = ml > 0) { Icon(Icons.AutoMirrored.Filled.Undo, "Rückgängig") }
                FilledTonalButton(onClick = { onAdd(250) }) { Text("+250 ml") }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(glasses) { i ->
                    val c by animateColorAsState(
                        if (i < filled) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceVariant,
                        tween(400, delayMillis = 40 * i), label = "glass",
                    )
                    Box(Modifier.weight(1f).height(10.dp).clip(CircleShape).background(c))
                }
            }
        }
    }
}

@Composable
private fun FastingCard(s: UserSettings, onStart: (Int) -> Unit, onStop: () -> Unit, modifier: Modifier) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(s.fastingStart) {
        while (s.fastingStart != 0L) { now = System.currentTimeMillis(); delay(30_000) }
    }
    var hours by remember(s.fastingHours) { mutableStateOf(s.fastingHours) }
    val tertiary = MaterialTheme.colorScheme.tertiary
    val track = MaterialTheme.colorScheme.surfaceVariant

    SoftCard(modifier.animateContentSize()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (s.fastingStart == 0L) {
                IconBadge(Icons.Filled.Timer, tertiary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Intervallfasten", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        FilledTonalButton(onClick = { onStart(hours) }) { Text("Start") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(14, 16, 18).forEach { h ->
                            FilterChip(hours == h, { hours = h }, { Text("$h h", maxLines = 1, softWrap = false) })
                        }
                    }
                }
            } else {
                val elapsedMin = ((now - s.fastingStart) / 60_000).coerceAtLeast(0)
                val target = s.fastingHours * 60f
                val p by animateFloatAsState((elapsedMin / target).coerceIn(0f, 1f), tween(900), label = "fast")
                Box(contentAlignment = Alignment.Center) {
                    Canvas(Modifier.size(56.dp)) {
                        val st = 6.dp.toPx()
                        val arc = Size(size.width - st, size.height - st)
                        drawArc(track, 0f, 360f, false, Offset(st / 2, st / 2), arc, style = Stroke(st))
                        drawArc(tertiary, -90f, 360f * p, false, Offset(st / 2, st / 2), arc, style = Stroke(st, cap = StrokeCap.Round))
                    }
                    Text("${(p * 100).roundToInt()}%", style = MaterialTheme.typography.labelSmall)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "%d:%02d h gefastet".format(elapsedMin / 60, elapsedMin % 60),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    val left = (target - elapsedMin).roundToInt()
                    Text(
                        if (left > 0) "Noch %d:%02d h bis %d h".format(left / 60, left % 60, s.fastingHours) else "Ziel erreicht 🎉",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                TextButton(onClick = onStop) { Text("Beenden") }
            }
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
    onSaveRecipe: () -> Unit,
    aiBusy: Boolean,
    onPhoto: () -> Unit,
    onVoice: () -> Unit,
    modifier: Modifier,
) {
    var menu by remember { mutableStateOf(false) }
    val kcal by animateFloatAsState(entries.sumOf { it.kcal }.toFloat(), tween(600), label = "mealKcal")
    SoftCard(modifier) {
        Column(Modifier.animateContentSize().padding(vertical = 8.dp)) {
            Row(Modifier.padding(start = 16.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBadge(meal.icon(), meal.color())
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(meal.label, style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (entries.isEmpty()) "Noch nichts eingetragen" else "${entries.size} Einträge",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text("${kcal.roundToInt()} kcal", fontWeight = FontWeight.SemiBold)
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Mehr") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Von gestern kopieren") },
                            leadingIcon = { Icon(Icons.Filled.ContentCopy, null) },
                            onClick = { menu = false; onCopyYesterday() },
                        )
                        DropdownMenuItem(
                            text = { Text("Als Rezept speichern") },
                            leadingIcon = { Icon(Icons.Filled.BookmarkAdd, null) },
                            onClick = { menu = false; onSaveRecipe() },
                        )
                    }
                }
            }
            if (entries.isNotEmpty()) HorizontalDivider(Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
            entries.forEach { e ->
                key(e.id) {
                    val visible = remember { MutableTransitionState(false).apply { targetState = true } }
                    AnimatedVisibility(visibleState = visible, enter = fadeIn() + expandVertically()) {
                        EntryRow(e, onClick = { onEntryClick(e) })
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onAdd) {
                    Icon(Icons.Filled.Add, null)
                    Spacer(Modifier.width(4.dp))
                    Text("Hinzufügen")
                }
                Spacer(Modifier.weight(1f))
                AnimatedContent(aiBusy, label = "aiBusy") { busy ->
                    if (busy) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 8.dp)) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.tertiary)
                            Spacer(Modifier.width(8.dp))
                            Text("KI rechnet …", style = MaterialTheme.typography.labelMedium)
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalIconButton(onClick = onPhoto) { Icon(Icons.Filled.PhotoCamera, "Essen fotografieren") }
                            FilledTonalIconButton(onClick = onVoice) { Icon(Icons.Filled.Mic, "Essen per Sprache eintragen") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EntryRow(e: DiaryEntry, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (e.source in setOf(EntrySource.AI_PHOTO, EntrySource.AI_TEXT, EntrySource.AI_LABEL)) {
                    Icon(
                        Icons.Filled.AutoAwesome, "KI-Schätzung",
                        Modifier.padding(end = 4.dp).size(14.dp),
                        tint = MaterialTheme.colorScheme.tertiary,
                    )
                }
                Text(e.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
            }
            val details = listOfNotNull(e.brand, e.amountGrams?.gramText(), e.note).joinToString(" · ")
            if (details.isNotEmpty()) {
                Text(
                    details, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text("${e.kcal.roundToInt()}", style = MaterialTheme.typography.titleSmall)
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
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NutrientPill("kcal", entry.kcal.roundToInt().toString(), MaterialTheme.colorScheme.primary)
                    NutrientPill("E", entry.protein.gramText(), MacroColors.protein)
                    NutrientPill("K", entry.carbs.gramText(), MacroColors.carbs)
                    NutrientPill("F", entry.fat.gramText(), MacroColors.fat)
                }
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

@Composable
private fun NutrientPill(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleSmall, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun QuickAiDialog(
    result: QuickAiResult,
    onConfirm: (List<AiFoodItem>) -> Unit,
    onUndo: () -> Unit,
    onClose: () -> Unit,
) {
    val items = result.estimate.items
    var selected by remember(result) { mutableStateOf(items.indices.toSet()) }
    val total = if (result.alreadyAdded) items.sumOf { it.kcal } else items.filterIndexed { i, _ -> i in selected }.sumOf { it.kcal }

    AlertDialog(
        onDismissRequest = { if (result.alreadyAdded) onClose() },
        icon = { Icon(Icons.Filled.AutoAwesome, null, tint = MaterialTheme.colorScheme.tertiary) },
        title = {
            Text(if (result.alreadyAdded) "Zu ${result.meal.label} hinzugefügt" else "Deine Mahlzeit")
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                result.photo?.let { bytes ->
                    val bmp = remember(bytes) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }
                    if (bmp != null) {
                        Image(
                            bmp, null, contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().height(150.dp).clip(MaterialTheme.shapes.medium),
                        )
                    }
                }
                result.spokenText?.let {
                    Text("„$it“", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (result.estimate.summary.isNotBlank()) {
                    Text(result.estimate.summary, style = MaterialTheme.typography.bodyMedium)
                }
                Text("${total.roundToInt()} kcal", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                items.forEachIndexed { i, item ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!result.alreadyAdded) {
                            Checkbox(checked = i in selected, onCheckedChange = { on -> selected = if (on) selected + i else selected - i })
                        }
                        Column(Modifier.weight(1f)) {
                            Text(item.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "${item.amountGrams.roundToInt()} g · E ${item.protein.gramText()} · K ${item.carbs.gramText()} · F ${item.fat.gramText()}",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text("${item.kcal.roundToInt()}", style = MaterialTheme.typography.titleSmall)
                    }
                }
                if (result.estimate.notes.isNotBlank()) {
                    Text(result.estimate.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    if (result.alreadyAdded) "Werte sind KI-Schätzungen – tippe im Tagebuch auf einen Eintrag, um die Menge zu ändern."
                    else "Werte sind KI-Schätzungen.",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            if (result.alreadyAdded) {
                TextButton(onClick = onClose) { Text("Fertig") }
            } else {
                TextButton(enabled = selected.isNotEmpty(), onClick = {
                    onConfirm(items.filterIndexed { i, _ -> i in selected })
                }) { Text("Zu ${result.meal.label} hinzufügen") }
            }
        },
        dismissButton = {
            if (result.alreadyAdded) {
                TextButton(onClick = onUndo) { Text("Rückgängig", color = MaterialTheme.colorScheme.error) }
            } else {
                TextButton(onClick = onClose) { Text("Verwerfen") }
            }
        },
    )
}
