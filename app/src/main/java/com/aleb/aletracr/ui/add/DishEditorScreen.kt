package com.aleb.aletracr.ui.add

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aleb.aletracr.data.RecipeItem
import com.aleb.aletracr.data.Repository
import com.aleb.aletracr.remote.FoodProduct
import com.aleb.aletracr.ui.NumberField
import com.aleb.aletracr.ui.SoftCard
import com.aleb.aletracr.ui.Staggered
import com.aleb.aletracr.util.gramText
import com.aleb.aletracr.util.toDecimal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class DishEditorViewModel(private val repo: Repository, private val dishId: Long) : ViewModel() {
    val name = MutableStateFlow("")
    val items = MutableStateFlow<List<RecipeItem>>(emptyList())

    val query = MutableStateFlow("")
    val results = MutableStateFlow<List<FoodProduct>>(emptyList())
    val searching = MutableStateFlow(false)
    val searchError = MutableStateFlow<String?>(null)
    private var searchJob: Job? = null

    init {
        if (dishId != 0L) viewModelScope.launch {
            repo.recipes.recipe(dishId)?.let { name.value = it.name }
            items.value = repo.recipes.items(dishId)
        }
    }

    fun setQuery(q: String) {
        query.value = q
        searchJob?.cancel()
        if (q.trim().length < 2) { results.value = emptyList(); return }
        searchJob = viewModelScope.launch {
            delay(400)
            searching.value = true
            searchError.value = null
            val local = repo.searchLocal(q.trim())
            results.value = local
            try {
                val online = repo.searchOnline(q.trim())
                results.value = (local + online).distinctBy { it.barcode ?: (it.name + it.brand) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                searchError.value = "Online-Datenbank gerade nicht erreichbar – lokale Treffer angezeigt."
            } finally {
                searching.value = false
            }
        }
    }

    fun addProduct(p: FoodProduct, grams: Double) {
        val f = grams / 100.0
        items.update {
            it + RecipeItem(
                recipeId = dishId, name = p.name, brand = p.brand, amountGrams = grams,
                kcal = p.kcalPer100 * f, protein = p.proteinPer100 * f, carbs = p.carbsPer100 * f,
                fat = p.fatPer100 * f, sugar = (p.sugarPer100 ?: 0.0) * f, fiber = (p.fiberPer100 ?: 0.0) * f,
            )
        }
        query.value = ""
        results.value = emptyList()
    }

    fun addQuick(name: String, kcal: Double) {
        items.update { it + RecipeItem(recipeId = dishId, name = name.ifBlank { "Zutat" }, kcal = kcal) }
    }

    /** Changing grams scales that ingredient's nutrition values. */
    fun setGrams(index: Int, grams: Double) = items.update { list ->
        list.mapIndexed { i, it ->
            val old = it.amountGrams
            if (i != index || old == null || old <= 0) it else {
                val f = grams / old
                it.copy(amountGrams = grams, kcal = it.kcal * f, protein = it.protein * f, carbs = it.carbs * f,
                    fat = it.fat * f, sugar = it.sugar * f, fiber = it.fiber * f)
            }
        }
    }

    fun remove(index: Int) = items.update { it.filterIndexed { i, _ -> i != index } }

    fun save(onDone: () -> Unit) = viewModelScope.launch {
        repo.saveDish(dishId, name.value, items.value)
        onDone()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DishEditorScreen(repo: Repository, dishId: Long, onDone: () -> Unit) {
    val vm: DishEditorViewModel = viewModel(
        key = "dish-$dishId",
        factory = viewModelFactory { initializer { DishEditorViewModel(repo, dishId) } },
    )
    val name by vm.name.collectAsState()
    val items by vm.items.collectAsState()
    val query by vm.query.collectAsState()
    val results by vm.results.collectAsState()
    val searching by vm.searching.collectAsState()
    val searchError by vm.searchError.collectAsState()
    var picked by remember { mutableStateOf<FoodProduct?>(null) }
    var quickOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (dishId == 0L) "Neues Gericht" else "Gericht bearbeiten") },
                navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") } },
                actions = {
                    TextButton(enabled = name.isNotBlank() && items.isNotEmpty(), onClick = { vm.save(onDone) }) { Text("Speichern") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                OutlinedTextField(
                    value = name, onValueChange = { vm.name.value = it },
                    label = { Text("Name des Gerichts, z. B. Mein Frühstücksmüsli") },
                    singleLine = true, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                SoftCard {
                    Column(Modifier.padding(16.dp)) {
                        Text("${items.sumOf { it.kcal }.roundToInt()} kcal", style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.primary)
                        Text(
                            "${items.size} Zutaten · E ${items.sumOf { it.protein }.gramText()} · K ${items.sumOf { it.carbs }.gramText()} · F ${items.sumOf { it.fat }.gramText()}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            itemsIndexed(items, key = { i, it -> "$i-${it.name}" }) { i, it ->
                Staggered(i.coerceAtMost(6)) {
                    IngredientRow(it, onGrams = { g -> vm.setGrams(i, g) }, onDelete = { vm.remove(i) })
                }
            }
            item {
                Text("Zutat hinzufügen", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                OutlinedTextField(
                    value = query, onValueChange = vm::setQuery,
                    placeholder = { Text("Lebensmittel suchen, z. B. Haferflocken") },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    singleLine = true, shape = CircleShape,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { vm.setQuery(query) }),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                AnimatedVisibility(searching) { LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 4.dp)) }
                searchError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            }
            itemsIndexed(results.take(25), key = { i, p -> "r$i-${p.barcode ?: p.name}" }) { _, p ->
                ListItem(
                    modifier = Modifier.clickable { picked = p },
                    headlineContent = { Text(p.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = {
                        Text(listOfNotNull(p.brand, "${p.kcalPer100.roundToInt()} kcal/100 g").joinToString(" · "), maxLines = 1)
                    },
                    trailingContent = { Icon(Icons.Filled.Add, "Hinzufügen") },
                )
            }
            item {
                OutlinedButton(onClick = { quickOpen = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Bolt, null); Spacer(Modifier.width(6.dp)); Text("Zutat nur mit Kalorien")
                }
            }
            item {
                Button(
                    enabled = name.isNotBlank() && items.isNotEmpty(),
                    onClick = { vm.save(onDone) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                ) { Text("Gericht speichern") }
            }
        }
    }

    picked?.let { p ->
        var grams by remember(p) { mutableStateOf((p.servingGrams ?: 100.0).roundToInt().toString()) }
        val g = grams.toDecimal() ?: 0.0
        AlertDialog(
            onDismissRequest = { picked = null },
            title = { Text(p.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(grams, { grams = it }, "Menge", Modifier.fillMaxWidth(), suffix = "g")
                    Text("${(p.kcalPer100 * g / 100).roundToInt()} kcal", style = MaterialTheme.typography.headlineSmall)
                }
            },
            confirmButton = { Button(enabled = g > 0, onClick = { vm.addProduct(p, g); picked = null }) { Text("Hinzufügen") } },
            dismissButton = { TextButton(onClick = { picked = null }) { Text("Abbrechen") } },
        )
    }

    if (quickOpen) {
        var n by remember { mutableStateOf("") }
        var k by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { quickOpen = false },
            title = { Text("Zutat mit Kalorien") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(n, { n = it }, label = { Text("Name") }, singleLine = true)
                    NumberField(k, { k = it }, "Kalorien", suffix = "kcal")
                }
            },
            confirmButton = {
                Button(enabled = (k.toDecimal() ?: 0.0) > 0, onClick = { vm.addQuick(n, k.toDecimal() ?: 0.0); quickOpen = false }) { Text("Hinzufügen") }
            },
            dismissButton = { TextButton(onClick = { quickOpen = false }) { Text("Abbrechen") } },
        )
    }
}

@Composable
private fun IngredientRow(item: RecipeItem, onGrams: (Double) -> Unit, onDelete: () -> Unit) {
    var grams by remember(item.name) { mutableStateOf(item.amountGrams?.roundToInt()?.toString() ?: "") }
    LaunchedEffect(item.amountGrams) {
        val g = item.amountGrams?.roundToInt()?.toString()
        if (g != null && grams.toDecimal()?.roundToInt()?.toString() != g) grams = g
    }
    SoftCard {
        Row(Modifier.padding(start = 16.dp, top = 6.dp, bottom = 6.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${item.kcal.roundToInt()} kcal" + (item.brand?.let { " · $it" } ?: ""), style = MaterialTheme.typography.bodySmall)
            }
            if (item.amountGrams != null) {
                NumberField(grams, { s -> grams = s; s.toDecimal()?.takeIf { it > 0 }?.let(onGrams) }, "g", Modifier.width(90.dp))
            }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "Entfernen") }
        }
    }
}
