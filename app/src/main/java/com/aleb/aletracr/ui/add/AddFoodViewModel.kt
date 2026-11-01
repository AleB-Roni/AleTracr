package com.aleb.aletracr.ui.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aleb.aletracr.data.EntrySource
import com.aleb.aletracr.data.Meal
import com.aleb.aletracr.data.Repository
import com.aleb.aletracr.data.toProduct
import com.aleb.aletracr.remote.AiException
import com.aleb.aletracr.remote.AiFoodItem
import com.aleb.aletracr.remote.FoodProduct
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchState(
    val query: String = "",
    val loading: Boolean = false,
    val results: List<FoodProduct> = emptyList(),
    val error: String? = null,
    val searched: Boolean = false,
)

/** An AI-recognised item the user can still edit or deselect before saving. */
data class EditableAiItem(val item: AiFoodItem, val selected: Boolean = true)

data class AiState(
    val description: String = "",
    val image: ByteArray? = null,
    val loading: Boolean = false,
    val labelLoading: Boolean = false,
    val items: List<EditableAiItem> = emptyList(),
    val notes: String? = null,
    val summary: String? = null,
    val error: String? = null,
)

class AddFoodViewModel(
    private val repo: Repository,
    val date: String,
    initialMeal: Meal,
) : ViewModel() {
    val meal = MutableStateFlow(initialMeal)

    private val _search = MutableStateFlow(SearchState())
    val search = _search.asStateFlow()

    private val _ai = MutableStateFlow(AiState())
    val ai = _ai.asStateFlow()

    /** Product chosen for the portion dialog. */
    val selected = MutableStateFlow<Pair<FoodProduct, EntrySource>?>(null)

    val message = MutableStateFlow<String?>(null)

    fun showMessage(text: String) { message.value = text }

    val recent = repo.foods.recent().map { list -> list.map { it.toProduct() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val favorites = repo.foods.favorites().map { list -> list.map { it.toProduct() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recipes = repo.recipes.recipes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Whether the product in the portion dialog is a favourite. */
    val selectedIsFavorite = MutableStateFlow(false)

    private var searchJob: Job? = null

    // --- Search ---------------------------------------------------------------

    fun setQuery(q: String) = _search.update { it.copy(query = q) }

    fun runSearch() {
        val q = _search.value.query.trim()
        if (q.length < 2) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _search.update { it.copy(loading = true, error = null) }
            val local = repo.searchLocal(q)
            _search.update { it.copy(results = local) }
            try {
                val online = repo.searchOnline(q)
                val merged = (local + online).distinctBy { it.barcode ?: (it.name + it.brand) }
                _search.update { it.copy(results = merged, loading = false, searched = true) }
            } catch (e: Exception) {
                _search.update {
                    it.copy(loading = false, searched = true, error = "Online-Suche fehlgeschlagen: ${e.message ?: "unbekannter Fehler"}")
                }
            }
        }
    }

    fun onBarcode(code: String) = viewModelScope.launch {
        _search.update { it.copy(loading = true, error = null) }
        try {
            val p = repo.lookupBarcode(code)
            if (p == null) {
                _search.update { it.copy(loading = false, error = "Produkt $code nicht gefunden. Tipp: Nutze den KI-Tab oder Schnelleintrag.") }
            } else {
                _search.update { it.copy(loading = false) }
                select(p, EntrySource.BARCODE)
            }
        } catch (e: Exception) {
            _search.update { it.copy(loading = false, error = "Barcode-Suche fehlgeschlagen: ${e.message}") }
        }
    }

    fun select(p: FoodProduct, source: EntrySource = EntrySource.SEARCH) {
        selected.value = p to source
        viewModelScope.launch { selectedIsFavorite.value = repo.isFavorite(p) }
    }

    fun logSelected(grams: Double, onDone: () -> Unit) {
        val (p, source) = selected.value ?: return
        viewModelScope.launch {
            repo.logProduct(date, meal.value, p, grams, source)
            selected.value = null
            onDone()
        }
    }

    fun toggleFavorite(p: FoodProduct) = viewModelScope.launch {
        repo.toggleFavorite(p)
        selectedIsFavorite.value = repo.isFavorite(p)
        message.value = if (selectedIsFavorite.value) "Zu Favoriten hinzugefügt" else "Aus Favoriten entfernt"
    }

    // --- Recipes ----------------------------------------------------------------

    fun logRecipe(id: Long, portion: Double, onDone: () -> Unit) = viewModelScope.launch {
        repo.logRecipe(date, meal.value, id, portion)
        onDone()
    }

    fun deleteRecipe(id: Long) = viewModelScope.launch {
        repo.deleteRecipe(id)
        message.value = "Rezept gelöscht"
    }

    // --- Quick add ------------------------------------------------------------

    fun quickAdd(name: String, kcal: Double, p: Double, c: Double, f: Double, note: String, onDone: () -> Unit) {
        viewModelScope.launch {
            repo.quickAdd(date, meal.value, name, kcal, p, c, f, note)
            onDone()
        }
    }

    // --- AI -------------------------------------------------------------------

    fun setDescription(s: String) = _ai.update { it.copy(description = s) }
    fun setImage(bytes: ByteArray?) = _ai.update { it.copy(image = bytes, error = null) }

    fun analyze() {
        val state = _ai.value
        if (state.description.isBlank() && state.image == null) {
            _ai.update { it.copy(error = "Mach ein Foto oder beschreibe, was du gegessen hast.") }
            return
        }
        viewModelScope.launch {
            val client = repo.aiClient()
            if (client == null) {
                _ai.update { it.copy(error = "Bitte zuerst in den Einstellungen einen KI-Key hinterlegen (Gemini ist kostenlos).") }
                return@launch
            }
            _ai.update { it.copy(loading = true, error = null, items = emptyList(), notes = null, summary = null) }
            try {
                val result = client.estimateMeal(state.description, state.image)
                _ai.update {
                    it.copy(
                        loading = false,
                        items = result.items.map { item -> EditableAiItem(item) },
                        notes = result.notes.takeIf(String::isNotBlank),
                        summary = result.summary.takeIf(String::isNotBlank),
                        error = if (result.items.isEmpty()) "Kein Essen erkannt." else null,
                    )
                }
            } catch (e: AiException) {
                _ai.update { it.copy(loading = false, error = e.message) }
            } catch (e: Exception) {
                _ai.update { it.copy(loading = false, error = "Fehler: ${e.message}") }
            }
        }
    }

    /** Photo of a nutrition table -> product (per 100 g) -> portion dialog. */
    fun scanLabel(image: ByteArray) {
        viewModelScope.launch {
            val client = repo.aiClient()
            if (client == null) {
                _ai.update { it.copy(error = "Bitte zuerst in den Einstellungen einen KI-Key hinterlegen (Gemini ist kostenlos).") }
                return@launch
            }
            _ai.update { it.copy(labelLoading = true, error = null) }
            try {
                val product = client.readLabel(image)
                _ai.update { it.copy(labelLoading = false) }
                select(product, EntrySource.AI_LABEL)
            } catch (e: AiException) {
                _ai.update { it.copy(labelLoading = false, error = e.message) }
            } catch (e: Exception) {
                _ai.update { it.copy(labelLoading = false, error = "Fehler: ${e.message}") }
            }
        }
    }

    fun toggleAiItem(index: Int) = _ai.update { s ->
        s.copy(items = s.items.mapIndexed { i, it -> if (i == index) it.copy(selected = !it.selected) else it })
    }

    /** Editing the grams of an AI item rescales its nutrition values. */
    fun setAiGrams(index: Int, grams: Double) = _ai.update { s ->
        s.copy(items = s.items.mapIndexed { i, e ->
            if (i != index || e.item.amountGrams <= 0) e else {
                val f = grams / e.item.amountGrams
                val it = e.item
                e.copy(item = it.copy(
                    amountGrams = grams, kcal = it.kcal * f, protein = it.protein * f, carbs = it.carbs * f,
                    fat = it.fat * f, sugar = it.sugar * f, fiber = it.fiber * f,
                ))
            }
        })
    }

    fun saveAiItems(onDone: () -> Unit) {
        val s = _ai.value
        val chosen = s.items.filter { it.selected }.map { it.item }
        if (chosen.isEmpty()) return
        viewModelScope.launch {
            repo.logAiItems(date, meal.value, chosen, fromPhoto = s.image != null)
            onDone()
        }
    }
}
