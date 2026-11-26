package preview

import de.leon.fitstreak.core.anim.ExerciseAnimation
import de.leon.fitstreak.core.exercises.ExerciseLibrary
import de.leon.fitstreak.core.model.BodyPart
import de.leon.fitstreak.figure3d.FigureModel
import de.leon.fitstreak.figure3d.GlbFile
import de.leon.fitstreak.figure3d.Mat4
import de.leon.fitstreak.figure3d.Orbit
import de.leon.fitstreak.figure3d.Paint3
import de.leon.fitstreak.figure3d.Prop3Kind
import de.leon.fitstreak.figure3d.Prop3Role
import de.leon.fitstreak.figure3d.Rig3D
import de.leon.fitstreak.figure3d.V3
import de.leon.fitstreak.figure3d.paintFor
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
private fun model(file: File) = FigureModel(GlbFile(file.readBytes()))

private class Canvas3(val w: Int, val h: Int, eye: V3, target: V3, fovY: Float) {
    val color = IntArray(w * h)
    val depth = FloatArray(w * h) { Float.MAX_VALUE }
    private val fwd = (target - eye).normalized()
    private val right = (fwd cross V3.Y).normalized()
    private val up = right cross fwd
    private val eyePos = eye
    private val f = (h / 2f) / tan(Math.toRadians(fovY / 2.0)).toFloat()
    private val light = (right * -0.45f + up * 0.75f - fwd * 0.6f).normalized()
    private val fillLight = (right * 0.7f - up * 0.15f - fwd * 0.6f).normalized()

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
            // Hauptlicht + weiches Fülllicht von unten/vorn, damit Schattenseiten Form behalten
            val key = max(0f, nn dot light)
            val fill = max(0f, nn dot fillLight)
            return (0.36f + 0.56f * key + 0.14f * fill).coerceAtMost(1.06f)
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

    /** Deckkraft-Maske (0..1) im Ausgaberaster – für den Bodenschatten der freigestellten Bilder. */
    fun coverage(factor: Int): FloatArray {
        val ow = w / factor; val oh = h / factor
        val out = FloatArray(ow * oh)
        for (y in 0 until oh) for (x in 0 until ow) {
            var n = 0
            for (dy in 0 until factor) for (dx in 0 until factor) if (depth[(y * factor + dy) * w + x * factor + dx] != Float.MAX_VALUE) n++
            out[y * ow + x] = n / (factor * factor).toFloat()
        }
        return out
    }

    /** Freigestelltes Bild (ARGB) mit weichem Schatten darunter. */
    fun cutout(factor: Int, shadow: FloatArray?): BufferedImage {
        val ow = w / factor; val oh = h / factor
        val out = BufferedImage(ow, oh, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until oh) for (x in 0 until ow) {
            var r = 0; var g = 0; var b = 0; var n = 0
            for (dy in 0 until factor) for (dx in 0 until factor) {
                val i = (y * factor + dy) * w + x * factor + dx
                if (depth[i] == Float.MAX_VALUE) continue
                val c = color[i]
                r += (c shr 16) and 0xFF; g += (c shr 8) and 0xFF; b += c and 0xFF; n++
            }
            val cover = n / (factor * factor).toFloat()
            val sh = (shadow?.get(y * ow + x) ?: 0f) * 0.26f
            // Figur über Schatten (Schatten = Schwarz mit Deckkraft sh)
            val alpha = cover + sh * (1f - cover)
            if (alpha <= 0.002f) continue
            val k = if (n == 0) 0f else cover / alpha / n
            val a = (alpha * 255f).toInt().coerceIn(0, 255)
            out.setRGB(x, y, (a shl 24) or ((r * k).toInt().coerceIn(0, 255) shl 16) or ((g * k).toInt().coerceIn(0, 255) shl 8) or (b * k).toInt().coerceIn(0, 255))
        }
        return out
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
    val figure = model(File(assets, "figure.glb"))
    val box = model(File(assets, "box.glb"))
    val cylinder = model(File(assets, "cylinder.glb"))
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
        val canvas = Canvas3(size * 2, size * 2, orbit.eye(orbit.distance(Orbit.FOV, 1f)), orbit.target, Orbit.FOV)
        draw(canvas, anim, time, primary, secondary, mat = true, flatten = null)
        return canvas.image(0xF4F4F5)
    }

    /**
     * Freigestelltes Bild für die App (durchsichtiger Hintergrund, weicher Bodenschatten statt Matte) –
     * funktioniert so im hellen und im dunklen Design.
     */
    fun cutout(
        anim: ExerciseAnimation, time: Float, primary: Set<BodyPart>, secondary: Set<BodyPart>,
        width: Int, height: Int, orbit: Orbit, zoom: Float = 1f,
    ): BufferedImage {
        val factor = 4
        val eye = orbit.eye(orbit.distance(Orbit.FOV, width / height.toFloat()) / zoom)
        val canvas = Canvas3(width * factor, height * factor, eye, orbit.target, Orbit.FOV)
        draw(canvas, anim, time, primary, secondary, mat = false, flatten = null)
        // Schatten: dieselbe Szene, entlang der Lichtrichtung auf den Boden gedrückt, dann weichgezeichnet
        val shadowCanvas = Canvas3(width * factor, height * factor, eye, orbit.target, Orbit.FOV)
        val sun = V3(-0.35f, -1f, -0.25f)
        draw(shadowCanvas, anim, time, primary, secondary, mat = false) { p -> V3(p.x - sun.x * (p.y / sun.y), 0f, p.z - sun.z * (p.y / sun.y)) }
        val shadow = blur(shadowCanvas.coverage(factor), width, height, radius = maxOf(3, width / 70))
        return canvas.cutout(factor, shadow)
    }

