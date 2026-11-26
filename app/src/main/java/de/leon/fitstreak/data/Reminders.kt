package de.leon.fitstreak.data

import de.leon.fitstreak.core.plan.PlanGenerator
import java.time.LocalDate
import java.time.ZonedDateTime

/** Erholungs-Tipps für Ruhetage (Heute-Screen und Erinnerung). */
val recoveryTips = listOf(
    "Muskeln wachsen in der Pause, nicht im Training. Genieß den Ruhetag.",
    "Heute zählt Schlaf: 8 Stunden sind dein bester Muskel-Booster.",
    "Iss zu jeder Mahlzeit etwas Eiweiß – Eier, Quark, Hähnchen, Linsen.",
    "Ein Spaziergang lockert die Muskeln und beschleunigt die Erholung.",
    "Trink genug Wasser – auch an Ruhetagen arbeiten deine Muskeln am Aufbau.",
    "Muskelkater? Leichte Bewegung und Wärme helfen besser als Stillsitzen.",
    "Geduld zahlt sich aus: Sichtbare Muskeln brauchen Monate, nicht Tage.",
)

/** Text einer Erinnerung. */
data class ReminderMessage(val title: String, val text: String, val withSnooze: Boolean)

/** Texte der Benachrichtigungen – reine Funktionen, unabhängig von Android. */
object ReminderTexts {
    /** Tägliche Erinnerung. null = nichts senden (z. B. schon trainiert). */
    fun daily(data: AppData, today: LocalDate): ReminderMessage? {
        if (!data.settings.onboardingDone) return null
        val streak = data.streak(today)
        val workout = data.workoutFor(today)
        if (workout == null) {
            val tip = recoveryTips[today.dayOfYear % recoveryTips.size]
            val tail = if (streak.display > 0) " Deine ${streak.display}-Tage-Streak läuft weiter." else ""
            return ReminderMessage("Heute ist Ruhetag", tip + tail, withSnooze = false)
        }
        if (data.trainedOn(today)) return null
        val minutes = PlanGenerator.estimateMinutes(workout.exercises.map { it.toPlan() })
        val text = if (streak.state.current > 0) "Halte deine ${streak.state.current}-Tage-Streak! Ca. $minutes Minuten."
        else "Starte heute deine Streak – ca. $minutes Minuten."
        return ReminderMessage("Heute: ${workout.template.name}", text, withSnooze = true)
    }

    /** Streak-Retter: nur wenn Trainingstag, noch nicht trainiert und eine Streak läuft. */
    fun saver(data: AppData, today: LocalDate): ReminderMessage? {
        if (!data.settings.onboardingDone) return null
        val workout = data.workoutFor(today) ?: return null
        if (data.trainedOn(today)) return null
        val streak = data.streak(today)
        if (streak.state.current <= 0) return null
        val shield = if (streak.state.shields > 0) " (Du hast noch ${streak.state.shields} Streak-Schutz.)" else ""
        return ReminderMessage(
            "Deine ${streak.state.current}-Tage-Streak ist in Gefahr",
            "Heute noch nicht trainiert: ${workout.template.name} wartet. Auch ein kurzes Training zählt.$shield",
            withSnooze = true,
        )
    }
}


object ReminderTimes {
    /**
     * Nächster Zeitpunkt für [minutesOfDay] (Minuten seit Mitternacht, lokale Zeit) nach [now].
     * Reine Funktion – berücksichtigt die Zeitzone von [now] und Sommer-/Winterzeit.
     */
    fun nextTrigger(minutesOfDay: Int, now: ZonedDateTime): ZonedDateTime {
        val minutes = minutesOfDay.coerceIn(0, 24 * 60 - 1)
        var candidate = now.toLocalDate().atTime(minutes / 60, minutes % 60).atZone(now.zone)
        if (!candidate.isAfter(now.plusSeconds(30))) {
            candidate = now.toLocalDate().plusDays(1).atTime(minutes / 60, minutes % 60).atZone(now.zone)
        }
        return candidate
    }
}
