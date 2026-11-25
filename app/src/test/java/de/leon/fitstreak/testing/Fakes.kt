package de.leon.fitstreak.testing

import de.leon.fitstreak.core.model.Equipment
import de.leon.fitstreak.core.model.Goal
import de.leon.fitstreak.core.model.Rating
import de.leon.fitstreak.data.FitRepository
import de.leon.fitstreak.data.OnboardingInput
import de.leon.fitstreak.data.db.AchievementEntity
import de.leon.fitstreak.data.db.DayRecordEntity
import de.leon.fitstreak.data.db.FitDao
import de.leon.fitstreak.data.db.FitnessTestEntity
import de.leon.fitstreak.data.db.HabitDayEntity
import de.leon.fitstreak.data.db.MeasurementEntity
import de.leon.fitstreak.data.db.SessionEntity
import de.leon.fitstreak.data.db.SetLogEntity
import de.leon.fitstreak.data.db.TemplateExerciseEntity
import de.leon.fitstreak.data.db.WorkoutTemplateEntity
import de.leon.fitstreak.data.settings.Settings
import de.leon.fitstreak.data.settings.SettingsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.ZoneId

/** In-Memory-Ersatz für das Room-DAO. */
class FakeDao : FitDao {
    val t = MutableStateFlow(listOf<WorkoutTemplateEntity>())
    val te = MutableStateFlow(listOf<TemplateExerciseEntity>())
    val s = MutableStateFlow(listOf<SessionEntity>())
    val sl = MutableStateFlow(listOf<SetLogEntity>())
    val dr = MutableStateFlow(listOf<DayRecordEntity>())
    val h = MutableStateFlow(listOf<HabitDayEntity>())
    val m = MutableStateFlow(listOf<MeasurementEntity>())
    val ft = MutableStateFlow(listOf<FitnessTestEntity>())
    val a = MutableStateFlow(listOf<AchievementEntity>())
    private var nextId = 1L
    private fun id() = nextId++

    override fun templates(): Flow<List<WorkoutTemplateEntity>> = t
    override fun templateExercises(): Flow<List<TemplateExerciseEntity>> = te
    override suspend fun insertTemplate(template: WorkoutTemplateEntity): Long { val i = id(); t.update { it + template.copy(id = i) }; return i }
    override suspend fun updateTemplate(template: WorkoutTemplateEntity) = t.update { l -> l.map { if (it.id == template.id) template else it } }
    override suspend fun deleteTemplate(id: Long) = t.update { l -> l.filter { it.id != id } }
    override suspend fun deleteRotationTemplates() = t.update { l -> l.filter { !it.inRotation } }
    override suspend fun deleteOrphanTemplateExercises() { val ids = t.value.map { it.id }.toSet(); te.update { l -> l.filter { it.templateId in ids } } }
    override suspend fun insertTemplateExercises(exercises: List<TemplateExerciseEntity>) = te.update { l -> (l + exercises.map { if (it.id == 0L) it.copy(id = id()) else it }).sortedWith(compareBy({ it.templateId }, { it.position })) }
    override suspend fun updateTemplateExercises(exercises: List<TemplateExerciseEntity>) { val by = exercises.associateBy { it.id }; te.update { l -> l.map { by[it.id] ?: it }.sortedWith(compareBy({ it.templateId }, { it.position })) } }
    override suspend fun deleteTemplateExercise(id: Long) = te.update { l -> l.filter { it.id != id } }
    override fun sessions(): Flow<List<SessionEntity>> = s
    override suspend fun insertSession(session: SessionEntity): Long { val i = id(); s.update { it + session.copy(id = i) }; return i }
    override suspend fun updateSession(session: SessionEntity) = s.update { l -> l.map { if (it.id == session.id) session else it } }
    override suspend fun deleteSession(id: Long) = s.update { l -> l.filter { it.id != id } }
    override fun setLogs(): Flow<List<SetLogEntity>> = sl
    override suspend fun upsertSetLog(log: SetLogEntity): Long { val i = if (log.id == 0L) id() else log.id; sl.update { l -> l.filter { it.id != i } + log.copy(id = i) }; return i }
    override suspend fun deleteSetLog(id: Long) = sl.update { l -> l.filter { it.id != id } }
    override suspend fun deleteSetLogsOfSession(sessionId: Long) = sl.update { l -> l.filter { it.sessionId != sessionId } }
    override suspend fun rateExercise(sessionId: Long, exerciseId: String, rating: Int) = sl.update { l -> l.map { if (it.sessionId == sessionId && it.exerciseId == exerciseId) it.copy(rating = rating) else it } }
    override fun dayRecords(): Flow<List<DayRecordEntity>> = dr
    override suspend fun upsertDayRecords(records: List<DayRecordEntity>) { val days = records.map { it.epochDay }.toSet(); dr.update { l -> (l.filter { it.epochDay !in days } + records).sortedBy { it.epochDay } } }
    override fun habits(): Flow<List<HabitDayEntity>> = h
    override suspend fun upsertHabit(habit: HabitDayEntity) = h.update { l -> l.filter { it.epochDay != habit.epochDay } + habit }
    override fun measurements(): Flow<List<MeasurementEntity>> = m
    override suspend fun upsertMeasurement(measurement: MeasurementEntity): Long { val i = if (measurement.id == 0L) id() else measurement.id; m.update { l -> l.filter { it.id != i } + measurement.copy(id = i) }; return i }
    override suspend fun deleteMeasurement(id: Long) = m.update { l -> l.filter { it.id != id } }
    override fun fitnessTests(): Flow<List<FitnessTestEntity>> = ft
    override suspend fun insertFitnessTest(test: FitnessTestEntity): Long { val i = id(); ft.update { it + test.copy(id = i) }; return i }
    override fun achievements(): Flow<List<AchievementEntity>> = a
    override suspend fun insertAchievements(achievements: List<AchievementEntity>) { val have = a.value.map { it.id }.toSet(); a.update { l -> l + achievements.filter { it.id !in have } } }
    override suspend fun clearTemplates() { t.value = emptyList() }
    override suspend fun clearTemplateExercises() { te.value = emptyList() }
    override suspend fun clearSessions() { s.value = emptyList() }
    override suspend fun clearSetLogs() { sl.value = emptyList() }
    override suspend fun clearDayRecords() { dr.value = emptyList() }
    override suspend fun clearHabits() { h.value = emptyList() }
    override suspend fun clearMeasurements() { m.value = emptyList() }
    override suspend fun clearFitnessTests() { ft.value = emptyList() }
    override suspend fun clearAchievements() { a.value = emptyList() }
    override suspend fun insertTemplates(list: List<WorkoutTemplateEntity>) { t.update { it + list } }
    override suspend fun insertSessions(list: List<SessionEntity>) { s.update { it + list } }
    override suspend fun insertSetLogs(list: List<SetLogEntity>) { sl.update { it + list } }
    override suspend fun insertHabits(list: List<HabitDayEntity>) { h.update { it + list } }
    override suspend fun insertMeasurements(list: List<MeasurementEntity>) { m.update { it + list } }
    override suspend fun insertFitnessTests(list: List<FitnessTestEntity>) { ft.update { it + list } }
}

