package de.leon.fitstreak.figure3d

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Minimaler GLB-Leser (glTF 2.0 binär): JSON-Teil + Zugriff auf Accessor-Daten. */
class GlbFile(bytes: ByteArray) {
    val json: JsonObject
    private val bin: ByteBuffer

    init {
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        require(buf.getInt(0) == 0x46546C67) { "Keine GLB-Datei" }
        val jsonLength = buf.getInt(12)
        json = Json.parseToJsonElement(String(bytes, 20, jsonLength, Charsets.UTF_8)).jsonObject
        val binStart = 20 + jsonLength
        bin = if (binStart + 8 <= bytes.size) {
            val binLength = buf.getInt(binStart)
            ByteBuffer.wrap(bytes, binStart + 8, binLength).slice().order(ByteOrder.LITTLE_ENDIAN)
        } else ByteBuffer.allocate(0)
    }

    private fun arr(name: String): JsonArray = json[name]?.jsonArray ?: JsonArray(emptyList())

    val nodes: List<JsonObject> get() = arr("nodes").map { it.jsonObject }
    val meshes: List<JsonObject> get() = arr("meshes").map { it.jsonObject }
    val materials: List<JsonObject> get() = arr("materials").map { it.jsonObject }
    val skins: List<JsonObject> get() = arr("skins").map { it.jsonObject }

    private fun width(type: String) = when (type) {
        "SCALAR" -> 1; "VEC2" -> 2; "VEC3" -> 3; "VEC4" -> 4; "MAT4" -> 16
        else -> error("Unbekannter Accessor-Typ $type")
    }

    /** Liest einen Accessor als Floats (Ganzzahl-Typen werden umgewandelt). */
    fun floats(accessor: Int): FloatArray {
        val acc = arr("accessors")[accessor].jsonObject
        val view = arr("bufferViews")[acc["bufferView"]!!.jsonPrimitive.int].jsonObject
        val count = acc["count"]!!.jsonPrimitive.int
        val w = width(acc["type"]!!.jsonPrimitive.content)
        val component = acc["componentType"]!!.jsonPrimitive.int
        val size = when (component) { 5120, 5121 -> 1; 5122, 5123 -> 2; else -> 4 }
        val start = (view["byteOffset"]?.jsonPrimitive?.int ?: 0) + (acc["byteOffset"]?.jsonPrimitive?.int ?: 0)
        val stride = view["byteStride"]?.jsonPrimitive?.int ?: (size * w)
        val out = FloatArray(count * w)
        for (i in 0 until count) for (k in 0 until w) {
            val p = start + i * stride + k * size
            out[i * w + k] = when (component) {
                5126 -> bin.getFloat(p)
                5121 -> (bin.get(p).toInt() and 0xFF).toFloat()
                5123 -> (bin.getShort(p).toInt() and 0xFFFF).toFloat()
                5125 -> bin.getInt(p).toFloat()
                5120 -> bin.get(p).toFloat()
                else -> bin.getShort(p).toFloat()
            }
        }
        return out
    }

    fun ints(accessor: Int): IntArray = floats(accessor).let { f -> IntArray(f.size) { f[it].toInt() } }

    companion object {
        fun floatArray(o: JsonObject, key: String): FloatArray? = o[key]?.jsonArray?.map { it.jsonPrimitive.float }?.toFloatArray()
    }
}

/** Knoten der Figur: Name, Elternknoten und Ruhe-Transformation. */
class FigureNode(val name: String, val parent: Int, val restLocal: FloatArray)

/** Skelett aus der GLB-Datei: alle Knoten in einer Reihenfolge, in der Eltern vor Kindern stehen. */
class FigureSkeleton(val nodes: List<FigureNode>, val order: IntArray) {
    val restWorld: Array<FloatArray> = Array(nodes.size) { Mat4.identity() }.also { world ->
        for (i in order) {
            val n = nodes[i]
            world[i] = if (n.parent < 0) n.restLocal else Mat4.mul(world[n.parent], n.restLocal)
        }
    }

    fun index(name: String): Int = nodes.indexOfFirst { it.name == name || it.name.substringAfter(':') == name }

    companion object {
        fun from(glb: GlbFile): FigureSkeleton {
            val raw = glb.nodes
            val parent = IntArray(raw.size) { -1 }
            raw.forEachIndexed { i, n -> n["children"]?.jsonArray?.forEach { parent[it.jsonPrimitive.int] = i } }
            val nodes = raw.mapIndexed { i, n ->
                val matrix = GlbFile.floatArray(n, "matrix")
                    ?: Mat4.fromTrs(GlbFile.floatArray(n, "translation"), GlbFile.floatArray(n, "rotation"), GlbFile.floatArray(n, "scale"))
                FigureNode(n["name"]?.jsonPrimitive?.content ?: "node$i", parent[i], matrix)
            }
            // Eltern vor Kindern
            val order = ArrayList<Int>()
            val done = BooleanArray(nodes.size)
            fun visit(i: Int) {
                if (done[i]) return
                if (parent[i] >= 0) visit(parent[i])
                done[i] = true
                order += i
            }
            nodes.indices.forEach(::visit)
            return FigureSkeleton(nodes, order.toIntArray())
        }
    }
}
