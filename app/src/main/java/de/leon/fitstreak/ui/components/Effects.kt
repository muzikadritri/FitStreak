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
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Kreis-Fortschritt (z. B. Pausen-Timer, Countdown) mit Inhalt in der Mitte. */
@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = Brand.Blue,
    track: Color = Color.Gray.copy(alpha = 0.25f),
    strokeWidth: Dp = 12.dp,
    content: @Composable () -> Unit = {},
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = strokeWidth.toPx()
            val inset = sw / 2
            val arcSize = Size(size.width - sw, size.height - sw)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(sw))
            drawArc(color, -90f, 360f * progress.coerceIn(0f, 1f), false, Offset(inset, inset), arcSize, style = Stroke(sw, cap = StrokeCap.Round))
        }
        content()
    }
}

private class Particle(
    val x0: Float, val vx: Float, val vy: Float, val size: Float, val color: Color,
    val spin: Float, val delay: Float, val wobble: Float,
)

/** Konfetti-Regen von oben; läuft einmal ca. 3,5 Sekunden. */
@Composable
fun Confetti(modifier: Modifier = Modifier, seed: Int = 7, count: Int = 90) {
    val particles = remember(seed) {
        val rnd = Random(seed)
        val colors = listOf(Brand.Blue, Brand.Red, Brand.Yellow, Brand.Green, Brand.Orange, Color(0xFF8F6BFF))
        List(count) {
            Particle(
                x0 = rnd.nextFloat(), vx = (rnd.nextFloat() - 0.5f) * 0.25f, vy = 0.35f + rnd.nextFloat() * 0.45f,
                size = 6f + rnd.nextFloat() * 8f, color = colors[rnd.nextInt(colors.size)],
                spin = (rnd.nextFloat() - 0.5f) * 900f, delay = rnd.nextFloat() * 0.9f, wobble = rnd.nextFloat() * 6f,
            )
        }
    }
    var time by remember(seed) { mutableFloatStateOf(0f) }
    LaunchedEffect(seed) {
        val start = withFrameNanos { it }
        while (time < 4.5f) {
            time = (withFrameNanos { it } - start) / 1e9f
        }
    }
    Canvas(modifier) {
        val d = density
        for (p in particles) {
            val t = time - p.delay
            if (t < 0f) continue
            val y = (-0.05f + p.vy * t + 0.12f * t * t) * size.height
            if (y > size.height + 40f) continue
            val x = (p.x0 + p.vx * t) * size.width + sin(t * 5f + p.wobble) * 10f * d
            val alpha = (1f - (t - 2.8f).coerceAtLeast(0f) / 1.2f).coerceIn(0f, 1f)
            rotate(p.spin * t, Offset(x, y)) {
                drawRect(p.color.copy(alpha = alpha), Offset(x - p.size * d / 2, y - p.size * d / 4), Size(p.size * d, p.size * d * 0.5f))
            }
        }
    }
}

/** Strahlenkranz hinter einem Abzeichen (für das Meilenstein-Popup). */
@Composable
fun Sunburst(modifier: Modifier = Modifier, color: Color = Brand.Yellow, rotation: Float = 0f) {
    Canvas(modifier) {
        val c = Offset(size.width / 2, size.height / 2)
        val r = size.minDimension / 2
        val rays = 14
        for (i in 0 until rays) {
            val a = (i * 2 * PI / rays + rotation * PI / 180).toFloat()
            val inner = r * 0.52f
            val outer = r * (if (i % 2 == 0) 1f else 0.82f)
            drawLine(
                color.copy(alpha = 0.55f),
                Offset(c.x + cos(a) * inner, c.y + sin(a) * inner),
                Offset(c.x + cos(a) * outer, c.y + sin(a) * outer),
                strokeWidth = r * 0.07f, cap = StrokeCap.Round,
            )
        }
    }
}
