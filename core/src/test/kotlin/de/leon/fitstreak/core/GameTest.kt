package de.leon.fitstreak.core

import de.leon.fitstreak.core.game.AchievementStats
import de.leon.fitstreak.core.game.Achievements
import de.leon.fitstreak.core.game.Level
import de.leon.fitstreak.core.game.Xp
import de.leon.fitstreak.core.game.levelFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameTest {
    @Test
    fun `XP fuer ein Training`() {
        // 20 Sätze × 10 + 50 fürs Training
        assertEquals(250, Xp.forWorkout(sets = 20, records = 0))
        // + 2 Rekorde × 25 + Bonus für 7 Tage Streak
        assertEquals(250 + 50 + 70, Xp.forWorkout(20, 2, listOf(7)))
    }

    @Test
    fun `Streak-Bonus nur an Meilensteinen`() {
        assertEquals(0, Xp.streakBonus(5))
        assertEquals(30, Xp.streakBonus(3))
        assertEquals(1000, Xp.streakBonus(100))
    }

    @Test
    fun `Level-Grenzen`() {
        assertEquals(Level.ANFAENGER, levelFor(0).level)
        assertEquals(Level.ANFAENGER, levelFor(499).level)
        assertEquals(Level.MOTIVIERT, levelFor(500).level)
        assertEquals(Level.LEGENDE, levelFor(1_000_000).level)
        assertNull(levelFor(1_000_000).next)
        assertEquals(1f, levelFor(1_000_000).progress)
    }

    @Test
    fun `Level-Fortschritt`() {
        val info = levelFor(1250) // Motiviert: 500..2000
        assertEquals(Level.MOTIVIERT, info.level)
        assertEquals(Level.FIT, info.next)
        assertEquals(750, info.xpIntoLevel)
        assertEquals(1500, info.xpForLevel)
        assertEquals(0.5f, info.progress, 0.001f)
    }

    @Test
    fun `Erfolge werden passend freigeschaltet`() {
        assertTrue(Achievements.unlocked(AchievementStats()).isEmpty())
        val s = AchievementStats(workouts = 1, bestStreak = 14, totalPushups = 1000, records = 1, totalVolumeKg = 9_999.0)
        val ids = Achievements.unlocked(s)
        assertEquals(setOf("first_workout", "streak_3", "streak_7", "streak_14", "pushups_1000", "first_record"), ids)
    }

    @Test
    fun `alle Erfolge haben eindeutige IDs`() {
        assertEquals(Achievements.all.size, Achievements.all.map { it.id }.toSet().size)
        val everything = AchievementStats(1, 100, 1000, 1, 1, 1, 10_000.0)
        assertEquals(Achievements.all.map { it.id }.toSet(), Achievements.unlocked(everything))
    }
}
