package de.leon.fitstreak.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.leon.fitstreak.core.game.levelFor
import de.leon.fitstreak.core.plan.PlanGenerator
import de.leon.fitstreak.core.streak.StreakEngine
import de.leon.fitstreak.data.AppData
import de.leon.fitstreak.data.DayState
import de.leon.fitstreak.data.Habit
import de.leon.fitstreak.data.TemplateWithExercises
import de.leon.fitstreak.data.WeekDay
import de.leon.fitstreak.data.recoveryTips
import de.leon.fitstreak.data.exercise
import de.leon.fitstreak.data.toPlan
import de.leon.fitstreak.ui.AppViewModel
import de.leon.fitstreak.ui.Haptic
import de.leon.fitstreak.ui.LocalPlatform
import de.leon.fitstreak.ui.components.Banner
import de.leon.fitstreak.ui.components.FigureBox
import de.leon.fitstreak.ui.components.FitCard
import de.leon.fitstreak.ui.components.FitIcon
import de.leon.fitstreak.ui.components.FitIconView
import de.leon.fitstreak.ui.components.Pill
import de.leon.fitstreak.ui.components.PrimaryButton
import de.leon.fitstreak.ui.components.ProgressBar
import de.leon.fitstreak.ui.components.RoundIconButton
import de.leon.fitstreak.ui.components.StaticExerciseFigure
import de.leon.fitstreak.ui.components.WindowInsetsCompat
import de.leon.fitstreak.ui.theme.Brand
import de.leon.fitstreak.ui.theme.fit
import de.leon.fitstreak.ui.workout.formatTarget
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFormat = DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMAN)
private val dayNames = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")

@Composable
fun TodayScreen(
    vm: AppViewModel,
    onStartWorkout: (Long) -> Unit,
    onOpenPlan: () -> Unit,
    onExercise: (String) -> Unit,
) {
    val data by vm.data.collectAsState()
    val today by vm.today.collectAsState()
    val platform = LocalPlatform.current
    val streak = remember(data, today) { data.streak(today) }
    val workout = remember(data, today) { data.workoutFor(today) }
    val trainedToday = data.trainedOn(today)
    val openSession = data.sessions.firstOrNull { it.finishedAt == null && it.epochDay == today.toEpochDay() }
    val level = levelFor(data.settings.xp)

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(Modifier.padding(WindowInsetsCompat.statusBars()), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(today.format(dateFormat), color = fit.muted, style = MaterialTheme.typography.labelLarge)
                    Text(
                        if (data.settings.name.isBlank()) "Hallo!" else "Hallo, ${data.settings.name}!",
                        style = MaterialTheme.typography.headlineMedium,
                    )
                }
                Pill("Lvl ${level.number} · ${level.level.title}", fit.xp, contentColor = fit.onXp)
            }
        }

        item { StreakCard(streak.display, maxOf(data.settings.streakBest, streak.display), streak.state.shields, streak.todayPending, data.weekBar(today)) }

        if (data.settings.shieldNoticeDays.isNotEmpty()) item {
            val days = data.settings.shieldNoticeDays.map { LocalDate.ofEpochDay(it).format(DateTimeFormatter.ofPattern("EEEE", Locale.GERMAN)) }
            Banner(
                FitIcon.SHIELD, "Streak-Schutz eingesetzt",
                "Am ${days.joinToString(" und ")} hast du das Training verpasst – dein Streak-Schutz hat die Streak gerettet.",
                Brand.Blue,
            ) { TextButton(onClick = vm::dismissShieldNotice) { Text("Alles klar") } }
        }

        if (data.deloadSuggested(today)) item {
            Banner(
                FitIcon.MOON, "Zeit für eine Deload-Woche?",
                "Du trainierst seit über 6 Wochen regelmäßig. Eine Woche mit weniger Sätzen hilft Muskeln und Gelenken, sich komplett zu erholen – danach bist du stärker.",
                Brand.Blue,
            ) {
                TextButton(onClick = vm::startDeload) { Text("Deload starten") }
                TextButton(onClick = vm::snoozeDeload) { Text("Später") }
            }
        }
        if (data.inDeload(today)) item {
            Banner(FitIcon.MOON, "Deload-Woche läuft", "Diese Woche ein Satz weniger pro Übung. Gönn dir die Erholung.", Brand.Blue)
        }

        item {
            if (workout != null) {
                WorkoutCard(
                    workout = workout, done = trainedToday, resume = openSession?.templateId == workout.id,
                    onStart = { platform.haptic(Haptic.TICK); onStartWorkout(workout.id) },
                    onExercise = onExercise, onOpenPlan = onOpenPlan,
                )
            } else {
                RestDayCard(data, today, trainedToday, onOpenPlan)
            }
        }

        item { HabitsCard(data, today, vm) }

        item {
            FitCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FitIconView(FitIcon.BOLT, tint = fit.xp, size = 26.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("${data.settings.xp} XP", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    Text(level.next?.let { "noch ${it.minXp - level.xp} bis „${it.title}“" } ?: "Höchstes Level!", color = fit.muted, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(10.dp))
                ProgressBar(level.progress, color = fit.xp)
            }
        }
    }
}

