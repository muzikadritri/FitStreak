package de.leon.fitstreak.figure3d

import de.leon.fitstreak.core.anim.ExerciseAnimation
import de.leon.fitstreak.core.anim.View
import de.leon.fitstreak.core.model.BodyPart
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.tan

/** Farbrollen der Figur – die konkreten Farben kommen aus dem Theme. */
enum class Paint3 { BODY, JOINT, PRIMARY, SECONDARY }

/**
 * Materialnamen im Modell: `s_<REGION>` = Oberfläche, `j_<REGION>` = Gelenkteile.
 * REGION ist `BODY` oder ein [BodyPart] – so lässt sich jeder Muskel einzeln einfärben.
 */
fun paintFor(material: String, primary: Set<BodyPart>, secondary: Set<BodyPart>): Paint3 {
    if (material.startsWith("j_")) return Paint3.JOINT
    val part = BodyPart.entries.firstOrNull { it.name == material.substringAfter('_') } ?: return Paint3.BODY
    return when (part) {
        in primary -> Paint3.PRIMARY
        in secondary -> Paint3.SECONDARY
        else -> Paint3.BODY
    }
}

/**
 * Kamera, die um die Figur kreist. [yaw] 0° = von vorn, -90° = von der rechten Körperseite.
 * [halfWidth]/[halfHeight]: halbe Ausdehnung der ganzen Bewegung um [target].
 */
class Orbit(val target: V3, val halfWidth: Float, val halfHeight: Float, val yaw: Float, val pitch: Float) {
    fun eye(distance: Float): V3 {
        val y = Math.toRadians(yaw.toDouble())
        val p = Math.toRadians(pitch.toDouble())
        return target + V3((cos(p) * sin(y)).toFloat(), sin(p).toFloat(), (cos(p) * cos(y)).toFloat()) * distance
    }

    /** Abstand, bei dem die ganze Bewegung ins Bild passt – egal ob die Ansicht breit oder hoch ist. */
    fun distance(fovYDeg: Float, aspect: Float): Float {
        val ty = tan(Math.toRadians(fovYDeg / 2.0)).toFloat()
        val tx = ty * aspect
        // Zuschlag: etwas Rand, und der vordere Teil der Szene liegt näher an der Kamera
        return max(halfWidth / tx, halfHeight / ty) * 1.1f + 0.45f
    }

    fun with(yaw: Float = this.yaw, pitch: Float = this.pitch) = Orbit(target, halfWidth, halfHeight, yaw, pitch.coerceIn(-5f, 70f))

    companion object {
        const val FOV = 28f

        /** Startansicht: schräg von vorn, damit die Tiefe sichtbar ist – die Bewegung bleibt gut lesbar. */
        fun forAnimation(animation: ExerciseAnimation, lift: Float): Orbit {
            val anim = animation.forThreeD
            val b = anim.bounds
            val cx = (b.minX + b.maxX) / 2f
            val cy = (b.maxY + lift) / 2f
            val side = anim.view == View.SIDE
            val target = if (side) V3(0f, cy, cx) else V3(cx, cy, 0f)
            return Orbit(target, max(b.width / 2f, 0.45f), max(cy, 0.42f), yaw = if (side) -62f else -24f, pitch = 12f)
        }
    }
}
