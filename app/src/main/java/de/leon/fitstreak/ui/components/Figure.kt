package de.leon.fitstreak.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
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
import de.leon.fitstreak.core.anim.Anchor
import de.leon.fitstreak.core.anim.Band
import de.leon.fitstreak.core.anim.Body
import de.leon.fitstreak.core.anim.Box
import de.leon.fitstreak.core.anim.BoxStyle
import de.leon.fitstreak.core.anim.Dumbbell
import de.leon.fitstreak.core.anim.DumbbellLook
import de.leon.fitstreak.core.anim.ExerciseAnimation
import de.leon.fitstreak.core.anim.Fixed
import de.leon.fitstreak.core.anim.Hands
import de.leon.fitstreak.core.anim.JointRef
import de.leon.fitstreak.core.anim.OnJoint
import de.leon.fitstreak.core.anim.Pose
import de.leon.fitstreak.core.anim.PullBar
import de.leon.fitstreak.core.anim.Skeleton
import de.leon.fitstreak.core.anim.Vec
import de.leon.fitstreak.core.anim.View
import de.leon.fitstreak.core.anim.Wall
import de.leon.fitstreak.core.anim.dir
import de.leon.fitstreak.core.anim.lerp
import de.leon.fitstreak.core.anim.resolve
import de.leon.fitstreak.core.model.BodyPart
import de.leon.fitstreak.core.model.Exercise
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.max
import kotlin.math.min

@Immutable
data class FigureColors(
    val body: Color,
    val bodyFar: Color,
    val ground: Color,
    val primary: Color,
    val secondary: Color,
    val prop: Color,
    val propLight: Color,
    val band: Color,
    /** Dünne Kontur um nahe Gliedmaßen, damit sie sich vom Rumpf abheben. */
    val outline: Color,
)

object FigureDefaults {
    // Die Figur ist der farbige Moment der App: Körper in Text-Grau,
    // Zielmuskel in Scheiben-Rot, unterstützende Muskeln in Scheiben-Gelb.
    val Light = FigureColors(
        body = Color(0xFF171717),
        bodyFar = Color(0xFFA8A8AD),
        ground = Color(0xFFD4D4D8),
        primary = Color(0xFFDC2626),
        secondary = Color(0xFFE0A100),
        prop = Color(0xFF5B5B61),
        propLight = Color(0xFFE4E4E7),
        band = Color(0xFF16A34A),
        outline = Color(0xFFF4F4F5),
    )
    val Dark = FigureColors(
        body = Color(0xFFEDEDED),
        bodyFar = Color(0xFF66666B),
        ground = Color(0xFF3A3A3D),
        primary = Color(0xFFE5484D),
        secondary = Color(0xFFF2B600),
        prop = Color(0xFFA1A1A6),
        propLight = Color(0xFF303033),
        band = Color(0xFF4CC38A),
        outline = Color(0xFF232325),
    )

    @Composable
    fun colors(): FigureColors = if (isSystemInDarkTheme()) Dark else Light
}

/**
 * Große, steuerbare Übungsanimation (Pause, Zeitlupe).
 * Läuft über die Frame-Uhr, damit Pause und Tempo-Wechsel ohne Sprung funktionieren.
 */
@Composable
fun ExerciseFigure(
    exercise: Exercise,
    modifier: Modifier = Modifier,
    playing: Boolean = true,
    speed: Float = 1f,
    colors: FigureColors = FigureDefaults.colors(),
    onPhase: (Int) -> Unit = {},
) {
    val anim = exercise.animation
    var time by remember(exercise.id) { mutableFloatStateOf(0f) }
    val currentSpeed by rememberUpdatedState(speed)
    val currentOnPhase by rememberUpdatedState(onPhase)
    LaunchedEffect(exercise.id, playing) {
        if (!playing) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            time = (time + (now - last) / 1e9f * currentSpeed) % anim.cycleSeconds
            last = now
        }
    }
    LaunchedEffect(exercise.id) {
        snapshotFlow { anim.frameAt(time).phaseIndex }.distinctUntilChanged().collect { currentOnPhase(it) }
    }
    val primary = exercise.primaryParts
    val secondary = exercise.secondaryParts
    Canvas(modifier) {
        drawExercise(anim, anim.frameAt(time).pose, primary, secondary, colors)
    }
}

