package de.leon.fitstreak.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.leon.fitstreak.data.AppData
import de.leon.fitstreak.data.FitRepository
import de.leon.fitstreak.data.Habit
import de.leon.fitstreak.data.OnboardingInput
import de.leon.fitstreak.data.db.TemplateExerciseEntity
import de.leon.fitstreak.data.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Zentrales ViewModel: Datenstand der App als StateFlow + alle Aktionen außerhalb des laufenden Trainings. */
class AppViewModel(private val repo: FitRepository) : ViewModel() {

    val data: StateFlow<AppData> = repo.data.stateIn(viewModelScope, SharingStarted.Eagerly, AppData())

    private val _today = MutableStateFlow(repo.today())
    val today: StateFlow<LocalDate> = _today.asStateFlow()

    fun nowMillis(): Long = repo.nowMillis()

    /** Beim Öffnen/Zurückkehren: Datum aktualisieren und vergangene Tage für die Streak festschreiben. */
    fun refresh() {
        _today.value = repo.today()
        viewModelScope.launch { repo.refreshStreak() }
    }

    fun completeOnboarding(input: OnboardingInput, onDone: () -> Unit = {}) = viewModelScope.launch {
        repo.completeOnboarding(input)
        _today.value = repo.today()
        onDone()
    }

    fun updateSettings(transform: (Settings) -> Settings) = viewModelScope.launch { repo.updateSettings(transform) }

    fun updateProfileAndPlan(transform: (Settings) -> Settings) = viewModelScope.launch { repo.updateProfileAndPlan(transform) }

    fun regeneratePlan() = viewModelScope.launch { repo.regeneratePlan() }

    fun toggleHabit(habit: Habit) = viewModelScope.launch { repo.toggleHabit(habit) }

    fun setWater(glasses: Int) = viewModelScope.launch { repo.setWater(glasses) }

    fun dismissShieldNotice() = viewModelScope.launch { repo.dismissShieldNotice() }

    fun startDeload() = viewModelScope.launch { repo.startDeload() }

    fun snoozeDeload() = viewModelScope.launch { repo.snoozeDeload() }

    fun addMeasurement(arm: Double?, chest: Double?, waist: Double?) = viewModelScope.launch { repo.addMeasurement(arm, chest, waist) }

    fun deleteMeasurement(id: Long) = viewModelScope.launch { repo.deleteMeasurement(id) }

    fun addFitnessTest(pushups: Int, plankSec: Int, squats: Int) = viewModelScope.launch { repo.addFitnessTest(pushups, plankSec, squats) }

    // --- Plan bearbeiten ---
    fun createCustomWorkout(name: String, onCreated: (Long) -> Unit) = viewModelScope.launch { onCreated(repo.createCustomWorkout(name)) }

    fun renameWorkout(id: Long, name: String) = viewModelScope.launch { repo.renameWorkout(id, name) }

    fun deleteWorkout(id: Long) = viewModelScope.launch { repo.deleteWorkout(id) }

    fun addExercise(templateId: Long, exerciseId: String) = viewModelScope.launch { repo.addExercise(templateId, exerciseId) }

    fun removeExercise(rowId: Long) = viewModelScope.launch { repo.removeExercise(rowId) }

    fun swapExercise(rowId: Long, newExerciseId: String) = viewModelScope.launch { repo.swapExercise(rowId, newExerciseId) }

    fun updatePrescription(row: TemplateExerciseEntity) = viewModelScope.launch { repo.updatePrescription(row) }

    fun moveExercise(rowId: Long, delta: Int) = viewModelScope.launch { repo.moveExercise(rowId, delta) }

    // --- Backup ---
    suspend fun exportBackup(): String = repo.exportBackup()

    fun importBackup(json: String, onResult: (error: String?) -> Unit) = viewModelScope.launch {
        try {
            repo.importBackup(json)
            _today.value = repo.today()
            repo.refreshStreak()
            onResult(null)
        } catch (e: IllegalArgumentException) {
            onResult(e.message ?: "Import fehlgeschlagen.")
        } catch (e: Exception) {
            onResult("Import fehlgeschlagen: ${e.message}")
        }
    }
}
