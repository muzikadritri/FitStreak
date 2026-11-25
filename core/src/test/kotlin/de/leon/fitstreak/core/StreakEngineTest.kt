package de.leon.fitstreak.core

import de.leon.fitstreak.core.streak.DayKind
import de.leon.fitstreak.core.streak.StreakEngine
import de.leon.fitstreak.core.streak.StreakState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class StreakEngineTest {
    // Montag
    private val start: LocalDate = LocalDate.of(2026, 1, 5)

    // Mo, Di, Do, Fr
    private val trainingDays = setOf(1, 2, 4, 5)
    private fun isTraining(d: LocalDate) = d.dayOfWeek.value in trainingDays

    private fun run(state: StreakState, today: LocalDate, trained: Set<LocalDate>) =
        StreakEngine.advance(state, today, start, ::isTraining) { it in trained }

    @Test
    fun `erstes Training startet die Streak`() {
        val u = run(StreakState(), start, setOf(start))
        assertEquals(1, u.state.current)
        assertEquals(1, u.display)
        assertFalse(u.todayPending)
        assertEquals(DayKind.TRAINED, u.days.single().kind)
    }

    @Test
    fun `Trainingstag ohne Training ist heute nur offen und bricht noch nichts`() {
        val s = run(StreakState(), start, setOf(start)).state
        val u = run(s, start.plusDays(1), setOf(start))
        assertEquals(1, u.state.current)
        assertTrue(u.todayPending)
        assertFalse(u.broken)
    }

    @Test
    fun `Ruhetage brechen die Streak nicht und zaehlen mit`() {
        // Mo + Di trainiert, Mi Ruhetag, Do trainiert
        val trained = setOf(start, start.plusDays(1), start.plusDays(3))
        val u = run(StreakState(), start.plusDays(3), trained)
        assertEquals(4, u.state.current)
        assertEquals(listOf(DayKind.TRAINED, DayKind.TRAINED, DayKind.REST, DayKind.TRAINED), u.days.map { it.kind })
    }

    @Test
    fun `laufender Ruhetag zaehlt in der Anzeige schon mit`() {
        val trained = setOf(start, start.plusDays(1))
        val u = run(StreakState(), start.plusDays(2), trained) // Mittwoch = Ruhetag
        assertEquals(2, u.state.current)
        assertEquals(3, u.display)
        assertFalse(u.todayPending)
    }

    @Test
    fun `Ruhetage allein starten keine Streak`() {
        // Start an einem Mittwoch (Ruhetag), nie trainiert
        val wed = start.plusDays(2)
        val u = StreakEngine.advance(StreakState(), wed.plusDays(1), wed, ::isTraining) { false }
        assertEquals(0, u.state.current)
        assertEquals(0, u.display)
    }

    @Test
    fun `verpasster Trainingstag ohne Schutz setzt auf null`() {
        val trained = setOf(start)
        val u = run(StreakState(), start.plusDays(2), trained) // Di verpasst
        assertEquals(0, u.state.current)
        assertEquals(1, u.state.best)
        assertTrue(u.broken)
        assertEquals(DayKind.MISSED, u.days.last().kind)
    }

    @Test
    fun `nach sieben Tagen gibt es einen Streak-Schutz und er wird automatisch eingesetzt`() {
        // Eine volle Woche: Mo, Di, (Mi), Do, Fr, (Sa), (So) = 7 Tage
        val week1 = setOf(0L, 1L, 3L, 4L).map { start.plusDays(it) }.toSet()
        val afterWeek = run(StreakState(), start.plusDays(7), week1) // Montag der 2. Woche, noch offen
        assertEquals(7, afterWeek.state.current)
        assertEquals(1, afterWeek.state.shields)
        assertEquals(1, afterWeek.shieldsEarned)

        // Montag der 2. Woche verpasst → Schutz greift, Streak bleibt
        val u = run(afterWeek.state, start.plusDays(8), week1)
        assertEquals(7, u.state.current)
        assertEquals(0, u.state.shields)
        assertEquals(listOf(start.plusDays(7)), u.shieldsUsed)
        assertFalse(u.broken)
        assertEquals(DayKind.SHIELDED, u.days.single().kind)

        // Dienstag auch verpasst → kein Schutz mehr → gebrochen
        val u2 = run(u.state, start.plusDays(9), week1)
        assertEquals(0, u2.state.current)
        assertTrue(u2.broken)
        assertEquals(7, u2.state.best)
    }

    @Test
    fun `hoechstens zwei Streak-Schutz`() {
        var s = StreakState()
        val trained = (0L until 40L).map { start.plusDays(it) }.filter(::isTraining).toSet()
        s = run(s, start.plusDays(39), trained).state
        assertEquals(40, s.current)
        assertEquals(StreakEngine.MAX_SHIELDS, s.shields)
    }

    @Test
    fun `zusaetzliches Training am Ruhetag zaehlt nur einmal`() {
        val wed = start.plusDays(2)
        val trained = setOf(start, start.plusDays(1), wed)
        val u = run(StreakState(), wed, trained)
        assertEquals(3, u.state.current)
        assertEquals(3, u.display)
        // erneutes Auswerten am selben Tag ändert nichts
        val again = run(u.state, wed, trained)
        assertEquals(3, again.state.current)
        assertTrue(again.days.isEmpty())
    }

    @Test
    fun `Mitternacht - Training um 23_59 zaehlt fuer den Starttag`() {
        val zone = ZoneId.of("Europe/Berlin")
        val startMillis = ZonedDateTime.of(2026, 1, 5, 23, 59, 0, 0, zone).toInstant().toEpochMilli()
        val day = StreakEngine.localDate(startMillis, zone)
        assertEquals(start, day)
        // Auswertung um 00:01 am Folgetag (Dienstag, Trainingstag, noch offen)
        val nextDay = StreakEngine.localDate(startMillis + 2 * 60_000, zone)
        assertEquals(start.plusDays(1), nextDay)
        val u = run(StreakState(), nextDay, setOf(day))
        assertEquals(1, u.state.current)
        assertTrue(u.todayPending)
        assertFalse(u.broken)
    }

    @Test
    fun `Zeitzone bestimmt das lokale Datum`() {
        // 22:30 UTC am 5.1. ist in Berlin 23:30 am 5.1., in Tokio schon der 6.1.
        val millis = ZonedDateTime.of(2026, 1, 5, 22, 30, 0, 0, ZoneId.of("UTC")).toInstant().toEpochMilli()
        assertEquals(LocalDate.of(2026, 1, 5), StreakEngine.localDate(millis, ZoneId.of("Europe/Berlin")))
        assertEquals(LocalDate.of(2026, 1, 6), StreakEngine.localDate(millis, ZoneId.of("Asia/Tokyo")))
    }

    @Test
    fun `Zeitzonenwechsel zurueck in die Vergangenheit aendert nichts`() {
        val trained = setOf(start, start.plusDays(1))
        val s = run(StreakState(), start.plusDays(1), trained).state
        assertEquals(2, s.current)
        // Nach einem Flug nach Westen ist "heute" wieder der Vortag
        val u = run(s, start, trained)
        assertEquals(s, u.state)
        assertTrue(u.days.isEmpty())
        assertEquals(2, u.display)
        assertFalse(u.todayPending)
    }

    @Test
    fun `lange Pause wird Tag fuer Tag ausgewertet`() {
        val trained = setOf(start, start.plusDays(1))
        val u = run(StreakState(), start.plusDays(30), trained)
        assertEquals(0, u.state.current)
        assertEquals(3, u.state.best) // Mo, Di, Mi (Ruhetag)
        assertEquals(start.plusDays(29), u.state.lastEvaluated)
    }
}
