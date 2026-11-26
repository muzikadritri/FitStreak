package de.leon.fitstreak.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.leon.fitstreak.core.model.Exercise
import de.leon.fitstreak.core.model.LoadType
import de.leon.fitstreak.core.model.PlanExercise
import de.leon.fitstreak.core.model.Rating
import de.leon.fitstreak.core.progression.ProgressionEngine
import de.leon.fitstreak.data.AppData
import de.leon.fitstreak.data.FitRepository
import de.leon.fitstreak.data.WorkoutSummary
import de.leon.fitstreak.data.db.SetLogEntity
import de.leon.fitstreak.data.exercise
import de.leon.fitstreak.data.toPlan
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max

data class SetUi(val index: Int, val reps: Int, val weightKg: Double, val done: Boolean, /** vom Nutzer angepasst */ val edited: Boolean = false)

data class ExerciseUi(
    val rowId: Long,
    val exercise: Exercise,
    val plan: PlanExercise,
    val targetSets: Int,
    val sets: List<SetUi>,
    val rating: Rating?,
    /** z. B. „3×10 mit 12 kg“ */
    val lastText: String?,
) {
    val doneCount: Int get() = sets.count { it.done }
    val complete: Boolean get() = doneCount >= targetSets
    val nextSet: SetUi? get() = sets.firstOrNull { !it.done }
}

data class RestUi(val endsAt: Long, val totalSec: Int)

enum class WorkoutPhase { LOADING, WARMUP, TRAINING, SUMMARY, GONE }

data class WorkoutUi(
    val phase: WorkoutPhase = WorkoutPhase.LOADING,
    val title: String = "",
    val exercises: List<ExerciseUi> = emptyList(),
    val expanded: Int = 0,
    val rest: RestUi? = null,
    val summary: WorkoutSummary? = null,
    val startedAt: Long = 0,
    val deload: Boolean = false,
) {
    val totalSets: Int get() = exercises.sumOf { it.targetSets }
    val doneSets: Int get() = exercises.sumOf { minOf(it.doneCount, it.targetSets) }
    val allDone: Boolean get() = exercises.isNotEmpty() && exercises.all { it.complete }
}

private data class Local(
    val sessionId: Long? = null,
    val expanded: Int? = null,
    val edits: Map<Pair<String, Int>, Pair<Int, Double>> = emptyMap(),
    val rest: RestUi? = null,
    val summary: WorkoutSummary? = null,
    val gone: Boolean = false,
)

/** Zustand eines laufenden Trainings. Sätze werden sofort gespeichert – nach einem App-Neustart geht es nahtlos weiter. */
class WorkoutViewModel(private val repo: FitRepository, private val templateId: Long) : ViewModel() {

    private val local = MutableStateFlow(Local())

    val ui: StateFlow<WorkoutUi> = combine(repo.data, local) { data, l -> build(data, l) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, WorkoutUi())

    init {
        viewModelScope.launch {
            val id = repo.startOrResumeSession(templateId)
            local.update { it.copy(sessionId = id) }
        }
    }

    fun now(): Long = repo.nowMillis()

    private fun build(data: AppData, l: Local): WorkoutUi {
        if (l.gone) return WorkoutUi(phase = WorkoutPhase.GONE)
        if (l.summary != null) return WorkoutUi(phase = WorkoutPhase.SUMMARY, title = l.summary.templateName, summary = l.summary)
        val sessionId = l.sessionId ?: return WorkoutUi()
        val session = data.sessions.firstOrNull { it.id == sessionId } ?: return WorkoutUi()
        val template = data.template(templateId) ?: return WorkoutUi(phase = WorkoutPhase.GONE)
        val deload = data.inDeload(repo.today())
        val logs = data.setLogs.filter { it.sessionId == sessionId }

        val exercises = template.exercises.mapNotNull { row ->
            val e = row.exercise ?: return@mapNotNull null
            val plan = row.toPlan()
            val target = if (deload) ProgressionEngine.deloadSets(plan.sets) else plan.sets
            val mine = logs.filter { it.exerciseId == e.id }
            val last = data.lastPerformance(e.id, excludeSessionId = sessionId)
            val count = max(target, (mine.maxOfOrNull { it.setIndex } ?: -1) + 1)
            val sets = (0 until count).map { i ->
                val log = mine.firstOrNull { it.setIndex == i }
                val edit = l.edits[e.id to i]
                when {
                    log != null -> SetUi(i, log.reps, log.weightKg, true)
                    edit != null -> SetUi(i, edit.first, edit.second, done = false, edited = true)
                    else -> SetUi(i, defaultReps(e, plan, last, i), plan.weightKg, false)
                }
            }
            ExerciseUi(
                rowId = row.id, exercise = e, plan = plan, targetSets = target, sets = sets,
                rating = mine.firstOrNull { it.rating > 0 }?.let { Rating.entries.getOrNull(it.rating - 1) },
                lastText = lastText(e, last),
            )
        }
        val firstOpen = exercises.indexOfFirst { !it.complete }.let { if (it < 0) exercises.lastIndex else it }
        val warmup = !session.warmupDone && logs.isEmpty()
        return WorkoutUi(
            phase = if (warmup) WorkoutPhase.WARMUP else WorkoutPhase.TRAINING,
            title = template.template.name,
            exercises = exercises,
            expanded = (l.expanded ?: firstOpen).coerceIn(0, max(0, exercises.lastIndex)),
            rest = l.rest,
            startedAt = session.startedAt,
            deload = deload,
        )
    }

