package com.aleb.aletracr.ui.add

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil.compose.AsyncImage
import com.aleb.aletracr.data.Meal
import com.aleb.aletracr.data.RecipeWithTotals
import com.aleb.aletracr.data.Repository
import com.aleb.aletracr.remote.FoodProduct
import com.aleb.aletracr.ui.IconBadge
import com.aleb.aletracr.ui.NumberField
import com.aleb.aletracr.ui.SoftCard
import com.aleb.aletracr.ui.Staggered
import com.aleb.aletracr.ui.theme.Gradients
import com.aleb.aletracr.ui.theme.MacroColors
import com.aleb.aletracr.util.ImageUtils
import com.aleb.aletracr.util.gramText
import com.aleb.aletracr.util.toDecimal
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private val TABS = listOf("Suchen", "Zuletzt", "Favoriten", "Rezepte", "KI", "Schnell")
const val QUICK_ADD_TAB = 5

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFoodScreen(repo: Repository, date: String, meal: Meal, initialTab: Int = 0, onDone: () -> Unit) {
    val vm: AddFoodViewModel = viewModel(
        key = "add-$date-$meal",
        factory = viewModelFactory { initializer { AddFoodViewModel(repo, date, meal) } },
    )
    var tab by rememberSaveable { mutableIntStateOf(initialTab.coerceIn(0, TABS.lastIndex)) }
    val selected by vm.selected.collectAsState()
    val isFavorite by vm.selectedIsFavorite.collectAsState()
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
            PrimaryScrollableTabRow(selectedTabIndex = tab, edgePadding = 12.dp, divider = {}) {
                TABS.forEachIndexed { i, t ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) })
                }
            }
            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    val dir = if (targetState > initialState) 1 else -1
                    (slideInHorizontally(tween(260)) { it * dir / 3 } + fadeIn(tween(260))) togetherWith
                        (slideOutHorizontally(tween(200)) { -it * dir / 3 } + fadeOut(tween(200)))
                },
                label = "tabs",
            ) { t ->
                when (t) {
                    0 -> SearchTab(vm)
                    1 -> ProductList(vm, vm.recent.collectAsState().value, "Hier erscheinen deine zuletzt gegessenen Produkte.")
                    2 -> ProductList(vm, vm.favorites.collectAsState().value, "Markiere Produkte mit ☆ als Favorit.")
                    3 -> RecipesTab(vm, onDone)
                    4 -> AiTab(vm, onDone)
                    else -> QuickAddTab(vm, onDone)
                }
            }
        }
    }

    selected?.let { (product, _) ->
        PortionDialog(
            product = product,
            isFavorite = isFavorite,
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
            Text(meal.label, style = MaterialTheme.typography.titleLarge)
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
                shape = CircleShape,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { vm.runSearch() }),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            FilledTonalIconButton(
                onClick = {
                    GmsBarcodeScanning.getClient(context).startScan()
                        .addOnSuccessListener { barcode -> barcode.rawValue?.let(vm::onBarcode) }
                },
                modifier = Modifier.size(52.dp),
            ) {
                Icon(Icons.Filled.QrCodeScanner, "Barcode scannen")
            }
        }
        AnimatedVisibility(state.loading) {
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        }
        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        }
        val empty = if (state.searched) "Keine Treffer." else "Suche in Open Food Facts – über 3 Mio. Produkte. Oder scanne einen Barcode."
        ProductList(vm, state.results, empty)
    }
}

