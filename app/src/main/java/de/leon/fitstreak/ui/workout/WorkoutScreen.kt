package de.leon.fitstreak.ui.workout

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.runtime.withFrameNanos
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
import de.leon.fitstreak.ui.components.IconTap
import de.leon.fitstreak.ui.components.Meta
import de.leon.fitstreak.ui.components.Pill
import de.leon.fitstreak.ui.components.PlateMark
import de.leon.fitstreak.ui.components.StatStrip
import de.leon.fitstreak.ui.components.TextAction
import de.leon.fitstreak.ui.components.ThinDivider
import de.leon.fitstreak.ui.components.hairline
import de.leon.fitstreak.ui.components.pressable
import de.leon.fitstreak.ui.components.rememberFrameMillis
import de.leon.fitstreak.ui.components.PrimaryButton
import de.leon.fitstreak.ui.components.ProgressBar
import de.leon.fitstreak.ui.components.ProgressRing
import de.leon.fitstreak.ui.components.ScreenScaffold
import de.leon.fitstreak.ui.components.StaticExerciseFigure
import de.leon.fitstreak.ui.components.Stepper
import de.leon.fitstreak.ui.theme.FitText
import de.leon.fitstreak.ui.theme.Motion
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
        WorkoutPhase.LOADING -> Box(Modifier.fillMaxSize().background(fit.bg), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = fit.muted, strokeWidth = 2.dp)
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
    // Der Ring läuft gleichmäßig zwischen den Sekunden-Schritten weiter
    val ring by animateFloatAsState(
        remaining.toFloat() / move.seconds,
        if (paused || remaining == move.seconds) tween(0) else tween(1000, easing = LinearEasing), label = "warmupRing",
    )

    ScreenScaffold(title = "Aufwärmen", onBack = onClose, actions = {
        TextAction("Überspringen", onDone, Modifier.padding(end = 12.dp))
    }) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Meta("$title · ${index + 1} von ${moves.size}")
            Spacer(Modifier.height(8.dp))
            ProgressBar((index + 1f - remaining.toFloat() / move.seconds) / moves.size, height = 4.dp)
            Spacer(Modifier.height(16.dp))
            FigureBox(Modifier.fillMaxWidth().height(240.dp)) { WarmupFigure(move.id, paused) }
            Spacer(Modifier.height(16.dp))
            Text(move.name, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Text(move.hint.trimEnd('.'), color = fit.textSecondary, textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            ProgressRing(progress = { ring }, modifier = Modifier.size(144.dp)) {
                Text(remaining.toString(), style = FitText.figureLarge)
            }
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PrimaryButton(if (paused) "Weiter" else "Pause", { paused = !paused }, Modifier.weight(1f), color = fit.cardMuted, contentColor = fit.text)
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
                IconTap(FitIcon.CLOSE, "Training verlassen", { confirmClose = true })
            },
            bottomBar = {
                Column(Modifier.background(fit.bg).navigationBarsPadding()) {
                    ThinDivider()
                    AnimatedVisibility(
                        visible = ui.rest != null,
                        enter = expandVertically(Motion.base()) + fadeIn(Motion.base()),
                        exit = shrinkVertically(Motion.fast()) + fadeOut(Motion.fast()),
                    ) {
                        ui.rest?.let { RestBar(it, vm) }
                    }
                    PrimaryButton(
                        if (ui.allDone) "Training abschließen" else "Training beenden · ${ui.doneSets}/${ui.totalSets} Sätze",
                        onClick = { if (ui.allDone || ui.doneSets == 0) vm.finish() else confirmClose = true },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        color = if (ui.allDone) fit.done else fit.cardMuted,
                        contentColor = if (ui.allDone) Color.White else fit.text,
                    )
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
                        ProgressBar(if (ui.totalSets == 0) 0f else ui.doneSets.toFloat() / ui.totalSets, color = fit.done, height = 4.dp)
                        if (ui.deload) {
                            Spacer(Modifier.height(12.dp))
                            Banner(FitIcon.MOON, "Deload-Woche", "Diese Woche ein Satz weniger pro Übung. Die Vorgaben bleiben, dein Körper erholt sich.", fit.accentInk)
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
    Text("${sec / 60}:${(sec % 60).toString().padStart(2, '0')}", color = fit.muted, style = FitText.figureSmall)
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
    val figure by animateDpAsState(if (expanded) 96.dp else 56.dp, Motion.base(), label = "figure")
    // Auf- und Zuklappen gleitet: die Karte wächst, statt zu springen
    FitCard(Modifier.animateContentSize(Motion.base()), onClick = if (expanded) null else onExpand, padding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FigureBox(Modifier.size(figure).pressable(onClick = onInfo)) {
                if (expanded) MiniExerciseFigure(e, Modifier.fillMaxSize().padding(4.dp))
                else StaticExerciseFigure(e, 0f, Modifier.fillMaxSize().padding(4.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(e.name, style = MaterialTheme.typography.titleMedium)
                Meta(formatTarget(e, ex.targetSets, ex.plan.repMin, ex.plan.repMax, ex.plan.weightKg))
                if (expanded) {
                    ex.lastText?.let {
                        Spacer(Modifier.height(4.dp))
                        Meta("Letztes Mal: $it", color = fit.textSecondary)
                    }
                    if (ex.plan.tempo != Tempo.NORMAL) {
                        Spacer(Modifier.height(8.dp))
                        Pill("Tempo: ${ex.plan.tempo.label}", fit.cardMuted)
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            if (ex.complete) PlateMark(fit.done, size = 20.dp)
            else if (!expanded) Meta("${ex.doneCount}/${ex.targetSets}")
        }

        if (expanded) {
            Spacer(Modifier.height(4.dp))
            TextAction("So geht's ansehen", onInfo)
            Spacer(Modifier.height(4.dp))
            // Sätze als Chips: antippen = erledigt, nochmal antippen = zurücknehmen
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val next = ex.nextSet
                for (set in ex.sets) {
                    SetChip(e, set, isNext = set.index == next?.index, onClick = { onSetTap(set) })
                }
            }
            val next = ex.nextSet
            if (next != null) {
                Spacer(Modifier.height(16.dp))
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
                Spacer(Modifier.height(16.dp))
                PrimaryButton(
                    text = if (e.isTimed) "Satz ${next.index + 1} starten" else "Satz ${next.index + 1} erledigt",
                    onClick = { onSetTap(next) },
                    leading = { FitIconView(if (e.isTimed) FitIcon.TIMER else FitIcon.CHECK, size = 20.dp) },
                )
            }
            if (ex.complete) {
                Spacer(Modifier.height(16.dp))
                Text("Wie war's?", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (r in Rating.entries) {
                        ChoiceChip(r.label, selected = ex.rating == r, onClick = { onRate(r) }, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun SetChip(e: Exercise, set: SetUi, isNext: Boolean, onClick: () -> Unit) {
    val shape = MaterialTheme.shapes.small
    val border by animateColorAsState(if (isNext) fit.accent else fit.hairline, Motion.fast(), label = "setBorder")
    val fg by animateColorAsState(if (set.done) fit.textSecondary else fit.text, Motion.fast(), label = "setText")
    Row(
        Modifier.pressable(scale = 0.96f, onClick = onClick).clip(shape).background(fit.cardMuted)
            .border(if (isNext) 1.5.dp else 1.dp, border, shape).height(40.dp).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Die Scheibe füllt sich, sobald der Satz geschafft ist
        PlateMark(if (set.done) fit.done else fit.muted, filled = set.done, size = 16.dp)
        Spacer(Modifier.width(8.dp))
        Text(formatSet(e, set.reps, set.weightKg), color = fg, style = FitText.figureSmall)
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
    // Der Ring liest die Zeit mit jedem Frame – läuft gleichmäßig statt in Sprüngen
    val frameNow = rememberFrameMillis(rest.endsAt) { vm.now() }
    val left = ceil((rest.endsAt - now) / 1000.0).toInt().coerceAtLeast(0)
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        ProgressRing(
            progress = { (rest.endsAt - frameNow()).toFloat() / (rest.totalSec * 1000f) },
            modifier = Modifier.size(56.dp), strokeWidth = 4.dp,
        ) {
            Text(left.toString(), style = FitText.figureSmall)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Pause", style = MaterialTheme.typography.titleMedium)
            Meta("durchatmen")
        }
        TextAction("−15", { vm.extendRest(-15) }, Modifier.padding(horizontal = 8.dp), color = fit.textSecondary)
        TextAction("+15", { vm.extendRest(15) }, Modifier.padding(horizontal = 8.dp), color = fit.textSecondary)
        TextAction("Weiter", vm::skipRest, Modifier.padding(horizontal = 8.dp))
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
        // jeden Frame aktualisieren: der Ring läuft flüssig
        while (elapsedMs < targetSec * 1000L) {
            withFrameNanos { }
            elapsedMs = System.currentTimeMillis() - start
        }
        platform.sound(Sound.DONE)
        platform.haptic(Haptic.SUCCESS)
        if (side < sides) side++ else onFinish(targetSec)
    }

    val left = ceil((targetSec * 1000L - elapsedMs) / 1000.0).toInt().coerceAtLeast(0)
    Box(
        // fängt alle Taps ab, damit nichts dahinter ausgelöst wird
        Modifier.fillMaxSize().background(fit.bg)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
            Text(exercise.name, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            if (sides > 1) Meta("Seite $side von $sides")
            Spacer(Modifier.height(16.dp))
            FigureBox(Modifier.size(168.dp)) { MiniExerciseFigure(exercise, Modifier.fillMaxSize().padding(8.dp)) }
            Spacer(Modifier.height(24.dp))
            ProgressRing(
                progress = { if (running) 1f - elapsedMs / (targetSec * 1000f) else 1f },
                modifier = Modifier.size(208.dp), strokeWidth = 8.dp,
                color = if (running) fit.accent else fit.muted,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (running) left.toString() else lead.toString(), style = FitText.hero.copy(fontSize = androidx.compose.ui.unit.TextUnit(72f, androidx.compose.ui.unit.TextUnitType.Sp), lineHeight = androidx.compose.ui.unit.TextUnit(76f, androidx.compose.ui.unit.TextUnitType.Sp)))
                    Text(if (running) "Sekunden" else if (side == 1) "Mach dich bereit" else "Seite wechseln", color = fit.muted, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PrimaryButton("Abbrechen", { onFinish(null) }, Modifier.weight(1f), color = fit.cardMuted, contentColor = fit.text)
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
    val reduced = remember { platform.reducedMotion() }
    LaunchedEffect(Unit) {
        platform.sound(Sound.DONE)
        platform.haptic(Haptic.SUCCESS)
        delay(350)
        // Die Streak zählt hoch
        streakAnim.animateTo(s.streakAfter.toFloat(), tween(700, easing = Motion.EaseOut))
    }
    LaunchedEffect(Unit) { xpAnim.animateTo(s.xpEarned.toFloat(), tween(900, easing = Motion.EaseOut)) }

    Box(Modifier.fillMaxSize().background(fit.bg)) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(WindowInsetsPadding()).navigationBarsPadding().padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(40.dp))
            Meta(s.templateName)
            Text("Training geschafft", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(24.dp))

            Row(verticalAlignment = Alignment.Bottom) {
                Text(streakAnim.value.roundToInt().toString(), style = FitText.hero)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.padding(bottom = 16.dp)) {
                    FitIconView(FitIcon.FLAME, tint = fit.streak, size = 24.dp)
                    Spacer(Modifier.height(4.dp))
                    Text(if (s.streakAfter == 1) "Tag Streak" else "Tage Streak", style = MaterialTheme.typography.titleMedium, color = fit.textSecondary)
                }
            }
            Text(
                when {
                    s.shieldsEarned > 0 -> "Streak verlängert · ${s.shieldsEarned} Streak-Schutz verdient"
                    s.streakAfter > s.streakBefore -> "Streak verlängert"
                    else -> "Deine Streak läuft weiter"
                },
                style = MaterialTheme.typography.bodyMedium, color = fit.textSecondary,
            )
            Spacer(Modifier.height(24.dp))

            StatStrip(
                listOf(
                    "${s.durationMin} min" to "Dauer",
                    s.setCount.toString() to "Sätze",
                    (if (s.volumeKg >= 1) "${s.volumeKg.roundToInt()} kg" else "–") to "Volumen",
                ),
            )
            Spacer(Modifier.height(12.dp))

            FitCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FitIconView(FitIcon.BOLT, tint = fit.xpInk, size = 20.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("+${xpAnim.value.roundToInt()} XP", style = FitText.figure, modifier = Modifier.weight(1f))
                    Meta("Level ${s.levelAfter.number} · ${s.levelAfter.level.title}")
                }
                Spacer(Modifier.height(12.dp))
                ProgressBar(s.levelAfter.progress, color = fit.xp, height = 4.dp)
                s.levelAfter.next?.let {
                    Spacer(Modifier.height(8.dp))
                    Meta("noch ${it.minXp - s.levelAfter.xp} XP bis ${it.title}")
                }
            }

            if (s.records.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                FitCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FitIconView(FitIcon.TROPHY, tint = fit.xpInk, size = 20.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(if (s.records.size == 1) "Neuer Rekord" else "${s.records.size} neue Rekorde", style = MaterialTheme.typography.titleMedium)
                    }
                    for (r in s.records) {
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(r.exerciseName, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            Text(r.text, style = FitText.figureSmall)
                        }
                    }
                }
            }

            if (s.suggestions.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                FitCard {
                    Text("Für das nächste Mal", style = MaterialTheme.typography.titleMedium)
                    s.suggestions.forEachIndexed { i, sg ->
                        Spacer(Modifier.height(12.dp))
                        if (i > 0) {
                            ThinDivider()
                            Spacer(Modifier.height(12.dp))
                        }
                        Row(verticalAlignment = Alignment.Top) {
                            Box(Modifier.padding(top = 4.dp)) { PlateMark(if (sg.isProgress) fit.done else fit.muted, filled = sg.isProgress, size = 12.dp) }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(sg.exerciseName, style = MaterialTheme.typography.titleSmall)
                                Text(sg.message, color = fit.textSecondary, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            PrimaryButton("Fertig", onClose)
            Spacer(Modifier.height(32.dp))
        }
        if (!reduced) Confetti(Modifier.fillMaxSize())
    }

    // Meilensteine: erst Erfolge, dann Level-Aufstieg
    val achievement: Achievement? = s.newAchievements.getOrNull(popupIndex)
    if (achievement != null) {
        MilestoneDialog("Erfolg freigeschaltet", "${achievement.title} · ${achievement.description}", { popupIndex++ }) {
            AchievementBadge(achievement, unlocked = true, size = 112.dp)
        }
    } else if (levelUp && popupIndex == s.newAchievements.size) {
        MilestoneDialog("Level ${s.levelAfter.number}", "Du bist jetzt ${s.levelAfter.level.title}", { popupIndex++ }) {
            Box(Modifier.size(112.dp).clip(CircleShape).background(fit.text), contentAlignment = Alignment.Center) {
                Text(s.levelAfter.number.toString(), style = FitText.figureLarge, color = fit.bg)
            }
        }
    }
}

@Composable
private fun WindowInsetsPadding(): PaddingValues = de.leon.fitstreak.ui.components.WindowInsetsCompat.statusBars()
