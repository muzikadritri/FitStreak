package de.leon.fitstreak.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.leon.fitstreak.ui.theme.Brand
import de.leon.fitstreak.ui.theme.fit
import kotlin.math.sin
import kotlin.random.Random

/**
 * Kreis-Fortschritt (Pausen-Timer, Countdown). [progress] wird als Lambda in der Zeichenphase gelesen –
 * so läuft der Ring mit jedem Frame weich, ohne die Umgebung neu zu berechnen.
 */
@Composable
fun ProgressRing(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = fit.accent,
    track: Color = fit.cardMuted,
    strokeWidth: Dp = 8.dp,
    content: @Composable () -> Unit = {},
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = strokeWidth.toPx()
            val inset = sw / 2
            val arcSize = Size(size.width - sw, size.height - sw)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(sw))
            val p = progress().coerceIn(0f, 1f)
            if (p > 0f) drawArc(color, -90f, 360f * p, false, Offset(inset, inset), arcSize, style = Stroke(sw, cap = StrokeCap.Round))
        }
        content()
    }
}

/** Liefert die laufende Zeit in Millisekunden, mit jedem Frame aktualisiert (für weiche Timer). */
@Composable
fun rememberFrameMillis(key: Any?, now: () -> Long): () -> Long {
    var value by remember(key) { mutableFloatStateOf(0f) }
    val base = remember(key) { now() }
    LaunchedEffect(key) {
        while (true) {
            withFrameNanos { }
            value = (now() - base).toFloat()
        }
    }
    return { base + value.toLong() }
}

private class Particle(
    val x0: Float, val vx: Float, val vy: Float, val size: Float, val color: Color,
    val spin: Float, val delay: Float, val wobble: Float,
)

/** Konfetti in den Farben der Hantelscheiben – einmalig nach einem abgeschlossenen Training. */
@Composable
fun Confetti(modifier: Modifier = Modifier, seed: Int = 7, count: Int = 70) {
    val particles = remember(seed) {
        val rnd = Random(seed)
        val colors = listOf(Brand.Blue, Brand.Red, Brand.Yellow, Brand.Green)
        List(count) {
            Particle(
                x0 = rnd.nextFloat(), vx = (rnd.nextFloat() - 0.5f) * 0.2f, vy = 0.3f + rnd.nextFloat() * 0.4f,
                size = 5f + rnd.nextFloat() * 6f, color = colors[rnd.nextInt(colors.size)],
                spin = (rnd.nextFloat() - 0.5f) * 700f, delay = rnd.nextFloat() * 0.7f, wobble = rnd.nextFloat() * 6f,
            )
        }
    }
    var time by remember(seed) { mutableFloatStateOf(0f) }
    LaunchedEffect(seed) {
        val start = withFrameNanos { it }
        while (time < 4.2f) {
            time = (withFrameNanos { it } - start) / 1e9f
        }
    }
    Canvas(modifier) {
        if (time >= 4.2f) return@Canvas
        val d = density
        for (p in particles) {
            val t = time - p.delay
            if (t < 0f) continue
            val y = (-0.05f + p.vy * t + 0.12f * t * t) * size.height
            if (y > size.height + 40f) continue
            val x = (p.x0 + p.vx * t) * size.width + sin(t * 4f + p.wobble) * 8f * d
            val alpha = (1f - (t - 2.6f).coerceAtLeast(0f) / 1.2f).coerceIn(0f, 1f)
            rotate(p.spin * t, Offset(x, y)) {
                drawRect(p.color.copy(alpha = alpha), Offset(x - p.size * d / 2, y - p.size * d / 4), Size(p.size * d, p.size * d * 0.5f))
            }
        }
    }
}
