package de.leon.fitstreak

import de.leon.fitstreak.core.model.Rating
import de.leon.fitstreak.data.Backup
import de.leon.fitstreak.data.DayState
import de.leon.fitstreak.data.Habit
import de.leon.fitstreak.data.ReminderTexts
import de.leon.fitstreak.testing.World
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Durchstich durch Repository + Kernlogik mit In-Memory-Datenbank und verstellbarer Uhr. */
class RepositoryTest {
    // Montag
    private val monday: LocalDate = LocalDate.of(2026, 3, 2)

    @Test
    fun `Onboarding erzeugt den Startplan`() = runBlocking {
        val w = World(monday)
        w.onboard()
        val data = w.repo.snapshot()
        assertTrue(data.settings.onboardingDone)
        assertEquals(listOf("Oberkörper A", "Unterkörper A", "Oberkörper B", "Unterkörper B"), data.rotation.map { it.template.name })
        assertEquals("Oberkörper A", data.workoutFor(monday)?.template?.name)
        assertNull("Mittwoch ist Ruhetag", data.workoutFor(monday.plusDays(2)))
        assertEquals(1, data.tests.size)
    }

    @Test
    fun `Training abschliessen - Streak, XP, Erfolg und Vorgabe fuers naechste Mal`() = runBlocking {
        val w = World(monday)
        w.onboard()
        val workout = w.repo.snapshot().workoutFor(monday)!!
        val id = w.repo.startOrResumeSession(workout.id)
        var sets = 0
        for (row in workout.exercises) {
            repeat(row.sets) { i -> w.repo.logSet(id, row.exerciseId, i, row.repMax, row.weightKg); sets++ }
            w.repo.rateExercise(id, row.exerciseId, Rating.PASST)
        }
        val summary = w.repo.finishSession(id)!!
        assertEquals(sets, summary.setCount)
        assertEquals(0, summary.streakBefore)
        assertEquals(1, summary.streakAfter)
        assertEquals(sets * 10 + 50, summary.xpEarned)
        assertEquals(listOf("first_workout"), summary.newAchievements.map { it.id })
        assertTrue(summary.records.isEmpty()) // erstes Mal ist kein Rekord

        val data = w.repo.snapshot()
        assertEquals(summary.xpEarned, data.settings.xp)
        assertEquals(1, data.settings.streakCurrent)
        assertTrue(data.trainedOn(monday))
        assertEquals(DayState.TRAINED, data.dayState(monday, monday))
        // Liegestütze 4×12 geschafft → nächste Stufe der Leiter
        val first = data.rotation.first().exercises.first()
        assertEquals("liegestuetze_erhoeht", first.exerciseId)
        // Curls mit 12 kg (einzige Hantel) am oberen Ende → langsameres Tempo
        val curls = data.rotation.first().exercises.first { it.exerciseId == "bizeps_curls" }
        assertEquals(1, curls.tempo)
        assertEquals(12.0, curls.weightKg, 0.0)
    }

    @Test
    fun `Rekord beim zweiten Mal`() = runBlocking {
        val w = World(monday)
        w.onboard()
        w.trainToday()
        w.advanceDays(7)
        w.repo.refreshStreak()
        val workout = w.repo.snapshot().workoutFor(w.repo.today())!!
        val id = w.repo.startOrResumeSession(workout.id)
        val row = workout.exercises.first { it.exerciseId == "einarmiges_rudern" }
        repeat(row.sets) { w.repo.logSet(id, row.exerciseId, it, row.repMax, row.weightKg) }
        val summary = w.repo.finishSession(id)!!
        assertEquals(listOf("Einarmiges Rudern"), summary.records.map { it.exerciseName })
        assertTrue("first_record" in summary.newAchievements.map { it.id })
    }

    @Test
    fun `Streak ueber eine Woche mit Ruhetagen und automatischem Schutz`() = runBlocking {
        val w = World(monday)
        w.onboard()
        // Tag 7 (Montag der 2. Woche) wird ausgelassen
        w.simulate(9, skip = setOf(7))
        val data = w.repo.snapshot()
        // Woche 1: 7 Tage → 1 Schutz verdient; Mo verpasst → Schutz verbraucht; Di trainiert → 8
        assertEquals(8, data.settings.streakCurrent)
        assertEquals(0, data.settings.shields)
        assertEquals(listOf(monday.plusDays(7).toEpochDay()), data.settings.shieldNoticeDays)
        assertEquals(DayState.SHIELDED, data.dayState(monday.plusDays(7), w.repo.today()))
        assertEquals(DayState.REST, data.dayState(monday.plusDays(2), w.repo.today()))
    }

    @Test
    fun `nicht beendetes Training vom Vortag wird nachtraeglich gewertet`() = runBlocking {
        val w = World(monday)
        w.onboard()
        val workout = w.repo.snapshot().workoutFor(monday)!!
        val id = w.repo.startOrResumeSession(workout.id)
        w.repo.logSet(id, workout.exercises.first().exerciseId, 0, 8, 0.0)
        // App wird geschlossen, am nächsten Tag wieder geöffnet
        w.advanceDays(1)
        w.repo.refreshStreak()
        val data = w.repo.snapshot()
        assertTrue(data.trainedOn(monday))
        assertEquals(1, data.settings.streakCurrent)
        assertTrue(data.sessions.all { it.finishedAt != null })
    }

