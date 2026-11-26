package de.leon.fitstreak.ui.today

import androidx.compose.animation.animateColorAsState
import de.leon.fitstreak.ui.Art
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import de.leon.fitstreak.core.game.levelFor
import de.leon.fitstreak.core.plan.PlanGenerator
import de.leon.fitstreak.core.streak.StreakEngine
import de.leon.fitstreak.data.AppData
import de.leon.fitstreak.data.Habit
import de.leon.fitstreak.data.TemplateWithExercises
import de.leon.fitstreak.data.WeekDay
import de.leon.fitstreak.data.exercise
import de.leon.fitstreak.data.recoveryTips
import de.leon.fitstreak.data.toPlan
import de.leon.fitstreak.ui.AppViewModel
import de.leon.fitstreak.ui.Haptic
import de.leon.fitstreak.ui.LocalPlatform
import de.leon.fitstreak.ui.components.Banner
import de.leon.fitstreak.ui.components.CheckMark
import de.leon.fitstreak.ui.components.FigureBox
import de.leon.fitstreak.ui.components.FitCard
import de.leon.fitstreak.ui.components.FitIcon
import de.leon.fitstreak.ui.components.FitIconView
import de.leon.fitstreak.ui.components.Meta
import de.leon.fitstreak.ui.components.MiniExerciseFigure
import de.leon.fitstreak.ui.components.PlateMark
import de.leon.fitstreak.ui.components.PrimaryButton
import de.leon.fitstreak.ui.components.ProgressBar
import de.leon.fitstreak.ui.components.RoundIconButton
import de.leon.fitstreak.ui.components.TextAction
import de.leon.fitstreak.ui.components.ThinDivider
import de.leon.fitstreak.ui.components.WeekBarbell
import de.leon.fitstreak.ui.components.WindowInsetsCompat
import de.leon.fitstreak.ui.components.pressable
import de.leon.fitstreak.ui.components.riseIn
import de.leon.fitstreak.ui.theme.FitText
import de.leon.fitstreak.ui.theme.Motion
import de.leon.fitstreak.ui.theme.fit
import de.leon.fitstreak.ui.workout.formatTarget
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFormat = DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMAN)
private val weekdayFormat = DateTimeFormatter.ofPattern("EEEE", Locale.GERMAN)

/** Der Auftritt der Heute-Seite läuft nur einmal pro App-Start, nicht bei jedem Tab-Wechsel. */
private var entrancePlayed = false

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
    val play = remember { !entrancePlayed && !platform.reducedMotion() }.also { entrancePlayed = true }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(Modifier.padding(WindowInsetsCompat.statusBars()).riseIn(0, play), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text(today.format(dateFormat), color = fit.muted, style = MaterialTheme.typography.bodyMedium)
                    Text("Heute", style = MaterialTheme.typography.headlineMedium)
                }
                Meta("Level ${level.number} · ${level.level.title}", Modifier.padding(bottom = 8.dp))
            }
        }

        item {
            StreakBlock(
                streak = streak.display,
                best = maxOf(data.settings.streakBest, streak.display),
                shields = streak.state.shields,
                pending = streak.todayPending,
                week = data.weekBar(today),
                modifier = Modifier.riseIn(1, play),
            )
        }

        if (data.settings.shieldNoticeDays.isNotEmpty()) item {
            val days = data.settings.shieldNoticeDays.map { LocalDate.ofEpochDay(it).format(weekdayFormat) }
            Banner(
                FitIcon.SHIELD, "Streak-Schutz eingesetzt",
                "Am ${days.joinToString(" und ")} hast du das Training verpasst. Dein Streak-Schutz hat die Streak gerettet.",
                fit.accentInk,
            ) { TextAction("Alles klar", vm::dismissShieldNotice) }
        }

        if (data.deloadSuggested(today)) item {
            Banner(
                FitIcon.MOON, "Zeit für eine Deload-Woche",
                "Du trainierst seit über 6 Wochen regelmäßig. Eine Woche mit weniger Sätzen lässt Muskeln und Gelenke komplett erholen, danach bist du stärker.",
                fit.accentInk,
            ) {
                TextAction("Deload starten", vm::startDeload)
                TextAction("Später", vm::snoozeDeload, color = fit.textSecondary)
            }
        }
        if (data.inDeload(today)) item {
            Banner(FitIcon.MOON, "Deload-Woche läuft", "Diese Woche ein Satz weniger pro Übung", fit.accentInk)
        }

        item {
            Box(Modifier.riseIn(2, play)) {
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
        }

        item { HabitsCard(data, today, vm, Modifier.riseIn(3, play)) }

        item {
            FitCard(Modifier.riseIn(4, play)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FitIconView(FitIcon.BOLT, tint = fit.xpInk, size = 20.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("${data.settings.xp} XP", style = FitText.figureSmall, modifier = Modifier.weight(1f))
                    Meta(level.next?.let { "noch ${it.minXp - level.xp} bis ${it.title}" } ?: "höchstes Level")
                }
                Spacer(Modifier.height(12.dp))
                ProgressBar(level.progress, color = fit.xp, height = 4.dp)
            }
        }
    }
}

