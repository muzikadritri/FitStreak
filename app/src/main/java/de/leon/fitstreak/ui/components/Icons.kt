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

/**
 * Ein einziges Icon-Set für die ganze App: selbst gezeichnete Linien-Icons im 24er-Raster,
 * überall dieselbe Strichstärke.
 */
enum class FitIcon {
    FLAME, DUMBBELL, CHART, PERSON, TODAY, SHIELD, BOLT, DROP, MOON, PAUSE, PLAY, TROPHY, MINUS, PLUS,
    SWAP, TIMER, EGG, PLATE, NO_SUGAR, BODY, WEIGHT, FLAG, CALENDAR, PUSHUP, CHECK, SNAIL, BULB,
    BACK, CLOSE, SEARCH, CHEVRON, UP, DOWN, GEAR,
}

@Composable
fun FitIconView(icon: FitIcon, modifier: Modifier = Modifier, tint: Color = LocalContentColor.current, size: Dp = 20.dp) {
    Canvas(modifier.size(size)) { drawFitIcon(icon, tint) }
}

fun DrawScope.drawFitIcon(icon: FitIcon, tint: Color) {
    val u = size.minDimension / 24f
    fun o(x: Float, y: Float) = Offset(x * u, y * u)
    val stroke = Stroke(1.7f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(tint, o(x1, y1), o(x2, y2), 1.7f * u, StrokeCap.Round)
    fun rect(x: Float, y: Float, w: Float, h: Float, r: Float) =
        drawRoundRect(tint, o(x, y), Size(w * u, h * u), CornerRadius(r * u), style = stroke)
    fun circle(x: Float, y: Float, r: Float) = drawCircle(tint, r * u, o(x, y), style = stroke)
    fun dot(x: Float, y: Float, r: Float = 1f) = drawCircle(tint, r * u, o(x, y))

    fun Path.m(x: Float, y: Float) = moveTo(x * u, y * u)
    fun Path.l(x: Float, y: Float) = lineTo(x * u, y * u)
    fun Path.c(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) =
        cubicTo(x1 * u, y1 * u, x2 * u, y2 * u, x3 * u, y3 * u)
    fun outline(build: Path.() -> Unit) = drawPath(Path().apply(build), tint, style = stroke)

    when (icon) {
        FitIcon.FLAME -> outline {
            m(12f, 2.5f); c(12f, 2.5f, 18.5f, 8f, 18.5f, 14.5f); c(18.5f, 18.6f, 15.6f, 21.5f, 12f, 21.5f)
            c(8.4f, 21.5f, 5.5f, 18.6f, 5.5f, 14.5f); c(5.5f, 11.8f, 6.8f, 9.8f, 8.4f, 8.2f)
            c(8.8f, 10.4f, 9.8f, 11.4f, 11f, 11.4f); c(10.6f, 7.8f, 12f, 2.5f, 12f, 2.5f); close()
        }
        FitIcon.DUMBBELL -> {
            line(8.5f, 12f, 15.5f, 12f)
            rect(5.5f, 7f, 3f, 10f, 1.2f)
            rect(15.5f, 7f, 3f, 10f, 1.2f)
            line(2.8f, 10f, 2.8f, 14f)
            line(21.2f, 10f, 21.2f, 14f)
        }
        FitIcon.CHART -> {
            line(5f, 20f, 5f, 13f); line(12f, 20f, 12f, 8f); line(19f, 20f, 19f, 4f)
        }
        FitIcon.PERSON -> {
            circle(12f, 8f, 3.6f)
            outline { m(4.5f, 20.5f); c(4.5f, 16.5f, 7.8f, 14.5f, 12f, 14.5f); c(16.2f, 14.5f, 19.5f, 16.5f, 19.5f, 20.5f) }
        }
        FitIcon.TODAY -> {
            rect(3.5f, 5f, 17f, 15.5f, 3f)
            line(3.5f, 10f, 20.5f, 10f)
            line(8f, 3f, 8f, 6.5f); line(16f, 3f, 16f, 6.5f)
            outline { m(8.8f, 15.2f); l(11f, 17.4f); l(15.4f, 13f) }
        }
        FitIcon.CALENDAR -> {
            rect(3.5f, 5f, 17f, 15.5f, 3f)
            line(3.5f, 10f, 20.5f, 10f)
            line(8f, 3f, 8f, 6.5f); line(16f, 3f, 16f, 6.5f)
            for (x in listOf(8f, 12f, 16f)) dot(x, 14f)
            for (x in listOf(8f, 12f)) dot(x, 17.2f)
        }
        FitIcon.SHIELD -> outline {
            m(12f, 3f); l(19.5f, 5.8f); l(19.5f, 11.5f); c(19.5f, 16.2f, 16.4f, 19.6f, 12f, 21.2f)
            c(7.6f, 19.6f, 4.5f, 16.2f, 4.5f, 11.5f); l(4.5f, 5.8f); close()
        }
        FitIcon.BOLT -> outline { m(13.2f, 2.8f); l(5.5f, 13.4f); l(11.2f, 13.4f); l(10.2f, 21.2f); l(18.5f, 10f); l(12.6f, 10f); close() }
        FitIcon.DROP -> outline {
            m(12f, 3f); c(12f, 3f, 18f, 10f, 18f, 15f); c(18f, 18.6f, 15.3f, 21f, 12f, 21f)
            c(8.7f, 21f, 6f, 18.6f, 6f, 15f); c(6f, 10f, 12f, 3f, 12f, 3f); close()
        }
        FitIcon.MOON -> outline {
            m(19.5f, 14.2f); c(18.4f, 18.2f, 14.8f, 21f, 10.8f, 21f); c(6.2f, 21f, 3f, 17.4f, 3f, 13f)
            c(3f, 8.8f, 5.8f, 5.4f, 9.6f, 4f); c(8.6f, 9.6f, 12.6f, 15.2f, 19.5f, 14.2f); close()
        }
        FitIcon.PAUSE -> { line(8.5f, 5.5f, 8.5f, 18.5f); line(15.5f, 5.5f, 15.5f, 18.5f) }
        FitIcon.PLAY -> outline { m(8f, 5f); l(18.5f, 12f); l(8f, 19f); close() }
        FitIcon.TROPHY -> {
            outline { m(7.5f, 4f); l(16.5f, 4f); l(16.5f, 9.5f); c(16.5f, 12.4f, 14.6f, 14.2f, 12f, 14.2f); c(9.4f, 14.2f, 7.5f, 12.4f, 7.5f, 9.5f); close() }
            outline { m(7.5f, 5.8f); c(3.8f, 5.8f, 3.8f, 10.4f, 7.8f, 10.4f) }
            outline { m(16.5f, 5.8f); c(20.2f, 5.8f, 20.2f, 10.4f, 16.2f, 10.4f) }
            line(12f, 14.2f, 12f, 18f)
            line(8.5f, 20f, 15.5f, 20f)
        }
        FitIcon.MINUS -> line(5.5f, 12f, 18.5f, 12f)
        FitIcon.PLUS -> { line(5.5f, 12f, 18.5f, 12f); line(12f, 5.5f, 12f, 18.5f) }
        FitIcon.CHECK -> outline { m(5f, 12.6f); l(9.8f, 17.4f); l(19f, 7f) }
        FitIcon.SWAP -> {
            line(4.5f, 8f, 19f, 8f); outline { m(15.8f, 4.8f); l(19f, 8f); l(15.8f, 11.2f) }
            line(5f, 16f, 19.5f, 16f); outline { m(8.2f, 12.8f); l(5f, 16f); l(8.2f, 19.2f) }
        }
        FitIcon.TIMER -> {
            circle(12f, 13.5f, 7.5f)
            line(12f, 13.5f, 12f, 9.5f); line(12f, 13.5f, 14.8f, 15.2f)
            line(9.8f, 3f, 14.2f, 3f)
        }
        FitIcon.EGG -> outline {
            m(12f, 3f); c(15.6f, 3f, 18.5f, 9f, 18.5f, 14f); c(18.5f, 18.2f, 15.6f, 21f, 12f, 21f)
            c(8.4f, 21f, 5.5f, 18.2f, 5.5f, 14f); c(5.5f, 9f, 8.4f, 3f, 12f, 3f); close()
        }
        FitIcon.PLATE -> {
            circle(13.5f, 12f, 6.5f)
            circle(13.5f, 12f, 2.6f)
            line(3.5f, 4.5f, 3.5f, 19.5f)
        }
        FitIcon.NO_SUGAR -> {
            outline { m(8f, 7.5f); l(16f, 7.5f); l(15f, 20.5f); l(9f, 20.5f); close() }
            line(12.4f, 7.5f, 14.2f, 3.2f)
            line(4.5f, 4.5f, 19.5f, 19.5f)
        }
        FitIcon.BODY -> {
            circle(12f, 5f, 2.3f)
            outline { m(7f, 9.5f); l(17f, 9.5f); l(15.6f, 15f); l(15.2f, 21f); l(12.8f, 21f); l(12f, 16f); l(11.2f, 21f); l(8.8f, 21f); l(8.4f, 15f); close() }
        }
        FitIcon.WEIGHT -> {
            outline { m(8f, 9f); l(16f, 9f); l(18.8f, 20.5f); l(5.2f, 20.5f); close() }
            circle(12f, 6.2f, 2.5f)
        }
        FitIcon.FLAG -> {
            line(6.5f, 3.5f, 6.5f, 21f)
            outline { m(6.5f, 4.5f); l(18.5f, 6.8f); l(15.4f, 9.6f); l(18.5f, 12.6f); l(6.5f, 13.5f) }
        }
        FitIcon.PUSHUP -> {
            circle(18.6f, 8f, 2f)
            line(3.5f, 17.5f, 16f, 10.8f)
            line(15.6f, 11.2f, 16.2f, 17.6f)
            line(3f, 20.5f, 21f, 20.5f)
        }
        FitIcon.SNAIL -> {
            circle(10f, 12f, 5.2f)
            dot(10f, 12f, 1.3f)
            outline { m(3.5f, 19f); l(18.5f, 19f); c(20.6f, 19f, 21f, 16.2f, 19.8f, 13.4f); l(18.4f, 9f) }
        }
        FitIcon.BULB -> {
            outline {
                m(12f, 3f); c(15.8f, 3f, 18f, 5.8f, 18f, 9f); c(18f, 11.8f, 16.2f, 13.2f, 15.2f, 15.6f)
                l(8.8f, 15.6f); c(7.8f, 13.2f, 6f, 11.8f, 6f, 9f); c(6f, 5.8f, 8.2f, 3f, 12f, 3f); close()
            }
            line(9.6f, 18.4f, 14.4f, 18.4f); line(10.6f, 21f, 13.4f, 21f)
        }
        FitIcon.BACK -> { line(5f, 12f, 19f, 12f); outline { m(10.5f, 6.5f); l(5f, 12f); l(10.5f, 17.5f) } }
        FitIcon.CLOSE -> { line(6f, 6f, 18f, 18f); line(18f, 6f, 6f, 18f) }
        FitIcon.SEARCH -> { circle(10.8f, 10.8f, 6f); line(15.4f, 15.4f, 20f, 20f) }
        FitIcon.CHEVRON -> outline { m(9.5f, 6f); l(15.5f, 12f); l(9.5f, 18f) }
        FitIcon.UP -> { line(12f, 5f, 12f, 19f); outline { m(6.5f, 10.5f); l(12f, 5f); l(17.5f, 10.5f) } }
        FitIcon.DOWN -> { line(12f, 5f, 12f, 19f); outline { m(6.5f, 13.5f); l(12f, 19f); l(17.5f, 13.5f) } }
        FitIcon.GEAR -> {
            circle(12f, 12f, 3.2f)
            circle(12f, 12f, 8f)
            for (i in 0 until 4) {
                val a = Math.toRadians(45.0 + i * 90).toFloat()
                val cx = kotlin.math.cos(a); val sy = kotlin.math.sin(a)
                line(12f + cx * 8f, 12f + sy * 8f, 12f + cx * 10.2f, 12f + sy * 10.2f)
            }
        }
    }
}
