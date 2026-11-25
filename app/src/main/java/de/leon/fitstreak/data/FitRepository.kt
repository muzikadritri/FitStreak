package de.leon.fitstreak.data

import de.leon.fitstreak.core.exercises.ExerciseLibrary
import de.leon.fitstreak.core.game.Achievement
import de.leon.fitstreak.core.game.Achievements
import de.leon.fitstreak.core.game.LevelInfo
import de.leon.fitstreak.core.game.Xp
import de.leon.fitstreak.core.game.levelFor
import de.leon.fitstreak.core.model.Equipment
import de.leon.fitstreak.core.model.Goal
import de.leon.fitstreak.core.model.PlanExercise
import de.leon.fitstreak.core.model.Rating
import de.leon.fitstreak.core.plan.PlanGenerator
import de.leon.fitstreak.core.progression.ProgressionEngine
import de.leon.fitstreak.core.stats.SetResult
import de.leon.fitstreak.core.stats.Stats
import de.leon.fitstreak.core.streak.StreakEngine
import de.leon.fitstreak.core.streak.StreakUpdate
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlin.math.max

/** Wird vom Android-Teil gesetzt: Erinnerungen neu planen, Widget aktualisieren. */
interface AppHooks {
    fun remindersChanged() {}
    fun dataChanged() {}

    object None : AppHooks
}

data class OnboardingInput(
    val name: String,
    val goals: Set<Goal>,
    val equipment: Set<Equipment>,
    val dumbbellKg: List<Double>,
    val daysPerWeek: Int,
    val weekdays: List<Int>,
    val maxPushups: Int,
    val maxPlankSec: Int,
    val maxSquats: Int,
    val reminderEnabled: Boolean,
    val reminderMinutes: Int,
)

data class RecordInfo(val exerciseName: String, val text: String)

data class Suggestion(val exerciseName: String, val message: String, val isProgress: Boolean)

data class WorkoutSummary(
    val templateName: String,
    val durationMin: Int,
    val setCount: Int,
    val volumeKg: Double,
    val records: List<RecordInfo>,
    val xpEarned: Int,
    val levelBefore: LevelInfo,
    val levelAfter: LevelInfo,
    val streakBefore: Int,
    val streakAfter: Int,
    val shieldsEarned: Int,
    val suggestions: List<Suggestion>,
    val newAchievements: List<Achievement>,
)

enum class Habit { PROTEIN, ENOUGH_FOOD, NO_SUGAR, SLEEP }

