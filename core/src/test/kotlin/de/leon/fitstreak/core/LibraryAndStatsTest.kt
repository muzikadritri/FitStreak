package de.leon.fitstreak.core

import de.leon.fitstreak.core.exercises.ExerciseLibrary
import de.leon.fitstreak.core.model.Muscle
import de.leon.fitstreak.core.stats.Best
import de.leon.fitstreak.core.stats.SetResult
import de.leon.fitstreak.core.stats.Stats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryAndStatsTest {
    @Test
    fun `rund 40 Uebungen mit eindeutigen IDs und vollstaendiger Anleitung`() {
        val all = ExerciseLibrary.all
        assertTrue(all.size >= 40)
        assertEquals(all.size, all.map { it.id }.toSet().size)
        for (e in all) {
            assertTrue("${e.id}: Schritte", e.steps.size in 3..4)
            assertTrue("${e.id}: Fehler", e.mistakes.size >= 2)
            assertTrue("${e.id}: Atmung", e.breathing.isNotBlank())
            assertTrue("${e.id}: Muskeln", e.primary.isNotEmpty())
            assertTrue("${e.id}: Bereich", e.repRange.first < e.repRange.last)
        }
    }

    @Test
    fun `jede Muskelgruppe hat Uebungen ohne Geraete`() {
        for (m in Muscle.entries) {
            if (m == Muscle.BIZEPS) continue // Bizeps isoliert braucht Hantel, Band oder Stange
            assertTrue("$m", ExerciseLibrary.byMuscle(m).any { it.equipment.isEmpty() })
        }
    }

    @Test
    fun `Varianten-Leitern sind lueckenlos sortiert`() {
        val ladders = ExerciseLibrary.all.mapNotNull { it.ladder }.toSet()
        assertTrue("liegestuetze" in ladders)
        for (l in ladders) {
            val ranks = ExerciseLibrary.ladder(l).map { it.ladderRank }
            assertEquals("Leiter $l", (1..ranks.size).toList(), ranks)
        }
        assertEquals(
            listOf("knie_liegestuetze", "liegestuetze", "liegestuetze_erhoeht", "archer_liegestuetze"),
            ExerciseLibrary.ladder("liegestuetze").map { it.id },
        )
    }

    @Test
    fun `Animationen sind in sich stimmig`() {
        for (e in ExerciseLibrary.all) {
            val a = e.animation
            assertTrue("${e.id}: Dauer", a.cycleSeconds > 0f)
            // läuft zyklisch ohne Fehler und bleibt über dem Boden-Bereich
            for (i in 0..20) a.frameAt(a.cycleSeconds * i / 10f)
            assertTrue("${e.id}: Bounds", a.bounds.width > 0.2f && a.bounds.height > 0.2f)
        }
        // jede Übung hat eine eigene Animation mit mindestens zwei Keyframes und beschrifteten Phasen
        for (e in ExerciseLibrary.all) {
            assertTrue("${e.id}: eigene Animation fehlt", de.leon.fitstreak.core.exercises.hasOwnAnimation(e.id))
            assertTrue("${e.id}: Keyframes", e.animation.poses.size in 2..4)
            assertTrue("${e.id}: Phasen-Beschriftung", e.animation.phases.all { it.label.isNotBlank() })
        }
        for (w in ExerciseLibrary.warmup) assertTrue(w.id, w.animation.poses.size >= 2)
        assertEquals(6, ExerciseLibrary.warmup.size)
        assertTrue(ExerciseLibrary.warmup.sumOf { it.seconds } in 180..240)
    }

    @Test
    fun `geschaetztes 1RM nach Epley`() {
        assertEquals(12.0, Stats.epley1Rm(12.0, 1), 0.001)
        assertEquals(16.0, Stats.epley1Rm(12.0, 10), 0.001)
        assertEquals(0.0, Stats.epley1Rm(0.0, 10), 0.001)
    }

    @Test
    fun `Rekord-Erkennung`() {
        val first = Stats.bestOf(listOf(SetResult(10, 12.0)))
        assertFalse("erstes Mal ist kein Rekord", Stats.isRecord(null, first))
        assertTrue(Stats.isRecord(first, Stats.bestOf(listOf(SetResult(11, 12.0)))))
        assertTrue(Stats.isRecord(first, Stats.bestOf(listOf(SetResult(6, 14.0)))))
        assertFalse(Stats.isRecord(first, Stats.bestOf(listOf(SetResult(10, 12.0)))))
        // ohne Gewicht zählen die Wiederholungen
        val bw = Best(maxReps = 12)
        assertTrue(Stats.isRecord(bw, Best(maxReps = 13)))
        assertFalse(Stats.isRecord(bw, Best(maxReps = 12)))
    }

    @Test
    fun `Saetze je Muskel - primaer voll, sekundaer halb`() {
        val m = Stats.muscleSets(mapOf("liegestuetze" to 4, "bizeps_curls" to 3))
        assertEquals(4f, m[Muscle.BRUST])
        assertEquals(2f, m[Muscle.TRIZEPS])
        assertEquals(3f, m[Muscle.BIZEPS])
    }

    @Test
    fun `Erholung`() {
        val h = 3_600_000L
        assertTrue(Stats.recovery(Muscle.BRUST, null, 0).ready)
        assertEquals(24, Stats.recovery(Muscle.BRUST, 0, 24 * h).hoursLeft)
        assertTrue(Stats.recovery(Muscle.BRUST, 0, 48 * h).ready)
        assertTrue(Stats.recovery(Muscle.BAUCH, 0, 24 * h).ready)
    }

    @Test
    fun `Deload-Vorschlag nach sechs regelmaessigen Wochen`() {
        assertFalse(Stats.shouldSuggestDeload(5, 20))
        assertTrue(Stats.shouldSuggestDeload(6, 18))
        assertFalse(Stats.shouldSuggestDeload(8, 4))
    }
}