/** Kleine Endlos-Animation für Listen (InfiniteTransition). */
@Composable
fun MiniExerciseFigure(
    exercise: Exercise,
    modifier: Modifier = Modifier,
    animate: Boolean = true,
    colors: FigureColors = FigureDefaults.colors(),
) {
    val anim = exercise.animation
    val primary = exercise.primaryParts
    val secondary = exercise.secondaryParts
    if (animate) {
        val transition = rememberInfiniteTransition(label = "figure")
        val time = transition.animateFloat(
            initialValue = 0f,
            targetValue = anim.cycleSeconds,
            animationSpec = infiniteRepeatable(
                tween((anim.cycleSeconds * 1000).toInt(), easing = LinearEasing),
                RepeatMode.Restart,
            ),
            label = "time",
        )
        Canvas(modifier) { drawExercise(anim, anim.frameAt(time.value).pose, primary, secondary, colors) }
    } else {
        StaticExerciseFigure(exercise, 0f, modifier, colors)
    }
}

/** Standbild zu einem festen Zeitpunkt der Bewegung. */
@Composable
fun StaticExerciseFigure(
    exercise: Exercise,
    time: Float,
    modifier: Modifier = Modifier,
    colors: FigureColors = FigureDefaults.colors(),
) {
    val anim = exercise.animation
    Canvas(modifier) {
        drawExercise(anim, anim.frameAt(time).pose, exercise.primaryParts, exercise.secondaryParts, colors)
    }
}

