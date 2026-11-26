package de.leon.fitstreak.ui.progress

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.leon.fitstreak.core.model.LoadType
import de.leon.fitstreak.core.model.Muscle
import de.leon.fitstreak.core.progression.ProgressionEngine
import de.leon.fitstreak.data.AppData
import de.leon.fitstreak.data.DayState
import de.leon.fitstreak.ui.AppViewModel
import de.leon.fitstreak.ui.components.Banner
import de.leon.fitstreak.ui.components.BarChart
import de.leon.fitstreak.ui.components.BodyMap
import de.leon.fitstreak.ui.components.ChoiceChip
import de.leon.fitstreak.ui.components.FitCard
import de.leon.fitstreak.ui.components.FitIcon
import de.leon.fitstreak.ui.components.Heatmap
import de.leon.fitstreak.ui.components.LegendDot
import de.leon.fitstreak.ui.components.LineChart
import de.leon.fitstreak.ui.components.NumberField
import de.leon.fitstreak.ui.components.Pill
import de.leon.fitstreak.ui.components.ThinDivider
import de.leon.fitstreak.ui.components.WindowInsetsCompat
import de.leon.fitstreak.ui.components.dayStateColor
import de.leon.fitstreak.ui.components.toDoubleDe
import de.leon.fitstreak.ui.theme.Brand
import de.leon.fitstreak.ui.theme.fit
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val shortDate = DateTimeFormatter.ofPattern("d.M.")

/** Farbe nach Anzahl Sätze pro Woche. */
fun setsColor(sets: Float): Color? = when {
    sets <= 0f -> null
    sets < 4f -> Brand.Yellow
    sets < 9f -> Brand.Orange
    else -> Brand.Red
}

private fun fmtSets(v: Float): String = if (v == v.toInt().toFloat()) v.toInt().toString() else String.format("%.1f", v).replace('.', ',')

@Composable
fun ProgressScreen(vm: AppViewModel, onExercise: (String) -> Unit) {
    val data by vm.data.collectAsState()
    val today by vm.today.collectAsState()
    var showTest by remember { mutableStateOf(false) }
    var showMeasure by remember { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { Text("Fortschritt", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(WindowInsetsCompat.statusBars())) }
        item { MuscleOverview(data, today, vm.nowMillis()) }
        item { WeeklyCharts(data, today) }
        item { HeatmapCard(data, today) }
        item { RecordsCard(data, onExercise) }
        item { TestCard(data, today) { showTest = true } }
        item { MeasurementsCard(data, onAdd = { showMeasure = true }) }
    }

    if (showTest) {
        var pushups by remember { mutableStateOf("") }
        var plank by remember { mutableStateOf("") }
        var squats by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showTest = false },
            title = { Text("Monatstest") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Jeweils so viele saubere Wiederholungen wie möglich – mit Pause zwischen den Übungen.")
                    NumberField(pushups, { pushups = it }, "Liegestütze (max.)", Modifier.fillMaxWidth())
                    NumberField(plank, { plank = it }, "Plank (Sekunden)", Modifier.fillMaxWidth())
                    NumberField(squats, { squats = it }, "Kniebeugen (max.)", Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(
                    enabled = pushups.isNotBlank() && plank.isNotBlank() && squats.isNotBlank(),
                    onClick = {
                        vm.addFitnessTest(pushups.toIntOrNull() ?: 0, plank.toIntOrNull() ?: 0, squats.toIntOrNull() ?: 0)
                        showTest = false
                    },
                ) { Text("Speichern") }
            },
            dismissButton = { TextButton(onClick = { showTest = false }) { Text("Abbrechen") } },
        )
    }
    if (showMeasure) {
        var arm by remember { mutableStateOf("") }
        var chest by remember { mutableStateOf("") }
        var waist by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showMeasure = false },
            title = { Text("Körpermaße eintragen") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Am besten morgens und immer an derselben Stelle messen. Leere Felder werden übersprungen.")
                    NumberField(arm, { arm = it }, "Oberarm (angespannt)", Modifier.fillMaxWidth(), suffix = "cm", decimal = true)
                    NumberField(chest, { chest = it }, "Brust", Modifier.fillMaxWidth(), suffix = "cm", decimal = true)
                    NumberField(waist, { waist = it }, "Taille", Modifier.fillMaxWidth(), suffix = "cm", decimal = true)
                }
            },
            confirmButton = {
                TextButton(
                    enabled = arm.isNotBlank() || chest.isNotBlank() || waist.isNotBlank(),
                    onClick = {
                        vm.addMeasurement(arm.toDoubleDe(), chest.toDoubleDe(), waist.toDoubleDe())
                        showMeasure = false
                    },
                ) { Text("Speichern") }
            },
            dismissButton = { TextButton(onClick = { showMeasure = false }) { Text("Abbrechen") } },
        )
    }
}