    @Test
    fun `leeres Training wird verworfen`() = runBlocking {
        val w = World(monday)
        w.onboard()
        val id = w.repo.startOrResumeSession(w.repo.snapshot().rotation.first().id)
        assertNull(w.repo.finishSession(id))
        assertTrue(w.repo.snapshot().sessions.isEmpty())
    }

    @Test
    fun `Gewohnheiten geben und nehmen XP`() = runBlocking {
        val w = World(monday)
        w.onboard()
        w.repo.toggleHabit(Habit.PROTEIN)
        w.repo.toggleHabit(Habit.SLEEP)
        assertEquals(10, w.repo.snapshot().settings.xp)
        w.repo.toggleHabit(Habit.SLEEP)
        assertEquals(5, w.repo.snapshot().settings.xp)
        w.repo.setWater(8)
        assertEquals(10, w.repo.snapshot().settings.xp)
        assertTrue(w.repo.snapshot().habit(monday).protein)
    }

    @Test
    fun `Backup - Export und Import stellen alles wieder her`() = runBlocking {
        val w = World(monday)
        w.onboard()
        w.simulate(5)
        w.repo.toggleHabit(Habit.NO_SUGAR)
        w.repo.addMeasurement(33.5, null, 80.0)
        val json = w.repo.exportBackup()
        val before = w.repo.snapshot()

        val other = World(monday.plusDays(5))
        other.repo.importBackup(json)
        val after = other.repo.snapshot()
        assertEquals(before.settings, after.settings)
        assertEquals(before.templates, after.templates)
        assertEquals(before.finishedSessions, after.sessions)
        assertEquals(before.finishedLogs, after.setLogs)
        assertEquals(before.dayRecords, after.dayRecords)
        assertEquals(before.habits, after.habits)
        assertEquals(before.measurements, after.measurements)
        assertEquals(before.achievements, after.achievements)
    }

    @Test
    fun `ungueltiges Backup wird abgelehnt`() {
        for (bad in listOf("", "kein json", "{\"app\":\"Andere\"}", "[1,2,3]")) {
            try {
                Backup.decode(bad)
                throw AssertionError("hätte fehlschlagen müssen: $bad")
            } catch (_: IllegalArgumentException) {
            }
        }
    }

    @Test
    fun `Erinnerungstexte passen zum Tag`() = runBlocking {
        val w = World(monday)
        w.onboard()
        // Trainingstag ohne Streak
        var data = w.repo.snapshot()
        val first = ReminderTexts.daily(data, monday)!!
        assertEquals("Heute: Oberkörper A", first.title)
        assertTrue(first.withSnooze)
        assertNull("ohne Streak kein Streak-Retter", ReminderTexts.saver(data, monday))
        // nach dem Training: keine Erinnerung mehr
        w.trainToday()
        data = w.repo.snapshot()
        assertNull(ReminderTexts.daily(data, monday))
        // Dienstag: Streak läuft, noch nicht trainiert
        w.advanceDays(1)
        w.repo.refreshStreak()
        data = w.repo.snapshot()
        val tuesday = w.repo.today()
        assertTrue(ReminderTexts.daily(data, tuesday)!!.text.contains("1-Tage-Streak"))
        assertNotNull(ReminderTexts.saver(data, tuesday))
        // Mittwoch: Ruhetag mit Tipp, kein Streak-Retter
        w.trainToday()
        w.advanceDays(1)
        w.repo.refreshStreak()
        data = w.repo.snapshot()
        val rest = ReminderTexts.daily(data, w.repo.today())!!
        assertEquals("Heute ist Ruhetag", rest.title)
        assertFalse(rest.withSnooze)
        assertNull(ReminderTexts.saver(data, w.repo.today()))
    }

    @Test
    fun `Plan bearbeiten - tauschen, hinzufuegen, eigenes Workout`() = runBlocking {
        val w = World(monday)
        w.onboard()
        var t = w.repo.snapshot().rotation.first()
        val curls = t.exercises.first { it.exerciseId == "bizeps_curls" }
        w.repo.swapExercise(curls.id, "konzentrations_curls")
        t = w.repo.snapshot().rotation.first()
        assertTrue(t.exercises.any { it.exerciseId == "konzentrations_curls" })
        assertFalse(t.exercises.any { it.exerciseId == "bizeps_curls" })

        val custom = w.repo.createCustomWorkout("Arme extra")
        w.repo.addExercise(custom, "hammer_curls")
        w.repo.addExercise(custom, "dips_stuhl")
        val c = w.repo.snapshot().template(custom)!!
        assertEquals(listOf("hammer_curls", "dips_stuhl"), c.exercises.map { it.exerciseId })
        assertFalse(c.template.inRotation)
        w.repo.moveExercise(c.exercises[1].id, -1)
        assertEquals(listOf("dips_stuhl", "hammer_curls"), w.repo.snapshot().template(custom)!!.exercises.map { it.exerciseId })

        // Plan neu erstellen lässt eigene Workouts in Ruhe
        w.repo.regeneratePlan()
        val data = w.repo.snapshot()
        assertEquals(4, data.rotation.size)
        assertEquals(1, data.customWorkouts.size)
        assertEquals(2, data.customWorkouts.first().exercises.size)
    }
}
