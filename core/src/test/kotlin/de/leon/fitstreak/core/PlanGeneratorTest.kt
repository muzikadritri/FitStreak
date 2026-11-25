package de.leon.fitstreak.core

import de.leon.fitstreak.core.exercises.ExerciseLibrary
import de.leon.fitstreak.core.model.Equipment
import de.leon.fitstreak.core.model.Goal
import de.leon.fitstreak.core.model.LoadType
import de.leon.fitstreak.core.model.Muscle
import de.leon.fitstreak.core.model.Slot
import de.leon.fitstreak.core.model.UserProfile
import de.leon.fitstreak.core.plan.PlanGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PlanGeneratorTest {
    private val coreSlots = setOf(Slot.CORE_STATIC, Slot.CORE_FLEX, Slot.CORE_ROT, Slot.CORE_DYN)

    /** Startplan aus der Aufgabenstellung: 4 Tage, Sixpack + Oberkörper, eine 12-kg-Hantel. */
    private val leon = UserProfile(
        goals = setOf(Goal.SIXPACK, Goal.OBERKOERPER),
        equipment = setOf(Equipment.KURZHANTEL),
        dumbbellKg = listOf(12.0),
        daysPerWeek = 4,
        maxPushups = 10, maxPlankSec = 60, maxSquats = 20,
    )

    private fun allProfiles(): List<UserProfile> = buildList {
        val equipmentSets = listOf(
            emptySet(),
            setOf(Equipment.KURZHANTEL),
            setOf(Equipment.KLIMMZUGSTANGE, Equipment.BAND),
            Equipment.entries.toSet(),
        )
        for (days in 3..5) for (eq in equipmentSets) for (goal in Goal.entries) {
            add(UserProfile(setOf(goal), eq, if (Equipment.KURZHANTEL in eq) listOf(5.0, 12.0) else emptyList(), days, 12, 45, 25))
        }
    }

    @Test
    fun `Anzahl und Namen der Workouts je Split`() {
        assertEquals(listOf("Ganzkörper A", "Ganzkörper B"), PlanGenerator.generate(leon.copy(daysPerWeek = 3)).map { it.name })
        assertEquals(listOf("Oberkörper A", "Unterkörper A", "Oberkörper B", "Unterkörper B"), PlanGenerator.generate(leon).map { it.name })
        assertEquals(listOf("Push", "Pull", "Beine", "Oberkörper", "Ganzkörper"), PlanGenerator.generate(leon.copy(daysPerWeek = 5)).map { it.name })
    }

    @Test
    fun `nur Uebungen mit vorhandener Ausruestung`() {
        for (p in allProfiles()) {
            for (w in PlanGenerator.generate(p)) for (pe in w.exercises) {
                val e = ExerciseLibrary.require(pe.exerciseId)
                assertTrue("${e.name} braucht ${e.equipment}, vorhanden ${p.equipment}", ExerciseLibrary.isAvailable(e, p.equipment))
            }
        }
    }

    @Test
    fun `jedes Workout hat ein bis zwei Core-Uebungen, mit Sixpack-Ziel immer zwei`() {
        for (p in allProfiles()) {
            for (w in PlanGenerator.generate(p)) {
                val core = w.exercises.count { ExerciseLibrary.require(it.exerciseId).slot in coreSlots }
                val expected = if (Goal.SIXPACK in p.goals) 2 else 1
                assertEquals("${w.name} bei ${p.goals}", expected, core)
            }
        }
    }

    @Test
    fun `Sixpack-Ziel bringt mehr Core-Volumen`() {
        fun coreSets(p: UserProfile) = PlanGenerator.generate(p).sumOf { w ->
            w.exercises.filter { ExerciseLibrary.require(it.exerciseId).slot in coreSlots }.sumOf { it.sets }
        }
        assertTrue(coreSets(leon) > coreSets(leon.copy(goals = setOf(Goal.OBERKOERPER))))
    }

    @Test
    fun `Zielmuskeln bekommen mehr Saetze`() {
        fun setsFor(p: UserProfile, muscles: Set<Muscle>) = PlanGenerator.generate(p).sumOf { w ->
            w.exercises.filter { pe -> ExerciseLibrary.require(pe.exerciseId).primary.any { it in muscles } }.sumOf { it.sets }
        }
        val upper = Goal.OBERKOERPER.muscles
        assertTrue(setsFor(leon, upper) > setsFor(leon.copy(goals = setOf(Goal.BEINE_PO)), upper))
        val legs = Goal.BEINE_PO.muscles
        assertTrue(setsFor(leon.copy(goals = setOf(Goal.BEINE_PO)), legs) > setsFor(leon, legs))
    }

    @Test
    fun `keine Uebung doppelt in einem Workout und sinnvolle Groesse`() {
        for (p in allProfiles()) {
            for (w in PlanGenerator.generate(p)) {
                val ids = w.exercises.map { it.exerciseId }
                assertEquals("${w.name}: $ids", ids.size, ids.toSet().size)
                assertTrue("${w.name} hat ${ids.size} Übungen (${p.equipment})", ids.size in 4..7)
                assertTrue(w.exercises.all { it.sets in 2..4 && it.repMin <= it.repMax && it.restSec >= 30 })
            }
        }
    }

    @Test
    fun `Startplan - 12 kg wird nur dort eingesetzt, wo es fuer Anfaenger passt`() {
        val plan = PlanGenerator.generate(leon)
        val ids = plan.flatMap { it.exercises }.map { it.exerciseId }.toSet()
        assertFalse("Seitheben mit 12 kg ist zu schwer", "seitheben" in ids)
        assertFalse("trizeps_kickbacks" in ids)
        assertTrue("bizeps_curls" in ids)
        assertTrue("liegestuetze" in ids)
        assertTrue("plank" in ids)
        for (pe in plan.flatMap { it.exercises }) {
            val e = ExerciseLibrary.require(pe.exerciseId)
            if (e.load == LoadType.DUMBBELL) assertEquals(12.0, pe.weightKg, 0.0) else assertEquals(0.0, pe.weightKg, 0.0)
        }
        // Curls mit 12 kg: Einstieg mit weniger Wiederholungen (5–10)
        val curls = plan.flatMap { it.exercises }.first { it.exerciseId == "bizeps_curls" }
        assertEquals(5, curls.repMin)
        assertEquals(10, curls.repMax)
        // Plank-Ziel aus dem Einstufungstest: 70 % von 60 s ≈ 40 s
        val plank = plan.flatMap { it.exercises }.first { it.exerciseId == "plank" }
        assertEquals(40, plank.repMin)
    }

    @Test
    fun `Einstufung waehlt die Liegestuetz-Variante`() {
        fun pushVariant(max: Int) = PlanGenerator.generate(leon.copy(maxPushups = max)).first().exercises.first().exerciseId
        assertEquals("knie_liegestuetze", pushVariant(3))
        assertEquals("liegestuetze", pushVariant(10))
        assertEquals("liegestuetze_erhoeht", pushVariant(30))
    }

    @Test
    fun `leichtere Hantel wird fuer kleine Muskeln gewaehlt`() {
        val p = leon.copy(dumbbellKg = listOf(4.0, 12.0))
        val plan = PlanGenerator.generate(p)
        val lateral = plan.flatMap { it.exercises }.firstOrNull { it.exerciseId == "seitheben" }
        assertEquals(4.0, lateral!!.weightKg, 0.0)
    }

    @Test
    fun `Rotation bei 3 Tagen - A B A und in der Folgewoche B A B`() {
        val monday = LocalDate.of(2026, 1, 5)
        val days = PlanGenerator.defaultWeekdays(3)
        fun idx(offset: Long) = PlanGenerator.workoutIndexFor(monday.plusDays(offset), monday, days, 2)
        assertEquals(listOf(0, null, 1, null, 0, null, null), (0L..6L).map(::idx))
        assertEquals(listOf(1, null, 0, null, 1, null, null), (7L..13L).map(::idx))
    }

    @Test
    fun `Rotation bei 4 und 5 Tagen bleibt jede Woche gleich`() {
        val monday = LocalDate.of(2026, 1, 5)
        val d4 = PlanGenerator.defaultWeekdays(4)
        assertEquals(listOf(0, 1, null, 2, 3, null, null), (0L..6L).map { PlanGenerator.workoutIndexFor(monday.plusDays(it), monday, d4, 4) })
        assertEquals(listOf(0, 1, null, 2, 3, null, null), (7L..13L).map { PlanGenerator.workoutIndexFor(monday.plusDays(it), monday, d4, 4) })
        val d5 = PlanGenerator.defaultWeekdays(5)
        assertEquals(5, (0L..6L).mapNotNull { PlanGenerator.workoutIndexFor(monday.plusDays(it), monday, d5, 5) }.toSet().size)
    }

    @Test
    fun `Planstart mitten in der Woche und Tage davor`() {
        val wednesday = LocalDate.of(2026, 1, 7)
        val days = listOf(1, 3, 5)
        assertEquals(1, PlanGenerator.workoutIndexFor(wednesday, wednesday, days, 2))
        assertNull(PlanGenerator.workoutIndexFor(wednesday.plusDays(1), wednesday, days, 2))
        // auch vor dem Planstart liefert die Funktion gültige Werte
        assertEquals(1, PlanGenerator.workoutIndexFor(wednesday.minusDays(9), wednesday, days, 2))
    }

    @Test
    fun `Alternativen haben gleiche Hauptmuskelgruppe und passende Ausruestung`() {
        val alts = PlanGenerator.alternativesFor("bizeps_curls", setOf(Equipment.KURZHANTEL))
        assertTrue(alts.isNotEmpty())
        assertTrue(alts.all { Muscle.BIZEPS in it.primary })
        assertTrue(alts.none { it.id == "bizeps_curls" || it.id == "band_curls" })
        assertEquals("hammer_curls", alts.first().id)
        val bodyweight = PlanGenerator.alternativesFor("liegestuetze", emptySet(), exclude = setOf("knie_liegestuetze"))
        assertTrue(bodyweight.none { it.equipment.isNotEmpty() || it.id == "knie_liegestuetze" })
    }

    @Test
    fun `Dauer wird plausibel geschaetzt`() {
        for (w in PlanGenerator.generate(leon)) {
            val min = PlanGenerator.estimateMinutes(w.exercises)
            assertTrue("${w.name}: $min min", min in 25..70)
        }
    }
}
