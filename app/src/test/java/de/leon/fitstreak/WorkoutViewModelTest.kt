package de.leon.fitstreak

import de.leon.fitstreak.core.model.Rating
import de.leon.fitstreak.testing.World
import de.leon.fitstreak.ui.workout.WorkoutPhase
import de.leon.fitstreak.ui.workout.WorkoutViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutViewModelTest {
    private val monday: LocalDate = LocalDate.of(2026, 3, 2)

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun start(world: World): WorkoutViewModel = runBlocking {
        world.onboard()
        WorkoutViewModel(world.repo, world.repo.snapshot().workoutFor(monday)!!.id)
    }

    @Test
    fun `Ablauf - Aufwaermen, Saetze, Pause, Bewertung, Abschluss`() {
        val world = World(monday)
        val vm = start(world)
        assertEquals(WorkoutPhase.WARMUP, vm.ui.value.phase)
        vm.finishWarmup()
        var ui = vm.ui.value
        assertEquals(WorkoutPhase.TRAINING, ui.phase)
        assertEquals("Oberkörper A", ui.title)
        assertEquals(0, ui.expanded)
        assertEquals(0, ui.doneSets)

        // Vorgabe: Liegestütze 4 × 6–12 → vorausgefüllt mit dem unteren Ende
        val first = ui.exercises.first()
        assertEquals("liegestuetze", first.exercise.id)
        assertEquals(4, first.sets.size)
        assertEquals(6, first.nextSet!!.reps)
        assertNull(first.lastText)

        // Wiederholungen anpassen und Satz abhaken → Pause startet
        vm.adjustReps(0, +2)
        assertEquals(8, vm.ui.value.exercises.first().nextSet!!.reps)
        vm.completeSet(0, 0)
        ui = vm.ui.value
        assertEquals(1, ui.doneSets)
        assertEquals(8, ui.exercises.first().sets.first().reps)
        assertTrue(ui.exercises.first().sets.first().done)
        assertNotNull(ui.rest)
        assertEquals(75, ui.rest!!.totalSec)
        vm.extendRest(15)
        assertEquals(90, vm.ui.value.rest!!.totalSec)
        vm.skipRest()
        assertNull(vm.ui.value.rest)

        // Satz zurücknehmen
        vm.undoSet(0, 0)
        assertEquals(0, vm.ui.value.doneSets)

        // erste Übung komplett → Bewertung → nächste Übung klappt auf
        repeat(4) { vm.completeSet(0, it) }
        assertTrue(vm.ui.value.exercises.first().complete)
        vm.rate(0, Rating.PASST)
        ui = vm.ui.value
        assertEquals(Rating.PASST, ui.exercises.first().rating)
        assertEquals(1, ui.expanded)

        // Gewicht anpassen bei der Hantel-Übung
        val rowing = ui.exercises[1]
        assertEquals(12.0, rowing.nextSet!!.weightKg, 0.0)
        vm.adjustWeight(1, up = true)
        assertEquals(13.0, vm.ui.value.exercises[1].nextSet!!.weightKg, 0.0)
        vm.adjustWeight(1, up = false)

        // Rest abhaken; nach dem allerletzten Satz gibt es keine Pause mehr
        vm.ui.value.exercises.forEachIndexed { i, ex -> ex.sets.forEach { s -> if (!s.done) vm.completeSet(i, s.index) } }
        ui = vm.ui.value
        assertTrue(ui.allDone)
        assertNull(ui.rest)

        vm.finish()
        ui = vm.ui.value
        assertEquals(WorkoutPhase.SUMMARY, ui.phase)
        assertEquals(ui.summary!!.setCount, runBlocking { world.repo.snapshot() }.finishedLogs.size)
        assertEquals(1, ui.summary!!.streakAfter)
    }

    @Test
    fun `beim naechsten Mal ist das letzte Training vorausgefuellt`() {
        val world = World(monday)
        val vm = start(world)
        vm.finishWarmup()
        // Rudern: 3 Sätze mit 10 Wdh. (im Zielbereich 8–12, Vorgabe bleibt)
        repeat(2) { vm.adjustReps(1, +1) }
        repeat(3) { vm.completeSet(1, it) }
        vm.finish()

        world.advanceDays(7)
        val again = runBlocking {
            world.repo.refreshStreak()
            WorkoutViewModel(world.repo, world.repo.snapshot().workoutFor(world.repo.today())!!.id)
        }
        again.finishWarmup()
        val rowing = again.ui.value.exercises.first { it.exercise.id == "einarmiges_rudern" }
        assertEquals("3×10 mit 12 kg", rowing.lastText)
        assertEquals(10, rowing.nextSet!!.reps)
        assertEquals(12.0, rowing.nextSet!!.weightKg, 0.0)
    }

    @Test
    fun `Training fortsetzen nach Verlassen`() {
        val world = World(monday)
        val vm = start(world)
        vm.finishWarmup()
        vm.completeSet(0, 0)
        vm.leave()
        assertEquals(WorkoutPhase.GONE, vm.ui.value.phase)

        val resumed = runBlocking { WorkoutViewModel(world.repo, world.repo.snapshot().workoutFor(monday)!!.id) }
        val ui = resumed.ui.value
        assertEquals(WorkoutPhase.TRAINING, ui.phase)
        assertEquals(1, ui.doneSets)
        assertEquals(1, runBlocking { world.repo.snapshot() }.sessions.size)
    }

    @Test
    fun `Verwerfen loescht das Training`() {
        val world = World(monday)
        val vm = start(world)
        vm.finishWarmup()
        vm.completeSet(0, 0)
        vm.discard()
        assertEquals(WorkoutPhase.GONE, vm.ui.value.phase)
        val data = runBlocking { world.repo.snapshot() }
        assertTrue(data.sessions.isEmpty())
        assertTrue(data.setLogs.isEmpty())
        assertFalse(data.trainedOn(monday))
    }

    @Test
    fun `Deload-Woche - ein Satz weniger, Vorgaben bleiben`() {
        val world = World(monday)
        runBlocking {
            world.onboard()
            world.repo.startDeload()
        }
        val vm = runBlocking { WorkoutViewModel(world.repo, world.repo.snapshot().workoutFor(monday)!!.id) }
        vm.finishWarmup()
        var ui = vm.ui.value
        assertTrue(ui.deload)
        assertEquals(3, ui.exercises.first().targetSets) // statt 4
        val before = runBlocking { world.repo.snapshot() }.rotation.first().exercises
        ui.exercises.forEachIndexed { i, ex -> ex.sets.forEach { s -> vm.adjustReps(i, +20); vm.completeSet(i, s.index) } }
        vm.finish()
        ui = vm.ui.value
        assertEquals(WorkoutPhase.SUMMARY, ui.phase)
        assertTrue(ui.summary!!.suggestions.isEmpty())
        assertEquals(before, runBlocking { world.repo.snapshot() }.rotation.first().exercises)
    }
}
