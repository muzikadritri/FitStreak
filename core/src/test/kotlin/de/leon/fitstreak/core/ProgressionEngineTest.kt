package de.leon.fitstreak.core

import de.leon.fitstreak.core.model.Equipment
import de.leon.fitstreak.core.model.PlanExercise
import de.leon.fitstreak.core.model.Rating
import de.leon.fitstreak.core.model.Tempo
import de.leon.fitstreak.core.progression.Change
import de.leon.fitstreak.core.progression.ProgressionEngine
import de.leon.fitstreak.core.stats.SetResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressionEngineTest {
    private val home = setOf(Equipment.KURZHANTEL)
    private fun sets(n: Int, reps: Int, kg: Double = 0.0) = List(n) { SetResult(reps, kg) }

    private val curls = PlanExercise("bizeps_curls", sets = 3, repMin = 5, repMax = 10, weightKg = 12.0, restSec = 60)

    @Test
    fun `oberes Ende geschafft und schwereres Gewicht vorhanden - mehr Gewicht`() {
        val r = ProgressionEngine.next(curls, sets(3, 10, 12.0), Rating.PASST, listOf(12.0, 14.0), home)
        assertEquals(Change.GEWICHT_HOCH, r.change)
        assertEquals(14.0, r.next.weightKg, 0.0)
        assertEquals(Tempo.NORMAL, r.next.tempo)
    }

    @Test
    fun `nur 12 kg vorhanden - erst Tempo, dann Pause, dann zusaetzlicher Satz`() {
        val only12 = listOf(12.0)
        val r1 = ProgressionEngine.next(curls, sets(3, 10, 12.0), Rating.PASST, only12, home)
        assertEquals(Change.TEMPO, r1.change)
        assertEquals(Tempo.LANGSAM, r1.next.tempo)
        assertEquals(12.0, r1.next.weightKg, 0.0)

        val r2 = ProgressionEngine.next(r1.next, sets(3, 10, 12.0), Rating.LEICHT, only12, home)
        assertEquals(Tempo.PAUSE, r2.next.tempo)

        val r3 = ProgressionEngine.next(r2.next, sets(3, 10, 12.0), Rating.PASST, only12, home)
        assertEquals(Change.SATZ_PLUS, r3.change)
        assertEquals(4, r3.next.sets)
    }

    @Test
    fun `zu grosser Gewichtssprung wird nicht vorgeschlagen`() {
        // 12 → 20 kg wäre +67 %
        val r = ProgressionEngine.next(curls, sets(3, 10, 12.0), Rating.PASST, listOf(12.0, 20.0), home)
        assertEquals(Change.TEMPO, r.change)
    }

    @Test
    fun `Koerpergewicht - schwerere Variante auf der Leiter`() {
        val pushups = PlanExercise("liegestuetze", 3, 6, 12, restSec = 75)
        val r = ProgressionEngine.next(pushups, sets(3, 12), Rating.PASST, emptyList(), emptySet())
        assertEquals(Change.VARIANTE_SCHWERER, r.change)
        assertEquals("liegestuetze_erhoeht", r.next.exerciseId)
        assertTrue(r.message.contains("erhöhten Füßen"))
    }

    @Test
    fun `Knie-Liegestuetze steigen zu normalen Liegestuetzen auf`() {
        val knee = PlanExercise("knie_liegestuetze", 3, 8, 15)
        val r = ProgressionEngine.next(knee, sets(3, 15), Rating.LEICHT, emptyList(), emptySet())
        assertEquals("liegestuetze", r.next.exerciseId)
        assertEquals(6, r.next.repMin)
        assertEquals(12, r.next.repMax)
    }

    @Test
    fun `im Zielbereich aber nicht oben - bleibt`() {
        val r = ProgressionEngine.next(curls, listOf(SetResult(10, 12.0), SetResult(8, 12.0), SetResult(7, 12.0)), Rating.PASST, listOf(12.0), home)
        assertEquals(Change.BLEIBT, r.change)
        assertEquals(curls, r.next)
    }

    @Test
    fun `schwer bewertet - bleibt trotz voller Wiederholungen`() {
        val r = ProgressionEngine.next(curls, sets(3, 10, 12.0), Rating.SCHWER, listOf(12.0, 14.0), home)
        assertEquals(Change.BLEIBT, r.change)
        assertEquals(12.0, r.next.weightKg, 0.0)
    }

    @Test
    fun `einmal nicht geschafft bleibt, zweimal wird leichter`() {
        val withTwo = listOf(8.0, 12.0)
        val r1 = ProgressionEngine.next(curls, sets(3, 3, 12.0), Rating.SCHWER, withTwo, home)
        assertEquals(Change.BLEIBT, r1.change)
        assertEquals(1, r1.next.failStreak)
        val r2 = ProgressionEngine.next(r1.next, sets(3, 4, 12.0), Rating.SCHWER, withTwo, home)
        assertEquals(Change.LEICHTER, r2.change)
        assertEquals(8.0, r2.next.weightKg, 0.0)
        assertEquals(0, r2.next.failStreak)
    }

    @Test
    fun `abgebrochene Saetze zaehlen als nicht geschafft`() {
        val r = ProgressionEngine.next(curls, sets(2, 10, 12.0), null, listOf(12.0), home)
        assertEquals(1, r.next.failStreak)
    }

    @Test
    fun `Erfolg setzt den Fehlversuch-Zaehler zurueck`() {
        val r = ProgressionEngine.next(curls.copy(failStreak = 1), sets(3, 7, 12.0), Rating.PASST, listOf(12.0), home)
        assertEquals(0, r.next.failStreak)
    }

    @Test
    fun `zweimal gescheitert ohne leichteres Gewicht - leichtere Variante`() {
        val pushups = PlanExercise("liegestuetze", 3, 6, 12, failStreak = 1)
        val r = ProgressionEngine.next(pushups, sets(3, 3), Rating.SCHWER, emptyList(), emptySet())
        assertEquals(Change.LEICHTER, r.change)
        assertEquals("knie_liegestuetze", r.next.exerciseId)
    }

    @Test
    fun `Zeit-Uebung - Ziel steigt um 5 Sekunden, bei leicht um 10`() {
        val plank = PlanExercise("plank", 3, repMin = 40, repMax = 60)
        assertEquals(45, ProgressionEngine.next(plank, sets(3, 40), Rating.PASST, emptyList(), emptySet()).next.repMin)
        assertEquals(50, ProgressionEngine.next(plank, sets(3, 42), Rating.LEICHT, emptyList(), emptySet()).next.repMin)
        assertEquals(40, ProgressionEngine.next(plank, sets(3, 38), Rating.PASST, emptyList(), emptySet()).next.repMin)
    }

    @Test
    fun `Zeit-Uebung am oberen Ende wechselt zur schwereren Variante`() {
        val plank = PlanExercise("plank", 3, repMin = 60, repMax = 60)
        val r = ProgressionEngine.next(plank, sets(3, 60), Rating.PASST, emptyList(), emptySet())
        assertEquals("hollow_hold", r.next.exerciseId)
    }

    @Test
    fun `Deload-Woche veraendert nichts`() {
        val r = ProgressionEngine.next(curls, sets(3, 10, 12.0), Rating.LEICHT, listOf(12.0, 14.0), home, deload = true)
        assertEquals(curls, r.next)
        assertEquals(2, ProgressionEngine.deloadSets(3))
        assertEquals(2, ProgressionEngine.deloadSets(2))
        assertEquals(3, ProgressionEngine.deloadSets(4))
    }

    @Test
    fun `Geraet steigert in 5-kg-Schritten`() {
        val lat = PlanExercise("latzug", 3, 8, 12, weightKg = 30.0)
        val r = ProgressionEngine.next(lat, sets(3, 12, 30.0), Rating.PASST, emptyList(), setOf(Equipment.STUDIO))
        assertEquals(35.0, r.next.weightKg, 0.0)
    }
}