/** Einzige Datenquelle für ViewModels, Benachrichtigungen und Widget. */
class FitRepository(
    private val dao: FitDao,
    private val settingsStore: SettingsStore,
    private val hooks: AppHooks = AppHooks.None,
    private val now: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    /** Führt den Block in einer Datenbank-Transaktion aus (Room: withTransaction). */
    private val transaction: suspend (suspend () -> Unit) -> Unit = { it() },
) {
    private val mutex = Mutex()

    val data: Flow<AppData> = combine(
        settingsStore.data,
        dao.templates(),
        dao.templateExercises(),
        dao.sessions(),
        dao.setLogs(),
        dao.dayRecords(),
        dao.habits(),
        dao.measurements(),
        dao.fitnessTests(),
        dao.achievements(),
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val templates = values[1] as List<WorkoutTemplateEntity>
        @Suppress("UNCHECKED_CAST")
        val exercises = (values[2] as List<TemplateExerciseEntity>).groupBy { it.templateId }
        @Suppress("UNCHECKED_CAST")
        AppData(
            loaded = true,
            settings = values[0] as Settings,
            templates = templates.map { TemplateWithExercises(it, exercises[it.id].orEmpty().sortedBy { e -> e.position }) },
            sessions = values[3] as List<SessionEntity>,
            setLogs = values[4] as List<SetLogEntity>,
            dayRecords = values[5] as List<DayRecordEntity>,
            habits = values[6] as List<HabitDayEntity>,
            measurements = values[7] as List<MeasurementEntity>,
            tests = values[8] as List<FitnessTestEntity>,
            achievements = values[9] as List<AchievementEntity>,
        )
    }

    fun today(): LocalDate = StreakEngine.localDate(now(), zone())
    fun nowMillis(): Long = now()

    suspend fun snapshot(): AppData = data.first()

    // ------------------------------------------------------------------ Onboarding & Plan

    suspend fun completeOnboarding(input: OnboardingInput) {
        val today = today()
        val settings = settingsStore.update {
            it.copy(
                onboardingDone = true,
                name = input.name.trim(),
                goals = input.goals.map { g -> g.name }.toSet(),
                equipment = input.equipment.map { e -> e.name }.toSet(),
                dumbbellKg = input.dumbbellKg.sorted(),
                daysPerWeek = input.daysPerWeek,
                weekdays = input.weekdays.sorted(),
                planStartDay = today.toEpochDay(),
                maxPushups = input.maxPushups,
                maxPlankSec = input.maxPlankSec,
                maxSquats = input.maxSquats,
                reminderEnabled = input.reminderEnabled,
                reminderMinutes = input.reminderMinutes,
                streakEvaluatedDay = today.toEpochDay() - 1,
                deloadCountFromDay = today.toEpochDay(),
            )
        }
        dao.insertFitnessTest(FitnessTestEntity(epochDay = today.toEpochDay(), pushups = input.maxPushups, plankSec = input.maxPlankSec, squats = input.maxSquats))
        writePlan(settings)
        hooks.remindersChanged()
        hooks.dataChanged()
    }

    /** Erstellt den Wochenplan neu (eigene Workouts bleiben erhalten). */
    suspend fun regeneratePlan() {
        writePlan(settingsStore.data.first())
        hooks.dataChanged()
    }

    private suspend fun writePlan(settings: Settings) {
        val plan = PlanGenerator.generate(settings.profile())
        transaction {
            dao.deleteRotationTemplates()
            dao.deleteOrphanTemplateExercises()
            plan.forEachIndexed { i, w ->
                val id = dao.insertTemplate(WorkoutTemplateEntity(name = w.name, focus = w.focus, position = i, inRotation = true))
                dao.insertTemplateExercises(w.exercises.mapIndexed { pos, pe -> pe.toEntity(id, pos) })
            }
        }
    }

    suspend fun updateSettings(transform: (Settings) -> Settings) {
        val before = settingsStore.data.first()
        val after = settingsStore.update(transform)
        if (before.reminderEnabled != after.reminderEnabled || before.reminderMinutes != after.reminderMinutes ||
            before.saverEnabled != after.saverEnabled || before.saverMinutes != after.saverMinutes
        ) hooks.remindersChanged()
        if (before.weekdays != after.weekdays) hooks.dataChanged()
    }

    /** Ziele/Ausrüstung/Tage geändert: Einstellungen speichern und Plan neu erzeugen. */
    suspend fun updateProfileAndPlan(transform: (Settings) -> Settings) {
        val s = settingsStore.update(transform)
        writePlan(s)
        hooks.remindersChanged()
        hooks.dataChanged()
    }

    // ------------------------------------------------------------------ Plan bearbeiten

    suspend fun createCustomWorkout(name: String): Long {
        val count = snapshot().templates.size
        val id = dao.insertTemplate(WorkoutTemplateEntity(name = name.ifBlank { "Mein Workout" }, focus = "Eigenes Workout", position = 100 + count, inRotation = false))
        return id
    }

    suspend fun renameWorkout(templateId: Long, name: String) {
        val t = snapshot().template(templateId) ?: return
        dao.updateTemplate(t.template.copy(name = name.ifBlank { t.template.name }))
        hooks.dataChanged()
    }

    suspend fun deleteWorkout(templateId: Long) {
        transaction {
            dao.deleteTemplate(templateId)
            dao.deleteOrphanTemplateExercises()
        }
        hooks.dataChanged()
    }

    suspend fun addExercise(templateId: Long, exerciseId: String) {
        val data = snapshot()
        val t = data.template(templateId) ?: return
        val e = ExerciseLibrary[exerciseId] ?: return
        // gleiche Übung schon in einem anderen Workout? Dann deren aktuelle Vorgabe übernehmen
        val existing = data.templates.flatMap { it.exercises }.firstOrNull { it.exerciseId == exerciseId }
        val plan = existing?.toPlan()?.copy(sets = 3) ?: PlanGenerator.prescriptionFor(e, data.settings.profile())
        dao.insertTemplateExercises(listOf(plan.toEntity(templateId, (t.exercises.maxOfOrNull { it.position } ?: -1) + 1)))
    }

    suspend fun removeExercise(templateExerciseId: Long) {
        dao.deleteTemplateExercise(templateExerciseId)
    }

    suspend fun swapExercise(templateExerciseId: Long, newExerciseId: String) {
        val data = snapshot()
        val row = data.templates.flatMap { it.exercises }.firstOrNull { it.id == templateExerciseId } ?: return
        val e = ExerciseLibrary[newExerciseId] ?: return
        val plan = PlanGenerator.prescriptionFor(e, data.settings.profile(), row.sets)
        dao.updateTemplateExercises(listOf(row.with(plan)))
    }

    suspend fun updatePrescription(row: TemplateExerciseEntity) {
        dao.updateTemplateExercises(listOf(row.copy(failStreak = 0)))
    }

    suspend fun moveExercise(templateExerciseId: Long, delta: Int) {
        val data = snapshot()
        val t = data.templates.firstOrNull { tpl -> tpl.exercises.any { it.id == templateExerciseId } } ?: return
        val list = t.exercises.toMutableList()
        val from = list.indexOfFirst { it.id == templateExerciseId }
        val to = (from + delta).coerceIn(0, list.lastIndex)
        if (from == to) return
        list.add(to, list.removeAt(from))
        dao.updateTemplateExercises(list.mapIndexed { i, e -> e.copy(position = i) })
    }

    // ------------------------------------------------------------------ Streak

    /** Schreibt vergangene Tage fest und speichert den Streak-Stand. */
    suspend fun refreshStreak(): StreakUpdate = mutex.withLock { refreshStreakLocked() }

    private suspend fun refreshStreakLocked(): StreakUpdate {
        val data = snapshot()
        if (!data.settings.onboardingDone) return data.streak(today())
        val update = data.streak(today())
        if (update.days.isNotEmpty() || update.state != data.settings.streakState()) {
            if (update.days.isNotEmpty()) {
                dao.upsertDayRecords(update.days.map { DayRecordEntity(it.date.toEpochDay(), it.kind.ordinal) })
            }
            settingsStore.update { s ->
                s.withStreak(update.state).copy(shieldNoticeDays = s.shieldNoticeDays + update.shieldsUsed.map { it.toEpochDay() })
            }
            hooks.dataChanged()
        }
        return update
    }

    suspend fun dismissShieldNotice() {
        settingsStore.update { it.copy(shieldNoticeDays = emptyList()) }
    }

    // ------------------------------------------------------------------ Training

    /** Startet ein Training oder setzt ein heute begonnenes, nicht beendetes fort. */
    suspend fun startOrResumeSession(templateId: Long): Long {
        val data = snapshot()
        val today = today().toEpochDay()
        data.sessions.firstOrNull { it.templateId == templateId && it.finishedAt == null && it.epochDay == today }?.let { return it.id }
        // alte, nie beendete Sessions aufräumen
        data.sessions.filter { it.finishedAt == null }.forEach { discardSession(it.id) }
        val t = data.template(templateId)
        return dao.insertSession(SessionEntity(epochDay = today, templateId = templateId, templateName = t?.template?.name ?: "Training", startedAt = now()))
    }

    suspend fun setWarmupDone(sessionId: Long) {
        val s = snapshot().sessions.firstOrNull { it.id == sessionId } ?: return
        dao.updateSession(s.copy(warmupDone = true))
    }

    suspend fun logSet(sessionId: Long, exerciseId: String, setIndex: Int, reps: Int, weightKg: Double) {
        val data = snapshot()
        val session = data.sessions.firstOrNull { it.id == sessionId } ?: return
        val existing = data.setLogs.firstOrNull { it.sessionId == sessionId && it.exerciseId == exerciseId && it.setIndex == setIndex }
        dao.upsertSetLog(
            SetLogEntity(
                id = existing?.id ?: 0, sessionId = sessionId, exerciseId = exerciseId, setIndex = setIndex,
                reps = reps, weightKg = weightKg, rating = existing?.rating ?: 0,
                epochDay = session.epochDay, doneAt = existing?.doneAt ?: now(),
            ),
        )
    }

    suspend fun removeSet(sessionId: Long, exerciseId: String, setIndex: Int) {
        snapshot().setLogs.firstOrNull { it.sessionId == sessionId && it.exerciseId == exerciseId && it.setIndex == setIndex }
            ?.let { dao.deleteSetLog(it.id) }
    }

    suspend fun rateExercise(sessionId: Long, exerciseId: String, rating: Rating) {
        dao.rateExercise(sessionId, exerciseId, rating.ordinal + 1)
    }

    suspend fun discardSession(sessionId: Long) {
        transaction {
            dao.deleteSetLogsOfSession(sessionId)
            dao.deleteSession(sessionId)
        }
    }

    /**
     * Schließt das Training ab: Volumen, Rekorde, Progression für das nächste Mal, XP, Streak, Erfolge.
     * Gibt null zurück, wenn kein einziger Satz eingetragen wurde (Session wird dann verworfen).
     */
    suspend fun finishSession(sessionId: Long): WorkoutSummary? = mutex.withLock {
        val data = snapshot()
        val session = data.sessions.firstOrNull { it.id == sessionId } ?: return@withLock null
        val logs = data.setLogs.filter { it.sessionId == sessionId }
        if (logs.isEmpty()) {
            discardSession(sessionId)
            return@withLock null
        }
        val today = LocalDate.ofEpochDay(session.epochDay)
        val byExercise = logs.groupBy { it.exerciseId }.mapValues { (_, l) -> l.sortedBy { it.setIndex } }
        val volume = logs.filter { ExerciseLibrary[it.exerciseId]?.isTimed == false }.sumOf { it.reps * it.weightKg }

        // Rekorde
        val records = byExercise.mapNotNull { (id, l) ->
            val e = ExerciseLibrary[id] ?: return@mapNotNull null
            val new = Stats.bestOf(l.map { SetResult(it.reps, it.weightKg) })
            if (!Stats.isRecord(data.best(id, excludeSessionId = sessionId), new)) return@mapNotNull null
            val text = if (new.maxWeightKg > 0) {
                val top = l.maxBy { Stats.epley1Rm(it.weightKg, it.reps) }
                "${ProgressionEngine.fmtKg(top.weightKg)} kg × ${top.reps}"
            } else "${new.maxReps} ${e.unit}"
            RecordInfo(e.name, text)
        }

        // Progression: Vorgaben für das nächste Mal
        val deload = data.inDeload(today)
        val template = data.template(session.templateId)
        val suggestions = mutableListOf<Suggestion>()
        val updates = mutableListOf<TemplateExerciseEntity>()
        if (template != null) {
            for (row in template.exercises) {
                val l = byExercise[row.exerciseId] ?: continue
                val e = row.exercise ?: continue
                val rating = l.firstOrNull { it.rating > 0 }?.let { Rating.entries.getOrNull(it.rating - 1) }
                val result = ProgressionEngine.next(
                    current = row.toPlan().let { if (deload) it.copy(sets = ProgressionEngine.deloadSets(it.sets)) else it },
                    results = l.map { SetResult(it.reps, it.weightKg) },
                    rating = rating,
                    dumbbellKg = data.settings.dumbbellKg,
                    equipment = data.settings.equipmentSet(),
                    deload = deload,
                )
                if (deload) continue
                if (result.message.isNotBlank()) suggestions += Suggestion(e.name, result.message, result.isProgress)
                val next = result.next
                updates += row.with(next)
                // Gleiche Übung in anderen Workouts mitziehen (Sätze dort bleiben)
                for (other in data.templates.filter { it.id != template.id }) {
                    val usedThere = other.exercises.map { it.exerciseId }.toSet()
                    for (o in other.exercises.filter { it.exerciseId == row.exerciseId }) {
                        if (next.exerciseId != o.exerciseId && next.exerciseId in usedThere) continue
                        updates += o.with(next.copy(sets = o.sets, restSec = if (next.exerciseId != o.exerciseId) next.restSec else o.restSec))
                    }
                }
            }
        }
        if (updates.isNotEmpty()) dao.updateTemplateExercises(updates)

        // Session abschließen, dann Streak neu auswerten
        val streakBefore = data.streak(today()).display
        val finishedAt = now()
        dao.updateSession(session.copy(finishedAt = finishedAt, volumeKg = volume, prCount = records.size, setCount = logs.size))
        val streak = refreshStreakLocked()
        val streakAfter = streak.display
        val milestones = Xp.streakMilestones.filter { it in (streakBefore + 1)..streakAfter }
        val xp = Xp.forWorkout(logs.size, records.size, milestones)

        val levelBefore = levelFor(data.settings.xp)
        val newSettings = settingsStore.update { it.copy(xp = it.xp + xp) }
        dao.updateSession(session.copy(finishedAt = finishedAt, volumeKg = volume, prCount = records.size, setCount = logs.size, xp = xp))

        val newAchievements = unlockAchievements()
        hooks.dataChanged()

        WorkoutSummary(
            templateName = session.templateName,
            durationMin = max(1, ((finishedAt - session.startedAt) / 60_000).toInt()),
            setCount = logs.size,
            volumeKg = volume,
            records = records,
            xpEarned = xp,
            levelBefore = levelBefore,
            levelAfter = levelFor(newSettings.xp),
            streakBefore = streakBefore,
            streakAfter = streakAfter,
            shieldsEarned = streak.shieldsEarned,
            suggestions = suggestions,
            newAchievements = newAchievements,
        )
    }

    private suspend fun unlockAchievements(): List<Achievement> {
        val data = snapshot()
        val have = data.achievements.map { it.id }.toSet()
        val fresh = Achievements.unlocked(data.achievementStats()) - have
        if (fresh.isEmpty()) return emptyList()
        dao.insertAchievements(fresh.map { AchievementEntity(it, now()) })
        return fresh.mapNotNull(Achievements::byId)
    }

    // ------------------------------------------------------------------ Gewohnheiten

    suspend fun toggleHabit(habit: Habit) {
        val day = today()
        val h = snapshot().habit(day)
        val (updated, on) = when (habit) {
            Habit.PROTEIN -> h.copy(protein = !h.protein) to !h.protein
            Habit.ENOUGH_FOOD -> h.copy(enoughFood = !h.enoughFood) to !h.enoughFood
            Habit.NO_SUGAR -> h.copy(noSugar = !h.noSugar) to !h.noSugar
            Habit.SLEEP -> h.copy(sleep = !h.sleep) to !h.sleep
        }
        dao.upsertHabit(updated)
        addXp(if (on) Xp.PER_HABIT else -Xp.PER_HABIT)
    }

    suspend fun setWater(glasses: Int) {
        val data = snapshot()
        val h = data.habit(today())
        val goal = data.settings.waterGoal
        val n = glasses.coerceIn(0, 20)
        dao.upsertHabit(h.copy(water = n))
        if (h.water < goal && n >= goal) addXp(Xp.PER_HABIT)
        if (h.water >= goal && n < goal) addXp(-Xp.PER_HABIT)
    }

    private suspend fun addXp(delta: Int) {
        settingsStore.update { it.copy(xp = max(0, it.xp + delta)) }
    }

    // ------------------------------------------------------------------ Körper & Tests

    suspend fun addMeasurement(upperArmCm: Double?, chestCm: Double?, waistCm: Double?) {
        if (upperArmCm == null && chestCm == null && waistCm == null) return
        dao.upsertMeasurement(MeasurementEntity(epochDay = today().toEpochDay(), upperArmCm = upperArmCm, chestCm = chestCm, waistCm = waistCm))
    }

    suspend fun deleteMeasurement(id: Long) = dao.deleteMeasurement(id)

    suspend fun addFitnessTest(pushups: Int, plankSec: Int, squats: Int) {
        dao.insertFitnessTest(FitnessTestEntity(epochDay = today().toEpochDay(), pushups = pushups, plankSec = plankSec, squats = squats))
        settingsStore.update { it.copy(maxPushups = max(it.maxPushups, pushups), maxPlankSec = max(it.maxPlankSec, plankSec), maxSquats = max(it.maxSquats, squats)) }
    }

    // ------------------------------------------------------------------ Deload

    suspend fun startDeload() {
        val monday = today().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toEpochDay()
        settingsStore.update { it.copy(deloadWeekStartDay = monday, deloadCountFromDay = monday + 7) }
    }

    suspend fun snoozeDeload() {
        settingsStore.update { it.copy(deloadSnoozedUntilDay = today().toEpochDay() + 7) }
    }

    // ------------------------------------------------------------------ Backup

    suspend fun exportBackup(): String = Backup.encode(snapshot(), now())

    /** Ersetzt alle Daten durch das Backup. Wirft IllegalArgumentException bei ungültiger Datei. */
    suspend fun importBackup(json: String) {
        val b = Backup.decode(json)
        transaction {
            dao.clearTemplates(); dao.clearTemplateExercises(); dao.clearSessions(); dao.clearSetLogs()
            dao.clearDayRecords(); dao.clearHabits(); dao.clearMeasurements(); dao.clearFitnessTests(); dao.clearAchievements()
            dao.insertTemplates(b.templates)
            dao.insertTemplateExercises(b.templateExercises)
            dao.insertSessions(b.sessions)
            dao.insertSetLogs(b.setLogs)
            dao.upsertDayRecords(b.dayRecords)
            dao.insertHabits(b.habits)
            dao.insertMeasurements(b.measurements)
            dao.insertFitnessTests(b.tests)
            dao.insertAchievements(b.achievements)
        }
        settingsStore.update { b.settings }
        hooks.remindersChanged()
        hooks.dataChanged()
    }
}