@Composable
private fun StreakCard(streak: Int, best: Int, shields: Int, pending: Boolean, week: List<WeekDay>) {
    Box(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.extraLarge)
            .background(Brush.linearGradient(listOf(fit.heroStart, fit.heroEnd))).padding(18.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FitIconView(FitIcon.FLAME, tint = if (streak > 0) fit.streak else Color.White.copy(alpha = 0.35f), size = 72.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(streak.toString(), color = fit.onHero, fontSize = 64.sp, fontWeight = FontWeight.Black, lineHeight = 64.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(if (streak == 1) "Tag" else "Tage", color = fit.onHero.copy(alpha = 0.85f), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 10.dp))
                    }
                    Text(
                        when {
                            streak == 0 -> "Starte heute deine Streak!"
                            pending -> "Trainiere heute, um die Streak zu halten."
                            else -> "Streak gesichert – weiter so!"
                        },
                        color = fit.onHero.copy(alpha = 0.9f), style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Rekord", color = fit.onHero.copy(alpha = 0.7f), style = MaterialTheme.typography.labelMedium)
                    Text("$best", color = fit.onHero, style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        repeat(StreakEngine.MAX_SHIELDS) { i ->
                            FitIconView(FitIcon.SHIELD, tint = if (i < shields) fit.xp else Color.White.copy(alpha = 0.25f), size = 20.dp)
                        }
                    }
                    Text("Schutz", color = fit.onHero.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                week.forEachIndexed { i, day -> WeekDot(dayNames[i], day) }
            }
        }
    }
}

