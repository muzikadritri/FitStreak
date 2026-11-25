package de.leon.fitstreak.ui.workout

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.leon.fitstreak.core.exercises.ExerciseLibrary
import de.leon.fitstreak.core.game.Achievement
import de.leon.fitstreak.core.model.Exercise
import de.leon.fitstreak.core.model.LoadType
import de.leon.fitstreak.core.model.Rating
import de.leon.fitstreak.core.model.Tempo
import de.leon.fitstreak.core.progression.ProgressionEngine
import de.leon.fitstreak.data.WorkoutSummary
import de.leon.fitstreak.ui.Haptic
import de.leon.fitstreak.ui.LocalPlatform
import de.leon.fitstreak.ui.Sound
import de.leon.fitstreak.ui.components.AchievementBadge
import de.leon.fitstreak.ui.components.Banner
import de.leon.fitstreak.ui.components.ChoiceChip
import de.leon.fitstreak.ui.components.Confetti
import de.leon.fitstreak.ui.components.ExerciseFigure
import de.leon.fitstreak.ui.components.FigureBox
import de.leon.fitstreak.ui.components.FitCard
import de.leon.fitstreak.ui.components.FitIcon
import de.leon.fitstreak.ui.components.FitIconView
import de.leon.fitstreak.ui.components.MilestoneDialog
import de.leon.fitstreak.ui.components.MiniExerciseFigure
import de.leon.fitstreak.ui.components.Pill
import de.leon.fitstreak.ui.components.PrimaryButton
import de.leon.fitstreak.ui.components.ProgressBar
import de.leon.fitstreak.ui.components.ProgressRing
import de.leon.fitstreak.ui.components.ScreenScaffold
import de.leon.fitstreak.ui.components.StaticExerciseFigure
import de.leon.fitstreak.ui.components.Stepper
import de.leon.fitstreak.ui.theme.Brand
import de.leon.fitstreak.ui.theme.fit
import kotlinx.coroutines.delay
import kotlin.math.ceil
import kotlin.math.roundToInt

fun formatSet(e: Exercise, reps: Int, weightKg: Double): String = when {
    e.isTimed -> "$reps Sek."
    weightKg > 0 -> "$reps × ${ProgressionEngine.fmtKg(weightKg)} kg"
    else -> "$reps Wdh."
}

fun formatTarget(e: Exercise, sets: Int, repMin: Int, repMax: Int, weightKg: Double): String {
    val side = if (e.perSide) " je Seite" else ""
    val main = if (e.isTimed) "$sets × $repMin Sek.$side" else "$sets × $repMin–$repMax Wdh.$side"
    return if (weightKg > 0) "$main · ${ProgressionEngine.fmtKg(weightKg)} kg" else main
}

@Composable
fun WorkoutScreen(vm: WorkoutViewModel, onClose: () -> Unit, onExercise: (String) -> Unit) {
    val ui by vm.ui.collectAsState()
    val platform = LocalPlatform.current
    platform.KeepScreenOn()

    when (ui.phase) {
        WorkoutPhase.LOADING -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        WorkoutPhase.GONE -> LaunchedEffect(Unit) { onClose() }
        WorkoutPhase.WARMUP -> WarmupView(title = ui.title, onDone = vm::finishWarmup, onClose = vm::leave)
        WorkoutPhase.TRAINING -> TrainingView(ui, vm, onExercise)
        WorkoutPhase.SUMMARY -> ui.summary?.let { SummaryView(it, onClose) }
    }
}

// ---------------------------------------------------------------------------------------- Aufwärmen