    private fun blur(src: FloatArray, w: Int, h: Int, radius: Int): FloatArray {
        var a = src
        repeat(3) {
            val tmp = FloatArray(w * h)
            for (y in 0 until h) for (x in 0 until w) {
                var sum = 0f
                for (d in -radius..radius) sum += a[y * w + (x + d).coerceIn(0, w - 1)]
                tmp[y * w + x] = sum / (2 * radius + 1)
            }
            val out = FloatArray(w * h)
            for (y in 0 until h) for (x in 0 until w) {
                var sum = 0f
                for (d in -radius..radius) sum += tmp[(y + d).coerceIn(0, h - 1) * w + x]
                out[y * w + x] = sum / (2 * radius + 1)
            }
            a = out
        }
        return a
    }

    private fun draw(
        canvas: Canvas3, anim: ExerciseAnimation, time: Float, primary: Set<BodyPart>, secondary: Set<BodyPart>,
        mat: Boolean, flatten: ((V3) -> V3)?,
    ) {
        val solved = rig.solve(anim, time)
        fun place(p: V3) = flatten?.invoke(p) ?: p

        // dieselbe CPU-Verformung wie in der App
        figure.pose(solved.world)
        for (mesh in figure.meshes) {
            val p = FloatArray(mesh.vertexCount * 3)
            val nrm = FloatArray(mesh.vertexCount * 3)
            figure.skin(mesh, p, nrm)
            fun pos(i: Int) = place(V3(p[i * 3], p[i * 3 + 1], p[i * 3 + 2]))
            fun nor(i: Int) = V3(nrm[i * 3], nrm[i * 3 + 1], nrm[i * 3 + 2])
            for (part in mesh.parts) {
                val rgb = paint(paintFor(part.material, primary, secondary))
                for (t in part.indices.indices step 3) {
                    val a = part.indices[t]; val b = part.indices[t + 1]; val c = part.indices[t + 2]
                    canvas.triangle(pos(a), pos(b), pos(c), nor(a), nor(b), nor(c), rgb)
                }
            }
        }
        for (prop in rig.props(anim, solved)) {
            if (!mat && prop.role == Prop3Role.MAT) continue
            val model = if (prop.kind == Prop3Kind.BOX) box else cylinder
            val mesh = model.meshes.first()
            val count = mesh.positions.size / 3
            val lin = Mat4.linear(prop.matrix)
            val pos = Array(count) { Mat4.point(prop.matrix, V3(mesh.positions[it * 3], mesh.positions[it * 3 + 1], mesh.positions[it * 3 + 2])) }
            val nor = Array(count) { lin * V3(mesh.normals[it * 3], mesh.normals[it * 3 + 1], mesh.normals[it * 3 + 2]) }
            for (part in mesh.parts) for (t in part.indices.indices step 3) {
                val a = part.indices[t]; val b = part.indices[t + 1]; val c = part.indices[t + 2]
                canvas.triangle(place(pos[a]), place(pos[b]), place(pos[c]), nor[a], nor[b], nor[c], role(prop.role))
            }
        }
    }
}

/**
 * art <outDir> – freigestellte Bilder der 3D-Figur für die App (Onboarding, Tipps, leere Zustände).
 * Die Ergebnisse liegen unter app/src/main/res/drawable-nodpi/.
 */
fun art(args: List<String>) {
    val out = File(args.getOrNull(0) ?: "out").apply { mkdirs() }
    val soft = Soft3D(File("../../../app/src/main/assets/figure3d"))
    class Shot(val name: String, val id: String, val at: Float, val yaw: Float, val pitch: Float, val w: Int, val h: Int, val zoom: Float = 1f, val paint: Boolean = true)
    val shots = listOf(
        Shot("art_welcome", "liegestuetze", 0.0f, -4f, 2f, 1536, 896, 1.18f),
        Shot("art_tips", "bizeps_curls", 0.33f, 22f, -2f, 900, 900, 0.88f),
        Shot("art_rest", "glute_bridge", 0.0f, -6f, 4f, 1440, 704, 1.2f, paint = false),
        Shot("art_empty", "kniebeugen", 0.6f, 28f, 0f, 900, 720, 0.88f),
    )
    for (s in shots.filter { args.size < 2 || it.name in args.drop(1) }) {
        val e = ExerciseLibrary.require(s.id)
        val base = Orbit.forAnimation(e.animation, soft.rig.lift)
        val orbit = base.with(yaw = base.yaw + s.yaw, pitch = base.pitch + s.pitch)
        val img = soft.cutout(
            e.animation, e.animation.cycleSeconds * s.at,
            if (s.paint) e.primaryParts else emptySet(), if (s.paint) e.secondaryParts else emptySet(),
            s.w, s.h, orbit, s.zoom,
        )
        // auf den Inhalt zuschneiden (mit etwas Rand), damit das Bild seinen Platz in der App ausfüllt
        var x0 = img.width; var y0 = img.height; var x1 = 0; var y1 = 0
        for (y in 0 until img.height) for (x in 0 until img.width) if ((img.getRGB(x, y) ushr 24) > 6) {
            x0 = min(x0, x); y0 = min(y0, y); x1 = max(x1, x); y1 = max(y1, y)
        }
        val pad = 14
        x0 = max(0, x0 - pad); y0 = max(0, y0 - pad); x1 = min(img.width - 1, x1 + pad); y1 = min(img.height - 1, y1 + pad)
        val file = File(out, "${s.name}.png")
        ImageIO.write(img.getSubimage(x0, y0, x1 - x0 + 1, y1 - y0 + 1), "png", file)
        println("geschrieben: $file (${x1 - x0 + 1}×${y1 - y0 + 1}, ${file.length() / 1024} kB)")
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