/** Der Moment der Seite: die Streak-Zahl groß, darunter die Woche als Hantelstange. */
@Composable
private fun StreakBlock(streak: Int, best: Int, shields: Int, pending: Boolean, week: List<WeekDay>, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(streak.toString(), style = FitText.hero, color = fit.text)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f).padding(bottom = 16.dp)) {
                FitIconView(FitIcon.FLAME, tint = if (streak > 0) fit.streak else fit.muted, size = 24.dp)
                Spacer(Modifier.height(4.dp))
                Text(if (streak == 1) "Tag Streak" else "Tage Streak", style = MaterialTheme.typography.titleMedium, color = fit.textSecondary)
            }
            Column(Modifier.padding(bottom = 16.dp), horizontalAlignment = Alignment.End) {
                Meta("Rekord $best")
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Meta("Schutz")
                    Spacer(Modifier.width(8.dp))
                    repeat(StreakEngine.MAX_SHIELDS) { i ->
                        if (i > 0) Spacer(Modifier.width(4.dp))
                        PlateMark(if (i < shields) fit.accent else fit.muted, filled = i < shields, size = 12.dp)
                    }
                }
            }
        }
        Text(
            when {
                streak == 0 -> "Mit dem ersten Training startet deine Streak"
                pending -> "Trainiere heute, um die Streak zu halten"
                else -> "Für heute gesichert"
            },
            style = MaterialTheme.typography.bodyMedium, color = fit.textSecondary,
        )
        Spacer(Modifier.height(20.dp))
        WeekBarbell(week)
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
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(workout.template.name, style = MaterialTheme.typography.headlineSmall)
                Text(workout.template.focus, color = fit.textSecondary, style = MaterialTheme.typography.bodyMedium)
            }
            if (done) {
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    PlateMark(fit.done, size = 12.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Erledigt", style = MaterialTheme.typography.labelMedium, color = fit.textSecondary)
                }
            } else {
                Meta("ca. ${PlanGenerator.estimateMinutes(workout.exercises.map { it.toPlan() })} min", Modifier.padding(top = 8.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
        PrimaryButton(
            text = when {
                done -> "Nochmal trainieren"
                resume -> "Training fortsetzen"
                else -> "Training starten"
            },
            onClick = onStart,
            color = if (done) fit.cardMuted else fit.accent,
            contentColor = if (done) fit.text else fit.onAccent,
        )
        Spacer(Modifier.height(8.dp))
        workout.exercises.forEachIndexed { i, row ->
            val e = row.exercise ?: return@forEachIndexed
            if (i > 0) ThinDivider(Modifier.padding(start = 60.dp))
            Row(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).pressable { onExercise(e.id) }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FigureBox(Modifier.size(48.dp)) { MiniExerciseFigure(e, Modifier.fillMaxSize().padding(4.dp)) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(e.name, style = MaterialTheme.typography.titleSmall)
                    Meta(formatTarget(e, row.sets, row.repMin, row.repMax, row.weightKg))
                }
                FitIconView(FitIcon.CHEVRON, tint = fit.muted, size = 16.dp)
            }
        }
        Spacer(Modifier.height(4.dp))
        TextAction("Wochenplan ansehen", onOpenPlan)
    }
}

