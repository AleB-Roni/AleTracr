package com.aleb.aletracr.food

import java.text.Normalizer

/** Nutrition source for a recognised food: values per 100 g plus unit weights. */
data class FoodMatch(
    val name: String,
    val brand: String? = null,
    val kcalPer100: Double,
    val proteinPer100: Double,
    val carbsPer100: Double,
    val fatPer100: Double,
    val sugarPer100: Double = 0.0,
    val fiberPer100: Double = 0.0,
    val unitGrams: Map<FoodUnit, Double> = emptyMap(),
    val defaultUnit: FoodUnit = FoodUnit.PIECE,
    val isWater: Boolean = false,
    /** Set when the match comes from the user's own food history. */
    val foodId: Long? = null,
) {
    fun gramsFor(unit: FoodUnit): Double = unitGrams[unit] ?: unit.defaultGrams
}

data class ParsedFood(
    val spoken: String,
    val match: FoodMatch,
    val grams: Double,
    val amountLabel: String,
) {
    private val f get() = grams / 100.0
    val kcal get() = match.kcalPer100 * f
    val protein get() = match.proteinPer100 * f
    val carbs get() = match.carbsPer100 * f
    val fat get() = match.fatPer100 * f
    val sugar get() = match.sugarPer100 * f
    val fiber get() = match.fiberPer100 * f
}

data class ParseResult(
    val recognized: List<ParsedFood>,
    /** Parts of the sentence no food was found for (can be sent to the AI). */
    val unknown: List<String>,
)

/**
 * Turns a spoken sentence like "Apfel 30 Gramm und zwei Scheiben Vollkornbrot mit Käse"
 * into food entries – entirely offline and in milliseconds.
 *
 * [history] looks up the user's own previously used foods first (they know brands etc.).
 */
