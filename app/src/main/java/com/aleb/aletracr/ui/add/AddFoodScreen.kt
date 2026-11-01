package com.aleb.aletracr.ui.add

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil.compose.AsyncImage
import com.aleb.aletracr.data.Meal
import com.aleb.aletracr.data.Repository
import com.aleb.aletracr.remote.FoodProduct
import com.aleb.aletracr.ui.NumberField
import com.aleb.aletracr.util.ImageUtils
import com.aleb.aletracr.util.gramText
import com.aleb.aletracr.util.toDecimal
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private val TABS = listOf("Suchen", "Zuletzt", "Favoriten", "KI", "Schnell")
const val QUICK_ADD_TAB = 4

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFoodScreen(repo: Repository, date: String, meal: Meal, initialTab: Int = 0, onDone: () -> Unit) {
    val vm: AddFoodViewModel = viewModel(
        key = "add-$date-$meal",
        factory = viewModelFactory { initializer { AddFoodViewModel(repo, date, meal) } },
    )
    var tab by rememberSaveable { mutableIntStateOf(initialTab.coerceIn(0, TABS.lastIndex)) }
    val selected by vm.selected.collectAsState()
    val message by vm.message.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); vm.message.value = null }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { MealPicker(vm) },
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            PrimaryScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp) {
                TABS.forEachIndexed { i, t ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) })
                }
            }
            when (tab) {
                0 -> SearchTab(vm)
                1 -> ProductList(vm, vm.recent.collectAsState().value, "Noch nichts eingetragen.")
                2 -> ProductList(vm, vm.favorites.collectAsState().value, "Markiere Produkte mit ☆ als Favorit.")
                3 -> AiTab(vm, onDone)
                QUICK_ADD_TAB -> QuickAddTab(vm, onDone)
            }
        }
    }

    selected?.let { (product, _) ->
        PortionDialog(
            product = product,
            onDismiss = { vm.selected.value = null },
            onConfirm = { grams -> vm.logSelected(grams, onDone) },
            onToggleFavorite = { vm.toggleFavorite(product) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MealPicker(vm: AddFoodViewModel) {
    val meal by vm.meal.collectAsState()
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        Row(
            Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(meal.label)
            ExposedDropdownMenuDefaults.TrailingIcon(expanded)
        }
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Meal.entries.forEach { m ->
                DropdownMenuItem(text = { Text(m.label) }, onClick = { vm.meal.value = m; expanded = false })
            }
        }
    }
}

// ----------------------------------------------------------------------------- Search

@Composable
private fun SearchTab(vm: AddFoodViewModel) {
    val state by vm.search.collectAsState()
    val context = LocalContext.current

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = state.query,
                onValueChange = vm::setQuery,
                placeholder = { Text("Produkt oder Marke suchen") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { vm.runSearch() }),
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = {
                GmsBarcodeScanning.getClient(context).startScan()
                    .addOnSuccessListener { barcode -> barcode.rawValue?.let(vm::onBarcode) }
            }) {
                Icon(Icons.Filled.QrCodeScanner, "Barcode scannen")
            }
        }
        if (state.loading) {
            Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp))
        }
        val empty = if (state.searched) "Keine Treffer." else "Daten von Open Food Facts – über 3 Mio. Produkte."
        ProductList(vm, state.results, empty)
    }
}

