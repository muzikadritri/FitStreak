package de.leon.fitstreak.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import de.leon.fitstreak.ui.LocalPlatform
import de.leon.fitstreak.ui.Art
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.leon.fitstreak.data.DayState
import de.leon.fitstreak.data.WeekDay
import de.leon.fitstreak.ui.theme.FitText
import de.leon.fitstreak.ui.theme.Motion
import de.leon.fitstreak.ui.theme.fit
import kotlin.math.max

private fun defaultLabel(v: Float): String =
    if (v == v.toInt().toFloat()) v.toInt().toString() else String.format("%.1f", v).replace('.', ',')

/**
 * Balkendiagramm (Canvas). Ein Akzent: der aktuelle (letzte) Balken, alle anderen in Grau.
 * Die Balken wachsen beim ersten Erscheinen einmal von der Grundlinie hoch.
 */
@Composable
fun BarChart(
    values: List<Float>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    color: Color = fit.accent,
    highlightLast: Boolean = true,
    height: Dp = 128.dp,
    valueLabel: (Float) -> String = ::defaultLabel,
) {
    val maxV = max(values.maxOrNull() ?: 0f, 1f)
    val neutral = fit.muted.copy(alpha = 0.35f)
    val grid = fit.hairline
    val grow = remember { Animatable(0f) }
    LaunchedEffect(Unit) { grow.animateTo(1f, Motion.enter(delay = 60)) }
    Column(modifier) {
        Row(Modifier.fillMaxWidth()) {
            for (v in values) {
                Text(
                    if (v > 0f) valueLabel(v) else "",
                    style = FitText.meta, color = fit.muted, textAlign = TextAlign.Center,
                    maxLines = 1, modifier = Modifier.weight(1f),
                )
            }
        }
        Canvas(Modifier.fillMaxWidth().height(height).padding(top = 4.dp)) {
            val n = values.size.coerceAtLeast(1)
            val slot = size.width / n
            val bw = (slot * 0.44f).coerceAtMost(28.dp.toPx())
            drawLine(grid, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
            drawLine(grid, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 1.dp.toPx())
            values.forEachIndexed { i, v ->
                val h = size.height * (v / maxV) * grow.value
                if (h > 0f) {
                    val x = slot * i + (slot - bw) / 2
                    val c = if (highlightLast && i == values.lastIndex) color else neutral
                    drawRoundRect(c, Offset(x, size.height - h), Size(bw, h), CornerRadius(4.dp.toPx()))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            for (l in labels) {
                Text(l, style = FitText.meta, color = fit.muted, textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** Liniendiagramm (Canvas): eine ruhige Linie, der letzte Punkt trägt den Akzent. */
@Composable
fun LineChart(
    values: List<Float>,
    modifier: Modifier = Modifier,
    color: Color = fit.accent,
    height: Dp = 144.dp,
    startLabel: String = "",
    endLabel: String = "",
    valueLabel: (Float) -> String = ::defaultLabel,
) {
    if (values.isEmpty()) {
        Column(
            modifier.fillMaxWidth().height(height),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
        ) {
            LocalPlatform.current.ArtImage(Art.EMPTY, Modifier.size(88.dp))
            Spacer(Modifier.height(4.dp))
            Text("Noch keine Daten", color = fit.muted, style = MaterialTheme.typography.bodyMedium)
        }
        return
    }
    val minV = values.min()
    val maxV = values.max()
    val grid = fit.hairline
    val lineColor = fit.text
    val surface = fit.card
    val draw = remember { Animatable(0f) }
    LaunchedEffect(Unit) { draw.animateTo(1f, Motion.enter(delay = 60)) }
    Column(modifier) {
        Row(Modifier.fillMaxWidth().height(height)) {
            Column(Modifier.width(44.dp).fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                Text(valueLabel(maxV), style = FitText.meta, color = fit.muted)
                Text(valueLabel(minV), style = FitText.meta, color = fit.muted)
            }
            Canvas(Modifier.weight(1f).fillMaxSize().padding(vertical = 8.dp)) {
                val span = (maxV - minV).takeIf { it > 0f } ?: 1f
                fun pt(i: Int): Offset {
                    val x = if (values.size == 1) size.width / 2 else size.width * i / (values.size - 1)
                    val y = size.height - (values[i] - minV) / span * size.height * (if (maxV == minV) 0.5f else 1f)
                    return Offset(x, y)
                }
                for (g in 0..2) {
                    val y = size.height * g / 2f
                    drawLine(grid, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                }
                if (values.size > 1) {
                    val line = Path().apply {
                        moveTo(pt(0).x, pt(0).y)
                        for (i in 1 until values.size) lineTo(pt(i).x, pt(i).y)
                    }
                    drawPath(line, lineColor.copy(alpha = draw.value), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
                val last = pt(values.lastIndex)
                drawCircle(surface, 8.dp.toPx(), last)
                drawCircle(color.copy(alpha = draw.value), 4.dp.toPx(), last)
            }
        }
        if (startLabel.isNotEmpty() || endLabel.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(start = 44.dp, top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(startLabel, style = FitText.meta, color = fit.muted)
                Text(endLabel, style = FitText.meta, color = fit.muted)
            }
        }
    }
}

@Composable
fun dayStateColor(state: DayState): Color = when (state) {
    DayState.TRAINED -> fit.done
    DayState.REST -> fit.muted.copy(alpha = 0.28f)
    DayState.SHIELDED -> fit.accent
    DayState.MISSED -> fit.streak
    DayState.OPEN, DayState.PLANNED, DayState.REST_AHEAD -> fit.cardMuted
}

/** Kalender-Heatmap: Spalten = Wochen, Zeilen = Mo–So. */
@Composable
fun Heatmap(weeks: List<List<WeekDay>>, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (d in listOf("Mo", "", "Mi", "", "Fr", "", "So")) {
                Box(Modifier.height(16.dp).width(24.dp), contentAlignment = Alignment.CenterStart) {
                    Text(d, style = FitText.meta, color = fit.muted)
                }
            }
        }
        for (week in weeks) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (day in week) {
                    val shape = RoundedCornerShape(4.dp)
                    Box(
                        Modifier.fillMaxWidth().height(16.dp).clip(shape).background(dayStateColor(day.state))
                            .let { if (day.isToday) it.border(1.5.dp, fit.text, shape) else it },
                    )
                }
            }
        }
    }
}
