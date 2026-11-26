package de.leon.fitstreak.figure3d

import kotlin.math.sqrt

/** Kleine 3D-Mathematik für das Skelett. Matrizen: 4×4, spaltenweise (wie glTF und Filament). */
data class V3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(o: V3) = V3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: V3) = V3(x - o.x, y - o.y, z - o.z)
    operator fun times(f: Float) = V3(x * f, y * f, z * f)
    operator fun unaryMinus() = V3(-x, -y, -z)
    infix fun dot(o: V3) = x * o.x + y * o.y + z * o.z
    infix fun cross(o: V3) = V3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
    val length: Float get() = sqrt(x * x + y * y + z * z)
    fun normalized(fallback: V3 = Y): V3 {
        val l = length
        return if (l < 1e-6f) fallback else V3(x / l, y / l, z / l)
    }

    /** Anteil senkrecht zu [axis] (axis muss normiert sein). */
    fun rejectFrom(axis: V3): V3 = this - axis * (this dot axis)

    companion object {
        val X = V3(1f, 0f, 0f)
        val Y = V3(0f, 1f, 0f)
        val Z = V3(0f, 0f, 1f)
        val ZERO = V3(0f, 0f, 0f)
    }
}

/** 3×3-Drehung als drei Spaltenvektoren. */
class M3(val cx: V3, val cy: V3, val cz: V3) {
    operator fun times(v: V3) = cx * v.x + cy * v.y + cz * v.z
    operator fun times(o: M3) = M3(this * o.cx, this * o.cy, this * o.cz)
    fun transposed() = M3(V3(cx.x, cy.x, cz.x), V3(cx.y, cy.y, cz.y), V3(cx.z, cy.z, cz.z))

    companion object {
        val IDENTITY = M3(V3.X, V3.Y, V3.Z)

        /** Drehung um [axis] (normiert) um [degrees]. */
        fun rotation(axis: V3, degrees: Float): M3 {
            val r = Math.toRadians(degrees.toDouble())
            val c = kotlin.math.cos(r).toFloat()
            val s = kotlin.math.sin(r).toFloat()
            fun turn(v: V3) = v * c + (axis cross v) * s + axis * ((axis dot v) * (1f - c))
            return M3(turn(V3.X), turn(V3.Y), turn(V3.Z))
        }

        /**
         * Rechtshändiges Achsensystem aus Knochenrichtung [dir] und Scharnierachse [hinge]:
         * Spalten = (dir, hinge × dir, hinge). [hinge] wird senkrecht zu [dir] gestellt.
         */
        fun frame(dir: V3, hinge: V3): M3 {
            val d = dir.normalized()
            val h = hinge.rejectFrom(d).normalized(fallback = if (kotlin.math.abs(d.x) < 0.9f) V3.X.rejectFrom(d).normalized() else V3.Z.rejectFrom(d).normalized())
            return M3(d, h cross d, h)
        }
    }
}

object Mat4 {
    fun identity() = floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)

    fun mul(a: FloatArray, b: FloatArray): FloatArray {
        val r = FloatArray(16)
        for (c in 0..3) for (row in 0..3) {
            var s = 0f
            for (k in 0..3) s += a[k * 4 + row] * b[c * 4 + k]
            r[c * 4 + row] = s
        }
        return r
    }

    fun translation(m: FloatArray) = V3(m[12], m[13], m[14])

    fun linear(m: FloatArray) = M3(V3(m[0], m[1], m[2]), V3(m[4], m[5], m[6]), V3(m[8], m[9], m[10]))

    fun compose(linear: M3, t: V3) = floatArrayOf(
        linear.cx.x, linear.cx.y, linear.cx.z, 0f,
        linear.cy.x, linear.cy.y, linear.cy.z, 0f,
        linear.cz.x, linear.cz.y, linear.cz.z, 0f,
        t.x, t.y, t.z, 1f,
    )

    fun point(m: FloatArray, p: V3) = V3(
        m[0] * p.x + m[4] * p.y + m[8] * p.z + m[12],
        m[1] * p.x + m[5] * p.y + m[9] * p.z + m[13],
        m[2] * p.x + m[6] * p.y + m[10] * p.z + m[14],
    )

    fun fromTrs(t: FloatArray?, r: FloatArray?, s: FloatArray?): FloatArray {
        val x = r?.get(0) ?: 0f; val y = r?.get(1) ?: 0f; val z = r?.get(2) ?: 0f; val w = r?.get(3) ?: 1f
        val sx = s?.get(0) ?: 1f; val sy = s?.get(1) ?: 1f; val sz = s?.get(2) ?: 1f
        return floatArrayOf(
            (1 - 2 * (y * y + z * z)) * sx, (2 * (x * y + z * w)) * sx, (2 * (x * z - y * w)) * sx, 0f,
            (2 * (x * y - z * w)) * sy, (1 - 2 * (x * x + z * z)) * sy, (2 * (y * z + x * w)) * sy, 0f,
            (2 * (x * z + y * w)) * sz, (2 * (y * z - x * w)) * sz, (1 - 2 * (x * x + y * y)) * sz, 0f,
            t?.get(0) ?: 0f, t?.get(1) ?: 0f, t?.get(2) ?: 0f, 1f,
        )
    }

    /** Inverse einer affinen Matrix (letzte Zeile 0 0 0 1). */
    fun inverseAffine(m: FloatArray): FloatArray {
        val a = m[0]; val b = m[4]; val c = m[8]
        val d = m[1]; val e = m[5]; val f = m[9]
        val g = m[2]; val h = m[6]; val i = m[10]
        val det = a * (e * i - f * h) - b * (d * i - f * g) + c * (d * h - e * g)
        val inv = if (kotlin.math.abs(det) < 1e-20f) 0f else 1f / det
        val r00 = (e * i - f * h) * inv; val r01 = (c * h - b * i) * inv; val r02 = (b * f - c * e) * inv
        val r10 = (f * g - d * i) * inv; val r11 = (a * i - c * g) * inv; val r12 = (c * d - a * f) * inv
        val r20 = (d * h - e * g) * inv; val r21 = (b * g - a * h) * inv; val r22 = (a * e - b * d) * inv
        val tx = m[12]; val ty = m[13]; val tz = m[14]
        return floatArrayOf(
            r00, r10, r20, 0f,
            r01, r11, r21, 0f,
            r02, r12, r22, 0f,
            -(r00 * tx + r01 * ty + r02 * tz), -(r10 * tx + r11 * ty + r12 * tz), -(r20 * tx + r21 * ty + r22 * tz), 1f,
        )
    }

    /** Verschiebung · Drehung (Achse y → [axis]) · Skalierung – für Zylinder zwischen zwei Punkten. */
    fun alongAxis(center: V3, axis: V3, length: Float, diameter: Float): FloatArray {
        val y = axis.normalized()
        val x = (if (kotlin.math.abs(y.x) < 0.9f) V3.X else V3.Z).rejectFrom(y).normalized()
        val z = x cross y
        return compose(M3(x * diameter, y * length, z * diameter), center)
    }

    fun box(center: V3, sizeX: Float, sizeY: Float, sizeZ: Float) =
        compose(M3(V3.X * sizeX, V3.Y * sizeY, V3.Z * sizeZ), center)
}