    /** Vorausgefüllt mit dem letzten Mal (bei gleichem Gewicht), sonst mit dem unteren Ende der Vorgabe. */
    private fun defaultReps(e: Exercise, plan: PlanExercise, last: List<SetLogEntity>, index: Int): Int {
        if (e.isTimed) return plan.repMin
        val prev = last.getOrNull(index) ?: last.lastOrNull() ?: return plan.repMin
        return if (abs(prev.weightKg - plan.weightKg) < 1e-6) prev.reps.coerceIn(1, 200) else plan.repMin
    }

    private fun lastText(e: Exercise, last: List<SetLogEntity>): String? {
        if (last.isEmpty()) return null
        val reps = last.map { it.reps }
        val unit = if (e.isTimed) " Sek." else ""
        val main = if (reps.toSet().size == 1) "${reps.size}×${reps.first()}$unit" else reps.joinToString(" / ") + unit
        val w = last.maxOf { it.weightKg }
        return if (w > 0) "$main mit ${ProgressionEngine.fmtKg(w)} kg" else main
    }

    private fun current(): WorkoutUi = ui.value

    fun expand(index: Int) = local.update { it.copy(expanded = index) }

    fun finishWarmup() = viewModelScope.launch { local.value.sessionId?.let { repo.setWarmupDone(it) } }

    fun adjustReps(exIndex: Int, delta: Int) {
        val ex = current().exercises.getOrNull(exIndex) ?: return
        val set = ex.nextSet ?: return
        val step = if (ex.exercise.isTimed) 5 * delta else delta
        val reps = (set.reps + step).coerceIn(1, 600)
        local.update { it.copy(edits = it.edits + ((ex.exercise.id to set.index) to (reps to set.weightKg))) }
    }

    fun adjustWeight(exIndex: Int, up: Boolean) {
        val ex = current().exercises.getOrNull(exIndex) ?: return
        val set = ex.nextSet ?: return
        val step = if (ex.exercise.load == LoadType.MACHINE) 2.5 else 1.0
        val w = max(0.0, set.weightKg + if (up) step else -step)
        local.update { it.copy(edits = it.edits + ((ex.exercise.id to set.index) to (set.reps to w))) }
    }

    /** Satz abhaken. [repsOverride] für Zeit-Übungen (tatsächlich gehaltene Sekunden). */
    fun completeSet(exIndex: Int, setIndex: Int, repsOverride: Int? = null) {
        val state = current()
        val ex = state.exercises.getOrNull(exIndex) ?: return
        val set = ex.sets.getOrNull(setIndex) ?: return
        val sessionId = local.value.sessionId ?: return
        val reps = repsOverride ?: set.reps
        val lastOfAll = state.exercises.withIndex().all { (i, e) -> if (i == exIndex) e.doneCount + 1 >= e.targetSets else e.complete }
        val restSec = (ex.plan.restSec + restAdjust).coerceAtLeast(15)
        // Wurde dieser Satz angepasst, gelten die Werte auch als Vorbelegung für die folgenden Sätze
        val carry = if (!set.edited) emptyMap() else ex.sets
            .filter { !it.done && it.index > setIndex && !it.edited }
            .associate { (ex.exercise.id to it.index) to (set.reps to set.weightKg) }
        local.update {
            it.copy(
                rest = if (lastOfAll) null else RestUi(now() + restSec * 1000L, restSec),
                edits = it.edits + carry,
                expanded = exIndex,
            )
        }
        viewModelScope.launch { repo.logSet(sessionId, ex.exercise.id, setIndex, reps, set.weightKg) }
    }

    private var restAdjust: Int = 0

    init {
        viewModelScope.launch { repo.data.collect { restAdjust = it.settings.restAdjustSec } }
    }

    fun undoSet(exIndex: Int, setIndex: Int) {
        val ex = current().exercises.getOrNull(exIndex) ?: return
        val sessionId = local.value.sessionId ?: return
        viewModelScope.launch { repo.removeSet(sessionId, ex.exercise.id, setIndex) }
    }

    fun rate(exIndex: Int, rating: Rating) {
        val state = current()
        val ex = state.exercises.getOrNull(exIndex) ?: return
        val sessionId = local.value.sessionId ?: return
        val next = state.exercises.withIndex().firstOrNull { (i, e) -> i != exIndex && !e.complete }?.index
        local.update { it.copy(expanded = next ?: exIndex) }
        viewModelScope.launch { repo.rateExercise(sessionId, ex.exercise.id, rating) }
    }

    fun skipRest() = local.update { it.copy(rest = null) }

    fun extendRest(seconds: Int) = local.update { l ->
        l.rest?.let { r ->
            val ends = max(now() + 1000, r.endsAt + seconds * 1000L)
            l.copy(rest = RestUi(ends, max(5, r.totalSec + seconds)))
        } ?: l
    }

    fun finish() = viewModelScope.launch {
        val sessionId = local.value.sessionId ?: return@launch
        val summary = repo.finishSession(sessionId)
        local.update { if (summary == null) it.copy(gone = true) else it.copy(summary = summary, rest = null) }
    }

    fun discard() = viewModelScope.launch {
        local.value.sessionId?.let { repo.discardSession(it) }
        local.update { it.copy(gone = true) }
    }

    /** Verlassen ohne zu beenden – die Session bleibt gespeichert und kann heute fortgesetzt werden. */
    fun leave() = local.update { it.copy(gone = true) }
}