class MemorySettings(initial: Settings = Settings()) : SettingsStore {
    private val state = MutableStateFlow(initial)
    override val data: Flow<Settings> = state
    override suspend fun update(transform: (Settings) -> Settings): Settings { state.update(transform); return state.value }
}

/** Testwelt mit verstellbarer Uhr. */
class World(startDay: LocalDate = LocalDate.of(2026, 3, 2)) {
    val zone: ZoneId = ZoneId.of("Europe/Berlin")
    var now: Long = startDay.atTime(17, 0).atZone(zone).toInstant().toEpochMilli()
    val dao = FakeDao()
    val settings = MemorySettings()
    val repo = FitRepository(dao, settings, now = { now }, zone = { zone })

    fun advanceDays(n: Int) { now += n * 86_400_000L }

    suspend fun onboard() = repo.completeOnboarding(
        OnboardingInput(
            name = "Leon", goals = setOf(Goal.SIXPACK, Goal.OBERKOERPER), equipment = setOf(Equipment.KURZHANTEL),
            dumbbellKg = listOf(12.0), daysPerWeek = 4, weekdays = listOf(1, 2, 4, 5),
            maxPushups = 10, maxPlankSec = 60, maxSquats = 20, reminderEnabled = true, reminderMinutes = 18 * 60,
        ),
    )

    /** Trainiert das heutige Workout komplett durch (alle Sätze am oberen Ende, wenn [strong]). */
    suspend fun trainToday(strong: Boolean = false): Boolean {
        val data = repo.snapshot()
        val w = data.workoutFor(repo.today()) ?: return false
        val id = repo.startOrResumeSession(w.id)
        for (row in w.exercises) {
            repeat(row.sets) { i ->
                val reps = if (strong) row.repMax else (row.repMin + (2 - i).coerceAtLeast(0)).coerceAtMost(row.repMax)
                repo.logSet(id, row.exerciseId, i, reps, row.weightKg)
                now += 90_000
            }
            repo.rateExercise(id, row.exerciseId, Rating.PASST)
        }
        repo.finishSession(id)
        return true
    }

    /** Simuliert [days] Tage: an Trainingstagen wird trainiert, außer an den Tagen in [skip] (Index ab 0). */
    suspend fun simulate(days: Int, skip: Set<Int> = emptySet()) {
        repeat(days) { d ->
            repo.refreshStreak()
            if (d !in skip) trainToday(strong = d % 3 == 0)
            repo.refreshStreak()
            advanceDays(1)
        }
        repo.refreshStreak()
    }
}
