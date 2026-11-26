package de.leon.fitstreak.core.anim

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 2D-Skelett für die Übungsfiguren.
 *
 * Koordinaten: x nach rechts (Blickrichtung der Figur in der Seitenansicht), y nach oben,
 * Boden bei y = 0, Einheit ≈ Meter (Figur ist ca. 1,7 hoch).
 *
 * Winkel in Grad, absolut: 0° zeigt nach unten, 90° nach vorn (rechts), 180° nach oben,
 * -90° nach hinten. Jede Pose ist damit eine starre Drehung einer nach rechts schauenden Figur:
 * Bauchlage = Kopf rechts (Rumpf 90°), Rückenlage = Kopf links (Rumpf -90°).
 */
data class Vec(val x: Float, val y: Float) {
    operator fun plus(o: Vec) = Vec(x + o.x, y + o.y)
    operator fun minus(o: Vec) = Vec(x - o.x, y - o.y)
    operator fun times(f: Float) = Vec(x * f, y * f)
    val length: Float get() = hypot(x, y)
}

fun lerp(a: Float, b: Float, f: Float): Float = a + (b - a) * f
fun lerp(a: Vec, b: Vec, f: Float): Vec = Vec(lerp(a.x, b.x, f), lerp(a.y, b.y, f))

/** Körpermaße der Figur. */
object Body {
    const val TORSO = 0.50f
    const val NECK = 0.20f
    const val HEAD_R = 0.105f
    const val UPPER_ARM = 0.28f
    const val FOREARM = 0.27f
    const val THIGH = 0.42f
    const val SHIN = 0.42f
    const val FOOT = 0.16f
    const val SHOULDER_HALF = 0.17f
    const val HIP_HALF = 0.09f

    /** Höhe des Knöchels über dem Boden, wenn der Fuß flach steht. */
    const val ANKLE = 0.05f

    /** Hüfthöhe im aufrechten Stand. */
    const val STAND_HIP = ANKLE + SHIN + THIGH

    /** Strichstärken (Welt-Einheiten). */
    const val LIMB_W = 0.085f
    const val TORSO_W = 0.17f
}

enum class View { SIDE, FRONT }

/** Arm oder Bein: entweder über Winkel (FK) oder über einen Zielpunkt (IK). */
sealed interface Limb

/** Absolute Winkel für oberes und unteres Segment. */
data class Fk(val upper: Float, val lower: Float) : Limb

/**
 * Zielpunkt für Hand bzw. Knöchel. Das Gelenk (Ellbogen/Knie) knickt anatomisch richtig:
 * Knie nach vorn, Ellbogen nach hinten – [flip] dreht das um.
 */
data class Ik(val x: Float, val y: Float, val flip: Boolean = false) : Limb

/**
 * Angaben nur für die 3D-Figur – alles, was aus der Bildebene herausführt und die flache
 * Zeichnung deshalb nicht zeigen kann. Die 2D-Figur ignoriert sie.
 */
data class Depth(
    /** Oberkörper dreht sich um die Wirbelsäule (Grad, positiv = zur linken Körperseite). */
    val twist: Float = 0f,
    /** Arm schwenkt um die Körper-Längsachse nach außen (Grad): aus „vor/hinter dem Körper“ wird „seitlich“. */
    val swingN: Float = 0f,
    val swingF: Float = 0f,
    /** Arm schwenkt um die Brust-Achse nach außen (Grad): aus „Richtung Kopf“ wird „seitlich“. */
    val turnN: Float = 0f,
    val turnF: Float = 0f,
) {
    companion object {
        val NONE = Depth()
    }
}

fun lerp(a: Depth, b: Depth, f: Float): Depth = if (a == b) a else Depth(
    lerp(a.twist, b.twist, f), lerp(a.swingN, b.swingN, f), lerp(a.swingF, b.swingF, f),
    lerp(a.turnN, b.turnN, f), lerp(a.turnF, b.turnF, f),
)

data class Pose(
    val hipX: Float,
    val hipY: Float,
    /** Richtung Hüfte → Schulter. 180° = aufrecht. */
    val torso: Float = 180f,
    /** Richtung Schulter → Kopf. null = Verlängerung des Rumpfes. */
    val head: Float? = null,
    val armN: Limb = Fk(0f, 0f),
    val armF: Limb = armN,
    val legN: Limb = Fk(0f, 0f),
    val legF: Limb = legN,
    /** Richtung Knöchel → Zehen. 90° = flach nach vorn. */
    val footN: Float = 90f,
    val footF: Float = footN,
    val depth: Depth = Depth.NONE,
)

