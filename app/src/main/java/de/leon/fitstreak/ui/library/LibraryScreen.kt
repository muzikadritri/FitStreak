package de.leon.fitstreak.ui.library

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import de.leon.fitstreak.core.exercises.ExerciseLibrary
import de.leon.fitstreak.core.model.Difficulty
import de.leon.fitstreak.core.model.Equipment
import de.leon.fitstreak.core.model.Exercise
import de.leon.fitstreak.core.model.Muscle
import de.leon.fitstreak.ui.components.ChoiceChip
import de.leon.fitstreak.ui.components.FigureBox
import de.leon.fitstreak.ui.components.FitCard
import de.leon.fitstreak.ui.components.FitIcon
import de.leon.fitstreak.ui.components.FitIconView
import de.leon.fitstreak.ui.components.Meta
import de.leon.fitstreak.ui.components.MiniExerciseFigure
import de.leon.fitstreak.ui.components.Pill
import de.leon.fitstreak.ui.components.WindowInsetsCompat
import de.leon.fitstreak.ui.theme.Brand
import de.leon.fitstreak.ui.theme.fit

fun difficultyColor(d: Difficulty): Color = when (d) {
    Difficulty.LEICHT -> Brand.Green
    Difficulty.MITTEL -> Brand.Yellow
    Difficulty.SCHWER -> Brand.Red
}

fun equipmentText(e: Exercise): String =
    if (e.equipment.isEmpty()) "Körpergewicht" else e.equipment.joinToString(" + ") { it.label }

@Composable
fun ExerciseRow(e: Exercise, onClick: () -> Unit, animate: Boolean = true, trailing: (@Composable () -> Unit)? = null) {
    FitCard(onClick = onClick, padding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FigureBox(Modifier.size(76.dp)) { MiniExerciseFigure(e, Modifier.fillMaxSize().padding(4.dp), animate = animate) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(e.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    (e.primary + e.secondary).joinToString(" · ") { it.label },
                    style = MaterialTheme.typography.bodySmall, color = fit.muted, maxLines = 1,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Pill(e.difficulty.label, difficultyColor(e.difficulty))
                    Meta("· " + equipmentText(e))
                }
            }
            trailing?.invoke()
        }
    }
}

/** Suche + Filter nach Muskelgruppe und Ausrüstung. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(onExercise: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var muscle by remember { mutableStateOf<Muscle?>(null) }
    var equipment by remember { mutableStateOf<Equipment?>(null) }

    val results = remember(query, muscle, equipment) {
        ExerciseLibrary.all.filter { e ->
            (query.isBlank() || e.name.contains(query.trim(), ignoreCase = true) || e.primary.any { it.label.contains(query.trim(), ignoreCase = true) }) &&
                (muscle == null || muscle in e.primary || muscle in e.secondary) &&
                when (equipment) {
                    null -> true
                    Equipment.KOERPERGEWICHT -> e.equipment.isEmpty()
                    else -> equipment in e.equipment
                }
        }.sortedWith(compareBy<Exercise> { if (muscle != null && muscle in it.primary) 0 else 1 }.thenBy { it.name })
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(Modifier.padding(WindowInsetsCompat.statusBars())) {
                Text("Übungen", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    placeholder = { Text("Übung oder Muskel suchen") },
                    leadingIcon = { FitIconView(FitIcon.SEARCH, tint = fit.muted, size = 20.dp) },
                    modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
                )
                Spacer(Modifier.height(12.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceChip("Alle Muskeln", muscle == null, { muscle = null })
                    for (m in Muscle.entries) ChoiceChip(m.label, muscle == m, { muscle = if (muscle == m) null else m })
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceChip("Jede Ausrüstung", equipment == null, { equipment = null })
                    for (e in Equipment.entries) ChoiceChip(e.label, equipment == e, { equipment = if (equipment == e) null else e })
                }
                Spacer(Modifier.height(8.dp))
                Meta("${results.size} Übungen")
            }
        }
        items(results, key = { it.id }) { e -> ExerciseRow(e, { onExercise(e.id) }) }
        if (results.isEmpty()) item { Text("Keine Übung gefunden", color = fit.muted, modifier = Modifier.padding(24.dp)) }
    }
}
