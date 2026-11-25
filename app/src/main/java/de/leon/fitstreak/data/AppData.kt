package de.leon.fitstreak.data

import de.leon.fitstreak.core.exercises.ExerciseLibrary
import de.leon.fitstreak.core.game.AchievementStats
import de.leon.fitstreak.core.model.Exercise
import de.leon.fitstreak.core.model.Muscle
import de.leon.fitstreak.core.model.PlanExercise
import de.leon.fitstreak.core.model.Tempo
import de.leon.fitstreak.core.plan.PlanGenerator
import de.leon.fitstreak.core.stats.Best
import de.leon.fitstreak.core.stats.Recovery
import de.leon.fitstreak.core.stats.SetResult
import de.leon.fitstreak.core.stats.Stats
import de.leon.fitstreak.core.streak.DayKind
import de.leon.fitstreak.core.streak.StreakEngine
import de.leon.fitstreak.core.streak.StreakUpdate
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
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

data class TemplateWithExercises(
    val template: WorkoutTemplateEntity,
    val exercises: List<TemplateExerciseEntity>,
) {
    val id: Long get() = template.id
}

fun TemplateExerciseEntity.toPlan() = PlanExercise(
    exerciseId = exerciseId, sets = sets, repMin = repMin, repMax = repMax, weightKg = weightKg,
    tempo = Tempo.entries.getOrElse(tempo) { Tempo.NORMAL }, restSec = restSec, failStreak = failStreak,
)

fun TemplateExerciseEntity.with(p: PlanExercise) = copy(
    exerciseId = p.exerciseId, sets = p.sets, repMin = p.repMin, repMax = p.repMax, weightKg = p.weightKg,
    tempo = p.tempo.ordinal, restSec = p.restSec, failStreak = p.failStreak,
)

fun PlanExercise.toEntity(templateId: Long, position: Int) = TemplateExerciseEntity(
    templateId = templateId, position = position, exerciseId = exerciseId, sets = sets, repMin = repMin,
    repMax = repMax, weightKg = weightKg, tempo = tempo.ordinal, restSec = restSec, failStreak = failStreak,
)

val TemplateExerciseEntity.exercise: Exercise? get() = ExerciseLibrary[exerciseId]

/** Status eines Tages in der Wochenleiste. */
enum class DayState { TRAINED, REST, MISSED, SHIELDED, OPEN, PLANNED, REST_AHEAD }

data class WeekDay(val date: LocalDate, val state: DayState, val isToday: Boolean)

data class ExerciseRecord(
    val exercise: Exercise,
    val best: Best,
    val lastDay: Long,
    val sessions: Int,
)

data class HistoryPoint(val epochDay: Long, val maxReps: Int, val maxWeightKg: Double, val best1Rm: Double, val volumeKg: Double)

