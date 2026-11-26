package preview

import de.leon.fitstreak.core.anim.ExerciseAnimation
import de.leon.fitstreak.core.exercises.ExerciseLibrary
import de.leon.fitstreak.core.model.BodyPart
import de.leon.fitstreak.figure3d.FigureSkeleton
import de.leon.fitstreak.figure3d.GlbFile
import de.leon.fitstreak.figure3d.Mat4
import de.leon.fitstreak.figure3d.Orbit
import de.leon.fitstreak.figure3d.Paint3
import de.leon.fitstreak.figure3d.Prop3Kind
import de.leon.fitstreak.figure3d.Prop3Role
import de.leon.fitstreak.figure3d.Rig3D
import de.leon.fitstreak.figure3d.V3
import de.leon.fitstreak.figure3d.paintFor
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.max
import kotlin.math.min
import kotlin.math.tan

/**
 * Software-Renderer (CPU-Skinning + Z-Buffer) für die 3D-Figur. Nur zum Prüfen ohne Gerät:
 * Er benutzt dieselbe Skelett-Lösung ([Rig3D]) und dieselben Modelldateien wie die App.
 */
private class Part(val material: String, val indices: IntArray)

private class SoftMesh(
    val positions: FloatArray, val normals: FloatArray,
    val joints: IntArray, val weights: FloatArray, val parts: List<Part>,
)

private class Model(file: File) {
    val glb = GlbFile(file.readBytes())
    val skeleton = FigureSkeleton.from(glb)
    val meshes: List<SoftMesh>
    val skinJoints: IntArray
    val inverseBind: Array<FloatArray>

    init {
        val materials = glb.materials.map { it["name"]!!.jsonPrimitive.content }
        meshes = glb.meshes.map { mesh ->
            val prims = mesh["primitives"]!!.jsonArray.map { it.jsonObject }
            val attrs = prims.first()["attributes"]!!.jsonObject
            fun acc(name: String) = attrs[name]?.jsonPrimitive?.int
            SoftMesh(
                positions = glb.floats(acc("POSITION")!!),
                normals = glb.floats(acc("NORMAL")!!),
                joints = acc("JOINTS_0")?.let(glb::ints) ?: IntArray(0),
                weights = acc("WEIGHTS_0")?.let(glb::floats) ?: FloatArray(0),
                parts = prims.map { p ->
                    require(p["attributes"]!!.jsonObject["POSITION"]!!.jsonPrimitive.int == acc("POSITION")) { "Primitive teilen sich die Vertices" }
                    Part(materials[p["material"]!!.jsonPrimitive.int], glb.ints(p["indices"]!!.jsonPrimitive.int))
                },
            )
        }
        val skin = glb.skins.firstOrNull()
        skinJoints = skin?.get("joints")?.jsonArray?.map { it.jsonPrimitive.int }?.toIntArray() ?: IntArray(0)
        inverseBind = skin?.get("inverseBindMatrices")?.jsonPrimitive?.int?.let { a ->
            val f = glb.floats(a)
            Array(skinJoints.size) { f.copyOfRange(it * 16, it * 16 + 16) }
        } ?: emptyArray()
    }
}

private class Canvas3(val w: Int, val h: Int, eye: V3, target: V3, fovY: Float) {
    val color = IntArray(w * h)
    val depth = FloatArray(w * h) { Float.MAX_VALUE }
    private val fwd = (target - eye).normalized()
    private val right = (fwd cross V3.Y).normalized()
    private val up = right cross fwd
    private val eyePos = eye
    private val f = (h / 2f) / tan(Math.toRadians(fovY / 2.0)).toFloat()
    private val light = (right * -0.45f + up * 0.75f - fwd * 0.6f).normalized()

    private fun project(p: V3): FloatArray {
        val d = p - eyePos
        val z = d dot fwd
        return floatArrayOf(w / 2f + (d dot right) / z * f, h / 2f - (d dot up) / z * f, z)
    }

