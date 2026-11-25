package de.leon.fitstreak.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
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
import de.leon.fitstreak.ui.theme.Brand
import de.leon.fitstreak.ui.theme.fit
import kotlin.math.max

/** Balkendiagramm (Canvas). Beschriftung unter jedem Balken, Wert darüber. */
@Composable
fun BarChart(
    values: List<Float>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    highlightLast: Boolean = true,
    height: Dp = 140.dp,
    valueLabel: (Float) -> String = { if (it == it.toInt().toFloat()) it.toInt().toString() else String.format("%.1f", it) },
) {
    val maxV = max(values.maxOrNull() ?: 0f, 1f)
    val track = fit.cardMuted
    Column(modifier) {
        Row(Modifier.fillMaxWidth()) {
            for (v in values) {
                Text(
                    if (v > 0f) valueLabel(v) else "",
                    style = MaterialTheme.typography.labelSmall, color = fit.muted, textAlign = TextAlign.Center,
                    maxLines = 1, modifier = Modifier.weight(1f),
                )
            }
        }
        Canvas(Modifier.fillMaxWidth().height(height).padding(top = 4.dp)) {
            val n = values.size.coerceAtLeast(1)
            val slot = size.width / n
            val bw = slot * 0.56f
            values.forEachIndexed { i, v ->
                val x = slot * i + (slot - bw) / 2
                drawRoundRect(track, Offset(x, 0f), Size(bw, size.height), CornerRadius(bw / 3))
                val h = size.height * (v / maxV)
                if (h > 0f) {
                    val c = if (highlightLast && i == values.lastIndex) color else color.copy(alpha = 0.55f)
                    drawRoundRect(c, Offset(x, size.height - h), Size(bw, h), CornerRadius(bw / 3))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            for (l in labels) {
                Text(l, style = MaterialTheme.typography.labelSmall, color = fit.muted, textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** Liniendiagramm (Canvas) mit Fläche, Punkten und Min/Max-Beschriftung. */
@Composable
fun LineChart(
    values: List<Float>,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    height: Dp = 150.dp,
    startLabel: String = "",
    endLabel: String = "",
    valueLabel: (Float) -> String = { if (it == it.toInt().toFloat()) it.toInt().toString() else String.format("%.1f", it) },
) {
    if (values.isEmpty()) {
        Box(modifier.fillMaxWidth().height(height), contentAlignment = Alignment.Center) {
            Text("Noch keine Daten", color = fit.muted, style = MaterialTheme.typography.bodyMedium)
        }
        return
    }
    val minV = values.min()
    val maxV = values.max()
    val grid = MaterialTheme.colorScheme.outlineVariant
    Column(modifier) {
        Row(Modifier.fillMaxWidth().height(height)) {
            Column(Modifier.width(44.dp).fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                Text(valueLabel(maxV), style = MaterialTheme.typography.labelSmall, color = fit.muted)
                Text(valueLabel(minV), style = MaterialTheme.typography.labelSmall, color = fit.muted)
            }
            Canvas(Modifier.weight(1f).fillMaxSize().padding(vertical = 6.dp)) {
                val span = (maxV - minV).takeIf { it > 0f } ?: 1f
                fun pt(i: Int): Offset {
                    val x = if (values.size == 1) size.width / 2 else size.width * i / (values.size - 1)
                    val y = size.height - (values[i] - minV) / span * size.height * (if (maxV == minV) 0.5f else 1f)
                    return Offset(x, y)
                }
                for (g in 0..2) {
                    val y = size.height * g / 2f
                    drawLine(grid, Offset(0f, y), Offset(size.width, y), 1f)
                }
                if (values.size > 1) {
                    val line = Path().apply {
                        moveTo(pt(0).x, pt(0).y)
                        for (i in 1 until values.size) lineTo(pt(i).x, pt(i).y)
                    }
                    val area = Path().apply {
                        addPath(line)
                        lineTo(pt(values.lastIndex).x, size.height)
                        lineTo(pt(0).x, size.height)
                        close()
                    }
                    drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.28f), color.copy(alpha = 0f))))
                    drawPath(line, color, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
                for (i in values.indices) {
                    val last = i == values.lastIndex
                    drawCircle(color, (if (last) 6.dp else 4.dp).toPx(), pt(i))
                    if (last) drawCircle(Color.White, 2.5.dp.toPx(), pt(i))
                }
            }
        }
        if (startLabel.isNotEmpty() || endLabel.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(start = 44.dp, top = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(startLabel, style = MaterialTheme.typography.labelSmall, color = fit.muted)
                Text(endLabel, style = MaterialTheme.typography.labelSmall, color = fit.muted)
            }
        }
    }
}

@Composable
fun dayStateColor(state: DayState): Color = when (state) {
    DayState.TRAINED -> fit.done
    DayState.REST -> fit.done.copy(alpha = 0.3f)
    DayState.SHIELDED -> Brand.Blue.copy(alpha = 0.7f)
    DayState.MISSED -> fit.streak.copy(alpha = 0.75f)
    DayState.OPEN, DayState.PLANNED, DayState.REST_AHEAD -> fit.cardMuted
}

/** Kalender-Heatmap: Spalten = Wochen, Zeilen = Mo–So. */
@Composable
fun Heatmap(weeks: List<List<WeekDay>>, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (d in listOf("Mo", "", "Mi", "", "Fr", "", "So")) {
                Box(Modifier.height(18.dp).width(20.dp), contentAlignment = Alignment.CenterStart) {
                    Text(d, style = MaterialTheme.typography.labelSmall, color = fit.muted)
                }
            }
        }
        for (week in weeks) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (day in week) {
                    val shape = RoundedCornerShape(5.dp)
                    Box(
                        Modifier.fillMaxWidth().height(18.dp).clip(shape).background(dayStateColor(day.state))
                            .let { if (day.isToday) it.border(2.dp, MaterialTheme.colorScheme.onSurface, shape) else it },
                    )
                }
            }
        }
    }
}

@Composable
fun LegendDot(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(12.dp).height(12.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Spacer(Modifier.width(5.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = fit.muted)
    }
}
