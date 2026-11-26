package de.leon.fitstreak.core.game

/** XP-Regeln. */
object Xp {
    const val PER_SET = 10
    const val PER_HABIT = 5
    const val PER_WORKOUT = 50
    const val PER_RECORD = 25

    val streakMilestones = listOf(3, 7, 14, 30, 100)

    fun streakBonus(streak: Int): Int = when (streak) {
        3 -> 30
        7 -> 70
        14 -> 150
        30 -> 300
        100 -> 1000
        else -> 0
    }

    /** XP für ein abgeschlossenes Training. [streakReached] = alle neu erreichten Streak-Stände. */
    fun forWorkout(sets: Int, records: Int, streakReached: Collection<Int> = emptyList()): Int =
        sets * PER_SET + PER_WORKOUT + records * PER_RECORD + streakReached.sumOf { streakBonus(it) }
}

enum class Level(val title: String, val minXp: Int) {
    ANFAENGER("Anfänger", 0),
    MOTIVIERT("Motiviert", 500),
    FIT("Fit", 2_000),
    ATHLET("Athlet", 6_000),
    MASCHINE("Maschine", 15_000),
    LEGENDE("Legende", 40_000),
}

data class LevelInfo(
    val level: Level,
    val next: Level?,
    val xp: Int,
    val xpIntoLevel: Int,
    val xpForLevel: Int,
    val progress: Float,
) {
    val number: Int get() = level.ordinal + 1
}

fun levelFor(xp: Int): LevelInfo {
    val level = Level.entries.last { xp >= it.minXp }
    val next = Level.entries.getOrNull(level.ordinal + 1)
    val into = xp - level.minXp
    val span = (next?.minXp ?: level.minXp) - level.minXp
    val progress = if (next == null) 1f else (into.toFloat() / span).coerceIn(0f, 1f)
    return LevelInfo(level, next, xp, into, span, progress)
}

enum class AchievementIcon { START, FLAMME, LIEGESTUETZ, POKAL, KALENDER, KOERPER, GEWICHT }

data class Achievement(
    val id: String,
    val title: String,
    val description: String,
    val icon: AchievementIcon,
    /** Zahl auf dem Abzeichen (z. B. 7 für 7 Tage), 0 = keine. */
    val badge: Int = 0,
)

data class AchievementStats(
    val workouts: Int = 0,
    val bestStreak: Int = 0,
    val totalPushups: Int = 0,
    val records: Int = 0,
    val perfectWeeks: Int = 0,
    val allMuscleWeeks: Int = 0,
    val totalVolumeKg: Double = 0.0,
)

object Achievements {
    val all: List<Achievement> = listOf(
        Achievement("first_workout", "Erster Schritt", "Erstes Training abgeschlossen", AchievementIcon.START),
        Achievement("streak_3", "Dranbleiber", "3 Tage Streak", AchievementIcon.FLAMME, 3),
        Achievement("streak_7", "Eine Woche stark", "7 Tage Streak", AchievementIcon.FLAMME, 7),
        Achievement("streak_14", "Zwei Wochen Feuer", "14 Tage Streak", AchievementIcon.FLAMME, 14),
        Achievement("streak_30", "Monats-Maschine", "30 Tage Streak", AchievementIcon.FLAMME, 30),
        Achievement("streak_100", "Unaufhaltsam", "100 Tage Streak", AchievementIcon.FLAMME, 100),
        Achievement("pushups_1000", "1000 Liegestütze", "Insgesamt 1000 Liegestütze geschafft", AchievementIcon.LIEGESTUETZ),
        Achievement("first_record", "Neuer Rekord", "Ersten persönlichen Rekord aufgestellt", AchievementIcon.POKAL),
        Achievement("perfect_week", "Perfekte Woche", "Alle geplanten Trainings einer Woche erledigt", AchievementIcon.KALENDER),
        Achievement("all_muscles", "Rundum trainiert", "Alle Muskelgruppen in einer Woche trainiert", AchievementIcon.KOERPER),
        Achievement("volume_10t", "10 Tonnen", "10.000 kg Gesamtvolumen bewegt", AchievementIcon.GEWICHT),
    )

    fun byId(id: String): Achievement? = all.firstOrNull { it.id == id }

    fun unlocked(s: AchievementStats): Set<String> = buildSet {
        if (s.workouts >= 1) add("first_workout")
        for (n in Xp.streakMilestones) if (s.bestStreak >= n) add("streak_$n")
        if (s.totalPushups >= 1000) add("pushups_1000")
        if (s.records >= 1) add("first_record")
        if (s.perfectWeeks >= 1) add("perfect_week")
        if (s.allMuscleWeeks >= 1) add("all_muscles")
        if (s.totalVolumeKg >= 10_000.0) add("volume_10t")
    }
}
