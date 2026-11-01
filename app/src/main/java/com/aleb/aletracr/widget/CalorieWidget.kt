package com.aleb.aletracr.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.Button
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.aleb.aletracr.AleTracrApp
import com.aleb.aletracr.MainActivity
import com.aleb.aletracr.notify.CalorieNotification
import com.aleb.aletracr.notify.TodayStatus
import com.aleb.aletracr.ui.theme.MacroColors
import kotlin.math.abs
import kotlin.math.roundToInt

/** Homescreen widget: remaining calories, progress, macros and a quick-add button. */
class CalorieWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repo = (context.applicationContext as AleTracrApp).repository
        val status = TodayStatus.load(context, repo)
        provideContent {
            GlanceTheme {
                WidgetContent(context, status)
            }
        }
    }

    companion object {
        suspend fun refresh(context: Context) {
            runCatching { CalorieWidget().updateAll(context) }
        }
    }
}

class CalorieWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CalorieWidget()
}

@Composable
private fun WidgetContent(context: Context, s: TodayStatus) {
    val remaining = s.remaining.roundToInt()
    val over = remaining < 0
    val openApp = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val quickAdd = Intent(context, MainActivity::class.java)
        .putExtra(CalorieNotification.EXTRA_OPEN_QUICK_ADD, true)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(24.dp)
            .padding(16.dp)
            .clickable(actionStartActivity(openApp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("AleTracr · Heute", style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                "${abs(remaining)}",
                style = TextStyle(
                    color = if (over) GlanceTheme.colors.error else GlanceTheme.colors.primary,
                    fontSize = 34.sp, fontWeight = FontWeight.Bold,
                ),
            )
            Spacer(GlanceModifier.width(6.dp))
            Text(
                if (over) "kcal zu viel" else "kcal übrig",
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp),
                modifier = GlanceModifier.padding(bottom = 6.dp),
            )
        }
        LinearProgressIndicator(
            progress = if (s.goal > 0) (s.eaten / s.goal).toFloat().coerceIn(0f, 1f) else 0f,
            modifier = GlanceModifier.fillMaxWidth().height(8.dp),
            color = if (over) GlanceTheme.colors.error else GlanceTheme.colors.primary,
            backgroundColor = GlanceTheme.colors.surfaceVariant,
        )
        Spacer(GlanceModifier.height(8.dp))
        Row(GlanceModifier.fillMaxWidth()) {
            Macro("E", s.protein, s.proteinGoal, ColorProvider(MacroColors.protein), GlanceModifier.defaultWeight())
            Macro("K", s.carbs, s.carbsGoal, ColorProvider(MacroColors.carbs), GlanceModifier.defaultWeight())
            Macro("F", s.fat, s.fatGoal, ColorProvider(MacroColors.fat), GlanceModifier.defaultWeight())
        }
        Spacer(GlanceModifier.height(8.dp))
        Button(text = "+ Eintragen", onClick = actionStartActivity(quickAdd), modifier = GlanceModifier.fillMaxWidth())
    }
}

@Composable
private fun Macro(label: String, value: Double, goal: Double, color: ColorProvider, modifier: GlanceModifier) {
    Text(
        "$label ${value.roundToInt()}/${goal.roundToInt()} g",
        style = TextStyle(color = color, fontSize = 12.sp, fontWeight = FontWeight.Medium),
        modifier = modifier,
    )
}