class SpeechFoodParser(
    private val history: (String) -> FoodMatch? = { null },
    private val foods: List<CommonFood> = CommonFoods.all,
) {
    private data class Draft(var qty: Double? = null, var unit: FoodUnit? = null, val words: MutableList<String> = mutableListOf()) {
        var lastWasAmount = false
        val hasAmount get() = qty != null || unit != null
        fun isEmpty() = words.isEmpty() && !hasAmount
    }

    /** Built-in foods indexed by every normalised alias. */
    private val index: List<Pair<String, CommonFood>> = foods.flatMap { food ->
        (listOf(food.name) + food.aliases).map { norm(it) to food }
    }.sortedByDescending { it.first.length }

    fun parse(text: String): ParseResult {
        val drafts = mutableListOf<Draft>()
        var cur = Draft()
        fun finish() {
            if (!cur.isEmpty()) drafts += cur
            cur = Draft()
        }

        val tokens = text.lowercase()
            .replace(Regex("""(\d)([a-zäöüß])"""), "$1 $2") // "30g" -> "30 g"
            .replace(Regex("""(\d),(\d)"""), "$1.$2")        // "1,5" -> "1.5"
            .split(Regex("""[\s,;!?]+|\.(?!\d)|(?<=\D)/"""))
            .filter { it.isNotBlank() }

        for (raw in tokens) {
            val t = raw.trim('-', '\'', '"')
            when {
                t in SEPARATORS -> finish()
                parseNumber(t) != null -> {
                    val n = parseNumber(t)!!
                    if (t in HALF_WORDS && cur.qty != null && cur.words.isEmpty()) {
                        cur.qty = cur.qty!! * n               // "ein halber" -> 0.5
                    } else {
                        // A second number starts a new item; "Apfel 30 Gramm" keeps the number with the name.
                        if (cur.qty != null && (cur.words.isNotEmpty() || cur.unit != null)) finish()
                        cur.qty = n
                    }
                    cur.lastWasAmount = true
                }
                unitOf(t) != null -> {
                    val u = unitOf(t)!!
                    if (cur.unit != null && cur.words.isNotEmpty()) finish()
                    cur.unit = u
                    cur.lastWasAmount = true
                }
                t in FILLER -> Unit
                else -> {
                    // "Apfel 30 Gramm Banane": a new name after a complete item starts a new item
                    if (cur.words.isNotEmpty() && cur.hasAmount && cur.lastWasAmount) finish()
                    cur.words += t
                    cur.lastWasAmount = false
                }
            }
        }
        finish()

        val recognized = mutableListOf<ParsedFood>()
        val unknown = mutableListOf<String>()
        // Amount-only drafts ("200 Gramm") attach to the previous/next name-only draft.
        val merged = mergeDangling(drafts)
        for (d in merged) {
            if (d.words.isEmpty()) continue
            val phrase = d.words.joinToString(" ")
            val spoken = describe(d)
            val match = findFood(phrase)
            if (match == null) {
                unknown += spoken
                continue
            }
            val unit = d.unit ?: defaultUnitFor(d.qty, match)
            val qty = d.qty ?: 1.0
            val grams = when (unit) {
                FoodUnit.GRAM, FoodUnit.ML -> qty
                FoodUnit.KILO, FoodUnit.LITER -> qty * 1000
                else -> qty * match.gramsFor(unit)
            }
            recognized += ParsedFood(spoken, match, grams, amountLabel(qty, unit, grams))
        }
        return ParseResult(recognized, unknown)
    }

    private fun mergeDangling(drafts: List<Draft>): List<Draft> {
        val out = mutableListOf<Draft>()
        var pending: Draft? = null
        for (d in drafts) {
            when {
                d.words.isEmpty() && out.isNotEmpty() && !out.last().hasAmount -> {
                    out.last().qty = d.qty; out.last().unit = d.unit
                }
                d.words.isEmpty() -> pending = d
                pending != null && !d.hasAmount -> {
                    d.qty = pending.qty; d.unit = pending.unit; pending = null; out += d
                }
                else -> out += d
            }
        }
        return out
    }

    private fun describe(d: Draft): String = buildString {
        d.qty?.let { append(formatQty(it)).append(' ') }
        d.unit?.let { append(it.words.first()).append(' ') }
        append(d.words.joinToString(" "))
    }.trim()

    /** Finds the best food for a phrase: own history first, then built-in table. */
    fun findFood(phrase: String): FoodMatch? {
        val p = norm(phrase)
        if (p.isBlank()) return null
        history(phrase)?.let { return it }

        // 1) whole phrase or one of its words equals an alias (longest alias first)
        val words = p.split(' ')
        index.firstOrNull { (alias, _) -> alias == p }?.let { return it.second.toMatch() }
        index.firstOrNull { (alias, _) -> p.containsWord(alias) }?.let { return it.second.toMatch() }
        // 2) singular/plural variants of each word
        for (w in words.sortedByDescending { it.length }) {
            for (variant in variants(w)) {
                index.firstOrNull { it.first == variant }?.let { return it.second.toMatch() }
            }
        }
        // 3) compound words: "vollkornbrötchen" contains "brötchen" -> prefer longest alias contained at the end
        for (w in words.sortedByDescending { it.length }) {
            index.firstOrNull { (alias, _) -> alias.length >= 4 && w.endsWith(alias) }?.let { return it.second.toMatch() }
        }
        // 4) small typos from speech recognition
        for (w in words.filter { it.length >= 4 }) {
            val maxDist = if (w.length >= 7) 2 else 1
            index.minByOrNull { levenshtein(it.first, w) }?.let { (alias, food) ->
                if (levenshtein(alias, w) <= maxDist) return food.toMatch()
            }
        }
        return null
    }

    private fun CommonFood.toMatch() = FoodMatch(
        name = name, kcalPer100 = kcal, proteinPer100 = protein, carbsPer100 = carbs, fatPer100 = fat,
        sugarPer100 = sugar, fiberPer100 = fiber, unitGrams = units, defaultUnit = defaultUnit, isWater = isWater,
    )

    companion object {
        private val SEPARATORS = setOf("und", "mit", "plus", "sowie", "dazu", "außerdem", "ausserdem", "noch", "&", "+")
        private val FILLER = setOf(
            "ich", "habe", "hab", "hatte", "hatten", "gegessen", "getrunken", "gefrühstückt", "esse", "trinke", "trank", "aß",
            "etwa", "ca", "circa", "zirka", "ungefähr", "so", "rund", "knapp", "fast", "von", "vom", "der", "die", "das",
            "den", "dem", "des", "zum", "zur", "heute", "morgen", "mittag", "abend", "frühstück", "mittagessen",
            "abendessen", "snack", "als", "auch", "bitte", "eintragen", "trag", "trage",
            "kleine", "kleinen", "kleiner", "kleines", "klein", "große", "großen", "großer", "großes", "groß",
        )
        val HALF_WORDS = setOf("halb", "halbe", "halben", "halber", "halbes")
        private val NUMBER_WORDS = mapOf(
            "ein" to 1.0, "eine" to 1.0, "einen" to 1.0, "einem" to 1.0, "einer" to 1.0, "eins" to 1.0,
            "zwei" to 2.0, "drei" to 3.0, "vier" to 4.0, "fünf" to 5.0, "sechs" to 6.0, "sieben" to 7.0,
            "acht" to 8.0, "neun" to 9.0, "zehn" to 10.0, "elf" to 11.0, "zwölf" to 12.0, "fünfzehn" to 15.0,
            "zwanzig" to 20.0, "dreißig" to 30.0, "vierzig" to 40.0, "fünfzig" to 50.0, "hundert" to 100.0,
            "einhundert" to 100.0, "zweihundert" to 200.0, "dreihundert" to 300.0, "fünfhundert" to 500.0,
            "halb" to 0.5, "halbe" to 0.5, "halben" to 0.5, "halber" to 0.5, "halbes" to 0.5,
            "eineinhalb" to 1.5, "anderthalb" to 1.5, "zweieinhalb" to 2.5, "dreieinhalb" to 3.5,
            "viertel" to 0.25, "paar" to 2.0,
        )

        fun parseNumber(t: String): Double? = t.replace(',', '.').toDoubleOrNull() ?: NUMBER_WORDS[t]

        private val UNIT_WORDS: Map<String, FoodUnit> =
            FoodUnit.entries.flatMap { u -> u.words.map { it to u } }.toMap()

        fun unitOf(t: String): FoodUnit? = UNIT_WORDS[t]

        /** "Apfel 30" = 30 g, "2 Äpfel" = 2 Stück, "12 Mandeln" = 12 Stück, "Joghurt 200" = 200 g. */
        private fun defaultUnitFor(qty: Double?, m: FoodMatch): FoodUnit = when {
            qty == null -> m.defaultUnit
            qty >= 20 -> if (m.defaultUnit == FoodUnit.GLASS || m.defaultUnit == FoodUnit.CUP || m.defaultUnit == FoodUnit.BOTTLE || m.defaultUnit == FoodUnit.CAN) FoodUnit.ML else FoodUnit.GRAM
            FoodUnit.PIECE in m.unitGrams -> FoodUnit.PIECE
            qty >= 10 -> FoodUnit.GRAM
            else -> m.defaultUnit
        }

        fun norm(s: String): String {
            val lower = s.lowercase().replace("ß", "ss")
            return Normalizer.normalize(lower, Normalizer.Form.NFD)
                .replace(Regex("""\p{M}"""), "")   // ä -> a
                .replace(Regex("""[^a-z0-9 ]"""), " ")
                .replace(Regex("""\s+"""), " ").trim()
        }

        private fun String.containsWord(w: String) = " $this ".contains(" $w ")

        private fun variants(w: String): List<String> = listOfNotNull(
            w, w.removeSuffix("n"), w.removeSuffix("en"), w.removeSuffix("e"), w.removeSuffix("s"),
            w.removeSuffix("er"), w.removeSuffix("nen"), w + "n", w + "e",
        ).distinct()

        fun levenshtein(a: String, b: String): Int {
            if (a == b) return 0
            val dp = IntArray(b.length + 1) { it }
            for (i in 1..a.length) {
                var prev = dp[0]
                dp[0] = i
                for (j in 1..b.length) {
                    val tmp = dp[j]
                    dp[j] = minOf(dp[j] + 1, dp[j - 1] + 1, prev + if (a[i - 1] == b[j - 1]) 0 else 1)
                    prev = tmp
                }
            }
            return dp[b.length]
        }

        private fun formatQty(q: Double) = if (q % 1.0 == 0.0) q.toInt().toString() else q.toString().replace('.', ',')

        private fun amountLabel(qty: Double, unit: FoodUnit, grams: Double): String {
            val g = "${grams.toInt()} g"
            return when (unit) {
                FoodUnit.GRAM -> g
                FoodUnit.ML -> "${qty.toInt()} ml"
                FoodUnit.KILO -> "${formatQty(qty)} kg"
                FoodUnit.LITER -> "${formatQty(qty)} l"
                else -> "${formatQty(qty)} × ${unit.words.first().replaceFirstChar { it.uppercase() }} · $g"
            }
        }
    }
}