@Composable
private fun EmptyState(text: String, icon: ImageVector = Icons.Filled.Restaurant) {
    Column(
        Modifier.fillMaxWidth().padding(top = 48.dp, start = 32.dp, end = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconBadge(icon, MaterialTheme.colorScheme.primary, size = 72.dp)
        Spacer(Modifier.height(16.dp))
        Text(
            text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ProductList(vm: AddFoodViewModel, products: List<FoodProduct>, emptyText: String) {
    if (products.isEmpty()) {
        EmptyState(emptyText)
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        itemsIndexed(products, key = { i, p -> (p.barcode ?: p.name) + i }) { i, p ->
            Staggered(i.coerceAtMost(8)) {
                ListItem(
                    modifier = Modifier.clickable { vm.select(p) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    leadingContent = {
                        if (p.imageUrl != null) {
                            AsyncImage(
                                model = p.imageUrl, contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(48.dp).clip(MaterialTheme.shapes.small),
                            )
                        } else {
                            IconBadge(Icons.Filled.Restaurant, MaterialTheme.colorScheme.primary, size = 48.dp)
                        }
                    },
                    headlineContent = { Text(p.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = {
                        val parts = listOfNotNull(
                            p.brand,
                            "${p.kcalPer100.roundToInt()} kcal/100 g",
                            p.servingGrams?.let { "Portion ${it.gramText()}" },
                        )
                        Text(parts.joinToString(" · "), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                )
            }
        }
    }
}

@Composable
private fun PortionDialog(
    product: FoodProduct,
    isFavorite: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit,
    onToggleFavorite: () -> Unit,
) {
    var grams by remember { mutableStateOf((product.servingGrams ?: 100.0).roundToInt().toString()) }
    val g = grams.toDecimal() ?: 0.0
    val f = g / 100.0
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(product.name, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                IconButton(onClick = onToggleFavorite) {
                    AnimatedContent(isFavorite, transitionSpec = { scaleIn() togetherWith fadeOut() }, label = "fav") { fav ->
                        Icon(
                            if (fav) Icons.Filled.Star else Icons.Filled.StarBorder, "Favorit",
                            tint = if (fav) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                AnimatedContent((product.kcalPer100 * f).roundToInt(), label = "kcal") { k ->
                    Text("$k kcal", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Nutrient("Eiweiß", (product.proteinPer100 * f).gramText(), MacroColors.protein)
                    Nutrient("KH", (product.carbsPer100 * f).gramText(), MacroColors.carbs)
                    Nutrient("Fett", (product.fatPer100 * f).gramText(), MacroColors.fat)
                    product.sugarPer100?.let { Nutrient("Zucker", (it * f).gramText(), MacroColors.sugar) }
                }
            }
        },
        confirmButton = { Button(enabled = g > 0, onClick = { onConfirm(g) }) { Text("Hinzufügen") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

@Composable
private fun Nutrient(label: String, value: String, color: Color) {
    Column {
        Text(value, style = MaterialTheme.typography.titleSmall, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ----------------------------------------------------------------------------- Recipes

@Composable
private fun RecipesTab(vm: AddFoodViewModel, onDone: () -> Unit) {
    val recipes by vm.recipes.collectAsState()
    var chosen by remember { mutableStateOf<RecipeWithTotals?>(null) }
    if (recipes.isEmpty()) {
        EmptyState(
            "Noch keine Rezepte. Trage eine Mahlzeit ein und wähle im Tagebuch ⋮ → „Als Rezept speichern“.",
            Icons.Filled.MenuBook,
        )
    } else {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(recipes, key = { it.id }) { r ->
                SoftCard(Modifier.clickable { chosen = r }) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(Icons.Filled.MenuBook, MaterialTheme.colorScheme.tertiary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(r.name, style = MaterialTheme.typography.titleMedium)
                            Text("${r.itemCount} Zutaten · ${r.kcal.roundToInt()} kcal", style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onClick = { vm.deleteRecipe(r.id) }) { Icon(Icons.Filled.Delete, "Löschen") }
                    }
                }
            }
        }
    }
    chosen?.let { r ->
        var portion by remember { mutableStateOf(1.0) }
        AlertDialog(
            onDismissRequest = { chosen = null },
            title = { Text(r.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Portion")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0.5 to "½", 1.0 to "1×", 1.5 to "1½", 2.0 to "2×").forEach { (v, l) ->
                            FilterChip(portion == v, { portion = v }, { Text(l) })
                        }
                    }
                    Text("${(r.kcal * portion).roundToInt()} kcal", style = MaterialTheme.typography.headlineSmall)
                }
            },
            confirmButton = { Button(onClick = { chosen = null; vm.logRecipe(r.id, portion, onDone) }) { Text("Eintragen") } },
            dismissButton = { TextButton(onClick = { chosen = null }) { Text("Abbrechen") } },
        )
    }
}

// ----------------------------------------------------------------------------- AI

@Composable
private fun AiTab(vm: AddFoodViewModel, onDone: () -> Unit) {
    val state by vm.ai.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var pendingIsLabel by remember { mutableStateOf(false) }

    fun load(uri: Uri, label: Boolean) = scope.launch {
        val bytes = withContext(Dispatchers.IO) { ImageUtils.loadScaledJpeg(context, uri) } ?: return@launch
        if (label) vm.scanLabel(bytes) else vm.setImage(bytes)
    }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = pendingUri
        if (ok && uri != null) load(uri, pendingIsLabel)
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) load(uri, false)
    }
    fun takePhoto(label: Boolean) {
        val uri = ImageUtils.newPhotoUri(context)
        pendingUri = uri
        pendingIsLabel = label
        camera.launch(uri)
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Box(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(Gradients.ai).padding(18.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.AutoAwesome, null, tint = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text("KI-Kalorienschätzung", color = Color.White, style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Fotografiere deinen Teller oder beschreibe dein Essen – die KI erkennt Lebensmittel, Mengen und Nährwerte.",
                        color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { takePhoto(false) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.PhotoCamera, null); Spacer(Modifier.width(6.dp)); Text("Foto")
                }
                OutlinedButton(
                    onClick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    modifier = Modifier.weight(1f),
                ) {
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
                            modifier = Modifier.fillMaxWidth().height(220.dp).clip(MaterialTheme.shapes.large),
                        )
                        FilledTonalIconButton(onClick = { vm.setImage(null) }, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
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
                label = { Text("Was hast du gegessen?") },
                placeholder = { Text("z. B. 2 Brötchen mit Butter und Käse, 1 Latte Macchiato") },
                minLines = 2,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item { AiButton(loading = state.loading, onClick = vm::analyze) }
        item {
            TextButton(onClick = { takePhoto(true) }, enabled = !state.labelLoading, modifier = Modifier.fillMaxWidth()) {
                if (state.labelLoading) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Lese Nährwerttabelle …")
                } else {
                    Icon(Icons.Filled.DocumentScanner, null); Spacer(Modifier.width(6.dp))
                    Text("Nährwerttabelle fotografieren")
                }
            }
        }
        state.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        if (state.items.isNotEmpty()) {
            itemsIndexed(state.items, key = { i, e -> "$i-${e.item.name}" }) { i, e ->
                Staggered(i) { AiItemRow(e, onToggle = { vm.toggleAiItem(i) }, onGrams = { vm.setAiGrams(i, it) }) }
            }
            item {
                val sel = state.items.filter { it.selected }
                SoftCard {
                    Column(Modifier.padding(16.dp)) {
                        Text("Summe: ${sel.sumOf { it.item.kcal }.roundToInt()} kcal", style = MaterialTheme.typography.titleLarge)
                        state.notes?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        Text(
                            "KI-Werte sind Schätzungen. Passe die Mengen bei Bedarf an.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item {
                Button(
                    onClick = { vm.saveAiItems(onDone) },
                    enabled = state.items.any { it.selected },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text("Ausgewählte hinzufügen") }
            }
        }
    }
}

/** Gradient button that pulses while the AI is working. */
@Composable
private fun AiButton(loading: Boolean, onClick: () -> Unit) {
    val pulse = rememberInfiniteTransition(label = "pulse")
    val alpha by pulse.animateFloat(
        initialValue = 1f, targetValue = 0.6f,
        animationSpec = infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse),
        label = "alpha",
    )
    Box(
        Modifier
            .fillMaxWidth()
            .height(54.dp)
            .alpha(if (loading) alpha else 1f)
            .clip(CircleShape)
            .background(Gradients.ai)
            .clickable(enabled = !loading, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (loading) {
                CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("KI analysiert …", color = Color.White, style = MaterialTheme.typography.labelLarge)
            } else {
                Icon(Icons.Filled.AutoAwesome, null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text("Kalorien schätzen", color = Color.White, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun AiItemRow(e: EditableAiItem, onToggle: () -> Unit, onGrams: (Double) -> Unit) {
    val item = e.item
    var grams by remember(item.name) { mutableStateOf(item.amountGrams.roundToInt().toString()) }
    SoftCard {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = e.selected, onCheckedChange = { onToggle() })
            Column(Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    "${item.kcal.roundToInt()} kcal · E ${item.protein.gramText()} · K ${item.carbs.gramText()} · F ${item.fat.gramText()}",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "Sicherheit: ${item.confidence}", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(100, 200, 300, 500).forEach { v ->
                FilterChip(kcal == v.toString(), { kcal = v.toString() }, { Text("$v") })
            }
        }
        NumberField(kcal, { kcal = it }, "Kalorien", suffix = "kcal", modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            name, { name = it }, label = { Text("Bezeichnung (optional)") }, singleLine = true,
            shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(protein, { protein = it }, "Eiweiß", Modifier.weight(1f), suffix = "g")
            NumberField(carbs, { carbs = it }, "KH", Modifier.weight(1f), suffix = "g")
            NumberField(fat, { fat = it }, "Fett", Modifier.weight(1f), suffix = "g")
        }
        OutlinedTextField(
            note, { note = it }, label = { Text("Notiz (optional)") },
            shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth(),
        )
        val k = kcal.toDecimal()
        Button(
            enabled = k != null && k > 0,
            onClick = {
                vm.quickAdd(
                    name, k ?: 0.0, protein.toDecimal() ?: 0.0, carbs.toDecimal() ?: 0.0,
                    fat.toDecimal() ?: 0.0, note, onDone,
                )
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text("Eintragen") }
    }
}
