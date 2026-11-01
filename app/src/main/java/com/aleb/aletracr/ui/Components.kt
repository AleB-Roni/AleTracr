package com.aleb.aletracr.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt

private val Smooth = tween<Float>(durationMillis = 900, easing = FastOutSlowInEasing)

/** Ring showing eaten vs. goal calories, animated, with an animated counter in the middle. */
@Composable
fun CalorieRing(
    eaten: Double,
    goal: Int,
    size: Dp = 160.dp,
    stroke: Dp = 16.dp,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    barColor: Color = MaterialTheme.colorScheme.primary,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    val target = if (goal > 0) (eaten / goal).toFloat() else 0f
    val progress by animateFloatAsState(target.coerceIn(0f, 1f), Smooth, label = "ring")
    val remaining = (goal - eaten).roundToInt()
    val shown by animateIntAsState(abs(remaining), tween(900, easing = FastOutSlowInEasing), label = "count")
    val over = remaining < 0

    Box(contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val s = stroke.toPx()
            val inset = s / 2
            val arc = Size(this.size.width - s, this.size.height - s)
            drawArc(trackColor, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(s))
            drawArc(barColor, -90f, 360f * progress, false, Offset(inset, inset), arc, style = Stroke(s, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$shown", style = MaterialTheme.typography.displaySmall, color = textColor)
            Text(
                if (over) "kcal zu viel" else "kcal übrig",
                style = MaterialTheme.typography.labelMedium,
                color = textColor.copy(alpha = 0.8f),
            )
        }
    }
}

/** Small animated progress bar with label, e.g. for macros. */
@Composable
fun MacroBar(
    label: String,
    value: Double,
    goal: Double,
    color: Color,
    modifier: Modifier = Modifier,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    unit: String = "g",
) {
    val target = if (goal > 0) (value / goal).toFloat().coerceIn(0f, 1f) else 0f
    val p by animateFloatAsState(target, Smooth, label = "macro")
    Column(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = textColor)
            Text("${value.roundToInt()}/${goal.roundToInt()} $unit", style = MaterialTheme.typography.labelSmall, color = textColor.copy(alpha = 0.75f))
        }
        Box(
            Modifier.padding(top = 4.dp).fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)).background(trackColor)
        ) {
            Box(Modifier.fillMaxWidth(p).height(8.dp).clip(RoundedCornerShape(50)).background(color))
        }
    }
}

/** Fades and slides content in once, delayed by [index] for a staggered list entrance. */
@Composable
fun Staggered(index: Int, content: @Composable () -> Unit) {
    val state = remember { MutableTransitionState(false).apply { targetState = true } }
    AnimatedVisibility(
        visibleState = state,
        enter = fadeIn(tween(450, delayMillis = index * 70)) +
            slideInVertically(tween(450, delayMillis = index * 70, easing = FastOutSlowInEasing)) { it / 5 },
    ) { content() }
}

/** Rounded, borderless card used throughout the app. */
@Composable
fun SoftCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        content = content,
    )
}

/** Colored circle with an icon, e.g. for meal headers. */
@Composable
fun IconBadge(icon: ImageVector, tint: Color, size: Dp = 40.dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(tint.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(size * 0.55f))
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = modifier)
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
        shape = MaterialTheme.shapes.medium,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}
