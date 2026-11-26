package de.leon.fitstreak.core.anim

import kotlin.math.max
import kotlin.math.min

/** Eine Phase der Bewegung: in [seconds] zur Pose [to]. Gleiches Ziel wie vorher = halten. */
data class Phase(val label: String, val seconds: Float, val to: Int)

enum class Hands { NEAR, FAR, BOTH }

enum class DumbbellLook {
    /** Hantel zeigt zum Betrachter (Kreis). */
    END_ON,

    /** Hantel von der Seite, Ausrichtung über den Winkel. */
    BAR,
}

enum class BoxStyle { CHAIR, BENCH, STEP }

enum class JointRef { HAND_N, HAND_F, ANKLE_N, ANKLE_F, TOE_N }

sealed interface Anchor
data class Fixed(val x: Float, val y: Float) : Anchor
data class OnJoint(val joint: JointRef) : Anchor

/** Requisiten, die mit der Figur gezeichnet werden. */
sealed interface Prop

data class Dumbbell(
    val hands: Hands = Hands.NEAR,
    val look: DumbbellLook = DumbbellLook.END_ON,
    /** Nur für [DumbbellLook.BAR]: Ausrichtung der Hantelachse (90° = waagerecht). */
    val angle: Float = 90f,
) : Prop

/** Stuhl, Bank oder Stufe: steht auf dem Boden, von [x] bis [x]+[w], Höhe [h]. */
data class Box(val x: Float, val w: Float, val h: Float, val style: BoxStyle = BoxStyle.BENCH) : Prop

/** Klimmzugstange (Querschnitt) an fester Position. */
data class PullBar(val x: Float, val y: Float) : Prop

/** Wand bei [x] (z. B. Wandsitzen). */
data class Wall(val x: Float, val height: Float = 1.6f) : Prop

/** Widerstandsband zwischen zwei Punkten. */
data class Band(val from: Anchor, val to: Anchor) : Prop

data class Frame(val pose: Pose, val phaseIndex: Int, val phaseFraction: Float)

data class Bounds(val minX: Float, val minY: Float, val maxX: Float, val maxY: Float) {
    val width get() = maxX - minX
    val height get() = maxY - minY
}

data class ExerciseAnimation(
    val view: View = View.SIDE,
    val poses: List<Pose>,
    val phases: List<Phase>,
    val props: List<Prop> = emptyList(),
    /** Fester Drehpunkt für die Hüftbahn, siehe [interpolate]. */
    val pivot: Vec? = null,
) {
    init {
        require(poses.isNotEmpty()) { "Mindestens eine Pose" }
        require(phases.isNotEmpty()) { "Mindestens eine Phase" }
        require(phases.all { it.to in poses.indices }) { "Phase zeigt auf unbekannte Pose" }
    }

    val cycleSeconds: Float = phases.sumOf { it.seconds.toDouble() }.toFloat()

    /** Pose zum Zeitpunkt [time] (Sekunden, läuft zyklisch). */
    fun frameAt(time: Float): Frame {
        var t = ((time % cycleSeconds) + cycleSeconds) % cycleSeconds
        var from = phases.last().to
        for ((i, phase) in phases.withIndex()) {
            if (t < phase.seconds || i == phases.lastIndex) {
                val fraction = (t / phase.seconds).coerceIn(0f, 1f)
                if (from == phase.to) return Frame(poses[from], i, fraction)
                // kurze Pause am Umkehrpunkt
                val dwell = min(0.25f, phase.seconds * 0.2f)
                val raw = (t / max(0.01f, phase.seconds - dwell)).coerceIn(0f, 1f)
                return Frame(interpolate(poses[from], poses[phase.to], easeInOut(raw), view, pivot), i, fraction)
            }
            t -= phase.seconds
            from = phase.to
        }
        error("unreachable")
    }

    /** Umschließendes Rechteck über die ganze Bewegung inkl. Requisiten. */
    val bounds: Bounds by lazy {
        var minX = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = 0.3f
        fun add(v: Vec, pad: Float) {
            minX = min(minX, v.x - pad); maxX = max(maxX, v.x + pad); maxY = max(maxY, v.y + pad)
        }
        val steps = 24
        for (s in 0 until steps) {
            val sk = frameAt(cycleSeconds * s / steps).pose.resolve(view)
            sk.joints().forEach { add(it, Body.LIMB_W) }
            add(sk.head, Body.HEAD_R + 0.02f)
        }
        for (p in props) when (p) {
            is Box -> { add(Vec(p.x, p.h), 0.02f); add(Vec(p.x + p.w, p.h), 0.02f) }
            is PullBar -> add(Vec(p.x, p.y), 0.12f)
            is Wall -> { add(Vec(p.x, p.height), 0.05f) }
            is Band -> listOf(p.from, p.to).forEach { if (it is Fixed) add(Vec(it.x, it.y), 0.05f) }
            is Dumbbell -> { minX -= 0.08f; maxX += 0.08f; maxY += 0.08f }
        }
        Bounds(minX, 0f, maxX, maxY)
    }
}

fun easeInOut(x: Float): Float {
    // „Smootherstep“: Geschwindigkeit UND Beschleunigung sind an beiden Enden null –
    // die Figur setzt weich an und kommt weich zum Stehen.
    val t = x.coerceIn(0f, 1f)
    return t * t * t * (t * (6f * t - 15f) + 10f)
}

private fun secLabel(prefix: String, s: Float): String {
    val txt = if (s == s.toInt().toFloat()) s.toInt().toString() else s.toString().replace('.', ',')
    return "$prefix – $txt Sek"
}

/** Posen = [oben, unten]: erst kontrolliert runter, dann hoch (Liegestütze, Kniebeugen …). */
fun downUp(down: Float = 2f, up: Float = 1f): List<Phase> =
    listOf(Phase(secLabel("runter", down), down, 1), Phase(secLabel("hoch", up), up, 0))

/** Posen = [unten, oben]: erst hoch, dann kontrolliert runter (Curls, Drücken, Rudern …). */
fun upDown(up: Float = 1f, down: Float = 2f, hold: Float = 0f): List<Phase> = buildList {
    add(Phase(secLabel("hoch", up), up, 1))
    if (hold > 0f) add(Phase(secLabel("halten", hold), hold, 1))
    add(Phase(secLabel("runter", down), down, 0))
}

/** Halteübung mit leichtem „Atmen“ zwischen zwei fast gleichen Posen. */
fun hold(seconds: Float = 2f): List<Phase> =
    listOf(Phase("halten – Spannung", seconds, 1), Phase("halten – ruhig atmen", seconds, 0))

/** Wechselseitige Übung: Posen = [Seite A, Seite B]. */
fun alternate(seconds: Float = 1f, labelA: String = "Seite wechseln", labelB: String = labelA): List<Phase> =
    listOf(Phase(labelA, seconds, 1), Phase(labelB, seconds, 0))
