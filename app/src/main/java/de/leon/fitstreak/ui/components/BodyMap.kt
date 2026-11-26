package de.leon.fitstreak.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import de.leon.fitstreak.core.model.Muscle
import de.leon.fitstreak.ui.theme.fit

/**
 * Körperkarte von vorne und hinten. [colorFor] liefert die Farbe einer Muskelgruppe
 * (null = nicht hervorgehoben).
 */
@Composable
fun BodyMap(
    colorFor: (Muscle) -> Color?,
    modifier: Modifier = Modifier,
    base: Color = fit.bodyBase,
) {
    val outline = fit.card
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        for (front in listOf(true, false)) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Canvas(Modifier.fillMaxWidth().aspectRatio(0.5f)) { drawBody(front, colorFor, base, outline) }
                Text(if (front) "Vorne" else "Hinten", style = MaterialTheme.typography.labelMedium, color = fit.muted)
            }
        }
    }
}

/** Zeichnet in einem 100×200-Raster. */
private fun DrawScope.drawBody(front: Boolean, colorFor: (Muscle) -> Color?, base: Color, gap: Color) {
    val u = size.width / 100f
    fun c(m: Muscle) = colorFor(m) ?: base
    fun rr(x: Float, y: Float, w: Float, h: Float, color: Color, r: Float = 5f) =
        drawRoundRect(color, Offset(x * u, y * u), Size(w * u, h * u), CornerRadius(r * u))
    fun limb(x1: Float, y1: Float, x2: Float, y2: Float, w: Float, color: Color) =
        drawLine(color, Offset(x1 * u, y1 * u), Offset(x2 * u, y2 * u), w * u, StrokeCap.Round)
    fun both(draw: (sign: Float) -> Unit) { draw(-1f); draw(1f) }

    // Kopf & Hals
    drawCircle(base, 10f * u, Offset(50f * u, 14f * u))
    rr(45f, 22f, 10f, 9f, base, 3f)

    // Unterarme, Hände
    both { s ->
        limb(50f + s * 30f, 66f, 50f + s * 34f, 92f, 8f, base)
        drawCircle(base, 4.6f * u, Offset((50f + s * 35f) * u, 99f * u))
    }
    // Hüfte
    rr(35f, 84f, 30f, 14f, base, 6f)
    // Unterschenkel, Füße (vorne neutral)
    both { s ->
        if (front) limb(50f + s * 9.5f, 146f, 50f + s * 9f, 180f, 9.5f, base)
        rr(50f + s * 9f - 6f + s * 1.5f, 186f, 12f, 7f, base, 3f)
    }

    if (front) {
        // Schultern
        both { s -> drawCircle(c(Muscle.SCHULTERN), 8.2f * u, Offset((50f + s * 21.5f) * u, 37f * u)) }
        // Bizeps (Oberarm vorne)
        both { s -> limb(50f + s * 26f, 46f, 50f + s * 29f, 60f, 9.5f, c(Muscle.BIZEPS)) }
        // Rumpf-Hintergrund
        rr(33f, 30f, 34f, 56f, base, 9f)
        // Brust
        both { s -> rr(50f + s * 8.8f - 7.6f, 33f, 15.2f, 15f, c(Muscle.BRUST), 5f) }
        // Bauch: 3 Reihen
        val abs = c(Muscle.BAUCH)
        for (row in 0..2) both { s -> rr(50f + s * 5.2f - 4.4f, 51f + row * 10.5f, 8.8f, 9f, abs, 3f) }
        // seitlicher Bauch
        both { s -> rr(50f + s * 14f - 2.2f, 52f, 4.4f, 28f, abs.copy(alpha = abs.alpha * 0.75f), 2.2f) }
        // Oberschenkel vorne
        both { s -> limb(50f + s * 9.5f, 104f, 50f + s * 9.5f, 134f, 14f, c(Muscle.BEINE)) }
    } else {
        // hintere Schulter
        both { s -> drawCircle(c(Muscle.SCHULTERN), 8.2f * u, Offset((50f + s * 21.5f) * u, 37f * u)) }
        // Trizeps (Oberarm hinten)
        both { s -> limb(50f + s * 26f, 46f, 50f + s * 29f, 60f, 9.5f, c(Muscle.TRIZEPS)) }
        // Rücken
        val back = c(Muscle.RUECKEN)
        rr(33f, 30f, 34f, 56f, base, 9f)
        rr(36f, 31f, 28f, 14f, back, 6f) // Nacken/oberer Rücken
        both { s -> rr(50f + s * 8.6f - 7.4f, 47f, 14.8f, 22f, back, 5f) } // Lat
        rr(42f, 71f, 16f, 13f, back.copy(alpha = back.alpha * 0.8f), 4f) // unterer Rücken
        // Po
        both { s -> drawCircle(c(Muscle.PO), 8.6f * u, Offset((50f + s * 8.6f) * u, 96f * u)) }
        // Beinrückseite + Waden
        both { s ->
            limb(50f + s * 9.5f, 112f, 50f + s * 9.5f, 136f, 13.5f, c(Muscle.BEINE))
            limb(50f + s * 9.5f, 150f, 50f + s * 9f, 172f, 10f, c(Muscle.BEINE))
        }
    }
    // feine Trennlinie in der Körpermitte
    drawLine(gap.copy(alpha = 0.6f), Offset(50f * u, 33f * u), Offset(50f * u, if (front) 82f * u else 70f * u), 0.9f * u)
}
