package com.aleb.aletracr.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Ring showing eaten vs. goal calories, with "remaining" in the middle. */
@Composable
fun CalorieRing(eaten: Double, goal: Int, size: Dp = 140.dp) {
    val progress = if (goal > 0) (eaten / goal).toFloat() else 0f
    val over = progress > 1f
    val track = MaterialTheme.colorScheme.surfaceVariant
    val bar = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Box(contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = 14.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            drawArc(
                bar, -90f, 360f * progress.coerceAtMost(1f), false, Offset(inset, inset), arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val remaining = (goal - eaten).roundToInt()
            Text(
                "${kotlin.math.abs(remaining)}",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            Text(if (over) "kcal zu viel" else "kcal übrig", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun MacroBar(label: String, value: Double, goal: Double, color: Color, modifier: Modifier = Modifier) {
    Column(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text("${value.roundToInt()}/${goal.roundToInt()} g", style = MaterialTheme.typography.labelSmall)
        }
        LinearProgressIndicator(
            progress = { if (goal > 0) (value / goal).toFloat().coerceIn(0f, 1f) else 0f },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            drawStopIndicator = {},
        )
    }
}

@Composable
fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    suffix: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { s -> onValueChange(s.filter { it.isDigit() || it == ',' || it == '.' }) },
        label = { Text(label) },
        suffix = suffix?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}
