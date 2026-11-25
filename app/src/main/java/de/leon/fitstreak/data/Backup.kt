package de.leon.fitstreak.data

import de.leon.fitstreak.data.db.AchievementEntity
import de.leon.fitstreak.data.db.DayRecordEntity
import de.leon.fitstreak.data.db.FitnessTestEntity
import de.leon.fitstreak.data.db.HabitDayEntity
import de.leon.fitstreak.data.db.MeasurementEntity
import de.leon.fitstreak.data.db.SessionEntity
import de.leon.fitstreak.data.db.SetLogEntity
import de.leon.fitstreak.data.db.TemplateExerciseEntity
import de.leon.fitstreak.data.db.WorkoutTemplateEntity
import de.leon.fitstreak.data.settings.Settings
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class BackupFile(
    val app: String = Backup.APP,
    val format: Int = Backup.FORMAT,
    val exportedAt: Long = 0,
    val settings: Settings = Settings(),
    val templates: List<WorkoutTemplateEntity> = emptyList(),
    val templateExercises: List<TemplateExerciseEntity> = emptyList(),
    val sessions: List<SessionEntity> = emptyList(),
    val setLogs: List<SetLogEntity> = emptyList(),
    val dayRecords: List<DayRecordEntity> = emptyList(),
    val habits: List<HabitDayEntity> = emptyList(),
    val measurements: List<MeasurementEntity> = emptyList(),
    val tests: List<FitnessTestEntity> = emptyList(),
    val achievements: List<AchievementEntity> = emptyList(),
)

/** Export/Import aller Daten als JSON. */
object Backup {
    const val APP = "FitStreak"
    const val FORMAT = 1

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun encode(data: AppData, exportedAt: Long): String = json.encodeToString(
        BackupFile.serializer(),
        BackupFile(
            exportedAt = exportedAt,
            settings = data.settings,
            templates = data.templates.map { it.template },
            templateExercises = data.templates.flatMap { it.exercises },
            // nur abgeschlossene Trainings sichern
            sessions = data.finishedSessions,
            setLogs = data.finishedLogs,
            dayRecords = data.dayRecords,
            habits = data.habits,
            measurements = data.measurements,
            tests = data.tests,
            achievements = data.achievements,
        ),
    )

    fun decode(text: String): BackupFile {
        val file = try {
            json.decodeFromString(BackupFile.serializer(), text)
        } catch (e: Exception) {
            throw IllegalArgumentException("Die Datei ist kein gültiges FitStreak-Backup.", e)
        }
        require(file.app == APP) { "Die Datei ist kein FitStreak-Backup." }
        require(file.format <= FORMAT) { "Das Backup stammt aus einer neueren App-Version." }
        return file
    }
}