    fun triangle(a: V3, b: V3, c: V3, na: V3, nb: V3, nc: V3, rgb: Int) {
        val pa = project(a); val pb = project(b); val pc = project(c)
        if (pa[2] <= 0.05f || pb[2] <= 0.05f || pc[2] <= 0.05f) return
        val area = (pb[0] - pa[0]) * (pc[1] - pa[1]) - (pb[1] - pa[1]) * (pc[0] - pa[0])
        if (kotlin.math.abs(area) < 1e-6f) return
        val x0 = max(0, min(pa[0], min(pb[0], pc[0])).toInt()); val x1 = min(w - 1, max(pa[0], max(pb[0], pc[0])).toInt() + 1)
        val y0 = max(0, min(pa[1], min(pb[1], pc[1])).toInt()); val y1 = min(h - 1, max(pa[1], max(pb[1], pc[1])).toInt() + 1)
        val r = (rgb shr 16) and 0xFF; val g = (rgb shr 8) and 0xFF; val bl = rgb and 0xFF
        fun shade(n: V3): Float {
            var nn = n.normalized()
            if ((nn dot fwd) > 0f) nn = -nn
            return 0.42f + 0.58f * max(0f, nn dot light)
        }
        val sa = shade(na); val sb = shade(nb); val sc = shade(nc)
        for (y in y0..y1) for (x in x0..x1) {
            val px = x + 0.5f; val py = y + 0.5f
            val w0 = ((pb[0] - px) * (pc[1] - py) - (pb[1] - py) * (pc[0] - px)) / area
            val w1 = ((pc[0] - px) * (pa[1] - py) - (pc[1] - py) * (pa[0] - px)) / area
            val w2 = 1f - w0 - w1
            if (w0 < 0f || w1 < 0f || w2 < 0f) continue
            val z = w0 * pa[2] + w1 * pb[2] + w2 * pc[2]
            val i = y * w + x
            if (z >= depth[i]) continue
            depth[i] = z
            val s = w0 * sa + w1 * sb + w2 * sc
            color[i] = (0xFF shl 24) or ((r * s).toInt().coerceIn(0, 255) shl 16) or ((g * s).toInt().coerceIn(0, 255) shl 8) or (bl * s).toInt().coerceIn(0, 255)
        }
    }

    /** 2×-Supersampling zurückrechnen. */
    fun image(background: Int): BufferedImage {
        val out = BufferedImage(w / 2, h / 2, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until h / 2) for (x in 0 until w / 2) {
            var r = 0; var g = 0; var b = 0
            for (dy in 0..1) for (dx in 0..1) {
                val i = (y * 2 + dy) * w + x * 2 + dx
                val c = if (depth[i] == Float.MAX_VALUE) background else color[i]
                r += (c shr 16) and 0xFF; g += (c shr 8) and 0xFF; b += c and 0xFF
            }
            out.setRGB(x, y, ((r / 4) shl 16) or ((g / 4) shl 8) or (b / 4))
        }
        return out
    }
}

private class Soft3D(assets: File) {
    val figure = Model(File(assets, "figure.glb"))
    val box = Model(File(assets, "box.glb"))
    val cylinder = Model(File(assets, "cylinder.glb"))
    val rig = Rig3D(figure.skeleton)

    private fun paint(p: Paint3) = when (p) {
        Paint3.BODY -> 0xE9E9EC
        Paint3.JOINT -> 0x2A2A2E
        Paint3.PRIMARY -> 0xDC2626
        Paint3.SECONDARY -> 0xE0A100
    }

    private fun role(r: Prop3Role) = when (r) {
        Prop3Role.MAT -> 0xD9D9DE
        Prop3Role.STEEL -> 0x9A9AA2
        Prop3Role.DARK -> 0x3A3A40
        Prop3Role.BAND -> 0x16A34A
    }

