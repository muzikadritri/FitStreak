package de.leon.fitstreak

import de.leon.fitstreak.data.ReminderTimes
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class ReminderSchedulerTest {
    private val berlin = ZoneId.of("Europe/Berlin")
    private fun at(day: Int, h: Int, m: Int) = ZonedDateTime.of(2026, 1, day, h, m, 0, 0, berlin)

    @Test
    fun `vor der Erinnerungszeit - heute`() {
        assertEquals(at(5, 18, 0), ReminderTimes.nextTrigger(18 * 60, at(5, 9, 30)))
    }

    @Test
    fun `nach der Erinnerungszeit - morgen`() {
        assertEquals(at(6, 18, 0), ReminderTimes.nextTrigger(18 * 60, at(5, 18, 0)))
        assertEquals(at(6, 18, 0), ReminderTimes.nextTrigger(18 * 60, at(5, 21, 15)))
    }

    @Test
    fun `direkt nach dem Ausloesen wird fuer den naechsten Tag geplant`() {
        // der Alarm feuert um 18:00:05 → nächster Termin ist morgen, nicht sofort wieder
        assertEquals(at(6, 18, 0), ReminderTimes.nextTrigger(18 * 60, at(5, 18, 0).plusSeconds(5)))
    }

    @Test
    fun `Sommerzeit-Umstellung behaelt die lokale Uhrzeit`() {
        val before = ZonedDateTime.of(2026, 3, 28, 19, 0, 0, 0, berlin) // Nacht darauf: Umstellung
        val next = ReminderTimes.nextTrigger(18 * 60, before)
        assertEquals(18, next.hour)
        assertEquals(29, next.dayOfMonth)
    }
}