@Composable
private fun MuscleOverview(data: AppData, today: LocalDate, now: Long) {
    val sets = remember(data, today) { data.muscleSetsInWeek(today) }
    val neglected = remember(data, today) { data.neglected(today) }
    FitCard {
        Text("Muskel-Übersicht", style = MaterialTheme.typography.titleLarge)
        Text("Trainierte Muskeln diese Woche – eingefärbt nach Sätzen", color = fit.muted, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(10.dp))
        BodyMap(colorFor = { setsColor(sets[it] ?: 0f) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp))
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            LegendDot(Brand.Yellow, "1–3 Sätze")
            LegendDot(Brand.Orange, "4–8")
            LegendDot(Brand.Red, "9+")
        }
        Spacer(Modifier.height(12.dp))
        for (m in Muscle.entries) {
            val s = sets[m] ?: 0f
            val rec = data.recovery(m, now)
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(m.label, style = MaterialTheme.typography.titleSmall)
                    Text("${fmtSets(s)} Sätze diese Woche", color = fit.muted, style = MaterialTheme.typography.bodySmall)
                }
                when {
                    data.lastTrained(m) == null -> Text("noch nicht trainiert", color = fit.muted, style = MaterialTheme.typography.bodySmall)
                    rec.ready -> Pill("bereit", fit.done)
                    else -> Pill("erholt sich noch – ${rec.hoursLeft} Std.", Brand.Orange)
                }
            }
        }
        if (neglected.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Banner(
                FitIcon.BULB, "Nicht vergessen",
                neglected.joinToString(", ") { m ->
                    val d = data.daysSince(m, today)
                    if (d == null) "${m.label} (noch nie)" else "${m.label} (seit $d Tagen)"
                } + " – diese Muskelgruppen kommen gerade zu kurz. Tausche im Plan eine Übung oder ergänze ein eigenes Workout.",
                Brand.Orange,
            )
        }
    }
}

@Composable
private fun WeeklyCharts(data: AppData, today: LocalDate) {
    var muscle by remember { mutableStateOf(Muscle.BRUST) }
    val sessions = remember(data, today) { data.weeklySessions(today) }
    val muscleWeeks = remember(data, today) { data.weeklyMuscleSets(today) }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        FitCard {
            Text("Trainings pro Woche", style = MaterialTheme.typography.titleLarge)
            Text("Ziel: ${data.settings.daysPerWeek} pro Woche", color = fit.muted, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(10.dp))
            BarChart(sessions.map { it.second.toFloat() }, sessions.map { it.first.format(shortDate) }, color = fit.done)
        }
        FitCard {
            Text("Volumen pro Muskelgruppe", style = MaterialTheme.typography.titleLarge)
            Text("Sätze pro Woche (10–20 pro Muskel gelten als gut für Muskelaufbau)", color = fit.muted, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (m in Muscle.entries) ChoiceChip(m.label, m == muscle, { muscle = m })
            }
            Spacer(Modifier.height(10.dp))
            BarChart(muscleWeeks.map { it.second[muscle] ?: 0f }, muscleWeeks.map { it.first.format(shortDate) }, valueLabel = ::fmtSets)
        }
    }
}

@Composable
private fun HeatmapCard(data: AppData, today: LocalDate) {
    FitCard {
        Text("Letzte 12 Wochen", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(10.dp))
        Heatmap(remember(data, today) { data.heatmap(today) })
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LegendDot(dayStateColor(DayState.TRAINED), "Training")
            LegendDot(dayStateColor(DayState.REST), "Ruhetag")
            LegendDot(dayStateColor(DayState.SHIELDED), "Schutz")
            LegendDot(dayStateColor(DayState.MISSED), "verpasst")
        }
    }
}