    fun render(
        anim: ExerciseAnimation, time: Float, primary: Set<BodyPart>, secondary: Set<BodyPart>,
        size: Int, orbit: Orbit = Orbit.forAnimation(anim, rig.lift),
    ): BufferedImage {
        val solved = rig.solve(anim, time)
        val canvas = Canvas3(size * 2, size * 2, orbit.eye(orbit.distance(Orbit.FOV, 1f)), orbit.target, Orbit.FOV)

        val skin = Array(figure.skinJoints.size) { Mat4.mul(solved.world[figure.skinJoints[it]], figure.inverseBind[it]) }
        for (mesh in figure.meshes) {
            val count = mesh.positions.size / 3
            val pos = arrayOfNulls<V3>(count)
            val nor = arrayOfNulls<V3>(count)
            for (v in 0 until count) {
                val p = V3(mesh.positions[v * 3], mesh.positions[v * 3 + 1], mesh.positions[v * 3 + 2])
                val n = V3(mesh.normals[v * 3], mesh.normals[v * 3 + 1], mesh.normals[v * 3 + 2])
                var sp = V3.ZERO; var sn = V3.ZERO
                for (k in 0..3) {
                    val wgt = mesh.weights[v * 4 + k]
                    if (wgt == 0f) continue
                    val m = skin[mesh.joints[v * 4 + k]]
                    sp += Mat4.point(m, p) * wgt
                    sn += (Mat4.linear(m) * n) * wgt
                }
                pos[v] = sp; nor[v] = sn
            }
            for (part in mesh.parts) {
                val rgb = paint(paintFor(part.material, primary, secondary))
                for (t in part.indices.indices step 3) {
                    val a = part.indices[t]; val b = part.indices[t + 1]; val c = part.indices[t + 2]
                    canvas.triangle(pos[a]!!, pos[b]!!, pos[c]!!, nor[a]!!, nor[b]!!, nor[c]!!, rgb)
                }
            }
        }
        for (prop in rig.props(anim, solved)) {
            val model = if (prop.kind == Prop3Kind.BOX) box else cylinder
            val mesh = model.meshes.first()
            val count = mesh.positions.size / 3
            val lin = Mat4.linear(prop.matrix)
            val pos = Array(count) { Mat4.point(prop.matrix, V3(mesh.positions[it * 3], mesh.positions[it * 3 + 1], mesh.positions[it * 3 + 2])) }
            val nor = Array(count) { lin * V3(mesh.normals[it * 3], mesh.normals[it * 3 + 1], mesh.normals[it * 3 + 2]) }
            for (part in mesh.parts) for (t in part.indices.indices step 3) {
                val a = part.indices[t]; val b = part.indices[t + 1]; val c = part.indices[t + 2]
                canvas.triangle(pos[a], pos[b], pos[c], nor[a], nor[b], nor[c], role(prop.role))
            }
        }
        return canvas.image(0xF4F4F5)
    }
}

/** sheet3d <outDir> [id …] – Kontaktbögen der 3D-Figur: vier Zeitpunkte plus zwei andere Blickwinkel. */
fun sheets3d(args: List<String>) {
    val out = File(args.getOrNull(0) ?: "out").apply { mkdirs() }
    val ids = args.drop(1).toSet()
    val soft = Soft3D(File("../../../app/src/main/assets/figure3d"))
    class Entry(val title: String, val anim: ExerciseAnimation, val primary: Set<BodyPart>, val secondary: Set<BodyPart>)
    val entries = ExerciseLibrary.all.map { Entry("${it.id} · ${it.name}", it.animation, it.primaryParts, it.secondaryParts) } +
        ExerciseLibrary.warmup.map { Entry("${it.id} · ${it.name}", it.animation, emptySet(), emptySet()) }
    val selected = entries.filter { e -> ids.isEmpty() || ids.any { e.title.startsWith("$it ·") } }
    val cell = 260
    selected.chunked(5).forEachIndexed { page, chunk ->
        val sheet = BufferedImage(cell * 6, chunk.size * (cell + 22), BufferedImage.TYPE_INT_RGB)
        val g = sheet.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.color = Color.WHITE; g.fillRect(0, 0, sheet.width, sheet.height)
        g.font = Font(Font.SANS_SERIF, Font.PLAIN, 13)
        chunk.forEachIndexed { row, e ->
            val y = row * (cell + 22)
            g.color = Color.BLACK
            g.drawString("${e.title}  (${e.anim.view})", 6, y + 15)
            val base = Orbit.forAnimation(e.anim, soft.rig.lift)
            val c = e.anim.cycleSeconds
            val mid = e.anim.phases.first().seconds * 0.98f
            val shots = listOf(
                0f to base, mid * 0.6f to base, mid to base,
                mid to base.with(yaw = base.yaw + 180f, pitch = 20f), mid to base.with(yaw = base.yaw + 75f, pitch = 35f),
                0f to base.with(yaw = base.yaw + 62f, pitch = 5f),
            )
            shots.forEachIndexed { col, (t, orbit) ->
                g.drawImage(soft.render(e.anim, t, e.primary, e.secondary, cell, orbit), col * cell, y + 22, null)
            }
        }
        g.dispose()
        val file = File(out, "sheet3d_${(page + 1).toString().padStart(2, '0')}.png")
        ImageIO.write(sheet, "png", file)
        println("geschrieben: $file")
    }
}
