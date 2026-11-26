package de.leon.fitstreak.figure3d

import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.sqrt

/** Teil eines Netzes mit eigenem Material (Muskelregion): Dreiecks-Indizes in die Vertices des Netzes. */
class FigurePart(val material: String, val indices: IntArray)

class FigureMesh(
    val positions: FloatArray,
    val normals: FloatArray,
    /** Je Vertex vier Gelenk-Nummern (Position in der Skin-Liste) und vier Gewichte. */
    val joints: IntArray,
    val weights: FloatArray,
    val parts: List<FigurePart>,
) {
    val vertexCount: Int get() = positions.size / 3
}

/**
 * Das Mannequin aus der GLB-Datei: Skelett, Netze und die Verformung der Netze durch das Skelett.
 *
 * Die Verformung (Skinning) rechnet die App selbst auf der CPU statt im Vertex-Shader: Der
 * Skinning-Shader der Engine braucht mehr Uniforms, als manche Grafiktreiber erlauben (dort bricht
 * die Engine hart ab). So läuft überall derselbe einfache Shader – und der Software-Renderer im
 * Prüfwerkzeug nutzt genau diese Funktion mit.
 */
class FigureModel(glb: GlbFile) {
    val skeleton: FigureSkeleton = FigureSkeleton.from(glb)
    val meshes: List<FigureMesh>
    private val skinJoints: IntArray
    private val inverseBind: Array<FloatArray>

    /** Gelenk-Matrizen des aktuellen Frames, je 12 Werte (3 Zeilen × 4 Spalten). */
    private val palette: FloatArray

    init {
        val materials = glb.materials.map { it["name"]?.jsonPrimitive?.content ?: "" }
        meshes = glb.meshes.map { mesh ->
            val prims = mesh["primitives"]!!.jsonArray.map { it.jsonObject }
            val attrs = prims.first()["attributes"]!!.jsonObject
            fun accessor(name: String) = attrs[name]?.jsonPrimitive?.int
            val position = accessor("POSITION")!!
            FigureMesh(
                positions = glb.floats(position),
                normals = glb.floats(accessor("NORMAL")!!),
                joints = accessor("JOINTS_0")?.let(glb::ints) ?: IntArray(0),
                weights = accessor("WEIGHTS_0")?.let(glb::floats) ?: FloatArray(0),
                parts = prims.map { p ->
                    require(p["attributes"]!!.jsonObject["POSITION"]!!.jsonPrimitive.int == position) { "Alle Teile eines Netzes teilen sich die Vertices" }
                    FigurePart(materials[p["material"]!!.jsonPrimitive.int], glb.ints(p["indices"]!!.jsonPrimitive.int))
                },
            )
        }
        val skin = glb.skins.firstOrNull()
        skinJoints = skin?.get("joints")?.jsonArray?.map { it.jsonPrimitive.int }?.toIntArray() ?: IntArray(0)
        inverseBind = skin?.get("inverseBindMatrices")?.jsonPrimitive?.int?.let { a ->
            val f = glb.floats(a)
            Array(skinJoints.size) { f.copyOfRange(it * 16, it * 16 + 16) }
        } ?: emptyArray()
        palette = FloatArray(skinJoints.size * 12)
    }

    /** Übernimmt die Welt-Matrizen aller Knoten (aus [Rig3D]) für die folgenden [skin]-Aufrufe. */
    fun pose(world: Array<FloatArray>) {
        for (j in skinJoints.indices) {
            val m = Mat4.mul(world[skinJoints[j]], inverseBind[j])
            val o = j * 12
            // zeilenweise ablegen: x' = r0·(x,y,z,1) …
            palette[o] = m[0]; palette[o + 1] = m[4]; palette[o + 2] = m[8]; palette[o + 3] = m[12]
            palette[o + 4] = m[1]; palette[o + 5] = m[5]; palette[o + 6] = m[9]; palette[o + 7] = m[13]
            palette[o + 8] = m[2]; palette[o + 9] = m[6]; palette[o + 10] = m[10]; palette[o + 11] = m[14]
        }
    }

    /** Verformt [mesh] mit der aktuellen Pose: Positionen und normierte Normalen (je 3 Werte pro Vertex). */
    fun skin(mesh: FigureMesh, outPositions: FloatArray, outNormals: FloatArray) {
        val p = palette
        val pos = mesh.positions
        val nor = mesh.normals
        val joints = mesh.joints
        val weights = mesh.weights
        for (v in 0 until mesh.vertexCount) {
            var m0 = 0f; var m1 = 0f; var m2 = 0f; var m3 = 0f
            var m4 = 0f; var m5 = 0f; var m6 = 0f; var m7 = 0f
            var m8 = 0f; var m9 = 0f; var m10 = 0f; var m11 = 0f
            val b = v * 4
            for (k in 0..3) {
                val w = weights[b + k]
                if (w == 0f) continue
                val o = joints[b + k] * 12
                m0 += w * p[o]; m1 += w * p[o + 1]; m2 += w * p[o + 2]; m3 += w * p[o + 3]
                m4 += w * p[o + 4]; m5 += w * p[o + 5]; m6 += w * p[o + 6]; m7 += w * p[o + 7]
                m8 += w * p[o + 8]; m9 += w * p[o + 9]; m10 += w * p[o + 10]; m11 += w * p[o + 11]
            }
            val i = v * 3
            val x = pos[i]; val y = pos[i + 1]; val z = pos[i + 2]
            outPositions[i] = m0 * x + m1 * y + m2 * z + m3
            outPositions[i + 1] = m4 * x + m5 * y + m6 * z + m7
            outPositions[i + 2] = m8 * x + m9 * y + m10 * z + m11
            val nx = nor[i]; val ny = nor[i + 1]; val nz = nor[i + 2]
            val ax = m0 * nx + m1 * ny + m2 * nz
            val ay = m4 * nx + m5 * ny + m6 * nz
            val az = m8 * nx + m9 * ny + m10 * nz
            val len = sqrt(ax * ax + ay * ay + az * az)
            val inv = if (len < 1e-12f) 0f else 1f / len
            outNormals[i] = ax * inv; outNormals[i + 1] = ay * inv; outNormals[i + 2] = az * inv
        }
    }

    companion object {
        /**
         * Normalen als Tangentenraum-Quaternionen (so erwartet sie die Engine): die kürzeste Drehung,
         * die +Z auf die Normale legt. [outQuats] bekommt 4 Werte pro Vertex.
         */
        fun quaternions(normals: FloatArray, outQuats: FloatArray) {
            for (v in 0 until normals.size / 3) {
                val nx = normals[v * 3]; val ny = normals[v * 3 + 1]; val nz = normals[v * 3 + 2]
                val w = 1f + nz
                val o = v * 4
                if (w < 1e-4f) {
                    // Normale zeigt nach -Z: halbe Drehung um X
                    outQuats[o] = 1f; outQuats[o + 1] = 0f; outQuats[o + 2] = 0f; outQuats[o + 3] = 0f
                } else {
                    val inv = 1f / sqrt(2f * w)
                    outQuats[o] = -ny * inv; outQuats[o + 1] = nx * inv; outQuats[o + 2] = 0f; outQuats[o + 3] = w * inv
                }
            }
        }
    }
}
