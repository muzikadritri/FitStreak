package de.leon.fitstreak.data.settings

import de.leon.fitstreak.core.model.Equipment
import de.leon.fitstreak.core.model.Goal
import de.leon.fitstreak.core.model.UserProfile
import de.leon.fitstreak.core.streak.StreakState
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import java.time.LocalDate

@Serializable
data class Settings(
    val onboardingDone: Boolean = false,
    val name: String = "",
    val goals: Set<String> = emptySet(),
    val equipment: Set<String> = emptySet(),
    val dumbbellKg: List<Double> = emptyList(),
    val daysPerWeek: Int = 4,
    /** ISO-Wochentage 1 = Montag … 7 = Sonntag. */
    val weekdays: List<Int> = listOf(1, 2, 4, 5),
    val planStartDay: Long = 0,
    val maxPushups: Int = 0,
    val maxPlankSec: Int = 0,
    val maxSquats: Int = 0,

    val reminderEnabled: Boolean = true,
    val reminderMinutes: Int = 18 * 60,
    val saverEnabled: Boolean = true,
    val saverMinutes: Int = 21 * 60,
    val soundEnabled: Boolean = true,
    val hapticsEnabled: Boolean = true,
    /** Verlängert/verkürzt alle Satzpausen (Sekunden). */
    val restAdjustSec: Int = 0,

    val streakCurrent: Int = 0,
    val streakBest: Int = 0,
    val shields: Int = 0,
    val streakEvaluatedDay: Long? = null,
    /** Tage, an denen ein Streak-Schutz eingesetzt wurde und der Hinweis noch nicht bestätigt ist. */
    val shieldNoticeDays: List<Long> = emptyList(),

    val xp: Int = 0,

    /** Montag der laufenden Deload-Woche. */
    val deloadWeekStartDay: Long? = null,
    /** Ab hier zählen die Wochen bis zum nächsten Deload-Vorschlag. */
    val deloadCountFromDay: Long = 0,
    val deloadSnoozedUntilDay: Long = 0,

    val waterGoal: Int = 8,
) {
    fun goalSet(): Set<Goal> = goals.mapNotNull { n -> Goal.entries.firstOrNull { it.name == n } }.toSet()
    fun equipmentSet(): Set<Equipment> = equipment.mapNotNull { n -> Equipment.entries.firstOrNull { it.name == n } }.toSet()

    fun profile() = UserProfile(
        goals = goalSet(),
        equipment = equipmentSet(),
        dumbbellKg = dumbbellKg,
        daysPerWeek = daysPerWeek,
        maxPushups = maxPushups,
        maxPlankSec = maxPlankSec,
        maxSquats = maxSquats,
    )

    fun streakState() = StreakState(
        current = streakCurrent,
        best = streakBest,
        shields = shields,
        lastEvaluated = streakEvaluatedDay?.let(LocalDate::ofEpochDay),
    )

    fun withStreak(s: StreakState) = copy(
        streakCurrent = s.current,
        streakBest = s.best,
        shields = s.shields,
        streakEvaluatedDay = s.lastEvaluated?.toEpochDay(),
    )
}

/** Abstraktion über DataStore, damit Repository und ViewModels ohne Android testbar sind. */
interface SettingsStore {
    val data: Flow<Settings>
    suspend fun update(transform: (Settings) -> Settings): Settings
}