@Composable
private fun WeekDot(label: String, day: WeekDay) {
    val (bg, content) = when (day.state) {
        DayState.TRAINED -> fit.done to FitIcon.CHECK
        DayState.REST -> Color.White.copy(alpha = 0.22f) to FitIcon.MOON
        DayState.REST_AHEAD -> Color.White.copy(alpha = 0.10f) to FitIcon.MOON
        DayState.SHIELDED -> fit.xp to FitIcon.SHIELD
        DayState.MISSED -> fit.streak to FitIcon.MINUS
        DayState.OPEN -> Color.White.copy(alpha = 0.22f) to FitIcon.DUMBBELL
        DayState.PLANNED -> Color.White.copy(alpha = 0.10f) to FitIcon.DUMBBELL
    }
    val iconAlpha = if (day.state == DayState.REST_AHEAD || day.state == DayState.PLANNED) 0.45f else 1f
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(bg)
                .let { if (day.isToday) it.border(2.dp, Color.White, CircleShape) else it },
            contentAlignment = Alignment.Center,
        ) {
            FitIconView(content, tint = (if (day.state == DayState.SHIELDED) fit.onXp else Color.White).copy(alpha = iconAlpha), size = 16.dp)
        }
        Spacer(Modifier.height(4.dp))
        Text(label, color = fit.onHero.copy(alpha = if (day.isToday) 1f else 0.7f), style = MaterialTheme.typography.labelSmall, fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun WorkoutCard(
    workout: TemplateWithExercises,
    done: Boolean,
    resume: Boolean,
    onStart: () -> Unit,
    onExercise: (String) -> Unit,
    onOpenPlan: () -> Unit,
) {
    FitCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("HEUTE", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                Text(workout.template.name, style = MaterialTheme.typography.headlineSmall)
                Text(
                    "${workout.template.focus} · ca. ${PlanGenerator.estimateMinutes(workout.exercises.map { it.toPlan() })} min",
                    color = fit.muted, style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (done) Pill("Erledigt", fit.done)
        }
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (row in workout.exercises) {
                val e = row.exercise ?: continue
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onExercise(e.id) }, verticalAlignment = Alignment.CenterVertically) {
                    FigureBox(Modifier.size(48.dp)) { StaticExerciseFigure(e, e.animation.cycleSeconds * 0.3f, Modifier.fillMaxSize().padding(3.dp)) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(e.name, style = MaterialTheme.typography.titleSmall)
                        Text(formatTarget(e, row.sets, row.repMin, row.repMax, row.weightKg), color = fit.muted, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        PrimaryButton(
            text = when {
                done -> "Nochmal trainieren"
                resume -> "Training fortsetzen"
                else -> "Training starten"
            },
            onClick = onStart,
            color = if (done) fit.cardMuted else MaterialTheme.colorScheme.primary,
            contentColor = if (done) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimary,
            leading = { FitIconView(FitIcon.PLAY, tint = if (done) MaterialTheme.colorScheme.onSurface else Color.White, size = 18.dp) },
        )
        TextButton(onClick = onOpenPlan, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Wochenplan ansehen / bearbeiten") }
    }
}

@Composable
private fun RestDayCard(data: AppData, today: LocalDate, trainedToday: Boolean, onOpenPlan: () -> Unit) {
    val next = remember(data, today) {
        (1L..7L).map { today.plusDays(it) }.firstNotNullOfOrNull { d -> data.workoutFor(d)?.let { d to it } }
    }
    FitCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(52.dp).clip(CircleShape).background(fit.done.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                FitIconView(FitIcon.MOON, tint = fit.done, size = 26.dp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("HEUTE", color = fit.done, style = MaterialTheme.typography.labelLarge)
                Text(if (trainedToday) "Extra-Training erledigt" else "Ruhetag", style = MaterialTheme.typography.headlineSmall)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(recoveryTips[(today.dayOfYear) % recoveryTips.size], style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(6.dp))
        Text("Ruhetage zählen für deine Streak.", color = fit.muted, style = MaterialTheme.typography.bodySmall)
        if (next != null) {
            Spacer(Modifier.height(10.dp))
            val (date, w) = next
            Text(
                "Nächstes Training: ${date.format(DateTimeFormatter.ofPattern("EEEE", Locale.GERMAN))} – ${w.template.name}",
                style = MaterialTheme.typography.titleSmall,
            )
        }
        TextButton(onClick = onOpenPlan) { Text("Trotzdem trainieren / Plan ansehen") }
    }
}

@Composable
private fun HabitsCard(data: AppData, today: LocalDate, vm: AppViewModel) {
    val platform = LocalPlatform.current
    val h = data.habit(today)
    val goal = data.settings.waterGoal
    val doneCount = listOf(h.protein, h.enoughFood, h.noSugar, h.sleep, h.water >= goal).count { it }
    FitCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Gewohnheiten", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Text("$doneCount / 5", color = if (doneCount == 5) fit.done else fit.muted, style = MaterialTheme.typography.titleMedium)
        }
        Text("Kein Kalorienzählen – nur gesunde Routinen. +5 XP je Haken.", color = fit.muted, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        HabitRow(FitIcon.EGG, "Eiweiß bei jeder Mahlzeit", h.protein) { platform.haptic(Haptic.TICK); vm.toggleHabit(Habit.PROTEIN) }
        HabitRow(FitIcon.PLATE, "Genug gegessen (Muskeln brauchen Energie)", h.enoughFood) { platform.haptic(Haptic.TICK); vm.toggleHabit(Habit.ENOUGH_FOOD) }
        HabitRow(FitIcon.NO_SUGAR, "Kein Zuckergetränk", h.noSugar) { platform.haptic(Haptic.TICK); vm.toggleHabit(Habit.NO_SUGAR) }
        HabitRow(FitIcon.MOON, "8 Stunden geschlafen", h.sleep) { platform.haptic(Haptic.TICK); vm.toggleHabit(Habit.SLEEP) }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(Brand.BlueBright.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                FitIconView(FitIcon.DROP, tint = Brand.BlueBright, size = 18.dp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Wasser", style = MaterialTheme.typography.titleSmall)
                Text("${h.water} von $goal Gläsern", color = if (h.water >= goal) fit.done else fit.muted, style = MaterialTheme.typography.bodySmall)
            }
            RoundIconButton(FitIcon.MINUS, { vm.setWater(h.water - 1) }, size = 40.dp, enabled = h.water > 0)
            Spacer(Modifier.width(8.dp))
            RoundIconButton(
                FitIcon.PLUS, { platform.haptic(Haptic.TICK); vm.setWater(h.water + 1) }, size = 40.dp,
                background = Brand.BlueBright, tint = Color.White,
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(goal) { i ->
                Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(50)).background(if (i < h.water) Brand.BlueBright else fit.cardMuted))
            }
        }
    }
}

@Composable
private fun HabitRow(icon: FitIcon, text: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onToggle).padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(if (checked) fit.done.copy(alpha = 0.18f) else fit.cardMuted), contentAlignment = Alignment.Center) {
            FitIconView(icon, tint = if (checked) fit.done else fit.muted, size = 18.dp)
        }
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Box(
            Modifier.size(28.dp).clip(CircleShape).background(if (checked) fit.done else Color.Transparent)
                .border(2.dp, if (checked) fit.done else MaterialTheme.colorScheme.outline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) FitIconView(FitIcon.CHECK, tint = Color.White, size = 16.dp)
        }
    }
}
