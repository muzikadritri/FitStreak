package de.leon.fitstreak.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Selbst gezeichnete Vektor-Icons (Canvas), jeweils in einem 24×24-Raster. */
enum class FitIcon {
    FLAME, DUMBBELL, CHART, PERSON, TODAY, SHIELD, BOLT, DROP, MOON, PAUSE, PLAY, TROPHY, MINUS, PLUS,
    SWAP, TIMER, EGG, PLATE, NO_SUGAR, BODY, WEIGHT, FLAG, CALENDAR, PUSHUP, CHECK, SNAIL, BULB,
}

@Composable
fun FitIconView(icon: FitIcon, modifier: Modifier = Modifier, tint: Color = LocalContentColor.current, size: Dp = 24.dp) {
    Canvas(modifier.size(size)) { drawFitIcon(icon, tint) }
}

fun DrawScope.drawFitIcon(icon: FitIcon, tint: Color, accent: Color = tint) {
    val u = size.minDimension / 24f
    fun o(x: Float, y: Float) = Offset(x * u, y * u)
    val sw = 2f * u
    val stroke = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun line(x1: Float, y1: Float, x2: Float, y2: Float, w: Float = 2f) =
        drawLine(tint, o(x1, y1), o(x2, y2), w * u, StrokeCap.Round)

    fun path(build: Path.() -> Unit) = Path().apply(build)
    fun Path.m(x: Float, y: Float) = moveTo(x * u, y * u)
    fun Path.l(x: Float, y: Float) = lineTo(x * u, y * u)
    fun Path.c(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) =
        cubicTo(x1 * u, y1 * u, x2 * u, y2 * u, x3 * u, y3 * u)

    when (icon) {
        FitIcon.FLAME -> {
            drawPath(path {
                m(12f, 2f); c(12f, 2f, 19f, 8f, 19f, 14.5f); c(19f, 18.8f, 15.9f, 22f, 12f, 22f)
                c(8.1f, 22f, 5f, 18.8f, 5f, 14.5f); c(5f, 11.5f, 6.5f, 9.3f, 8.3f, 7.5f)
                c(8.7f, 10f, 9.8f, 11.2f, 11f, 11.2f); c(10.6f, 7.5f, 12f, 2f, 12f, 2f); close()
            }, tint)
            drawPath(path {
                m(12f, 13f); c(12f, 13f, 15.2f, 15.8f, 15.2f, 18.3f); c(15.2f, 20.2f, 13.8f, 21.4f, 12f, 21.4f)
                c(10.2f, 21.4f, 8.8f, 20.2f, 8.8f, 18.3f); c(8.8f, 16.3f, 12f, 13f, 12f, 13f); close()
            }, if (accent == tint) Color.White.copy(alpha = 0.55f) else accent)
        }
        FitIcon.DUMBBELL -> {
            line(7f, 12f, 17f, 12f, 2.2f)
            drawRoundRect(tint, o(4.2f, 6f), Size(3.2f * u, 12f * u), CornerRadius(1.2f * u))
            drawRoundRect(tint, o(16.6f, 6f), Size(3.2f * u, 12f * u), CornerRadius(1.2f * u))
            drawRoundRect(tint, o(1.4f, 8.5f), Size(2.2f * u, 7f * u), CornerRadius(1f * u))
            drawRoundRect(tint, o(20.4f, 8.5f), Size(2.2f * u, 7f * u), CornerRadius(1f * u))
        }
        FitIcon.CHART -> {
            drawRoundRect(tint, o(3.5f, 13f), Size(4f * u, 8f * u), CornerRadius(1.2f * u))
            drawRoundRect(tint, o(10f, 8f), Size(4f * u, 13f * u), CornerRadius(1.2f * u))
            drawRoundRect(tint, o(16.5f, 3f), Size(4f * u, 18f * u), CornerRadius(1.2f * u))
        }
        FitIcon.PERSON -> {
            drawCircle(tint, 4.2f * u, o(12f, 7.5f))
            drawPath(path {
                m(4f, 21f); c(4f, 16f, 7.5f, 13.5f, 12f, 13.5f); c(16.5f, 13.5f, 20f, 16f, 20f, 21f); close()
            }, tint)
        }
        FitIcon.TODAY -> {
            drawRoundRect(tint, o(3.5f, 5f), Size(17f * u, 16f * u), CornerRadius(3f * u), style = stroke)
            line(3.5f, 10f, 20.5f, 10f)
            line(8f, 2.8f, 8f, 6.5f); line(16f, 2.8f, 16f, 6.5f)
            drawPath(path { m(8.5f, 15.5f); l(11f, 18f); l(15.8f, 13f) }, tint, style = stroke)
        }
        FitIcon.CALENDAR -> {
            drawRoundRect(tint, o(3.5f, 5f), Size(17f * u, 16f * u), CornerRadius(3f * u), style = stroke)
            line(3.5f, 10f, 20.5f, 10f)
            line(8f, 2.8f, 8f, 6.5f); line(16f, 2.8f, 16f, 6.5f)
            for (x in listOf(8f, 12f, 16f)) drawCircle(tint, 1.1f * u, o(x, 14f))
            for (x in listOf(8f, 12f)) drawCircle(tint, 1.1f * u, o(x, 17.5f))
        }
        FitIcon.SHIELD -> drawPath(path {
            m(12f, 2.5f); l(20f, 5.5f); l(20f, 11.5f); c(20f, 16.5f, 16.6f, 20.2f, 12f, 21.8f)
            c(7.4f, 20.2f, 4f, 16.5f, 4f, 11.5f); l(4f, 5.5f); close()
        }, tint)
        FitIcon.BOLT -> drawPath(path {
            m(13.5f, 2f); l(5f, 13.5f); l(11f, 13.5f); l(9.8f, 22f); l(19f, 9.8f); l(12.8f, 9.8f); close()
        }, tint)
        FitIcon.DROP -> drawPath(path {
            m(12f, 2.5f); c(12f, 2.5f, 18.5f, 10f, 18.5f, 15f); c(18.5f, 18.8f, 15.6f, 21.5f, 12f, 21.5f)
            c(8.4f, 21.5f, 5.5f, 18.8f, 5.5f, 15f); c(5.5f, 10f, 12f, 2.5f, 12f, 2.5f); close()
        }, tint)
        FitIcon.MOON -> drawPath(path {
            m(20f, 14.5f); c(18.8f, 18.6f, 15f, 21.5f, 10.8f, 21.5f); c(5.9f, 21.5f, 2.5f, 17.6f, 2.5f, 13f)
            c(2.5f, 8.6f, 5.5f, 5f, 9.6f, 3.6f); c(8.4f, 9.5f, 12.5f, 15.6f, 20f, 14.5f); close()
        }, tint)
        FitIcon.PAUSE -> {
            drawRoundRect(tint, o(6f, 4.5f), Size(4f * u, 15f * u), CornerRadius(1.2f * u))
            drawRoundRect(tint, o(14f, 4.5f), Size(4f * u, 15f * u), CornerRadius(1.2f * u))
        }
        FitIcon.PLAY -> drawPath(path { m(7f, 4f); l(20f, 12f); l(7f, 20f); close() }, tint)
        FitIcon.TROPHY -> {
            drawPath(path {
                m(7f, 3f); l(17f, 3f); l(17f, 9f); c(17f, 12.3f, 14.8f, 14.5f, 12f, 14.5f)
                c(9.2f, 14.5f, 7f, 12.3f, 7f, 9f); close()
            }, tint)
            drawPath(path { m(7f, 5f); c(3f, 5f, 3f, 10.5f, 7.5f, 10.5f) }, tint, style = Stroke(1.8f * u, cap = StrokeCap.Round))
            drawPath(path { m(17f, 5f); c(21f, 5f, 21f, 10.5f, 16.5f, 10.5f) }, tint, style = Stroke(1.8f * u, cap = StrokeCap.Round))
            line(12f, 14.5f, 12f, 18f, 2.4f)
            drawRoundRect(tint, o(7.5f, 18f), Size(9f * u, 3f * u), CornerRadius(1f * u))
        }
        FitIcon.MINUS -> line(5f, 12f, 19f, 12f, 2.6f)
        FitIcon.PLUS -> { line(5f, 12f, 19f, 12f, 2.6f); line(12f, 5f, 12f, 19f, 2.6f) }
        FitIcon.CHECK -> drawPath(path { m(4.5f, 12.5f); l(9.8f, 17.8f); l(19.5f, 6.5f) }, tint, style = Stroke(2.8f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
        FitIcon.SWAP -> {
            line(4f, 8f, 19f, 8f); drawPath(path { m(15.5f, 4.5f); l(19f, 8f); l(15.5f, 11.5f) }, tint, style = stroke)
            line(5f, 16f, 20f, 16f); drawPath(path { m(8.5f, 12.5f); l(5f, 16f); l(8.5f, 19.5f) }, tint, style = stroke)
        }
        FitIcon.TIMER -> {
            drawCircle(tint, 8f * u, o(12f, 13.5f), style = stroke)
            line(12f, 13.5f, 12f, 9f); line(12f, 13.5f, 15f, 15.5f)
            line(9.5f, 2.5f, 14.5f, 2.5f)
        }
        FitIcon.EGG -> drawPath(path {
            m(12f, 2.5f); c(16f, 2.5f, 19f, 9f, 19f, 14f); c(19f, 18.5f, 15.9f, 21.5f, 12f, 21.5f)
            c(8.1f, 21.5f, 5f, 18.5f, 5f, 14f); c(5f, 9f, 8f, 2.5f, 12f, 2.5f); close()
        }, tint)
        FitIcon.PLATE -> {
            drawCircle(tint, 6.5f * u, o(13f, 12f), style = stroke)
            drawCircle(tint, 3f * u, o(13f, 12f))
            line(3.5f, 4f, 3.5f, 20f); line(2f, 4f, 2f, 8.5f, 1.4f); line(5f, 4f, 5f, 8.5f, 1.4f)
        }
        FitIcon.NO_SUGAR -> {
            drawPath(path { m(7.5f, 7f); l(16.5f, 7f); l(15.3f, 21f); l(8.7f, 21f); close() }, tint, style = stroke)
            line(12.5f, 7f, 14.5f, 2.5f)
            line(3.5f, 3.5f, 20.5f, 20.5f, 2.4f)
        }
        FitIcon.BODY -> {
            drawCircle(tint, 2.6f * u, o(12f, 4.5f))
            drawPath(path {
                m(6f, 9f); l(18f, 9f); l(16f, 15f); l(15.5f, 22f); l(12.8f, 22f); l(12f, 16.5f)
                l(11.2f, 22f); l(8.5f, 22f); l(8f, 15f); close()
            }, tint)
            line(6.2f, 9.5f, 4.5f, 15f, 2.2f); line(17.8f, 9.5f, 19.5f, 15f, 2.2f)
        }
        FitIcon.WEIGHT -> {
            drawPath(path { m(7.5f, 8f); l(16.5f, 8f); l(19.5f, 21f); l(4.5f, 21f); close() }, tint)
            drawCircle(tint, 2.6f * u, o(12f, 5.5f), style = Stroke(1.8f * u))
        }
        FitIcon.FLAG -> {
            line(6f, 3f, 6f, 21.5f, 2.2f)
            drawPath(path { m(6f, 4f); l(19f, 6.5f); l(15.5f, 9.8f); l(19f, 13f); l(6f, 14f); close() }, tint)
        }
        FitIcon.PUSHUP -> {
            drawCircle(tint, 2.3f * u, o(19f, 8f))
            line(3f, 17.5f, 16.5f, 10.5f, 2.6f)
            line(16f, 11f, 16.5f, 18f, 2f)
            line(2.5f, 20.5f, 21.5f, 20.5f, 1.4f)
        }
        FitIcon.SNAIL -> {
            drawCircle(tint, 5.5f * u, o(10f, 12f), style = stroke)
            drawCircle(tint, 1.8f * u, o(10f, 12f))
            drawPath(path { m(3f, 19f); l(19f, 19f); c(21f, 19f, 21.5f, 16f, 20f, 13f); l(18.5f, 8.5f) }, tint, style = stroke)
            line(20f, 13f, 22f, 9.5f, 1.4f)
        }
        FitIcon.BULB -> {
            drawPath(path {
                m(12f, 2.5f); c(16f, 2.5f, 18.5f, 5.5f, 18.5f, 9f); c(18.5f, 12f, 16.5f, 13.5f, 15.5f, 16f)
                l(8.5f, 16f); c(7.5f, 13.5f, 5.5f, 12f, 5.5f, 9f); c(5.5f, 5.5f, 8f, 2.5f, 12f, 2.5f); close()
            }, tint)
            line(9.2f, 18.6f, 14.8f, 18.6f); line(10.2f, 21.2f, 13.8f, 21.2f)
        }
    }
}