@Composable
private fun WarmupView(title: String, onDone: () -> Unit, onClose: () -> Unit) {
    val platform = LocalPlatform.current
    val moves = ExerciseLibrary.warmup
    var index by remember { mutableIntStateOf(0) }
    var remaining by remember { mutableIntStateOf(moves.first().seconds) }
    var paused by remember { mutableStateOf(false) }
    val move = moves[index]

    LaunchedEffect(index, paused) {
        if (paused) return@LaunchedEffect
        while (remaining > 0) {
            delay(1000)
            remaining--
            if (remaining in 1..3) platform.sound(Sound.TICK)
        }
        platform.sound(Sound.GO)
        platform.haptic(Haptic.TICK)
        if (index < moves.lastIndex) {
            index++
            remaining = moves[index].seconds
        } else onDone()
    }

    ScreenScaffold(title = "Aufwärmen", onBack = onClose, actions = {
        TextButton(onClick = onDone) { Text("Überspringen") }
    }) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).padding(horizontal = 20.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("$title · ${index + 1} von ${moves.size}", color = fit.muted, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            ProgressBar((index + 1f - remaining.toFloat() / move.seconds) / moves.size, color = Brand.Orange)
            Spacer(Modifier.height(16.dp))
            FigureBox(Modifier.fillMaxWidth().height(240.dp)) {
                WarmupFigure(move.id, paused)
            }
            Spacer(Modifier.height(16.dp))
            Text(move.name, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Text(move.hint, color = fit.muted, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            ProgressRing(
                progress = remaining.toFloat() / move.seconds,
                modifier = Modifier.size(140.dp), color = Brand.Orange, track = fit.cardMuted,
            ) {
                Text(remaining.toString(), fontSize = 46.sp, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PrimaryButton(
                    if (paused) "Weiter" else "Pause", { paused = !paused }, Modifier.weight(1f),
                    color = fit.cardMuted, contentColor = MaterialTheme.colorScheme.onSurface,
                )
                PrimaryButton(if (index < moves.lastIndex) "Nächste" else "Los geht's", {
                    if (index < moves.lastIndex) {
                        index++
                        remaining = moves[index].seconds
                    } else onDone()
                }, Modifier.weight(1f))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun WarmupFigure(moveId: String, paused: Boolean) {
    val move = ExerciseLibrary.warmup.first { it.id == moveId }
    // Aufwärm-Bewegungen nutzen denselben Renderer wie die Übungen
    val pseudo = remember(moveId) {
        ExerciseLibrary.require("kniebeugen").copy(id = move.id, name = move.name, animation = move.animation, primary = emptyList(), secondary = emptyList(), highlight = emptySet())
    }
    ExerciseFigure(pseudo, Modifier.fillMaxSize().padding(8.dp), playing = !paused)
}

// ---------------------------------------------------------------------------------------- Training

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TrainingView(ui: WorkoutUi, vm: WorkoutViewModel, onExercise: (String) -> Unit) {
    val platform = LocalPlatform.current
    var confirmClose by remember { mutableStateOf(false) }
    var timedRun by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val listState = rememberLazyListState()

    LaunchedEffect(ui.expanded) {
        if (ui.exercises.isNotEmpty()) listState.animateScrollToItem(ui.expanded.coerceIn(0, ui.exercises.lastIndex))
    }

    Box(Modifier.fillMaxSize()) {
        ScreenScaffold(
            title = ui.title,
            onBack = null,
            actions = {
                ElapsedTime(ui.startedAt, vm)
                IconButton(onClick = { confirmClose = true }) { Icon(Icons.Filled.Close, contentDescription = "Training verlassen") }
            },
            bottomBar = {
                Surface(color = MaterialTheme.colorScheme.background, shadowElevation = 8.dp) {
                    Column(Modifier.navigationBarsPadding()) {
                        AnimatedVisibility(visible = ui.rest != null) {
                            ui.rest?.let { RestBar(it, vm) }
                        }
                        PrimaryButton(
                            if (ui.allDone) "Training abschließen" else "Training beenden (${ui.doneSets}/${ui.totalSets} Sätze)",
                            onClick = { if (ui.allDone || ui.doneSets == 0) vm.finish() else confirmClose = true },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            color = if (ui.allDone) fit.done else fit.cardMuted,
                            contentColor = if (ui.allDone) Color.White else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            },
        ) { pad ->
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(pad),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "progress") {
                    Column {
                        ProgressBar(if (ui.totalSets == 0) 0f else ui.doneSets.toFloat() / ui.totalSets, color = fit.done)
                        if (ui.deload) {
                            Spacer(Modifier.height(10.dp))
                            Banner(FitIcon.MOON, "Deload-Woche", "Diese Woche ein Satz weniger pro Übung. Die Vorgaben bleiben, dein Körper erholt sich.", Brand.Blue)
                        }
                    }
                }
                itemsIndexed(ui.exercises, key = { _, e -> e.rowId }) { index, ex ->
                    ExerciseCard(
                        ex = ex,
                        expanded = index == ui.expanded,
                        onExpand = { vm.expand(index) },
                        onInfo = { onExercise(ex.exercise.id) },
                        onSetTap = { set ->
                            if (set.done) vm.undoSet(index, set.index)
                            else if (ex.exercise.isTimed) timedRun = index to set.index
                            else {
                                platform.haptic(Haptic.SUCCESS)
                                vm.completeSet(index, set.index)
                            }
                        },
                        onReps = { vm.adjustReps(index, it) },
                        onWeight = { vm.adjustWeight(index, it) },
                        onRate = { vm.rate(index, it) },
                    )
                }
            }
        }

        timedRun?.let { (exIndex, setIndex) ->
            val ex = ui.exercises.getOrNull(exIndex)
            val set = ex?.sets?.getOrNull(setIndex)
            if (ex != null && set != null) {
                TimedSetOverlay(
                    exercise = ex.exercise,
                    targetSec = set.reps,
                    onFinish = { seconds ->
                        timedRun = null
                        if (seconds != null) vm.completeSet(exIndex, setIndex, seconds)
                    },
                )
            } else timedRun = null
        }
    }

    if (confirmClose) {
        AlertDialog(
            onDismissRequest = { confirmClose = false },
            title = { Text("Training verlassen?") },
            text = {
                Text(
                    if (ui.doneSets > 0) "Du hast ${ui.doneSets} von ${ui.totalSets} Sätzen geschafft. Beenden speichert das Training und zählt für deine Streak."
                    else "Du hast noch keinen Satz eingetragen.",
                )
            },
            confirmButton = {
                if (ui.doneSets > 0) TextButton(onClick = { confirmClose = false; vm.finish() }) { Text("Beenden & speichern") }
                else TextButton(onClick = { confirmClose = false; vm.discard() }) { Text("Verlassen") }
            },
            dismissButton = {
                Row {
                    if (ui.doneSets > 0) TextButton(onClick = { confirmClose = false; vm.discard() }) { Text("Verwerfen", color = MaterialTheme.colorScheme.error) }
                    TextButton(onClick = { confirmClose = false }) { Text("Weiter trainieren") }
                }
            },
        )
    }
}

@Composable
private fun ElapsedTime(startedAt: Long, vm: WorkoutViewModel) {
    var now by remember { mutableLongStateOf(vm.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = vm.now()
            delay(1000)
        }
    }
    val sec = ((now - startedAt) / 1000).coerceAtLeast(0)
    Text("${sec / 60}:${(sec % 60).toString().padStart(2, '0')}", color = fit.muted, style = MaterialTheme.typography.titleMedium)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExerciseCard(
    ex: ExerciseUi,
    expanded: Boolean,
    onExpand: () -> Unit,
    onInfo: () -> Unit,
    onSetTap: (SetUi) -> Unit,
    onReps: (Int) -> Unit,
    onWeight: (Boolean) -> Unit,
    onRate: (Rating) -> Unit,
) {
    val e = ex.exercise
    FitCard(onClick = if (expanded) null else onExpand, padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FigureBox(Modifier.size(if (expanded) 104.dp else 60.dp).clickable(onClick = onInfo)) {
                if (expanded) MiniExerciseFigure(e, Modifier.fillMaxSize().padding(4.dp))
                else StaticExerciseFigure(e, 0f, Modifier.fillMaxSize().padding(3.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(e.name, style = MaterialTheme.typography.titleMedium)
                Text(formatTarget(e, ex.targetSets, ex.plan.repMin, ex.plan.repMax, ex.plan.weightKg), color = fit.muted, style = MaterialTheme.typography.bodyMedium)
                if (expanded) {
                    ex.lastText?.let { Text("Letztes Mal: $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary) }
                    if (ex.plan.tempo != Tempo.NORMAL) {
                        Spacer(Modifier.height(4.dp))
                        Pill("Tempo: ${ex.plan.tempo.label}", Brand.Orange)
                    }
                    TextButton(onClick = onInfo, contentPadding = PaddingValues(0.dp)) { Text("So geht's ansehen") }
                }
            }
            if (ex.complete) {
                Box(Modifier.size(28.dp).clip(CircleShape).background(fit.done), contentAlignment = Alignment.Center) {
                    FitIconView(FitIcon.CHECK, tint = Color.White, size = 16.dp)
                }
            } else if (!expanded) {
                Text("${ex.doneCount}/${ex.targetSets}", color = fit.muted, style = MaterialTheme.typography.titleMedium)
            }
        }

        if (expanded) {
            Spacer(Modifier.height(12.dp))
            // Sätze als Chips: antippen = erledigt, nochmal antippen = zurücknehmen
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val next = ex.nextSet
                for (set in ex.sets) {
                    SetChip(e, set, isNext = set.index == next?.index, onClick = { onSetTap(set) })
                }
            }
            val next = ex.nextSet
            if (next != null) {
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Top) {
                    Stepper(
                        value = next.reps.toString(), label = if (e.isTimed) "Sekunden" else if (e.perSide) "Wdh. je Seite" else "Wiederholungen",
                        onMinus = { onReps(-1) }, onPlus = { onReps(1) }, minusEnabled = next.reps > 1,
                    )
                    if (e.load != LoadType.NONE) {
                        Stepper(
                            value = ProgressionEngine.fmtKg(next.weightKg), label = "kg",
                            onMinus = { onWeight(false) }, onPlus = { onWeight(true) }, minusEnabled = next.weightKg > 0,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                PrimaryButton(
                    text = if (e.isTimed) "Satz ${next.index + 1} starten" else "Satz ${next.index + 1} erledigt",
                    onClick = { onSetTap(next) },
                    color = if (e.isTimed) Brand.Orange else MaterialTheme.colorScheme.primary,
                    leading = { FitIconView(if (e.isTimed) FitIcon.TIMER else FitIcon.CHECK, tint = Color.White, size = 20.dp) },
                )
            }
            if (ex.complete) {
                Spacer(Modifier.height(14.dp))
                Text("Wie war's?", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (r in Rating.entries) {
                        val color = when (r) {
                            Rating.LEICHT -> fit.done
                            Rating.PASST -> MaterialTheme.colorScheme.primary
                            Rating.SCHWER -> fit.streak
                        }
                        ChoiceChip(r.label, selected = ex.rating == r, onClick = { onRate(r) }, selectedColor = color, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun SetChip(e: Exercise, set: SetUi, isNext: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    val bg = when {
        set.done -> fit.done
        else -> fit.cardMuted
    }
    val fg = if (set.done) Color.White else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier.clip(shape).background(bg)
            .let { if (isNext) it.border(2.dp, MaterialTheme.colorScheme.primary, shape) else it }
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (set.done) {
            FitIconView(FitIcon.CHECK, tint = Color.White, size = 14.dp)
            Spacer(Modifier.width(6.dp))
        }
        Text(formatSet(e, set.reps, set.weightKg), color = fg, style = MaterialTheme.typography.labelLarge)
    }
}

// ---------------------------------------------------------------------------------------- Pause

@Composable
private fun RestBar(rest: RestUi, vm: WorkoutViewModel) {
    val platform = LocalPlatform.current
    var now by remember { mutableLongStateOf(vm.now()) }
    LaunchedEffect(rest.endsAt) {
        var lastWhole = -1
        while (true) {
            now = vm.now()
            val left = ceil((rest.endsAt - now) / 1000.0).toInt()
            if (left <= 0) break
            if (left != lastWhole && left <= 3) platform.sound(Sound.TICK)
            lastWhole = left
            delay(200)
        }
        // Pause vorbei: Ton + Vibration
        platform.sound(Sound.GO)
        platform.haptic(Haptic.ALERT)
        vm.skipRest()
    }
    val left = ceil((rest.endsAt - now) / 1000.0).toInt().coerceAtLeast(0)
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primaryContainer).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProgressRing(
            progress = (rest.endsAt - now).toFloat() / (rest.totalSec * 1000f),
            modifier = Modifier.size(64.dp), strokeWidth = 7.dp,
            color = MaterialTheme.colorScheme.primary, track = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f),
        ) {
            Text(left.toString(), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Pause", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text("Durchatmen, locker schütteln", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f))
        }
        TextButton(onClick = { vm.extendRest(-15) }) { Text("−15") }
        TextButton(onClick = { vm.extendRest(15) }) { Text("+15") }
        TextButton(onClick = vm::skipRest) { Text("Weiter") }
    }
}

// ---------------------------------------------------------------------------------------- Zeit-Übung

/** Countdown mit 3-2-1-Vorlauf. Bei Übungen „je Seite“ folgt nach kurzem Wechsel die zweite Seite. */
@Composable
private fun TimedSetOverlay(exercise: Exercise, targetSec: Int, onFinish: (Int?) -> Unit) {
    val platform = LocalPlatform.current
    val sides = if (exercise.perSide) 2 else 1
    var side by remember { mutableIntStateOf(1) }
    var lead by remember { mutableIntStateOf(3) }
    var elapsedMs by remember { mutableLongStateOf(0L) }
    var running by remember { mutableStateOf(false) }

    LaunchedEffect(side) {
        lead = if (side == 1) 3 else 5
        running = false
        elapsedMs = 0
        while (lead > 0) {
            platform.sound(Sound.TICK)
            delay(1000)
            lead--
        }
        platform.sound(Sound.GO)
        platform.haptic(Haptic.TICK)
        running = true
        val start = System.currentTimeMillis()
        while (elapsedMs < targetSec * 1000L) {
            delay(100)
            elapsedMs = System.currentTimeMillis() - start
        }
        platform.sound(Sound.DONE)
        platform.haptic(Haptic.SUCCESS)
        if (side < sides) side++ else onFinish(targetSec)
    }

    val left = ceil((targetSec * 1000L - elapsedMs) / 1000.0).toInt().coerceAtLeast(0)
    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha = 0.97f)).clickable(enabled = false) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
            Text(exercise.name, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            if (sides > 1) Text("Seite $side von $sides", color = fit.muted)
            Spacer(Modifier.height(12.dp))
            FigureBox(Modifier.size(170.dp)) { MiniExerciseFigure(exercise, Modifier.fillMaxSize().padding(6.dp)) }
            Spacer(Modifier.height(20.dp))
            ProgressRing(
                progress = if (running) 1f - elapsedMs / (targetSec * 1000f) else 1f,
                modifier = Modifier.size(210.dp), strokeWidth = 16.dp,
                color = if (running) Brand.Orange else fit.muted, track = fit.cardMuted,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (running) left.toString() else lead.toString(), fontSize = 76.sp, fontWeight = FontWeight.Black)
                    Text(if (running) "Sekunden" else if (side == 1) "Mach dich bereit" else "Seite wechseln", color = fit.muted)
                }
            }
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PrimaryButton("Abbrechen", { onFinish(null) }, Modifier.weight(1f), color = fit.cardMuted, contentColor = MaterialTheme.colorScheme.onSurface)
                PrimaryButton("Stopp", {
                    val done = (elapsedMs / 1000).toInt()
                    onFinish(if (running && done >= 3) done else null)
                }, Modifier.weight(1f), enabled = running)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------- Zusammenfassung

@Composable
private fun SummaryView(s: WorkoutSummary, onClose: () -> Unit) {
    val platform = LocalPlatform.current
    var popupIndex by remember { mutableIntStateOf(0) }
    val levelUp = s.levelAfter.level != s.levelBefore.level
    val streakAnim = remember { Animatable(s.streakBefore.toFloat()) }
    val xpAnim = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        platform.sound(Sound.DONE)
        platform.haptic(Haptic.SUCCESS)
        delay(500)
        streakAnim.animateTo(s.streakAfter.toFloat(), tween(900))
    }
    LaunchedEffect(Unit) { xpAnim.animateTo(s.xpEarned.toFloat(), tween(1200)) }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(WindowInsetsPadding()).padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(28.dp))
            Box(Modifier.size(84.dp).clip(CircleShape).background(fit.done), contentAlignment = Alignment.Center) {
                FitIconView(FitIcon.CHECK, tint = Color.White, size = 44.dp)
            }
            Spacer(Modifier.height(14.dp))
            Text("Training geschafft!", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            Text(s.templateName, color = fit.muted, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(18.dp))

            // Streak springt hoch
            Box(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large)
                    .background(Brush.linearGradient(listOf(Brand.Orange, Brand.Red))).padding(18.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FitIconView(FitIcon.FLAME, tint = Color.White, size = 54.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${streakAnim.value.roundToInt()} Tage", color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.Black)
                        Text(
                            if (s.streakAfter > s.streakBefore) "Streak verlängert – stark!" else "Deine Streak läuft weiter.",
                            color = Color.White.copy(alpha = 0.9f),
                        )
                        if (s.shieldsEarned > 0) Text("+ ${s.shieldsEarned} Streak-Schutz verdient", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("${s.durationMin} min", "Dauer", Modifier.weight(1f))
                StatTile(s.setCount.toString(), "Sätze", Modifier.weight(1f))
                StatTile(if (s.volumeKg >= 1) s.volumeKg.roundToInt().toString() else "–", "kg Volumen", Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))

            FitCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FitIconView(FitIcon.BOLT, tint = fit.xp, size = 28.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("+${xpAnim.value.roundToInt()} XP", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                    Pill("Level ${s.levelAfter.number} · ${s.levelAfter.level.title}", fit.xp, contentColor = fit.onXp)
                }
                Spacer(Modifier.height(10.dp))
                ProgressBar(s.levelAfter.progress, color = fit.xp)
                s.levelAfter.next?.let {
                    Spacer(Modifier.height(4.dp))
                    Text("Noch ${it.minXp - s.levelAfter.xp} XP bis „${it.title}“", color = fit.muted, style = MaterialTheme.typography.bodySmall)
                }
            }

            if (s.records.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                FitCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FitIconView(FitIcon.TROPHY, tint = fit.xp, size = 24.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(if (s.records.size == 1) "Neuer Rekord!" else "${s.records.size} neue Rekorde!", style = MaterialTheme.typography.titleMedium)
                    }
                    for (r in s.records) {
                        Spacer(Modifier.height(6.dp))
                        Row {
                            Text(r.exerciseName, Modifier.weight(1f))
                            Text(r.text, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            if (s.suggestions.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                FitCard {
                    Text("Für das nächste Mal", style = MaterialTheme.typography.titleMedium)
                    for (sg in s.suggestions) {
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.Top) {
                            Box(
                                Modifier.padding(top = 6.dp).size(8.dp).clip(CircleShape)
                                    .background(if (sg.isProgress) fit.done else fit.muted),
                            )
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(sg.exerciseName, style = MaterialTheme.typography.titleSmall)
                                Text(sg.message, color = fit.muted, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            PrimaryButton("Fertig", onClose)
            Spacer(Modifier.height(28.dp))
        }
        Confetti(Modifier.fillMaxSize())
    }

    // Meilenstein-Popups: erst Erfolge, dann Level-Aufstieg
    val achievement: Achievement? = s.newAchievements.getOrNull(popupIndex)
    if (achievement != null) {
        MilestoneDialog("Erfolg freigeschaltet!", "${achievement.title}\n${achievement.description}", { popupIndex++ }) {
            AchievementBadge(achievement, unlocked = true, size = 110.dp)
        }
    } else if (levelUp && popupIndex == s.newAchievements.size) {
        MilestoneDialog("Level ${s.levelAfter.number}!", "Du bist jetzt „${s.levelAfter.level.title}“.", { popupIndex++ }) {
            Box(Modifier.size(110.dp).clip(CircleShape).background(fit.xp), contentAlignment = Alignment.Center) {
                Text(s.levelAfter.number.toString(), fontSize = 54.sp, fontWeight = FontWeight.Black, color = fit.onXp)
            }
        }
    }
}

@Composable
private fun WindowInsetsPadding(): PaddingValues = de.leon.fitstreak.ui.components.WindowInsetsCompat.statusBars()

@Composable
fun StatTile(value: String, label: String, modifier: Modifier = Modifier) {
    FitCard(modifier, padding = 12.dp) {
        Text(value, style = MaterialTheme.typography.titleLarge, maxLines = 1)
        Text(label, color = fit.muted, style = MaterialTheme.typography.labelMedium)
    }
}