@Composable
private fun ProductList(vm: AddFoodViewModel, products: List<FoodProduct>, emptyText: String) {
    if (products.isEmpty()) {
        Text(emptyText, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(products) { p ->
            ListItem(
                modifier = Modifier.clickable { vm.select(p) },
                leadingContent = {
                    if (p.imageUrl != null) {
                        AsyncImage(
                            model = p.imageUrl, contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)),
                        )
                    } else {
                        Spacer(Modifier.size(44.dp))
                    }
                },
                headlineContent = { Text(p.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = {
                    val serving = p.servingGrams?.let { " · Portion ${it.gramText()}" }.orEmpty()
                    Text(listOfNotNull(p.brand).joinToString() + (if (p.brand != null) " · " else "") +
                        "${p.kcalPer100.roundToInt()} kcal/100 g$serving", maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
            )
        }
    }
}

@Composable
private fun PortionDialog(
    product: FoodProduct,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit,
    onToggleFavorite: () -> Unit,
) {
    var grams by remember { mutableStateOf((product.servingGrams ?: 100.0).roundToInt().toString()) }
    var fav by remember { mutableStateOf(false) }
    val g = grams.toDecimal() ?: 0.0
    val f = g / 100.0
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(product.name, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                IconButton(onClick = { fav = !fav; onToggleFavorite() }) {
                    Icon(if (fav) Icons.Filled.Star else Icons.Filled.StarBorder, "Favorit")
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                product.brand?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                NumberField(grams, { grams = it }, "Menge", suffix = "g", modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    product.servingGrams?.let { s ->
                        FilterChip(
                            selected = g == s, onClick = { grams = s.roundToInt().toString() },
                            label = { Text(product.servingLabel ?: "1 Portion") },
                        )
                    }
                    FilterChip(selected = g == 100.0, onClick = { grams = "100" }, label = { Text("100 g") })
                }
                Text("${(product.kcalPer100 * f).roundToInt()} kcal", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Eiweiß ${(product.proteinPer100 * f).gramText()} · KH ${(product.carbsPer100 * f).gramText()} · Fett ${(product.fatPer100 * f).gramText()}",
                    style = MaterialTheme.typography.bodySmall,
                )
                product.sugarPer100?.let {
                    Text("davon Zucker ${(it * f).gramText()}", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { TextButton(enabled = g > 0, onClick = { onConfirm(g) }) { Text("Hinzufügen") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

// ----------------------------------------------------------------------------- AI

@Composable
private fun AiTab(vm: AddFoodViewModel, onDone: () -> Unit) {
    val state by vm.ai.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingUri by remember { mutableStateOf<Uri?>(null) }

    fun load(uri: Uri) = scope.launch {
        val bytes = withContext(Dispatchers.IO) { ImageUtils.loadScaledJpeg(context, uri) }
        vm.setImage(bytes)
    }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = pendingUri
        if (ok && uri != null) load(uri)
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) load(uri)
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                "Fotografiere dein Essen oder beschreibe es – die KI schätzt Kalorien und Makros.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    val uri = ImageUtils.newPhotoUri(context)
                    pendingUri = uri
                    camera.launch(uri)
                }) {
                    Icon(Icons.Filled.PhotoCamera, null); Spacer(Modifier.width(6.dp)); Text("Foto")
                }
                OutlinedButton(onClick = {
                    gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) {
                    Icon(Icons.Filled.PhotoLibrary, null); Spacer(Modifier.width(6.dp)); Text("Galerie")
                }
            }
        }
        state.image?.let { bytes ->
            item {
                val bmp = remember(bytes) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }
                if (bmp != null) {
                    Box {
                        Image(
                            bmp, "Foto der Mahlzeit", contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(12.dp)),
                        )
                        IconButton(onClick = { vm.setImage(null) }, modifier = Modifier.align(Alignment.TopEnd)) {
                            Icon(Icons.Filled.Close, "Foto entfernen")
                        }
                    }
                }
            }
        }
        item {
            OutlinedTextField(
                value = state.description,
                onValueChange = vm::setDescription,
                label = { Text("Was hast du gegessen? (optional bei Foto)") },
                placeholder = { Text("z. B. 2 Brötchen mit Butter und Käse, 1 Latte Macchiato") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Button(onClick = vm::analyze, enabled = !state.loading, modifier = Modifier.fillMaxWidth()) {
                if (state.loading) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Analysiere …")
                } else {
                    Icon(Icons.Filled.AutoAwesome, null); Spacer(Modifier.width(6.dp)); Text("Kalorien schätzen")
                }
            }
        }
        state.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        if (state.items.isNotEmpty()) {
            itemsIndexed(state.items) { i, e -> AiItemRow(e, onToggle = { vm.toggleAiItem(i) }, onGrams = { vm.setAiGrams(i, it) }) }
            item {
                val sel = state.items.filter { it.selected }
                Text(
                    "Summe: ${sel.sumOf { it.item.kcal }.roundToInt()} kcal",
                    style = MaterialTheme.typography.titleMedium,
                )
                state.notes?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                Text(
                    "KI-Werte sind Schätzungen. Passe die Mengen bei Bedarf an.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                Button(
                    onClick = { vm.saveAiItems(onDone) },
                    enabled = state.items.any { it.selected },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Ausgewählte hinzufügen") }
            }
        }
    }
}

@Composable
private fun AiItemRow(e: EditableAiItem, onToggle: () -> Unit, onGrams: (Double) -> Unit) {
    val item = e.item
    var grams by remember(item.name) { mutableStateOf(item.amountGrams.roundToInt().toString()) }
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = e.selected, onCheckedChange = { onToggle() })
            Column(Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    "${item.kcal.roundToInt()} kcal · E ${item.protein.gramText()} · K ${item.carbs.gramText()} · F ${item.fat.gramText()}",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text("Sicherheit: ${item.confidence}", style = MaterialTheme.typography.labelSmall)
            }
            NumberField(
                grams,
                { s -> grams = s; s.toDecimal()?.takeIf { v -> v > 0 }?.let(onGrams) },
                "g",
                modifier = Modifier.width(90.dp),
            )
        }
    }
}

// ----------------------------------------------------------------------------- Quick add

@Composable
private fun QuickAddTab(vm: AddFoodViewModel, onDone: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var kcal by rememberSaveable { mutableStateOf("") }
    var protein by rememberSaveable { mutableStateOf("") }
    var carbs by rememberSaveable { mutableStateOf("") }
    var fat by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Schnelleintrag – wenn du nur die Kalorien kennst.", style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(name, { name = it }, label = { Text("Bezeichnung (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        NumberField(kcal, { kcal = it }, "Kalorien", suffix = "kcal", modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(protein, { protein = it }, "Eiweiß", Modifier.weight(1f), suffix = "g")
            NumberField(carbs, { carbs = it }, "KH", Modifier.weight(1f), suffix = "g")
            NumberField(fat, { fat = it }, "Fett", Modifier.weight(1f), suffix = "g")
        }
        OutlinedTextField(note, { note = it }, label = { Text("Notiz (optional)") }, modifier = Modifier.fillMaxWidth())
        val k = kcal.toDecimal()
        Button(
            enabled = k != null && k > 0,
            onClick = {
                vm.quickAdd(
                    name, k ?: 0.0, protein.toDecimal() ?: 0.0, carbs.toDecimal() ?: 0.0,
                    fat.toDecimal() ?: 0.0, note, onDone,
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Eintragen") }
    }
}
