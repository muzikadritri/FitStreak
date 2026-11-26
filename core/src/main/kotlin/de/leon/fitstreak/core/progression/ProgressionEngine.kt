package de.leon.fitstreak.core.progression

import de.leon.fitstreak.core.exercises.ExerciseLibrary
import de.leon.fitstreak.core.model.Equipment
import de.leon.fitstreak.core.model.Exercise
import de.leon.fitstreak.core.model.LoadType
import de.leon.fitstreak.core.model.PlanExercise
import de.leon.fitstreak.core.model.Rating
import de.leon.fitstreak.core.model.Tempo
import de.leon.fitstreak.core.stats.SetResult
import kotlin.math.max
import kotlin.math.min

enum class Change {
    GEWICHT_HOCH, VARIANTE_SCHWERER, TEMPO, SATZ_PLUS, ZEIT_PLUS, MEHR_WDH,
    BLEIBT, LEICHTER,
}

data class ProgressionResult(
    val next: PlanExercise,
    val change: Change,
    /** Kurzer Hinweis für die Zusammenfassung, z. B. „Nächstes Mal: 14 kg“. */
    val message: String,
) {
    val isProgress: Boolean get() = change != Change.BLEIBT && change != Change.LEICHTER
}

/**
 * Muskelaufbau-Logik (doppelte Progression) als reine Funktion.
 *
 * 1. In allen Sätzen das obere Ende des Wdh.-Bereichs geschafft (und nicht „Schwer“):
 *    schwereres Gewicht → sonst schwerere Variante → sonst langsameres Tempo → Pause → zusätzlicher Satz.
 * 2. „Schwer“ oder unteres Ende verfehlt: Vorgabe bleibt. Zweimal in Folge verfehlt: leichter.
 */
object ProgressionEngine {
    const val MAX_SETS = 5
    const val MACHINE_STEP = 5.0

    fun next(
        current: PlanExercise,
        results: List<SetResult>,
        rating: Rating?,
        dumbbellKg: List<Double>,
        equipment: Set<Equipment>,
        deload: Boolean = false,
    ): ProgressionResult {
        val exercise = ExerciseLibrary[current.exerciseId]
            ?: return ProgressionResult(current, Change.BLEIBT, "")
        if (deload) return ProgressionResult(current, Change.BLEIBT, "Deload-Woche – Vorgabe bleibt.")
        if (results.isEmpty()) return ProgressionResult(current, Change.BLEIBT, "")
        return if (exercise.isTimed) nextTimed(exercise, current, results, rating, equipment)
        else nextReps(exercise, current, results, rating, dumbbellKg, equipment)
    }

