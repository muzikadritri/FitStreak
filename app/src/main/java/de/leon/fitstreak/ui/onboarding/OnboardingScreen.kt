package de.leon.fitstreak.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.leon.fitstreak.core.model.Equipment
import de.leon.fitstreak.core.model.Goal
import de.leon.fitstreak.core.plan.PlanGenerator
import de.leon.fitstreak.core.progression.ProgressionEngine
import de.leon.fitstreak.data.OnboardingInput
import de.leon.fitstreak.ui.LocalPlatform
import de.leon.fitstreak.ui.components.ChoiceChip
import de.leon.fitstreak.ui.components.FitIcon
import de.leon.fitstreak.ui.components.FitIconView
import de.leon.fitstreak.ui.components.NumberField
import de.leon.fitstreak.ui.components.PrimaryButton
import de.leon.fitstreak.ui.components.ProgressBar
import de.leon.fitstreak.ui.components.TimeStepper
import de.leon.fitstreak.ui.components.WindowInsetsCompat
import de.leon.fitstreak.ui.components.toDoubleDe
import de.leon.fitstreak.ui.theme.Brand
import de.leon.fitstreak.ui.theme.fit

private const val STEPS = 6
val weekdayNames = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")

/** Auswahlkarte mit Titel, Untertitel und Häkchen. */
@Composable
fun SelectCard(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier.fillMaxWidth().clip(shape)
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else fit.card)
            .border(2.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = fit.muted)
        }
        Box(
            Modifier.size(26.dp).clip(CircleShape).background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                .border(2.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape),
            contentAlignment = Alignment.Center,
        ) { if (selected) FitIconView(FitIcon.CHECK, tint = Color.White, size = 14.dp) }
    }
}