@Composable
private fun RestDayCard(data: AppData, today: LocalDate, trainedToday: Boolean, onOpenPlan: () -> Unit) {
    val next = remember(data, today) {
        (1L..7L).map { today.plusDays(it) }.firstNotNullOfOrNull { d -> data.workoutFor(d)?.let { d to it } }
    }
    FitCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (trainedToday) "Extra-Training erledigt" else "Ruhetag", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            FitIconView(FitIcon.MOON, tint = fit.muted, size = 20.dp)
        }
        Spacer(Modifier.height(8.dp))
        Text(recoveryTips[(today.dayOfYear) % recoveryTips.size], style = MaterialTheme.typography.bodyLarge, color = fit.textSecondary)
        if (!trainedToday) {
            Spacer(Modifier.height(8.dp))
            Spacer(Modifier.height(4.dp))
            LocalPlatform.current.ArtImage(Art.REST, Modifier.fillMaxWidth().height(148.dp).clip(MaterialTheme.shapes.medium))
        }
        Spacer(Modifier.height(12.dp))
        ThinDivider()
        Spacer(Modifier.height(12.dp))
        Meta("Ruhetage zählen für deine Streak")
        if (next != null) {
            val (date, w) = next
            Spacer(Modifier.height(4.dp))
            Text("Nächstes Training: ${date.format(weekdayFormat)}, ${w.template.name}", style = MaterialTheme.typography.titleSmall)
        }
        Spacer(Modifier.height(4.dp))
        TextAction("Trotzdem trainieren / Plan ansehen", onOpenPlan)
    }
}

@Composable
private fun HabitsCard(data: AppData, today: LocalDate, vm: AppViewModel, modifier: Modifier = Modifier) {
    val platform = LocalPlatform.current
    val h = data.habit(today)
    val goal = data.settings.waterGoal
    val doneCount = listOf(h.protein, h.enoughFood, h.noSugar, h.sleep, h.water >= goal).count { it }
    FitCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Gewohnheiten", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Meta("$doneCount/5", color = if (doneCount == 5) fit.done else fit.muted)
        }
        Text("Kein Kalorienzählen, nur Routinen · 5 XP je Haken", color = fit.muted, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        HabitRow(FitIcon.EGG, "Eiweiß bei jeder Mahlzeit", h.protein) { platform.haptic(Haptic.TICK); vm.toggleHabit(Habit.PROTEIN) }
        HabitRow(FitIcon.PLATE, "Genug gegessen", h.enoughFood) { platform.haptic(Haptic.TICK); vm.toggleHabit(Habit.ENOUGH_FOOD) }
        HabitRow(FitIcon.NO_SUGAR, "Kein Zuckergetränk", h.noSugar) { platform.haptic(Haptic.TICK); vm.toggleHabit(Habit.NO_SUGAR) }
        HabitRow(FitIcon.MOON, "8 Stunden geschlafen", h.sleep) { platform.haptic(Haptic.TICK); vm.toggleHabit(Habit.SLEEP) }
        Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(32.dp)) { FitIconView(FitIcon.DROP, tint = fit.muted, size = 20.dp) }
            Column(Modifier.weight(1f)) {
                Text("Wasser", style = MaterialTheme.typography.bodyLarge)
                Meta("${h.water}/$goal Gläser", color = if (h.water >= goal) fit.done else fit.muted)
            }
            RoundIconButton(FitIcon.MINUS, { vm.setWater(h.water - 1) }, size = 40.dp, enabled = h.water > 0)
            Spacer(Modifier.width(8.dp))
            RoundIconButton(FitIcon.PLUS, { platform.haptic(Haptic.TICK); vm.setWater(h.water + 1) }, size = 40.dp)
        }
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(goal) { i ->
                val c by animateColorAsState(if (i < h.water) fit.accent else fit.cardMuted, Motion.fast(), label = "water")
                Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(50)).background(c))
            }
        }
    }
}

@Composable
private fun HabitRow(icon: FitIcon, text: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(48.dp).clip(MaterialTheme.shapes.small).pressable(onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(32.dp)) { FitIconView(icon, tint = fit.muted, size = 20.dp) }
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        CheckMark(checked)
    }
}