class Skeleton(
    val head: Vec,
    val shoulderN: Vec, val shoulderF: Vec,
    val elbowN: Vec, val handN: Vec,
    val elbowF: Vec, val handF: Vec,
    val hipN: Vec, val hipF: Vec,
    val kneeN: Vec, val ankleN: Vec, val toeN: Vec,
    val kneeF: Vec, val ankleF: Vec, val toeF: Vec,
) {
    val shoulder: Vec get() = lerp(shoulderN, shoulderF, 0.5f)
    val hip: Vec get() = lerp(hipN, hipF, 0.5f)
    fun joints(): List<Vec> = listOf(
        head, shoulderN, shoulderF, elbowN, handN, elbowF, handF,
        hipN, hipF, kneeN, ankleN, toeN, kneeF, ankleF, toeF,
    )
}

fun dir(deg: Float, mirror: Boolean = false): Vec {
    val r = Math.toRadians(deg.toDouble())
    val sx = sin(r).toFloat()
    return Vec(if (mirror) -sx else sx, -cos(r).toFloat())
}

fun angleOf(v: Vec, mirror: Boolean = false): Float =
    Math.toDegrees(atan2((if (mirror) -v.x else v.x).toDouble(), (-v.y).toDouble())).toFloat()

private fun solveLimb(
    root: Vec, limb: Limb, l1: Float, l2: Float,
    /** +1: Gelenk liegt vorn (Knie), -1: hinten (Ellbogen). */
    frontSide: Float,
    mirror: Boolean,
): Pair<Vec, Vec> = when (limb) {
    is Fk -> {
        val joint = root + dir(limb.upper, mirror) * l1
        joint to (joint + dir(limb.lower, mirror) * l2)
    }
    is Ik -> {
        val target = Vec(limb.x, limb.y)
        val dv = target - root
        val dist = dv.length
        // obere Grenze ohne Epsilon: voll gestreckte Gliedmaßen bleiben exakt gerade
        val d = dist.coerceIn(abs(l1 - l2) + 1e-3f, l1 + l2)
        val ux = if (dist < 1e-5f) 0f else dv.x / dist
        val uy = if (dist < 1e-5f) -1f else dv.y / dist
        val cosA = ((l1 * l1 + d * d - l2 * l2) / (2 * l1 * d)).coerceIn(-1f, 1f)
        val along = l1 * cosA
        val perp = l1 * sqrt(max(0f, 1f - cosA * cosA))
        val side = frontSide * (if (limb.flip) -1f else 1f) * (if (mirror) -1f else 1f)
        val nx = -uy * side
        val ny = ux * side
        val joint = Vec(root.x + ux * along + nx * perp, root.y + uy * along + ny * perp)
        val reachable = dist <= l1 + l2 && dist >= abs(l1 - l2)
        val end = if (reachable) target else {
            val e = target - joint
            val el = max(e.length, 1e-5f)
            joint + e * (l2 / el)
        }
        joint to end
    }
}

fun Pose.resolve(view: View): Skeleton {
    val hip = Vec(hipX, hipY)
    val tDir = dir(torso)
    val shoulder = hip + tDir * Body.TORSO
    val headPos = shoulder + dir(head ?: torso) * Body.NECK
    val front = view == View.FRONT
    // Quer zur Rumpfrichtung: in der Frontansicht liegen Schultern/Hüften nebeneinander.
    val across = Vec(tDir.y, -tDir.x)
    val shN = if (front) shoulder + across * Body.SHOULDER_HALF else shoulder
    val shF = if (front) shoulder - across * Body.SHOULDER_HALF else shoulder
    val hpN = if (front) hip + across * Body.HIP_HALF else hip
    val hpF = if (front) hip - across * Body.HIP_HALF else hip
    val (elN, haN) = solveLimb(shN, armN, Body.UPPER_ARM, Body.FOREARM, -1f, false)
    val (elF, haF) = solveLimb(shF, armF, Body.UPPER_ARM, Body.FOREARM, -1f, front)
    val (knN, anN) = solveLimb(hpN, legN, Body.THIGH, Body.SHIN, 1f, false)
    val (knF, anF) = solveLimb(hpF, legF, Body.THIGH, Body.SHIN, 1f, front)
    return Skeleton(
        head = headPos,
        shoulderN = shN, shoulderF = shF,
        elbowN = elN, handN = haN, elbowF = elF, handF = haF,
        hipN = hpN, hipF = hpF,
        kneeN = knN, ankleN = anN, toeN = anN + dir(footN) * Body.FOOT,
        kneeF = knF, ankleF = anF, toeF = anF + dir(footF, front) * Body.FOOT,
    )
}

