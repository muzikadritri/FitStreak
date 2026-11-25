package de.leon.fitstreak.core.stats

import de.leon.fitstreak.core.exercises.ExerciseLibrary
import de.leon.fitstreak.core.model.Muscle
import kotlin.math.ceil

data class SetResult(val reps: Int, val weightKg: Double = 0.0)

/** Bestwerte einer Übung. */
data class Best(val maxReps: Int = 0, val maxWeightKg: Double = 0.0, val best1Rm: Double = 0.0)

data class Recovery(val hoursLeft: Int) {
    val ready: Boolean get() = hoursLeft <= 0
}

object Stats {
    /** Geschätztes 1RM nach Epley. */
    fun epley1Rm(weightKg: Double, reps: Int): Double = when {
        weightKg <= 0.0 || reps <= 0 -> 0.0
        reps == 1 -> weightKg
        else -> weightKg * (1.0 + reps / 30.0)
    }

    fun volumeKg(sets: List<SetResult>): Double = sets.sumOf { it.reps * it.weightKg }

    fun bestOf(sets: List<SetResult>): Best = Best(
        maxReps = sets.maxOfOrNull { it.reps } ?: 0,
        maxWeightKg = sets.maxOfOrNull { it.weightKg } ?: 0.0,
        best1Rm = sets.maxOfOrNull { epley1Rm(it.weightKg, it.reps) } ?: 0.0,
    )

    /**
     * Neuer persönlicher Rekord? Das allererste Mal zählt nicht (es gibt noch nichts zu schlagen).
     * Mit Gewicht: mehr Gewicht oder höheres geschätztes 1RM. Ohne Gewicht: mehr Wdh./Sekunden.
     */
    fun isRecord(old: Best?, new: Best): Boolean {
        if (old == null || (old.maxReps == 0 && old.maxWeightKg == 0.0)) return false
        return if (new.maxWeightKg > 0.0 || old.maxWeightKg > 0.0) {
            new.maxWeightKg > old.maxWeightKg + 1e-6 || new.best1Rm > old.best1Rm + 0.05
        } else {
            new.maxReps > old.maxReps
        }
    }

    /** Sätze je Muskelgruppe: primär zählt voll, sekundär halb. Eingabe: Übungs-ID → Anzahl Sätze. */
    fun muscleSets(setsByExercise: Map<String, Int>): Map<Muscle, Float> {
        val out = mutableMapOf<Muscle, Float>()
        for ((id, sets) in setsByExercise) {
            val e = ExerciseLibrary[id] ?: continue
            e.primary.forEach { out[it] = (out[it] ?: 0f) + sets }
            e.secondary.forEach { out[it] = (out[it] ?: 0f) + sets * 0.5f }
        }
        return out
    }

    /** Erholungszeit in Stunden nach einem Training der Muskelgruppe. */
    fun recoveryHours(muscle: Muscle): Int = when (muscle) {
        Muscle.BAUCH -> 24
        Muscle.BEINE, Muscle.RUECKEN, Muscle.BRUST, Muscle.PO -> 48
        else -> 36
    }

    fun recovery(muscle: Muscle, lastTrainedMillis: Long?, nowMillis: Long): Recovery {
        if (lastTrainedMillis == null) return Recovery(0)
        val passedH = (nowMillis - lastTrainedMillis) / 3_600_000.0
        return Recovery(ceil(recoveryHours(muscle) - passedH).toInt().coerceAtLeast(0))
    }

    /** Deload nach 6–8 Wochen regelmäßigem Training vorschlagen. */
    fun shouldSuggestDeload(weeksSinceLastDeload: Int, sessionsSince: Int): Boolean =
        weeksSinceLastDeload >= 6 && sessionsSince >= weeksSinceLastDeload * 2
}
