package com.aleb.aletracr.util

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val dayFormat = DateTimeFormatter.ofPattern("EEE, d. MMM", Locale.GERMAN)

fun Double.kcalText(): String = "${roundToInt()} kcal"
fun Double.gramText(): String = if (this < 10) "%.1f g".format(Locale.GERMAN, this) else "${roundToInt()} g"

fun LocalDate.label(today: LocalDate = LocalDate.now()): String = when (this) {
    today -> "Heute"
    today.minusDays(1) -> "Gestern"
    today.plusDays(1) -> "Morgen"
    else -> format(dayFormat)
}

/** Parses user input like "12,5" or "12.5". */
fun String.toDecimal(): Double? = trim().replace(',', '.').toDoubleOrNull()
