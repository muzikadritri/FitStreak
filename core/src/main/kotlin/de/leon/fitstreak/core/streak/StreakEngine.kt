package de.leon.fitstreak.core.streak

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class DayKind { TRAINED, REST, MISSED, SHIELDED }

data class StreakState(
    val current: Int = 0,
    val best: Int = 0,
    val shields: Int = 0,
    /** Letzter festgeschriebener Tag. */
    val lastEvaluated: LocalDate? = null,
)

data class DayResult(val date: LocalDate, val kind: DayKind)

data class StreakUpdate(
    val state: StreakState,
    /** Neu festgeschriebene Tage (in Reihenfolge). */
    val days: List<DayResult>,
    /** Tage, an denen automatisch ein Streak-Schutz eingesetzt wurde. */
    val shieldsUsed: List<LocalDate>,
    val shieldsEarned: Int,
    /** Die Streak wurde in diesem Schritt gebrochen (war vorher > 0). */
    val broken: Boolean,
    /** Heute ist Trainingstag und noch nicht trainiert. */
    val todayPending: Boolean,
    /** Anzeige-Wert: zählt einen laufenden Ruhetag heute schon mit. */
    val display: Int,
)

/**
 * Streak-Logik als reine Funktion.
 *
 * - Ein Tag zählt, wenn das geplante Training erledigt ist ODER es ein Ruhetag ist.
 * - Ruhetage zählen nur weiter, wenn die Streak schon läuft (sie starten keine Streak).
 * - Verpasster Trainingstag: Streak-Schutz wird automatisch verbraucht, sonst zurück auf 0.
 * - Streak-Schutz: +1 je [SHIELD_EVERY] Tage Streak, höchstens [MAX_SHIELDS].
 * - Vergangene Tage werden festgeschrieben; "heute" erst, wenn trainiert wurde.
 */
object StreakEngine {
    const val MAX_SHIELDS = 2
    const val SHIELD_EVERY = 7

    /** Lokales Datum eines Zeitpunkts – ein Training zählt für den Tag, an dem es begonnen wurde. */
    fun localDate(epochMillis: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()

    fun advance(
        state: StreakState,
        today: LocalDate,
        /** Erster auszuwertender Tag, falls noch nie ausgewertet wurde (Planstart). */
        start: LocalDate,
        isTrainingDay: (LocalDate) -> Boolean,
        trainedOn: (LocalDate) -> Boolean,
    ): StreakUpdate {
        var s = state
        val days = mutableListOf<DayResult>()
        val shieldsUsed = mutableListOf<LocalDate>()
        var earned = 0
        var broken = false

        fun count(day: LocalDate, kind: DayKind) {
            val current = s.current + 1
            var shields = s.shields
            if (current % SHIELD_EVERY == 0 && shields < MAX_SHIELDS) {
                shields++
                earned++
            }
            s = s.copy(current = current, best = maxOf(s.best, current), shields = shields, lastEvaluated = day)
            days += DayResult(day, kind)
        }

        var day = s.lastEvaluated?.plusDays(1) ?: start
        // Vergangene Tage festschreiben
        while (day.isBefore(today)) {
            when {
                trainedOn(day) -> count(day, DayKind.TRAINED)
                !isTrainingDay(day) -> {
                    if (s.current > 0) count(day, DayKind.REST) else {
                        s = s.copy(lastEvaluated = day)
                        days += DayResult(day, DayKind.REST)
                    }
                }
                s.current > 0 && s.shields > 0 -> {
                    s = s.copy(shields = s.shields - 1, lastEvaluated = day)
                    shieldsUsed += day
                    days += DayResult(day, DayKind.SHIELDED)
                }
                else -> {
                    if (s.current > 0) broken = true
                    s = s.copy(current = 0, lastEvaluated = day)
                    days += DayResult(day, DayKind.MISSED)
                }
            }
            day = day.plusDays(1)
        }

        // Heute: nur festschreiben, wenn trainiert wurde
        var pending = false
        var display = s.current
        if (day == today) {
            when {
                trainedOn(today) -> {
                    count(today, DayKind.TRAINED)
                    display = s.current
                }
                isTrainingDay(today) -> pending = true
                else -> if (s.current > 0) display = s.current + 1
            }
        }
        return StreakUpdate(s, days, shieldsUsed, earned, broken, pending, display)
    }
}
