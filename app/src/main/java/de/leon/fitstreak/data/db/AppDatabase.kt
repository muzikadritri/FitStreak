package de.leon.fitstreak.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        WorkoutTemplateEntity::class,
        TemplateExerciseEntity::class,
        SessionEntity::class,
        SetLogEntity::class,
        DayRecordEntity::class,
        HabitDayEntity::class,
        MeasurementEntity::class,
        FitnessTestEntity::class,
        AchievementEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): FitDao

    companion object {
        // Kein fallbackToDestructiveMigration: Bei Schema-Änderungen muss eine Migration
        // geschrieben werden, damit Trainingsdaten bei Updates erhalten bleiben.
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "fitstreak.db").build()
    }
}