/** Kompletter, unveränderlicher Datenstand der App. Alle Auswertungen sind reine Funktionen darauf. */
data class AppData(
    val loaded: Boolean = false,
    val settings: Settings = Settings(),
    val templates: List<TemplateWithExercises> = emptyList(),
    val sessions: List<SessionEntity> = emptyList(),
    val setLogs: List<SetLogEntity> = emptyList(),
    val dayRecords: List<DayRecordEntity> = emptyList(),
    val habits: List<HabitDayEntity> = emptyList(),
    val measurements: List<MeasurementEntity> = emptyList(),
    val tests: List<FitnessTestEntity> = emptyList(),
    val achievements: List<AchievementEntity> = emptyList(),
) {
    val rotation: List<TemplateWithExercises> get() = templates.filter { it.template.inRotation }
    val customWorkouts: List<TemplateWithExercises> get() = templates.filter { !it.template.inRotation }
    val finishedSessions: List<SessionEntity> get() = sessions.filter { it.finishedAt != null }
    val planStart: LocalDate get() = LocalDate.ofEpochDay(settings.planStartDay)

    private val finishedIds: Set<Long> by lazy { finishedSessions.map { it.id }.toSet() }
    private val trainedDays: Set<Long> by lazy { finishedSessions.map { it.epochDay }.toSet() }

    /** Sätze aus abgeschlossenen Trainings. */
    val finishedLogs: List<SetLogEntity> by lazy { setLogs.filter { it.sessionId in finishedIds } }

    fun template(id: Long): TemplateWithExercises? = templates.firstOrNull { it.id == id }

    fun isTrainingDay(date: LocalDate): Boolean = PlanGenerator.isTrainingDay(date, settings.weekdays)

    fun trainedOn(date: LocalDate): Boolean = date.toEpochDay() in trainedDays

    /** Geplantes Workout für [date] oder null (Ruhetag). */
    fun workoutFor(date: LocalDate): TemplateWithExercises? {
        val rot = rotation
        val idx = PlanGenerator.workoutIndexFor(date, planStart, settings.weekdays, rot.size) ?: return null
        return rot.getOrNull(idx)
    }

    /** Streak-Stand für die Anzeige (ohne zu speichern). */
    fun streak(today: LocalDate): StreakUpdate =
        StreakEngine.advance(settings.streakState(), today, planStart, ::isTrainingDay, ::trainedOn)

    fun inDeload(today: LocalDate): Boolean {
        val start = settings.deloadWeekStartDay ?: return false
        return today.toEpochDay() in start..(start + 6)
    }

    fun deloadSuggested(today: LocalDate): Boolean {
        if (!settings.onboardingDone || inDeload(today)) return false
        if (today.toEpochDay() < settings.deloadSnoozedUntilDay) return false
        val from = maxOf(settings.deloadCountFromDay, settings.planStartDay)
        val weeks = ((today.toEpochDay() - from) / 7).toInt()
        val sessionsSince = finishedSessions.count { it.epochDay >= from }
        return Stats.shouldSuggestDeload(weeks, sessionsSince)
    }

    fun habit(day: LocalDate): HabitDayEntity =
        habits.firstOrNull { it.epochDay == day.toEpochDay() } ?: HabitDayEntity(day.toEpochDay())

    // ---------- Wochenleiste & Heatmap ----------

    fun dayState(date: LocalDate, today: LocalDate): DayState {
        val record = dayRecords.firstOrNull { it.epochDay == date.toEpochDay() }
        return when {
            trainedOn(date) -> DayState.TRAINED
            record != null -> when (DayKind.entries.getOrElse(record.kind) { DayKind.REST }) {
                DayKind.TRAINED -> DayState.TRAINED
                DayKind.REST -> DayState.REST
                DayKind.MISSED -> DayState.MISSED
                DayKind.SHIELDED -> DayState.SHIELDED
            }
            date == today -> if (isTrainingDay(date)) DayState.OPEN else DayState.REST
            date.isAfter(today) -> if (isTrainingDay(date)) DayState.PLANNED else DayState.REST_AHEAD
            date.isBefore(planStart) -> DayState.REST_AHEAD
            else -> if (isTrainingDay(date)) DayState.MISSED else DayState.REST
        }
    }

    /** Montag bis Sonntag der Woche von [today]. */
    fun weekBar(today: LocalDate): List<WeekDay> {
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return (0L..6L).map { monday.plusDays(it) }.map { WeekDay(it, dayState(it, today), it == today) }
    }

    /** Letzte [weeks] Wochen (Spalten = Wochen, Montag zuerst). */
    fun heatmap(today: LocalDate, weeks: Int = 12): List<List<WeekDay>> {
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks((weeks - 1).toLong())
        return (0 until weeks).map { w ->
            (0L..6L).map { d ->
                val date = monday.plusWeeks(w.toLong()).plusDays(d)
                WeekDay(date, dayState(date, today), date == today)
            }
        }
    }

    // ---------- Leistung ----------

    /** Sätze des letzten abgeschlossenen Trainings dieser Übung (optional vor einer bestimmten Session). */
    fun lastPerformance(exerciseId: String, excludeSessionId: Long? = null): List<SetLogEntity> {
        val logs = finishedLogs.filter { it.exerciseId == exerciseId && it.sessionId != excludeSessionId }
        val last = logs.maxByOrNull { it.doneAt } ?: return emptyList()
        return logs.filter { it.sessionId == last.sessionId }.sortedBy { it.setIndex }
    }

    fun best(exerciseId: String, excludeSessionId: Long? = null): Best? {
        val logs = finishedLogs.filter { it.exerciseId == exerciseId && it.sessionId != excludeSessionId }
        if (logs.isEmpty()) return null
        return Stats.bestOf(logs.map { SetResult(it.reps, it.weightKg) })
    }

    fun records(): List<ExerciseRecord> =
        finishedLogs.groupBy { it.exerciseId }.mapNotNull { (id, logs) ->
            val e = ExerciseLibrary[id] ?: return@mapNotNull null
            ExerciseRecord(
                exercise = e,
                best = Stats.bestOf(logs.map { SetResult(it.reps, it.weightKg) }),
                lastDay = logs.maxOf { it.epochDay },
                sessions = logs.map { it.sessionId }.toSet().size,
            )
        }.sortedByDescending { it.lastDay }

    fun history(exerciseId: String): List<HistoryPoint> =
        finishedLogs.filter { it.exerciseId == exerciseId }.groupBy { it.sessionId }.values.map { logs ->
            val sets = logs.map { SetResult(it.reps, it.weightKg) }
            val b = Stats.bestOf(sets)
            HistoryPoint(logs.first().epochDay, b.maxReps, b.maxWeightKg, b.best1Rm, Stats.volumeKg(sets))
        }.sortedBy { it.epochDay }

    // ---------- Muskeln ----------

    private fun weekStart(d: LocalDate): LocalDate = d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /** Sätze je Muskelgruppe in der Woche von [day] (primär 1, sekundär 0,5). */
    fun muscleSetsInWeek(day: LocalDate): Map<Muscle, Float> {
        val start = weekStart(day).toEpochDay()
        val counts = finishedLogs.filter { it.epochDay in start..(start + 6) }.groupingBy { it.exerciseId }.eachCount()
        return Stats.muscleSets(counts)
    }

    fun weeklyMuscleSets(today: LocalDate, weeks: Int = 8): List<Pair<LocalDate, Map<Muscle, Float>>> =
        (weeks - 1 downTo 0).map { w ->
            val start = weekStart(today).minusWeeks(w.toLong())
            start to muscleSetsInWeek(start)
        }

    fun weeklySessions(today: LocalDate, weeks: Int = 8): List<Pair<LocalDate, Int>> =
        (weeks - 1 downTo 0).map { w ->
            val start = weekStart(today).minusWeeks(w.toLong())
            val s = start.toEpochDay()
            start to finishedSessions.count { it.epochDay in s..(s + 6) }
        }

    fun lastTrained(muscle: Muscle): Long? =
        finishedLogs.filter { ExerciseLibrary[it.exerciseId]?.primary?.contains(muscle) == true }.maxOfOrNull { it.doneAt }

    fun recovery(muscle: Muscle, nowMillis: Long): Recovery = Stats.recovery(muscle, lastTrained(muscle), nowMillis)

    /** Muskelgruppen, die seit mindestens 10 Tagen nicht (auch nicht nebenbei) trainiert wurden. */
    fun neglected(today: LocalDate): List<Muscle> {
        if (finishedSessions.size < 3) return emptyList()
        val lastByMuscle = mutableMapOf<Muscle, Long>()
        for (log in finishedLogs) {
            val e = ExerciseLibrary[log.exerciseId] ?: continue
            for (m in e.primary + e.secondary) lastByMuscle[m] = maxOf(lastByMuscle[m] ?: Long.MIN_VALUE, log.epochDay)
        }
        return Muscle.entries.filter { m ->
            val last = lastByMuscle[m]
            last == null || today.toEpochDay() - last >= 10
        }
    }

    fun daysSince(muscle: Muscle, today: LocalDate): Long? {
        val last = finishedLogs.filter {
            val e = ExerciseLibrary[it.exerciseId]
            e != null && (muscle in e.primary || muscle in e.secondary)
        }.maxOfOrNull { it.epochDay } ?: return null
        return today.toEpochDay() - last
    }

    // ---------- Erfolge ----------

    fun achievementStats(): AchievementStats {
        val logs = finishedLogs
        val byWeek = finishedSessions.groupBy { weekStart(LocalDate.ofEpochDay(it.epochDay)) }
        val perfectWeeks = byWeek.count { (_, s) -> s.map { it.epochDay }.toSet().size >= settings.daysPerWeek }
        val allMuscleWeeks = byWeek.keys.count { week ->
            val m = muscleSetsInWeek(week)
            Muscle.entries.all { (m[it] ?: 0f) > 0f }
        }
        return AchievementStats(
            workouts = finishedSessions.size,
            bestStreak = settings.streakBest,
            totalPushups = logs.filter { it.exerciseId in ExerciseLibrary.pushupIds }.sumOf { it.reps },
            records = finishedSessions.sumOf { it.prCount },
            perfectWeeks = perfectWeeks,
            allMuscleWeeks = allMuscleWeeks,
            totalVolumeKg = logs.sumOf { it.reps * it.weightKg },
        )
    }

    fun weeksSincePlanStart(today: LocalDate): Int =
        ChronoUnit.WEEKS.between(weekStart(planStart), weekStart(today)).toInt() + 1
}