private fun lerpAngle(a: Float, b: Float, f: Float): Float {
    var d = (b - a) % 360f
    if (d > 180f) d -= 360f
    if (d < -180f) d += 360f
    return a + d * f
}

private fun toFk(root: Vec, joint: Vec, end: Vec, mirror: Boolean) =
    Fk(angleOf(joint - root, mirror), angleOf(end - joint, mirror))

private fun lerpLimb(
    a: Limb, b: Limb, f: Float,
    fkA: () -> Fk, fkB: () -> Fk,
): Limb = when {
    a is Ik && b is Ik -> Ik(lerp(a.x, b.x, f), lerp(a.y, b.y, f), if (f < 0.5f) a.flip else b.flip)
    a is Fk && b is Fk -> Fk(lerp(a.upper, b.upper, f), lerp(a.lower, b.lower, f))
    else -> {
        // gemischt: beide Seiten in Winkel umrechnen und auf kürzestem Weg drehen
        val x = fkA()
        val y = fkB()
        Fk(lerpAngle(x.upper, y.upper, f), lerpAngle(x.lower, y.lower, f))
    }
}

/**
 * Zwischenpose. IK-Ziele und FK-Winkel werden getrennt interpoliert – Knochenlängen bleiben konstant.
 *
 * [pivot]: fester Drehpunkt der Bewegung (z. B. die Füße beim Liegestütz oder die Schultern bei der
 * Glute Bridge). Die Hüfte wandert dann auf einem Kreisbogen um diesen Punkt statt auf einer Geraden –
 * gestreckte Beine bleiben so auch zwischen den Keyframes gestreckt.
 */
fun interpolate(a: Pose, b: Pose, f: Float, view: View, pivot: Vec? = null): Pose {
    if (f <= 0f) return a
    if (f >= 1f) return b
    val sa by lazy { a.resolve(view) }
    val sb by lazy { b.resolve(view) }
    val front = view == View.FRONT
    val hip = if (pivot == null) {
        Vec(lerp(a.hipX, b.hipX, f), lerp(a.hipY, b.hipY, f))
    } else {
        val va = Vec(a.hipX, a.hipY) - pivot
        val vb = Vec(b.hipX, b.hipY) - pivot
        val ang = lerpAngle(angleOf(va), angleOf(vb), f)
        pivot + dir(ang) * lerp(va.length, vb.length, f)
    }
    return Pose(
        hipX = hip.x,
        hipY = hip.y,
        torso = lerp(a.torso, b.torso, f),
        head = if (a.head == null && b.head == null) null else lerp(a.head ?: a.torso, b.head ?: b.torso, f),
        armN = lerpLimb(a.armN, b.armN, f,
            { toFk(sa.shoulderN, sa.elbowN, sa.handN, false) }, { toFk(sb.shoulderN, sb.elbowN, sb.handN, false) }),
        armF = lerpLimb(a.armF, b.armF, f,
            { toFk(sa.shoulderF, sa.elbowF, sa.handF, front) }, { toFk(sb.shoulderF, sb.elbowF, sb.handF, front) }),
        legN = lerpLimb(a.legN, b.legN, f,
            { toFk(sa.hipN, sa.kneeN, sa.ankleN, false) }, { toFk(sb.hipN, sb.kneeN, sb.ankleN, false) }),
        legF = lerpLimb(a.legF, b.legF, f,
            { toFk(sa.hipF, sa.kneeF, sa.ankleF, front) }, { toFk(sb.hipF, sb.kneeF, sb.ankleF, front) }),
        footN = lerp(a.footN, b.footN, f),
        footF = lerp(a.footF, b.footF, f),
        depth = lerp(a.depth, b.depth, f),
    )
}
