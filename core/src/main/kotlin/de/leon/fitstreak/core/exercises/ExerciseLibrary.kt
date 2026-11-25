package de.leon.fitstreak.core.exercises

import de.leon.fitstreak.core.anim.ExerciseAnimation
import de.leon.fitstreak.core.anim.P
import de.leon.fitstreak.core.anim.Phase
import de.leon.fitstreak.core.model.Equipment
import de.leon.fitstreak.core.model.Exercise
import de.leon.fitstreak.core.model.Muscle

/** Eine Bewegung im geführten Aufwärmen. */
data class WarmupMove(
    val id: String,
    val name: String,
    val seconds: Int,
    val hint: String,
    val animation: ExerciseAnimation,
)

private val placeholderAnimation = ExerciseAnimation(
    poses = listOf(P.stand()),
    phases = listOf(Phase("", 2f, 0)),
)

private val allAnimations: Map<String, ExerciseAnimation> by lazy {
    upperBodyAnimations + lowerBodyAnimations + warmupAnimations
}

internal fun animationFor(id: String): ExerciseAnimation = allAnimations[id] ?: placeholderAnimation

internal fun hasOwnAnimation(id: String): Boolean = id in allAnimations

/** Alle Übungen der App – rein datengetrieben. */
object ExerciseLibrary {
    val all: List<Exercise> by lazy { upperBodyExercises + lowerBodyExercises }

    private val byId: Map<String, Exercise> by lazy { all.associateBy { it.id } }

    operator fun get(id: String): Exercise? = byId[id]

    fun require(id: String): Exercise = byId[id] ?: error("Unbekannte Übung: $id")

    /** Varianten-Leiter, leicht → schwer. */
    fun ladder(name: String): List<Exercise> = all.filter { it.ladder == name }.sortedBy { it.ladderRank }

    fun harderVariant(e: Exercise): Exercise? =
        e.ladder?.let { l -> ladder(l).firstOrNull { it.ladderRank > e.ladderRank } }

    fun easierVariant(e: Exercise): Exercise? =
        e.ladder?.let { l -> ladder(l).lastOrNull { it.ladderRank < e.ladderRank } }

    fun byMuscle(m: Muscle): List<Exercise> = all.filter { m in it.primary }

    /** Zählen für den Erfolg „1000 Liegestütze“. */
    val pushupIds: Set<String> = setOf(
        "knie_liegestuetze", "liegestuetze", "liegestuetze_erhoeht", "archer_liegestuetze",
        "enge_liegestuetze", "pike_liegestuetze",
    )

    /** Geführtes Aufwärmen, ca. 3,5 Minuten. */
    val warmup: List<WarmupMove> by lazy {
        listOf(
            WarmupMove("wu_marschieren", "Auf der Stelle marschieren", 40, "Knie hoch, Arme schwingen mit.", animationFor("wu_marschieren")),
            WarmupMove("wu_hampelmann", "Hampelmänner", 40, "Locker springen, gleichmäßig atmen.", animationFor("wu_hampelmann")),
            WarmupMove("wu_armkreisen", "Armkreisen", 30, "Große Kreise, erst vorwärts, dann rückwärts.", animationFor("wu_armkreisen")),
            WarmupMove("wu_rumpfdrehen", "Rumpfdrehen", 30, "Oberkörper locker von Seite zu Seite drehen.", animationFor("wu_rumpfdrehen")),
            WarmupMove("wu_kniebeugen", "Lockere Kniebeugen", 40, "Ohne Eile, Gelenke warm machen.", animationFor("kniebeugen")),
            WarmupMove("wu_bergsteiger", "Bergsteiger (langsam)", 30, "Im Stütz die Knie abwechselnd nach vorn ziehen.", animationFor("mountain_climbers")),
        )
    }

    fun isAvailable(e: Exercise, owned: Set<Equipment>): Boolean =
        e.equipment.all { it == Equipment.KOERPERGEWICHT || it in owned }
}