/** Zeichnet eine Pose samt Boden, Requisiten und Muskel-Hervorhebung. Ein Renderer für alle Übungen. */
fun DrawScope.drawExercise(
    anim: ExerciseAnimation,
    pose: Pose,
    primary: Set<BodyPart>,
    secondary: Set<BodyPart>,
    colors: FigureColors,
) {
    val b = anim.bounds
    val pad = size.minDimension * 0.05f
    val groundPad = size.height * 0.09f
    val scale = min((size.width - 2 * pad) / b.width, (size.height - pad - groundPad) / b.height)
    val ox = (size.width - b.width * scale) / 2f - b.minX * scale
    // Flache (liegende) Figuren vertikal etwas anheben, damit sie nicht am unteren Rand kleben.
    val groundY = min(size.height - groundPad, (size.height + b.height * scale) / 2f + groundPad * 0.3f)
    fun p(v: Vec) = Offset(ox + v.x * scale, groundY - v.y * scale)

    val front = anim.view == View.FRONT
    val sk = pose.resolve(anim.view)
    val lw = Body.LIMB_W * scale
    val tw = Body.TORSO_W * scale

    fun line(a: Vec, c: Vec, color: Color, w: Float = lw) =
        drawLine(color, p(a), p(c), w, StrokeCap.Round)

    fun partColor(part: BodyPart): Color? = when (part) {
        in primary -> colors.primary
        in secondary -> colors.secondary
        else -> null
    }

    /** Farbiger Streifen auf einem Segment; side: +1 Vorderseite, -1 Rückseite, 0 mittig. */
    fun stripe(a: Vec, c: Vec, from: Float, to: Float, side: Float, torso: Boolean, color: Color, alpha: Float) {
        val d = c - a
        val len = max(d.length, 1e-4f)
        val ux = d.x / len
        val uy = d.y / len
        val fx = if (torso) uy else -uy
        val fy = if (torso) -ux else ux
        val baseW = if (torso) Body.TORSO_W else Body.LIMB_W
        val s = if (front) 0f else side
        val off = Vec(fx, fy) * (s * baseW * 0.24f)
        val w = baseW * (if (s == 0f) 0.7f else 0.56f) * scale
        drawLine(color.copy(alpha = alpha), p(lerp(a, c, from) + off), p(lerp(a, c, to) + off), w, StrokeCap.Round)
    }

    fun armHighlights(shoulder: Vec, elbow: Vec, alpha: Float) {
        partColor(BodyPart.BICEPS)?.let { stripe(shoulder, elbow, 0.22f, 0.85f, 1f, false, it, alpha) }
        partColor(BodyPart.TRICEPS)?.let { stripe(shoulder, elbow, 0.18f, 0.85f, -1f, false, it, alpha) }
    }

    fun shoulderHighlight(shoulder: Vec, alpha: Float) {
        partColor(BodyPart.SHOULDER)?.let {
            drawCircle(it.copy(alpha = alpha), Body.LIMB_W * 0.78f * scale, p(shoulder))
        }
    }

    fun legHighlights(hip: Vec, knee: Vec, ankle: Vec, alpha: Float) {
        partColor(BodyPart.QUADS)?.let { stripe(hip, knee, 0.15f, 0.88f, 1f, false, it, alpha) }
        partColor(BodyPart.HAMSTRINGS)?.let { stripe(hip, knee, 0.15f, 0.88f, -1f, false, it, alpha) }
        partColor(BodyPart.CALVES)?.let { stripe(knee, ankle, 0.1f, 0.62f, -1f, false, it, alpha) }
    }

    fun foot(ankle: Vec, toe: Vec, color: Color) {
        val end = if (front) lerp(ankle, toe, 0.55f) else toe
        line(ankle, end, color, lw * 0.9f)
    }

    fun dumbbellAt(hand: Vec, forearmFrom: Vec, d: Dumbbell, far: Boolean) {
        val main = if (far && !front) colors.bodyFar else colors.prop
        when (d.look) {
            DumbbellLook.END_ON -> {
                drawCircle(main, 0.085f * scale, p(hand))
                drawCircle(colors.propLight, 0.045f * scale, p(hand))
                drawCircle(main, 0.018f * scale, p(hand))
            }
            DumbbellLook.BAR -> {
                val axis = dir(d.angle)
                val half = 0.15f
                val a = hand - axis * half
                val c = hand + axis * half
                line(a, c, main, 0.035f * scale)
                val n = Vec(-axis.y, axis.x)
                for (end in listOf(a, c)) {
                    drawLine(main, p(end - n * 0.075f), p(end + n * 0.075f), 0.07f * scale, StrokeCap.Round)
                }
            }
        }
    }

    fun anchorPos(a: Anchor): Vec = when (a) {
        is Fixed -> Vec(a.x, a.y)
        is OnJoint -> when (a.joint) {
            JointRef.HAND_N -> sk.handN
            JointRef.HAND_F -> sk.handF
            JointRef.ANKLE_N -> sk.ankleN
            JointRef.ANKLE_F -> sk.ankleF
            JointRef.TOE_N -> lerp(sk.ankleN, sk.toeN, 0.5f)
        }
    }

    // --- Boden ---
    drawLine(
        colors.ground,
        Offset(pad * 0.5f, groundY + lw * 0.05f),
        Offset(size.width - pad * 0.5f, groundY + lw * 0.05f),
        max(2f, 0.02f * scale),
        StrokeCap.Round,
    )

    // --- Requisiten hinter der Figur ---
    for (prop in anim.props) when (prop) {
        is Box -> {
            val tl = p(Vec(prop.x, prop.h))
            val w = prop.w * scale
            val h = prop.h * scale
            when (prop.style) {
                BoxStyle.CHAIR -> {
                    val seat = 0.06f * scale
                    drawRoundRect(colors.prop, tl, Size(w, seat), CornerRadius(seat / 2))
                    val legW = 0.035f * scale
                    drawLine(colors.prop, Offset(tl.x + legW, tl.y + seat / 2), Offset(tl.x + legW, groundY), legW)
                    drawLine(colors.prop, Offset(tl.x + w - legW, tl.y + seat / 2), Offset(tl.x + w - legW, groundY), legW)
                }
                BoxStyle.BENCH -> {
                    val pad2 = 0.07f * scale
                    drawRoundRect(colors.prop, tl, Size(w, pad2), CornerRadius(pad2 / 2))
                    val legW = 0.04f * scale
                    drawLine(colors.prop, Offset(tl.x + w * 0.15f, tl.y + pad2 / 2), Offset(tl.x + w * 0.15f, groundY), legW)
                    drawLine(colors.prop, Offset(tl.x + w * 0.85f, tl.y + pad2 / 2), Offset(tl.x + w * 0.85f, groundY), legW)
                }
                BoxStyle.STEP -> {
                    drawRoundRect(colors.propLight, tl, Size(w, h), CornerRadius(0.03f * scale))
                    drawRoundRect(colors.prop, tl, Size(w, h), CornerRadius(0.03f * scale), style = Stroke(0.02f * scale))
                }
            }
        }
        is Wall -> {
            val x = p(Vec(prop.x, 0f)).x
            val top = p(Vec(prop.x, prop.height)).y
            drawLine(colors.prop, Offset(x, groundY), Offset(x, top), 0.035f * scale, StrokeCap.Round)
            // Schraffur auf der Rückseite der Wand
            val step = 0.14f * scale
            var y = groundY - step * 0.5f
            while (y > top) {
                drawLine(colors.ground, Offset(x, y), Offset(x - 0.09f * scale, y - 0.09f * scale), 0.015f * scale)
                y -= step
            }
        }
        is PullBar -> {
            val c = p(Vec(prop.x, prop.y))
            val beamY = c.y - 0.10f * scale
            drawLine(colors.prop, Offset(c.x - 0.30f * scale, beamY), Offset(c.x + 0.30f * scale, beamY), 0.03f * scale, StrokeCap.Round)
            drawLine(colors.prop, Offset(c.x, beamY), c, 0.02f * scale)
        }
        is Band, is Dumbbell -> Unit
    }

    val farColor = if (front) colors.body else colors.bodyFar
    val farAlpha = if (front) 1f else 0.6f
    val dumbbell = anim.props.filterIsInstance<Dumbbell>().firstOrNull()

    fun drawArm(far: Boolean) {
        val sh = if (far) sk.shoulderF else sk.shoulderN
        val el = if (far) sk.elbowF else sk.elbowN
        val ha = if (far) sk.handF else sk.handN
        val color = if (far) farColor else colors.body
        val alpha = if (far) farAlpha else 1f
        if (!far && !front) {
            line(sh, el, colors.outline, lw * 1.28f)
            line(el, ha, colors.outline, lw * 1.18f)
        }
        line(sh, el, color)
        line(el, ha, color, lw * 0.9f)
        armHighlights(sh, el, alpha)
        shoulderHighlight(sh, alpha)
        if (dumbbell != null) {
            val wanted = dumbbell.hands == Hands.BOTH || (dumbbell.hands == Hands.FAR) == far
            if (wanted) dumbbellAt(ha, el, dumbbell, far)
        }
    }

    fun drawLeg(far: Boolean) {
        val hip = if (far) sk.hipF else sk.hipN
        val knee = if (far) sk.kneeF else sk.kneeN
        val ankle = if (far) sk.ankleF else sk.ankleN
        val toe = if (far) sk.toeF else sk.toeN
        val color = if (far) farColor else colors.body
        val alpha = if (far) farAlpha else 1f
        if (!far && !front) {
            line(lerp(hip, knee, 0.3f), knee, colors.outline, lw * 1.4f)
            line(knee, ankle, colors.outline, lw * 1.28f)
        }
        line(hip, knee, color, lw * 1.12f)
        line(knee, ankle, color)
        foot(ankle, toe, color)
        legHighlights(hip, knee, ankle, alpha)
    }

    fun drawTorso() {
        if (front) {
            val path = Path().apply {
                val a = p(sk.shoulderF); val c = p(sk.shoulderN); val d = p(sk.hipN); val e = p(sk.hipF)
                moveTo(a.x, a.y); lineTo(c.x, c.y); lineTo(d.x, d.y); lineTo(e.x, e.y); close()
            }
            drawPath(path, colors.body)
            drawPath(path, colors.body, style = Stroke(lw, join = StrokeJoin.Round))
            val sh = sk.shoulder
            val hip = sk.hip
            partColor(BodyPart.CHEST)?.let {
                val y = lerp(hip, sh, 0.78f)
                val across = (sk.shoulderN - sk.shoulderF) * 0.5f
                drawCircle(it, 0.075f * scale, p(y + across * 0.45f))
                drawCircle(it, 0.075f * scale, p(y - across * 0.45f))
            }
            // Rücken ist von vorn nicht zu sehen – als seitliche Streifen (Lat) andeuten
            partColor(BodyPart.UPPER_BACK)?.let {
                val across = (sk.shoulderN - sk.shoulderF) * 0.5f
                for (s in listOf(-1f, 1f)) {
                    drawLine(it, p(lerp(hip, sh, 0.5f) + across * (s * 0.72f)), p(lerp(hip, sh, 0.82f) + across * (s * 0.9f)), 0.05f * scale, StrokeCap.Round)
                }
            }
            partColor(BodyPart.ABS)?.let {
                drawLine(it, p(lerp(hip, sh, 0.12f)), p(lerp(hip, sh, 0.55f)), 0.11f * scale, StrokeCap.Round)
            }
            partColor(BodyPart.OBLIQUES)?.let {
                val across = (sk.hipN - sk.hipF) * 0.5f
                for (s in listOf(-1f, 1f)) {
                    drawLine(it, p(lerp(hip, sh, 0.15f) + across * s), p(lerp(hip, sh, 0.5f) + across * (s * 1.25f)), 0.045f * scale, StrokeCap.Round)
                }
            }
        } else {
            line(sk.hip, sk.shoulder, colors.body, tw)
            partColor(BodyPart.GLUTES)?.let {
                val d = sk.shoulder - sk.hip
                val len = max(d.length, 1e-4f)
                val back = Vec(-d.y / len, d.x / len)
                drawCircle(it, 0.082f * scale, p(sk.hip + back * 0.035f - d * (0.03f / len)))
            }
            partColor(BodyPart.CHEST)?.let { stripe(sk.hip, sk.shoulder, 0.62f, 0.9f, 1f, true, it, 1f) }
            partColor(BodyPart.ABS)?.let { stripe(sk.hip, sk.shoulder, 0.1f, 0.52f, 1f, true, it, 1f) }
            partColor(BodyPart.OBLIQUES)?.let { stripe(sk.hip, sk.shoulder, 0.14f, 0.5f, 0f, true, it, 1f) }
            partColor(BodyPart.UPPER_BACK)?.let { stripe(sk.hip, sk.shoulder, 0.45f, 0.9f, -1f, true, it, 1f) }
            partColor(BodyPart.LOWER_BACK)?.let { stripe(sk.hip, sk.shoulder, 0.1f, 0.4f, -1f, true, it, 1f) }
        }
    }

    fun drawHead() {
        line(sk.shoulder, sk.head, colors.body, lw * 0.8f)
        drawCircle(colors.body, Body.HEAD_R * scale, p(sk.head))
    }

    // Bänder mit festem Anker liegen hinter der Figur
    fun drawBands() {
        for (band in anim.props.filterIsInstance<Band>()) {
            val a = anchorPos(band.from)
            val c = anchorPos(band.to)
            drawLine(colors.band, p(a), p(c), 0.028f * scale, StrokeCap.Round)
            for (anchor in listOf(band.from, band.to)) {
                if (anchor is Fixed) drawCircle(colors.prop, 0.035f * scale, p(Vec(anchor.x, anchor.y)))
            }
        }
    }

    if (front) {
        drawLeg(far = true)
        drawLeg(far = false)
        drawTorso()
        if (BodyPart.GLUTES in primary || BodyPart.GLUTES in secondary) {
            partColor(BodyPart.GLUTES)?.let {
                drawLine(it.copy(alpha = 0.8f), p(sk.hipF), p(sk.hipN), 0.09f * scale, StrokeCap.Round)
            }
        }
        drawHead()
        drawBands()
        drawArm(far = true)
        drawArm(far = false)
    } else {
        drawArm(far = true)
        drawLeg(far = true)
        drawTorso()
        drawHead()
        drawLeg(far = false)
        drawBands()
        drawArm(far = false)
    }

    // Stange im Vordergrund (Hände greifen sie)
    for (prop in anim.props) if (prop is PullBar) {
        drawCircle(colors.prop, 0.04f * scale, p(Vec(prop.x, prop.y)))
        drawCircle(colors.propLight, 0.018f * scale, p(Vec(prop.x, prop.y)))
    }
}