    private fun nextReps(
        e: Exercise, cur: PlanExercise, results: List<SetResult>, rating: Rating?,
        dumbbellKg: List<Double>, equipment: Set<Equipment>,
    ): ProgressionResult {
        val allSets = results.size >= cur.sets
        val top = allSets && results.take(cur.sets).all { it.reps >= cur.repMax && it.weightKg >= cur.weightKg - 1e-6 }
        val failed = !allSets || results.any { it.reps < cur.repMin }

        if (top && rating != Rating.SCHWER) {
            // 1) mehr Gewicht
            nextWeight(e, cur, dumbbellKg)?.let { w ->
                val next = cur.copy(weightKg = w, repMin = baseMin(e, cur), repMax = cur.repMax, tempo = Tempo.NORMAL, failStreak = 0)
                return ProgressionResult(next, Change.GEWICHT_HOCH, "Nächstes Mal ${fmtKg(w)} kg.")
            }
            // 2) schwerere Variante
            harderAvailable(e, equipment)?.let { h ->
                val next = PlanExercise(h.id, min(cur.sets, 3).coerceAtLeast(3), h.repRange.first, h.repRange.last, 0.0, Tempo.NORMAL, h.restSec, 0)
                return ProgressionResult(next, Change.VARIANTE_SCHWERER, "Bereit für mehr: nächstes Mal ${h.name}.")
            }
            // 3) Tempo, 4) Pause, 5) zusätzlicher Satz
            return when {
                cur.tempo == Tempo.NORMAL ->
                    ProgressionResult(cur.copy(tempo = Tempo.LANGSAM, failStreak = 0), Change.TEMPO, "Nächstes Mal langsamer: 3 Sek. runter.")
                cur.tempo == Tempo.LANGSAM ->
                    ProgressionResult(cur.copy(tempo = Tempo.PAUSE, failStreak = 0), Change.TEMPO, "Nächstes Mal mit 1 Sek. Pause am schwersten Punkt.")
                cur.sets < MAX_SETS ->
                    ProgressionResult(cur.copy(sets = cur.sets + 1, failStreak = 0), Change.SATZ_PLUS, "Nächstes Mal ein Satz mehr (${cur.sets + 1} Sätze).")
                else ->
                    ProgressionResult(cur.copy(repMin = cur.repMin + 1, repMax = cur.repMax + 2, failStreak = 0), Change.MEHR_WDH, "Nächstes Mal bis ${cur.repMax + 2} Wdh.")
            }
        }

        if (failed) {
            val streak = cur.failStreak + 1
            if (streak < 2) {
                return ProgressionResult(cur.copy(failStreak = streak), Change.BLEIBT, "Vorgabe bleibt – nächstes Mal packst du das.")
            }
            return easier(e, cur, dumbbellKg)
        }

        val msg = if (top) "War schwer – die Vorgabe bleibt, bis es sich besser anfühlt."
        else "Im Zielbereich. Nächstes Mal 1–2 Wdh. mehr versuchen (Ziel: ${cur.sets}×${cur.repMax})."
        return ProgressionResult(cur.copy(failStreak = 0), Change.BLEIBT, msg)
    }

    private fun nextTimed(
        e: Exercise, cur: PlanExercise, results: List<SetResult>, rating: Rating?, equipment: Set<Equipment>,
    ): ProgressionResult {
        val target = cur.repMin
        val allSets = results.size >= cur.sets
        val reached = allSets && results.take(cur.sets).all { it.reps >= target }
        val failed = !allSets || results.any { it.reps < target * 0.8 }

        if (reached && rating != Rating.SCHWER) {
            if (target < cur.repMax) {
                val step = if (rating == Rating.LEICHT) 10 else 5
                val t = min(cur.repMax, target + step)
                return ProgressionResult(cur.copy(repMin = t, failStreak = 0), Change.ZEIT_PLUS, "Nächstes Mal $t Sek. halten.")
            }
            harderAvailable(e, equipment)?.let { h ->
                val next = PlanExercise(h.id, 3, h.repRange.first, h.repRange.last, 0.0, Tempo.NORMAL, h.restSec, 0)
                return ProgressionResult(next, Change.VARIANTE_SCHWERER, "Bereit für mehr: nächstes Mal ${h.name}.")
            }
            if (cur.sets < MAX_SETS) {
                return ProgressionResult(cur.copy(sets = cur.sets + 1, failStreak = 0), Change.SATZ_PLUS, "Nächstes Mal ein Satz mehr (${cur.sets + 1} Sätze).")
            }
            return ProgressionResult(cur.copy(failStreak = 0), Change.BLEIBT, "Top – halte dieses Niveau.")
        }
        if (failed) {
            val streak = cur.failStreak + 1
            if (streak < 2) return ProgressionResult(cur.copy(failStreak = streak), Change.BLEIBT, "Vorgabe bleibt – nächstes Mal packst du das.")
            val t = max(e.repRange.first, target - 5)
            if (t < target) return ProgressionResult(cur.copy(repMin = t, failStreak = 0), Change.LEICHTER, "Etwas kürzer: nächstes Mal $t Sek.")
            ExerciseLibrary.easierVariant(e)?.let { ez ->
                val next = PlanExercise(ez.id, cur.sets, ez.repRange.first, ez.repRange.last, 0.0, Tempo.NORMAL, ez.restSec, 0)
                return ProgressionResult(next, Change.LEICHTER, "Einen Schritt zurück: nächstes Mal ${ez.name}.")
            }
            return ProgressionResult(cur.copy(failStreak = 0), Change.BLEIBT, "Vorgabe bleibt.")
        }
        return ProgressionResult(cur.copy(failStreak = 0), Change.BLEIBT, "Vorgabe bleibt – sauber halten.")
    }

