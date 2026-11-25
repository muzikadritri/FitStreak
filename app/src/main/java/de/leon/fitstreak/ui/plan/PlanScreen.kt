package de.leon.fitstreak.ui.plan

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import de.leon.fitstreak.core.exercises.ExerciseLibrary
import de.leon.fitstreak.core.model.Exercise
import de.leon.fitstreak.core.model.LoadType
import de.leon.fitstreak.core.model.Muscle
import de.leon.fitstreak.core.model.Tempo
import de.leon.fitstreak.core.plan.PlanGenerator
import de.leon.fitstreak.core.progression.ProgressionEngine
import de.leon.fitstreak.data.TemplateWithExercises
import de.leon.fitstreak.data.db.TemplateExerciseEntity
import de.leon.fitstreak.data.exercise
import de.leon.fitstreak.data.toPlan
import de.leon.fitstreak.ui.AppViewModel
import de.leon.fitstreak.ui.components.ChoiceChip
import de.leon.fitstreak.ui.components.FigureBox
import de.leon.fitstreak.ui.components.FitCard
import de.leon.fitstreak.ui.components.FitIcon
import de.leon.fitstreak.ui.components.FitIconView
import de.leon.fitstreak.ui.components.Pill
import de.leon.fitstreak.ui.components.PrimaryButton
import de.leon.fitstreak.ui.components.ScreenScaffold
import de.leon.fitstreak.ui.components.StaticExerciseFigure
import de.leon.fitstreak.ui.components.Stepper
import de.leon.fitstreak.ui.library.ExerciseRow
import de.leon.fitstreak.ui.theme.Brand
import de.leon.fitstreak.ui.theme.fit
import de.leon.fitstreak.ui.workout.formatTarget
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/** Wochenplan: Workouts der Rotation + eigene Workouts. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanScreen(vm: AppViewModel, onBack: () -> Unit, onStart: (Long) -> Unit, onEdit: (Long) -> Unit) {
    val data by vm.data.collectAsState()
    val today by vm.today.collectAsState()
    var createDialog by remember { mutableStateOf(false) }
    val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val week = remember(data, today) { (0L..6L).map { monday.plusDays(it) }.map { it to data.workoutFor(it) } }

    ScreenScaffold("Wochenplan", onBack) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                FitCard {
                    Text("Diese Woche", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(6.dp))
                    for ((date, w) in week) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                date.format(DateTimeFormatter.ofPattern("EEE", Locale.GERMAN)).take(2),
                                style = MaterialTheme.typography.titleSmall, modifier = Modifier.width(36.dp),
                                color = if (date == today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            )
                            Text(w?.template?.name ?: "Ruhetag", color = if (w == null) fit.muted else MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                            if (data.trainedOn(date)) Pill("erledigt", fit.done) else if (date == today) Pill("heute", MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            item { Text("Workouts im Plan", style = MaterialTheme.typography.titleLarge) }
            items(data.rotation, key = { it.id }) { w -> WorkoutPlanCard(w, { onStart(w.id) }, { onEdit(w.id) }) }
            item { Text("Eigene Workouts", style = MaterialTheme.typography.titleLarge) }
            if (data.customWorkouts.isEmpty()) item { Text("Stell dir ein eigenes Workout aus der Übungsbibliothek zusammen.", color = fit.muted) }
            items(data.customWorkouts, key = { it.id }) { w -> WorkoutPlanCard(w, { onStart(w.id) }, { onEdit(w.id) }) }
            item {
                PrimaryButton("Eigenes Workout erstellen", { createDialog = true }, leading = { FitIconView(FitIcon.PLUS, tint = Color.White, size = 18.dp) })
            }
        }
    }

    if (createDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { createDialog = false },
            title = { Text("Neues Workout") },
            text = {
                OutlinedTextField(name, { if (it.length <= 30) name = it }, label = { Text("Name") }, singleLine = true, placeholder = { Text("z. B. Arme extra") })
            },
            confirmButton = {
                TextButton(onClick = {
                    createDialog = false
                    vm.createCustomWorkout(name) { id -> onEdit(id) }
                }) { Text("Erstellen") }
            },
            dismissButton = { TextButton(onClick = { createDialog = false }) { Text("Abbrechen") } },
        )
    }
}

@Composable
private fun WorkoutPlanCard(w: TemplateWithExercises, onStart: () -> Unit, onEdit: () -> Unit) {
    FitCard {
        Text(w.template.name, style = MaterialTheme.typography.titleLarge)
        Text(
            "${w.template.focus} · ${w.exercises.size} Übungen · ca. ${PlanGenerator.estimateMinutes(w.exercises.map { it.toPlan() })} min",
            color = fit.muted, style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(8.dp))
        Text(w.exercises.mapNotNull { it.exercise?.name }.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PrimaryButton("Bearbeiten", onEdit, Modifier.weight(1f), color = fit.cardMuted, contentColor = MaterialTheme.colorScheme.onSurface)
            PrimaryButton("Starten", onStart, Modifier.weight(1f), enabled = w.exercises.isNotEmpty())
        }
    }
}

/** Ein Workout bearbeiten: Übungen tauschen, Vorgaben ändern, sortieren, hinzufügen, entfernen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanEditScreen(vm: AppViewModel, templateId: Long, onBack: () -> Unit, onExercise: (String) -> Unit) {
    val data by vm.data.collectAsState()
    val template = data.template(templateId)
    var editRow by remember { mutableStateOf<TemplateExerciseEntity?>(null) }
    var swapRow by remember { mutableStateOf<TemplateExerciseEntity?>(null) }
    var addDialog by remember { mutableStateOf(false) }
    var renameDialog by remember { mutableStateOf(false) }
    var deleteDialog by remember { mutableStateOf(false) }

    if (template == null) {
        ScreenScaffold("Workout", onBack) { Text("Workout nicht gefunden.", Modifier.padding(it).padding(24.dp)) }
        return
    }
    val equipment = data.settings.equipmentSet()

    ScreenScaffold(
        template.template.name, onBack,
        actions = {
            TextButton(onClick = { renameDialog = true }) { Text("Umbenennen") }
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (template.exercises.isEmpty()) item { Text("Noch keine Übungen. Füge unten welche hinzu.", color = fit.muted) }
            items(template.exercises, key = { it.id }) { row ->
                val e = row.exercise ?: return@items
                val index = template.exercises.indexOf(row)
                FitCard(padding = 12.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FigureBox(Modifier.size(60.dp).clickable { onExercise(e.id) }) {
                            StaticExerciseFigure(e, e.animation.cycleSeconds * 0.3f, Modifier.fillMaxSize().padding(3.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(e.name, style = MaterialTheme.typography.titleMedium)
                            Text(formatTarget(e, row.sets, row.repMin, row.repMax, row.weightKg), color = fit.muted, style = MaterialTheme.typography.bodySmall)
                            val tempo = Tempo.entries.getOrElse(row.tempo) { Tempo.NORMAL }
                            if (tempo != Tempo.NORMAL) Text("Tempo: ${tempo.label}", color = Brand.Orange, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        ChoiceChip("Anpassen", false, { editRow = row })
                        ChoiceChip("Alternative vorschlagen", false, { swapRow = row })
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (index > 0) ChoiceChip("↑ hoch", false, { vm.moveExercise(row.id, -1) })
                        if (index < template.exercises.lastIndex) ChoiceChip("↓ runter", false, { vm.moveExercise(row.id, 1) })
                        Spacer(Modifier.weight(1f))
                        ChoiceChip("Entfernen", false, { vm.removeExercise(row.id) })
                    }
                }
            }
            item { PrimaryButton("Übung hinzufügen", { addDialog = true }, leading = { FitIconView(FitIcon.PLUS, tint = Color.White, size = 18.dp) }) }
            if (!template.template.inRotation) item {
                TextButton(onClick = { deleteDialog = true }) { Text("Workout löschen", color = MaterialTheme.colorScheme.error) }
            }
        }
    }

    editRow?.let { row -> PrescriptionDialog(row, onDismiss = { editRow = null }, onSave = { vm.updatePrescription(it); editRow = null }) }

    swapRow?.let { row ->
        val used = template.exercises.map { it.exerciseId }.toSet()
        val alternatives = remember(row.id) { PlanGenerator.alternativesFor(row.exerciseId, equipment, exclude = used) }
        ExercisePickerDialog(
            title = "Alternative für ${row.exercise?.name ?: ""}",
            subtitle = "Gleiche Muskelgruppe, passend zu deiner Ausrüstung",
            exercises = alternatives, showFilter = false,
            onDismiss = { swapRow = null },
            onPick = { vm.swapExercise(row.id, it.id); swapRow = null },
        )
    }

    if (addDialog) {
        val used = template.exercises.map { it.exerciseId }.toSet()
        val available = remember(used) { ExerciseLibrary.all.filter { it.id !in used && ExerciseLibrary.isAvailable(it, equipment) } }
        ExercisePickerDialog(
            title = "Übung hinzufügen", subtitle = "Nur Übungen, für die du die Ausrüstung hast",
            exercises = available, showFilter = true,
            onDismiss = { addDialog = false },
            onPick = { vm.addExercise(templateId, it.id); addDialog = false },
        )
    }

    if (renameDialog) {
        var name by remember { mutableStateOf(template.template.name) }
        AlertDialog(
            onDismissRequest = { renameDialog = false },
            title = { Text("Workout umbenennen") },
            text = { OutlinedTextField(name, { if (it.length <= 30) name = it }, singleLine = true, label = { Text("Name") }) },
            confirmButton = { TextButton(onClick = { vm.renameWorkout(templateId, name); renameDialog = false }) { Text("Speichern") } },
            dismissButton = { TextButton(onClick = { renameDialog = false }) { Text("Abbrechen") } },
        )
    }
    if (deleteDialog) {
        AlertDialog(
            onDismissRequest = { deleteDialog = false },
            title = { Text("Workout löschen?") },
            text = { Text("Dein Trainingsverlauf bleibt erhalten.") },
            confirmButton = { TextButton(onClick = { deleteDialog = false; vm.deleteWorkout(templateId); onBack() }) { Text("Löschen", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleteDialog = false }) { Text("Abbrechen") } },
        )
    }
}

@Composable
private fun PrescriptionDialog(row: TemplateExerciseEntity, onDismiss: () -> Unit, onSave: (TemplateExerciseEntity) -> Unit) {
    val e = row.exercise ?: return
    var draft by remember(row.id) { mutableStateOf(row) }
    val step = if (e.isTimed) 5 else 1
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(e.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Stepper(draft.sets.toString(), "Sätze", { draft = draft.copy(sets = draft.sets - 1) }, { draft = draft.copy(sets = draft.sets + 1) }, minusEnabled = draft.sets > 1, plusEnabled = draft.sets < 6)
                if (e.isTimed) {
                    Stepper(draft.repMin.toString(), "Sekunden (Ziel)", { draft = draft.copy(repMin = draft.repMin - step) }, { draft = draft.copy(repMin = draft.repMin + step, repMax = maxOf(draft.repMax, draft.repMin + step)) }, minusEnabled = draft.repMin > 5)
                } else {
                    Stepper(draft.repMin.toString(), "Wdh. von", { draft = draft.copy(repMin = draft.repMin - 1) }, { draft = draft.copy(repMin = draft.repMin + 1, repMax = maxOf(draft.repMax, draft.repMin + 2)) }, minusEnabled = draft.repMin > 1)
                    Stepper(draft.repMax.toString(), "Wdh. bis", { draft = draft.copy(repMax = draft.repMax - 1) }, { draft = draft.copy(repMax = draft.repMax + 1) }, minusEnabled = draft.repMax > draft.repMin + 1)
                }
                if (e.load != LoadType.NONE) {
                    val inc = if (e.load == LoadType.MACHINE) 2.5 else 1.0
                    Stepper(ProgressionEngine.fmtKg(draft.weightKg), "kg", { draft = draft.copy(weightKg = (draft.weightKg - inc).coerceAtLeast(0.0)) }, { draft = draft.copy(weightKg = draft.weightKg + inc) }, minusEnabled = draft.weightKg > 0)
                }
                Stepper(draft.restSec.toString(), "Sek. Pause", { draft = draft.copy(restSec = draft.restSec - 15) }, { draft = draft.copy(restSec = draft.restSec + 15) }, minusEnabled = draft.restSec > 15, plusEnabled = draft.restSec < 240)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (t in Tempo.entries) ChoiceChip(if (t == Tempo.NORMAL) "Normal" else if (t == Tempo.LANGSAM) "Langsam" else "Mit Pause", draft.tempo == t.ordinal, { draft = draft.copy(tempo = t.ordinal) })
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(draft) }) { Text("Speichern") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

@Composable
private fun ExercisePickerDialog(
    title: String,
    subtitle: String,
    exercises: List<Exercise>,
    showFilter: Boolean,
    onDismiss: () -> Unit,
    onPick: (Exercise) -> Unit,
) {
    var muscle by remember { mutableStateOf<Muscle?>(null) }
    val shown = if (muscle == null) exercises else exercises.filter { muscle in it.primary }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(16.dp).heightIn(max = 620.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(subtitle, color = fit.muted, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                if (showFilter) {
                    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        item { ChoiceChip("Alle", muscle == null, { muscle = null }) }
                        items(Muscle.entries.toList()) { m -> ChoiceChip(m.label, muscle == m, { muscle = m }) }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                if (shown.isEmpty()) Text("Keine passende Übung mit deiner Ausrüstung gefunden.", color = fit.muted, modifier = Modifier.padding(vertical = 16.dp))
                LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(shown, key = { it.id }) { e -> ExerciseRow(e, { onPick(e) }, animate = false) }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Abbrechen") }
            }
        }
    }
}