/** Eingabe der vorhandenen Hantelgewichte als Chips. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DumbbellEditor(weights: List<Double>, onChange: (List<Double>) -> Unit, modifier: Modifier = Modifier) {
    var input by rememberSaveable { mutableStateOf("") }
    Column(modifier) {
        Text("Welche Gewichte hast du? (kg pro Hantel)", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (w in weights.sorted()) {
                ChoiceChip("${ProgressionEngine.fmtKg(w)} kg  ✕", selected = true, onClick = { onChange(weights - w) })
            }
            if (weights.isEmpty()) Text("Noch kein Gewicht eingetragen.", color = fit.muted)
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            NumberField(input, { input = it }, "Gewicht", Modifier.weight(1f), suffix = "kg", decimal = true)
            Spacer(Modifier.width(10.dp))
            val value = input.toDoubleDe()
            TextButton(
                enabled = value != null && value > 0 && value <= 100 && value !in weights,
                onClick = { value?.let { onChange((weights + it).sorted()) }; input = "" },
            ) { Text("Hinzufügen") }
        }
    }
}

@Composable
fun WeekdayPicker(selected: List<Int>, onChange: (List<Int>) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        weekdayNames.forEachIndexed { i, name ->
            val day = i + 1
            val on = day in selected
            Box(
                Modifier.weight(1f).height(46.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (on) MaterialTheme.colorScheme.primary else fit.cardMuted)
                    .clickable { onChange(if (on) selected - day else (selected + day).sorted()) },
                contentAlignment = Alignment.Center,
            ) {
                Text(name, color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(onFinish: (OnboardingInput) -> Unit) {
    val platform = LocalPlatform.current
    var step by rememberSaveable { mutableIntStateOf(0) }
    var name by rememberSaveable { mutableStateOf("") }
    // Vorbelegt mit einem sinnvollen Start: 4 Tage, Sixpack + Oberkörper, eine 12-kg-Kurzhantel
    var goals by remember { mutableStateOf(setOf(Goal.SIXPACK, Goal.OBERKOERPER)) }
    var equipment by remember { mutableStateOf(setOf(Equipment.KURZHANTEL)) }
    var weights by remember { mutableStateOf(listOf(12.0)) }
    var days by rememberSaveable { mutableIntStateOf(4) }
    var weekdays by remember { mutableStateOf(PlanGenerator.defaultWeekdays(4)) }
    var pushups by rememberSaveable { mutableStateOf("8") }
    var plank by rememberSaveable { mutableStateOf("60") }
    var squats by rememberSaveable { mutableStateOf("20") }
    var reminderOn by rememberSaveable { mutableStateOf(true) }
    var reminderMinutes by rememberSaveable { mutableIntStateOf(18 * 60) }

    fun finish() = onFinish(
        OnboardingInput(
            name = name, goals = goals.ifEmpty { setOf(Goal.GANZKOERPER) }, equipment = equipment,
            dumbbellKg = if (Equipment.KURZHANTEL in equipment) weights else emptyList(),
            daysPerWeek = days, weekdays = weekdays,
            maxPushups = pushups.toIntOrNull() ?: 0, maxPlankSec = plank.toIntOrNull() ?: 0, maxSquats = squats.toIntOrNull() ?: 0,
            reminderEnabled = reminderOn, reminderMinutes = reminderMinutes,
        ),
    )
    val requestPermission = platform.rememberNotificationPermissionRequest { finish() }

    val canContinue = when (step) {
        1 -> goals.isNotEmpty()
        2 -> Equipment.KURZHANTEL !in equipment || weights.isNotEmpty()
        3 -> weekdays.size == days
        else -> true
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(WindowInsetsCompat.statusBars()).navigationBarsPadding().imePadding()) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            ProgressBar((step + 1f) / STEPS, Modifier.weight(1f), height = 8.dp)
            Spacer(Modifier.width(12.dp))
            Text("${step + 1}/$STEPS", color = fit.muted, style = MaterialTheme.typography.labelLarge)
        }
        AnimatedContent(targetState = step, modifier = Modifier.weight(1f), label = "step") { s ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                when (s) {
                    0 -> {
                        Spacer(Modifier.height(24.dp))
                        Box(
                            Modifier.size(96.dp).clip(RoundedCornerShape(28.dp)).background(Brush.linearGradient(listOf(Brand.BlueBright, Brand.BlueDeep))).align(Alignment.CenterHorizontally),
                            contentAlignment = Alignment.Center,
                        ) {
                            FitIconView(FitIcon.DUMBBELL, tint = Color.White, size = 60.dp)
                            FitIconView(FitIcon.FLAME, tint = Brand.Red, size = 30.dp, modifier = Modifier.align(Alignment.TopCenter).padding(top = 6.dp))
                        }
                        Spacer(Modifier.height(20.dp))
                        Text("Willkommen bei FitStreak", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Dein Trainingsplan für Muskelaufbau zu Hause – mit animierten Anleitungen, automatischer Steigerung und einer Streak, die dich dranbleiben lässt.",
                            color = fit.muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(28.dp))
                        Text("Wie heißt du?", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = name, onValueChange = { if (it.length <= 24) name = it }, singleLine = true,
                            label = { Text("Dein Name") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                        )
                        Spacer(Modifier.height(10.dp))
                        Text("Alles bleibt auf deinem Handy: kein Konto, kein Internet, keine Werbung.", color = fit.muted, style = MaterialTheme.typography.bodySmall)
                    }
                    1 -> {
                        StepTitle("Was ist dein Ziel?", "Mehrere möglich. Deine Ziel-Muskeln bekommen mehr Sätze.")
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            for (g in Goal.entries) SelectCard(g.label, g.detail, g in goals, { goals = if (g in goals) goals - g else goals + g })
                        }
                    }
                    2 -> {
                        StepTitle("Was hast du zu Hause?", "Der Plan nutzt nur Übungen, für die du die Ausrüstung hast.")
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            SelectCard(Equipment.KOERPERGEWICHT.label, Equipment.KOERPERGEWICHT.hint, true, {})
                            for (e in Equipment.entries.drop(1)) {
                                SelectCard(e.label, e.hint, e in equipment, { equipment = if (e in equipment) equipment - e else equipment + e })
                                if (e == Equipment.KURZHANTEL && e in equipment) {
                                    DumbbellEditor(weights, { weights = it }, Modifier.padding(start = 8.dp, bottom = 6.dp))
                                }
                            }
                        }
                    }
                    3 -> {
                        StepTitle("Wie oft willst du trainieren?", "Für Anfänger sind 3–4 Tage ideal. Ruhetage gehören zum Plan.")
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            for (d in 3..5) {
                                val sel = d == days
                                Column(
                                    Modifier.weight(1f).clip(RoundedCornerShape(18.dp))
                                        .background(if (sel) MaterialTheme.colorScheme.primary else fit.card)
                                        .border(2.dp, if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
                                        .clickable { days = d; weekdays = PlanGenerator.defaultWeekdays(d) }.padding(vertical = 16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    val c = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                    Text(d.toString(), style = MaterialTheme.typography.displaySmall, color = c)
                                    Text("Tage", color = c)
                                }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            when (days) {
                                3 -> "Ganzkörper A / B / A – nächste Woche B / A / B"
                                4 -> "Oberkörper / Unterkörper / Oberkörper / Unterkörper"
                                else -> "Push / Pull / Beine / Oberkörper / Ganzkörper"
                            },
                            style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(20.dp))
                        Text("An welchen Tagen?", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(8.dp))
                        WeekdayPicker(weekdays, { weekdays = it })
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (weekdays.size == days) "Passt: ${weekdays.size} Trainingstage, ${7 - weekdays.size} Ruhetage."
                            else "Bitte genau $days Tage auswählen (aktuell ${weekdays.size}).",
                            color = if (weekdays.size == days) fit.done else MaterialTheme.colorScheme.error,
                        )
                    }
                    4 -> {
                        StepTitle("Kurzer Einstufungstest", "Damit der Plan zu dir passt. Schätzen ist okay – du kannst den Test jeden Monat wiederholen.")
                        NumberField(pushups, { pushups = it }, "Liegestütze am Stück (max.)", Modifier.fillMaxWidth(), suffix = "Wdh.")
                        Spacer(Modifier.height(12.dp))
                        NumberField(plank, { plank = it }, "Plank halten (max.)", Modifier.fillMaxWidth(), suffix = "Sek.")
                        Spacer(Modifier.height(12.dp))
                        NumberField(squats, { squats = it }, "Kniebeugen am Stück (max.)", Modifier.fillMaxWidth(), suffix = "Wdh.")
                        Spacer(Modifier.height(12.dp))
                        Text("Wichtig: saubere Technik zählt mehr als die Zahl. Hör auf, bevor die Form zerfällt.", color = fit.muted, style = MaterialTheme.typography.bodySmall)
                    }
                    else -> {
                        StepTitle("Wann sollen wir dich erinnern?", "Eine Erinnerung am Tag – passend zum Workout. An Ruhetagen gibt's einen Erholungs-Tipp.")
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Tägliche Erinnerung", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            Switch(checked = reminderOn, onCheckedChange = { reminderOn = it })
                        }
                        Spacer(Modifier.height(16.dp))
                        if (reminderOn) {
                            TimeStepper(reminderMinutes, { reminderMinutes = it }, Modifier.fillMaxWidth())
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "Um 21:00 Uhr meldet sich zusätzlich der „Streak-Retter“, falls du an einem Trainingstag noch nicht trainiert hast. Beides kannst du in den Einstellungen ändern.",
                                color = fit.muted, style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
        Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (step > 0) {
                PrimaryButton("Zurück", { step-- }, Modifier.weight(0.6f), color = fit.cardMuted, contentColor = MaterialTheme.colorScheme.onSurface)
            }
            PrimaryButton(
                if (step == STEPS - 1) "Plan erstellen" else "Weiter",
                onClick = {
                    if (step < STEPS - 1) step++
                    else if (reminderOn) requestPermission() else finish()
                },
                modifier = Modifier.weight(1f), enabled = canContinue,
            )
        }
    }
}

@Composable
private fun StepTitle(title: String, subtitle: String) {
    Spacer(Modifier.height(8.dp))
    Text(title, style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(6.dp))
    Text(subtitle, color = fit.muted)
    Spacer(Modifier.height(18.dp))
}