    private fun easier(e: Exercise, cur: PlanExercise, dumbbellKg: List<Double>): ProgressionResult {
        // Reihenfolge: Tempo zurück → leichteres Gewicht → leichtere Variante → Satz weniger → weniger Wdh.
        if (cur.tempo != Tempo.NORMAL) {
            val t = if (cur.tempo == Tempo.PAUSE) Tempo.LANGSAM else Tempo.NORMAL
            return ProgressionResult(cur.copy(tempo = t, failStreak = 0), Change.LEICHTER, "Etwas leichter: Tempo wieder normaler.")
        }
        if (e.load == LoadType.DUMBBELL) {
            dumbbellKg.filter { it < cur.weightKg - 1e-6 }.maxOrNull()?.let { w ->
                return ProgressionResult(cur.copy(weightKg = w, failStreak = 0), Change.LEICHTER, "Etwas leichter: nächstes Mal ${fmtKg(w)} kg.")
            }
        }
        if (e.load == LoadType.MACHINE && cur.weightKg > MACHINE_STEP) {
            val w = cur.weightKg - MACHINE_STEP
            return ProgressionResult(cur.copy(weightKg = w, failStreak = 0), Change.LEICHTER, "Etwas leichter: nächstes Mal ${fmtKg(w)} kg.")
        }
        ExerciseLibrary.easierVariant(e)?.takeIf { it.load == LoadType.NONE }?.let { ez ->
            val next = PlanExercise(ez.id, cur.sets, ez.repRange.first, ez.repRange.last, 0.0, Tempo.NORMAL, ez.restSec, 0)
            return ProgressionResult(next, Change.LEICHTER, "Einen Schritt zurück: nächstes Mal ${ez.name}.")
        }
        if (cur.sets > 3) {
            return ProgressionResult(cur.copy(sets = cur.sets - 1, failStreak = 0), Change.LEICHTER, "Ein Satz weniger, dafür sauber.")
        }
        val newMin = max(3, cur.repMin - 2)
        if (newMin < cur.repMin) {
            return ProgressionResult(cur.copy(repMin = newMin, repMax = max(newMin + 3, cur.repMax - 2), failStreak = 0), Change.LEICHTER, "Ziel angepasst: $newMin–${max(newMin + 3, cur.repMax - 2)} Wdh.")
        }
        return ProgressionResult(cur.copy(failStreak = 0), Change.BLEIBT, "Vorgabe bleibt.")
    }

    /** Nächstschwereres Gewicht – nur wenn der Sprung machbar ist (höchstens +50 %). */
    private fun nextWeight(e: Exercise, cur: PlanExercise, dumbbellKg: List<Double>): Double? = when (e.load) {
        LoadType.DUMBBELL -> dumbbellKg.filter { it > cur.weightKg + 1e-6 }.minOrNull()
            ?.takeIf { cur.weightKg <= 0.0 || it <= cur.weightKg * 1.5 + 1e-6 }
        LoadType.MACHINE -> cur.weightKg + MACHINE_STEP
        LoadType.NONE -> null
    }

    private fun harderAvailable(e: Exercise, equipment: Set<Equipment>): Exercise? =
        ExerciseLibrary.harderVariant(e)?.takeIf { ExerciseLibrary.isAvailable(it, equipment) && it.load == LoadType.NONE }

    /** Nach einer Gewichtssteigerung beginnt man wieder am unteren Ende des Bereichs. */
    private fun baseMin(e: Exercise, cur: PlanExercise): Int = min(cur.repMin, e.repRange.first)

    /** Ein Satz weniger in der Deload-Woche (mindestens 2). */
    fun deloadSets(sets: Int): Int = max(2, sets - 1)

    fun fmtKg(kg: Double): String =
        if (kg == kg.toLong().toDouble()) kg.toLong().toString() else kg.toString().replace('.', ',')
}
