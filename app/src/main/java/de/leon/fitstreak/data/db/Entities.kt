package de.leon.fitstreak.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/** Ein Workout des Plans (inRotation = true) oder ein eigenes Workout. */
@Serializable
@Entity(tableName = "workout_template")
data class WorkoutTemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val focus: String = "",
    val position: Int = 0,
    val inRotation: Boolean = true,
)

/** Übung in einem Workout inklusive aktueller Vorgabe (Sätze, Wdh., Gewicht, Tempo). */
@Serializable
@Entity(tableName = "template_exercise", indices = [Index("templateId")])
data class TemplateExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val templateId: Long,
    val position: Int,
    val exerciseId: String,
    val sets: Int,
    val repMin: Int,
    val repMax: Int,
    val weightKg: Double = 0.0,
    val tempo: Int = 0,
    val restSec: Int = 60,
    val failStreak: Int = 0,
)

/** Ein durchgeführtes Training. epochDay = lokales Datum des Starts. */
@Serializable
@Entity(tableName = "session", indices = [Index("epochDay")])
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val templateId: Long,
    val templateName: String,
    val startedAt: Long,
    val finishedAt: Long? = null,
    val volumeKg: Double = 0.0,
    val xp: Int = 0,
    val prCount: Int = 0,
    val setCount: Int = 0,
    val warmupDone: Boolean = false,
)

/** Ein erledigter Satz. reps = Wiederholungen oder Sekunden. rating: 0 = keine, 1 leicht, 2 passt, 3 schwer. */
@Serializable
@Entity(tableName = "set_log", indices = [Index("sessionId"), Index("exerciseId")])
data class SetLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val exerciseId: String,
    val setIndex: Int,
    val reps: Int,
    val weightKg: Double = 0.0,
    val rating: Int = 0,
    val epochDay: Long,
    val doneAt: Long,
)

/** Festgeschriebener Streak-Status eines Tages (DayKind.ordinal). */
@Serializable
@Entity(tableName = "day_record")
data class DayRecordEntity(
    @PrimaryKey val epochDay: Long,
    val kind: Int,
)

@Serializable
@Entity(tableName = "habit_day")
data class HabitDayEntity(
    @PrimaryKey val epochDay: Long,
    val protein: Boolean = false,
    val enoughFood: Boolean = false,
    val water: Int = 0,
    val noSugar: Boolean = false,
    val sleep: Boolean = false,
)

@Serializable
@Entity(tableName = "measurement")
data class MeasurementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val upperArmCm: Double? = null,
    val chestCm: Double? = null,
    val waistCm: Double? = null,
)

@Serializable
@Entity(tableName = "fitness_test")
data class FitnessTestEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val pushups: Int,
    val plankSec: Int,
    val squats: Int,
)

@Serializable
@Entity(tableName = "achievement")
data class AchievementEntity(
    @PrimaryKey val id: String,
    val unlockedAt: Long,
)
