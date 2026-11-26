package de.leon.fitstreak.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.leon.fitstreak.data.DayState
import de.leon.fitstreak.data.WeekDay
import de.leon.fitstreak.ui.theme.Motion
import de.leon.fitstreak.ui.theme.fit

/**
 * Die Signatur der App: eine Hantelscheibe als Statuszeichen.
 * Gefüllt = geschafft, Ring = offen. Farbe trägt die Bedeutung.
 */
@Composable
fun PlateMark(color: Color, modifier: Modifier = Modifier, filled: Boolean = true, size: Dp = 12.dp) {
    val fill by animateFloatAsState(if (filled) 1f else 0f, Motion.fast(), label = "plate")
    val c by animateColorAsState(color, Motion.fast(), label = "plateColor")
    Canvas(modifier.size(size)) {
        val r = this.size.minDimension / 2
        val center = Offset(this.size.width / 2, this.size.height / 2)
        // offen: dünner Ring · gefüllt: Scheibe mit Loch in der Mitte – die Füllung wächst von außen nach innen
        val thin = r * 0.2f
        val full = r * 0.74f
        val w = thin + (full - thin) * fill
        drawCircle(c, r - w / 2, center, style = Stroke(w))
    }
}

/** Legende: Scheibe + ruhiger Text. */
@Composable
fun LegendDot(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        PlateMark(color, size = 12.dp)
        Spacer(Modifier.size(8.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = fit.muted)
    }
}

/**
 * Wochenleiste als Hantelstange: sieben Scheiben auf einer Stange, Mo–So.
 * Trainiert = volle grüne Scheibe, Ruhetag = kleine Scheibe, verpasst = roter Ring,
 * Streak-Schutz = blaue Scheibe, geplant = Ring.
 */
@Composable
fun WeekBarbell(week: List<WeekDay>, modifier: Modifier = Modifier) {
    val labels = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")
    val bar = fit.hairline
    val barStrong = fit.muted.copy(alpha = 0.45f)
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().height(32.dp).drawBehind {
                val y = size.height / 2
                // Stange mit Griffenden
                drawLine(barStrong, Offset(0f, y), Offset(size.width, y), 2.dp.toPx(), StrokeCap.Round)
                drawLine(bar, Offset(0f, y), Offset(size.width, y), 8.dp.toPx(), StrokeCap.Round)
            },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.size(4.dp))
            for (day in week) {
                Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) { DayPlate(day) }
            }
            Spacer(Modifier.size(4.dp))
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Spacer(Modifier.size(4.dp))
            week.forEachIndexed { i, day ->
                Box(Modifier.size(width = 32.dp, height = 16.dp), contentAlignment = Alignment.Center) {
                    Text(
                        labels.getOrElse(i) { "" },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (day.isToday) fit.text else fit.muted,
                    )
                }
            }
            Spacer(Modifier.size(4.dp))
        }
    }
}

@Composable
private fun DayPlate(day: WeekDay) {
    val surface = fit.bg
    val (color, size, filled) = when (day.state) {
        DayState.TRAINED -> Triple(fit.done, 28.dp, true)
        DayState.SHIELDED -> Triple(fit.accent, 28.dp, true)
        DayState.MISSED -> Triple(fit.streak, 24.dp, false)
        DayState.REST -> Triple(fit.muted, 16.dp, true)
        DayState.REST_AHEAD -> Triple(fit.muted.copy(alpha = 0.55f), 16.dp, false)
        DayState.OPEN -> Triple(fit.text, 28.dp, false)
        DayState.PLANNED -> Triple(fit.muted, 24.dp, false)
    }
    // Hintergrund-Scheibe verdeckt die Stange hinter offenen Ringen
    Box(contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) { drawCircle(surface) }
        PlateMark(color, filled = filled, size = size)
    }
}
