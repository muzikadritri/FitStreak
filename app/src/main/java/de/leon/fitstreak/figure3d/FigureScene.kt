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

/** Kamera, die um die Figur kreist. [yaw] 0° = von vorn, -90° = von der rechten Körperseite. */
class Orbit(val target: V3, val radius: Float, val yaw: Float, val pitch: Float) {
    fun eye(distance: Float): V3 {
        val y = Math.toRadians(yaw.toDouble())
        val p = Math.toRadians(pitch.toDouble())
        return target + V3((cos(p) * sin(y)).toFloat(), sin(p).toFloat(), (cos(p) * cos(y)).toFloat()) * distance
    }

    /** Abstand, bei dem die ganze Bewegung ins Bild passt. */
    fun distance(fovYDeg: Float, aspect: Float): Float {
        val half = Math.toRadians(fovYDeg / 2.0)
        val halfX = kotlin.math.atan(tan(half) * aspect)
        return (radius / sin(minOf(half, halfX))).toFloat()
    }

    fun with(yaw: Float = this.yaw, pitch: Float = this.pitch) = Orbit(target, radius, yaw, pitch.coerceIn(-5f, 70f))

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
            // Kugel um die Bewegung; die Figur ist quer zur Bildebene etwa schulterbreit
            val half = max(b.width / 2f, 0.35f)
            val radius = kotlin.math.sqrt(half * half + cy * cy + 0.05f) * 0.98f
            return Orbit(target, radius, yaw = if (side) -62f else -24f, pitch = 12f)
        }
    }
}
