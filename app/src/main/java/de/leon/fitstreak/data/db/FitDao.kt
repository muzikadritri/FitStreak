package de.leon.fitstreak.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Bewusst schlicht: Die Datenmengen sind klein, deshalb liefert das DAO ganze Tabellen als Flow
 * und alle Auswertungen passieren als reine Kotlin-Funktionen (testbar ohne Datenbank).
 */
@Dao
interface FitDao {
    // --- Plan ---
    @Query("SELECT * FROM workout_template ORDER BY position, id")
    fun templates(): Flow<List<WorkoutTemplateEntity>>

    @Query("SELECT * FROM template_exercise ORDER BY templateId, position, id")
    fun templateExercises(): Flow<List<TemplateExerciseEntity>>

    @Insert
    suspend fun insertTemplate(template: WorkoutTemplateEntity): Long

    @Update
    suspend fun updateTemplate(template: WorkoutTemplateEntity)

    @Query("DELETE FROM workout_template WHERE id = :id")
    suspend fun deleteTemplate(id: Long)

    @Query("DELETE FROM workout_template WHERE inRotation = 1")
    suspend fun deleteRotationTemplates()

    @Query("DELETE FROM template_exercise WHERE templateId NOT IN (SELECT id FROM workout_template)")
    suspend fun deleteOrphanTemplateExercises()

    @Insert
    suspend fun insertTemplateExercises(exercises: List<TemplateExerciseEntity>)

    @Update
    suspend fun updateTemplateExercises(exercises: List<TemplateExerciseEntity>)

    @Query("DELETE FROM template_exercise WHERE id = :id")
    suspend fun deleteTemplateExercise(id: Long)

    // --- Trainings ---
    @Query("SELECT * FROM session ORDER BY startedAt, id")
    fun sessions(): Flow<List<SessionEntity>>

    @Insert
    suspend fun insertSession(session: SessionEntity): Long

    @Update
    suspend fun updateSession(session: SessionEntity)

    @Query("DELETE FROM session WHERE id = :id")
    suspend fun deleteSession(id: Long)

    @Query("SELECT * FROM set_log ORDER BY doneAt, id")
    fun setLogs(): Flow<List<SetLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSetLog(log: SetLogEntity): Long

    @Query("DELETE FROM set_log WHERE id = :id")
    suspend fun deleteSetLog(id: Long)

    @Query("DELETE FROM set_log WHERE sessionId = :sessionId")
    suspend fun deleteSetLogsOfSession(sessionId: Long)

    @Query("UPDATE set_log SET rating = :rating WHERE sessionId = :sessionId AND exerciseId = :exerciseId")
    suspend fun rateExercise(sessionId: Long, exerciseId: String, rating: Int)

    // --- Streak, Gewohnheiten, Körper, Erfolge ---
    @Query("SELECT * FROM day_record ORDER BY epochDay")
    fun dayRecords(): Flow<List<DayRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDayRecords(records: List<DayRecordEntity>)

    @Query("SELECT * FROM habit_day ORDER BY epochDay")
    fun habits(): Flow<List<HabitDayEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHabit(habit: HabitDayEntity)

    @Query("SELECT * FROM measurement ORDER BY epochDay, id")
    fun measurements(): Flow<List<MeasurementEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMeasurement(measurement: MeasurementEntity): Long

    @Query("DELETE FROM measurement WHERE id = :id")
    suspend fun deleteMeasurement(id: Long)

    @Query("SELECT * FROM fitness_test ORDER BY epochDay, id")
    fun fitnessTests(): Flow<List<FitnessTestEntity>>

    @Insert
    suspend fun insertFitnessTest(test: FitnessTestEntity): Long

    @Query("SELECT * FROM achievement ORDER BY unlockedAt")
    fun achievements(): Flow<List<AchievementEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAchievements(achievements: List<AchievementEntity>)

    // --- Backup ---
    @Query("DELETE FROM workout_template") suspend fun clearTemplates()
    @Query("DELETE FROM template_exercise") suspend fun clearTemplateExercises()
    @Query("DELETE FROM session") suspend fun clearSessions()
    @Query("DELETE FROM set_log") suspend fun clearSetLogs()
    @Query("DELETE FROM day_record") suspend fun clearDayRecords()
    @Query("DELETE FROM habit_day") suspend fun clearHabits()
    @Query("DELETE FROM measurement") suspend fun clearMeasurements()
    @Query("DELETE FROM fitness_test") suspend fun clearFitnessTests()
    @Query("DELETE FROM achievement") suspend fun clearAchievements()

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertTemplates(list: List<WorkoutTemplateEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertSessions(list: List<SessionEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertSetLogs(list: List<SetLogEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertHabits(list: List<HabitDayEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertMeasurements(list: List<MeasurementEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertFitnessTests(list: List<FitnessTestEntity>)
}