@Composable
private fun RecordsCard(data: AppData, onExercise: (String) -> Unit) {
    val all = remember(data) { data.records() }
    var expanded by remember { mutableStateOf(false) }
    val records = if (expanded) all else all.take(6)
    FitCard {
        Text("Persönliche Rekorde", style = MaterialTheme.typography.titleLarge)
        if (records.isEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text("Nach deinem ersten Training erscheinen hier deine Bestwerte.", color = fit.muted)
        }
        records.forEachIndexed { i, r ->
            if (i > 0) ThinDivider()
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onExercise(r.exercise.id) }.padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(r.exercise.name, style = MaterialTheme.typography.titleSmall)
                    Text("${r.sessions}× trainiert", color = fit.muted, style = MaterialTheme.typography.bodySmall)
                }
                Column(horizontalAlignment = Alignment.End) {
                    if (r.exercise.load != LoadType.NONE && r.best.maxWeightKg > 0) {
                        Text("${ProgressionEngine.fmtKg(r.best.maxWeightKg)} kg · max. ${r.best.maxReps} Wdh.", fontWeight = FontWeight.Bold)
                        Text("1RM ≈ ${r.best.best1Rm.roundToInt()} kg", color = fit.muted, style = MaterialTheme.typography.bodySmall)
                    } else {
                        Text("${r.best.maxReps} ${r.exercise.unit}", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        if (all.size > 6) {
            TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Weniger anzeigen" else "Alle ${all.size} anzeigen") }
        }
        if (all.isNotEmpty()) Text("Tippe auf eine Übung für den Verlauf.", color = fit.muted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun TestCard(data: AppData, today: LocalDate, onAdd: () -> Unit) {
    val first = data.tests.firstOrNull()
    val last = data.tests.lastOrNull()
    FitCard {
        Text("Monatstest", style = MaterialTheme.typography.titleLarge)
        if (first == null || last == null) {
            Text("Noch kein Test eingetragen.", color = fit.muted)
        } else {
            Text(
                "Start am ${LocalDate.ofEpochDay(first.epochDay).format(shortDate)} · letzter Test ${LocalDate.ofEpochDay(last.epochDay).format(shortDate)}",
                color = fit.muted, style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(8.dp))
            TestRow("Liegestütze", first.pushups, last.pushups, "")
            TestRow("Plank", first.plankSec, last.plankSec, " s")
            TestRow("Kniebeugen", first.squats, last.squats, "")
            if (data.tests.size > 2) {
                Spacer(Modifier.height(8.dp))
                LineChart(data.tests.map { it.pushups.toFloat() }, height = 100.dp, color = Brand.Red)
                Text("Liegestütze im Verlauf", color = fit.muted, style = MaterialTheme.typography.labelMedium)
            }
            val since = today.toEpochDay() - last.epochDay
            if (since >= 30) {
                Spacer(Modifier.height(8.dp))
                Text("Der letzte Test ist $since Tage her – Zeit für einen neuen Vergleich!", color = Brand.Orange, style = MaterialTheme.typography.titleSmall)
            }
        }
        TextButton(onClick = onAdd) { Text("Test jetzt eintragen") }
    }
}

@Composable
private fun TestRow(label: String, start: Int, now: Int, unit: String) {
    val diff = now - start
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        Text("$start$unit → ", color = fit.muted)
        Text("$now$unit", fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(8.dp))
        if (diff != 0) Pill(if (diff > 0) "+$diff" else "$diff", if (diff > 0) fit.done else fit.muted)
    }
}

@Composable
private fun MeasurementsCard(data: AppData, onAdd: () -> Unit) {
    var which by remember { mutableIntStateOf(0) }
    val labels = listOf("Oberarm", "Brust", "Taille")
    val values = data.measurements.mapNotNull { m ->
        when (which) {
            0 -> m.upperArmCm
            1 -> m.chestCm
            else -> m.waistCm
        }?.let { m.epochDay to it.toFloat() }
    }
    FitCard {
        Text("Körpermaße (optional)", style = MaterialTheme.typography.titleLarge)
        Text("Einmal im Monat messen reicht – Muskeln wachsen langsam, aber stetig.", color = fit.muted, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            labels.forEachIndexed { i, l -> ChoiceChip(l, which == i, { which = i }) }
        }
        Spacer(Modifier.height(10.dp))
        if (values.isEmpty()) {
            Text("Noch keine Messung für ${labels[which]}.", color = fit.muted)
        } else {
            Text("${String.format("%.1f", values.last().second).replace('.', ',')} cm", style = MaterialTheme.typography.headlineSmall)
            if (values.size > 1) {
                val d = values.last().second - values.first().second
                Text("${if (d >= 0) "+" else ""}${String.format("%.1f", d).replace('.', ',')} cm seit dem Start", color = fit.muted, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(8.dp))
            LineChart(
                values.map { it.second }, height = 110.dp,
                startLabel = LocalDate.ofEpochDay(values.first().first).format(shortDate),
                endLabel = LocalDate.ofEpochDay(values.last().first).format(shortDate),
                valueLabel = { String.format("%.1f", it).replace('.', ',') },
            )
        }
        TextButton(onClick = onAdd) { Text("Messung eintragen") }
    }
}
