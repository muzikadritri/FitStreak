package preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.leon.fitstreak.core.anim.ExerciseAnimation
import de.leon.fitstreak.core.exercises.ExerciseLibrary
import de.leon.fitstreak.core.model.BodyPart
import de.leon.fitstreak.ui.components.FigureDefaults
import de.leon.fitstreak.ui.components.drawExercise
import org.jetbrains.skia.EncodedImageFormat
import java.io.File

@OptIn(ExperimentalComposeUiApi::class)
fun renderPng(file: File, width: Int, height: Int, density: Float = 1f, frames: Int = 1, content: @Composable () -> Unit) {
    val scene = ImageComposeScene(width = width, height = height, density = Density(density)) { content() }
    var img = scene.render(0)
    // weitere Frames, damit Effekte/Flows/Animationen ankommen (je 100 ms)
    for (i in 1 until frames) {
        Thread.sleep(15)
        img = scene.render(i * 100_000_000L)
    }
    file.writeBytes(img.encodeToData(EncodedImageFormat.PNG)!!.bytes)
    scene.close()
}

private class Entry(val title: String, val anim: ExerciseAnimation, val primary: Set<BodyPart>, val secondary: Set<BodyPart>)

/**
 * sheet <outDir> [id …]  – Kontaktbögen der Übungsanimationen (Keyframes + Zwischenbilder).
 */
fun main(args: Array<String>) {
    when (args.firstOrNull()) {
        "plan" -> printPlans()
        "sheet3d" -> sheets3d(args.drop(1))
        "screens" -> screens(File(args.getOrNull(1) ?: "out").apply { mkdirs() }, args.drop(2).toSet())
        else -> sheets(args)
    }
}

private fun printPlans() {
    val base = de.leon.fitstreak.core.model.UserProfile(
        goals = setOf(de.leon.fitstreak.core.model.Goal.SIXPACK, de.leon.fitstreak.core.model.Goal.OBERKOERPER),
        equipment = setOf(de.leon.fitstreak.core.model.Equipment.KURZHANTEL),
        dumbbellKg = listOf(12.0), daysPerWeek = 4, maxPushups = 10, maxPlankSec = 60, maxSquats = 20,
    )
    for (p in listOf(base, base.copy(daysPerWeek = 3), base.copy(daysPerWeek = 5, equipment = emptySet(), dumbbellKg = emptyList()))) {
        println("=== ${p.daysPerWeek} Tage, ${p.equipment}")
        for (w in de.leon.fitstreak.core.plan.PlanGenerator.generate(p)) {
            println("${w.name} (${w.focus}) ~${de.leon.fitstreak.core.plan.PlanGenerator.estimateMinutes(w.exercises)} min")
            for (e in w.exercises) println("   ${ExerciseLibrary.require(e.exerciseId).name}: ${e.sets} x ${e.repMin}-${e.repMax} @ ${e.weightKg} kg, Pause ${e.restSec}")
        }
    }
}

private fun sheets(args: Array<String>) {
    val out = File(args.getOrNull(1) ?: "out").apply { mkdirs() }
    val ids = args.drop(2).toSet()
    val entries = ExerciseLibrary.all.map { Entry("${it.id} · ${it.name}", it.animation, it.primaryParts, it.secondaryParts) } +
        ExerciseLibrary.warmup.map { Entry("${it.id} · ${it.name}", it.animation, emptySet(), emptySet()) }
    val selected = entries.filter { e -> ids.isEmpty() || ids.any { e.title.startsWith("$it ·") } }
    val perPage = 6
    selected.chunked(perPage).forEachIndexed { page, chunk ->
        val file = File(out, "sheet_${(page + 1).toString().padStart(2, '0')}.png")
        renderPng(file, 1500, 20 + chunk.size * 270) {
            Column(Modifier.fillMaxSize().background(Color.White).padding(10.dp)) {
                for (e in chunk) {
                    BasicText(e.title + "   [" + e.anim.phases.joinToString(" | ") { it.label } + "]", style = TextStyle(fontSize = 15.sp))
                    Row(Modifier.height(250.dp)) {
                        // Zeitpunkte: Ende jeder Phase (= Keyframe) und jeweils die Mitte davor
                        val times = buildList {
                            var t = 0f
                            for (ph in e.anim.phases) {
                                add(t + ph.seconds * 0.4f)
                                add(t + ph.seconds * 0.999f)
                                t += ph.seconds
                            }
                        }.take(6)
                        for (t in times) {
                            Box(Modifier.width(245.dp).height(245.dp).padding(3.dp).background(Color(0xFFF3F5FA))) {
                                androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                                    drawExercise(e.anim, e.anim.frameAt(t).pose, e.primary, e.secondary, FigureDefaults.Light)
                                }
                            }
                        }
                    }
                }
            }
        }
        println("geschrieben: $file  (${chunk.joinToString { it.title.substringBefore(" ·") }})")
    }
}
