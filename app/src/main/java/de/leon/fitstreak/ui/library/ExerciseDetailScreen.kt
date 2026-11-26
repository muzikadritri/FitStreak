package de.leon.fitstreak.ui.library

import androidx.compose.foundation.background
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import de.leon.fitstreak.core.exercises.ExerciseLibrary
import de.leon.fitstreak.core.model.Exercise
import de.leon.fitstreak.core.model.LoadType
import de.leon.fitstreak.core.progression.ProgressionEngine
import de.leon.fitstreak.data.AppData
import de.leon.fitstreak.ui.components.BodyMap
import de.leon.fitstreak.ui.components.ExerciseFigure
import de.leon.fitstreak.ui.components.FigureBox
import de.leon.fitstreak.ui.components.FigureDefaults
import de.leon.fitstreak.ui.components.FitCard
import de.leon.fitstreak.ui.components.FitIcon
import de.leon.fitstreak.ui.components.FitIconView
import de.leon.fitstreak.ui.components.LegendDot
import de.leon.fitstreak.ui.components.LineChart
import de.leon.fitstreak.ui.components.Meta
import de.leon.fitstreak.ui.components.Pill
import de.leon.fitstreak.ui.components.PlateMark
import de.leon.fitstreak.ui.components.hairline
import de.leon.fitstreak.ui.components.pressable
import de.leon.fitstreak.ui.components.ScreenScaffold
import de.leon.fitstreak.ui.components.StaticExerciseFigure
import de.leon.fitstreak.ui.theme.FitText
import de.leon.fitstreak.ui.theme.Motion
import de.leon.fitstreak.ui.theme.fit
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Große Animation mit Phasen-Beschriftung, Pause und Zeitlupe. */
@Composable
fun AnimationPlayer(e: Exercise, modifier: Modifier = Modifier) {
    var playing by remember(e.id) { mutableStateOf(true) }
    var slow by remember(e.id) { mutableStateOf(false) }
    var phase by remember(e.id) { mutableIntStateOf(0) }
    // Tempo-Wechsel gleitet, statt zu springen
    // Pause bremst sanft ab und läuft sanft wieder an
    val speed by animateFloatAsState(if (!playing) 0f else if (slow) 0.35f else 1f, Motion.enter(), label = "speed")
    Column(modifier) {
        FigureBox(Modifier.fillMaxWidth().height(280.dp)) {
            ExerciseFigure(e, Modifier.fillMaxSize().padding(12.dp), playing = playing || speed > 0.01f, speed = speed, onPhase = { phase = it })
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Aktuelle Phase der Bewegung – der Text blendet beim Wechsel weich über
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                PlateMark(fit.accent, size = 12.dp)
                Spacer(Modifier.width(8.dp))
                val label = e.animation.phases.getOrNull(phase)?.label ?: ""
                AnimatedContent(
                    targetState = label,
                    transitionSpec = { fadeIn(Motion.fast()) togetherWith fadeOut(Motion.fast()) },
                    label = "phase",
                ) { text -> Text(text, style = MaterialTheme.typography.titleSmall, maxLines = 1) }
            }
            ControlButton("Zeitlupe", FitIcon.SNAIL, active = slow) { slow = !slow }
            Spacer(Modifier.width(8.dp))
            ControlButton(if (playing) "Pause" else "Weiter", if (playing) FitIcon.PAUSE else FitIcon.PLAY, active = !playing) { playing = !playing }
        }
    }
}

@Composable
private fun ControlButton(label: String, icon: FitIcon, active: Boolean, onClick: () -> Unit) {
    val shape = MaterialTheme.shapes.small
    val bg by animateColorAsState(if (active) fit.text else fit.cardMuted, Motion.fast(), label = "ctl")
    val fg by animateColorAsState(if (active) fit.bg else fit.text, Motion.fast(), label = "ctlText")
    Row(
        Modifier.pressable(scale = 0.96f, onClick = onClick).clip(shape).background(bg)
            .let { if (active) it else it.hairline(shape) }.height(36.dp).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FitIconView(icon, tint = fg, size = 16.dp)
        Spacer(Modifier.width(8.dp))
        Text(label, color = fg, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExerciseDetailScreen(exerciseId: String, data: AppData, onBack: () -> Unit, onExercise: (String) -> Unit) {
    val e = ExerciseLibrary[exerciseId]
    if (e == null) {
        ScreenScaffold("Übung", onBack) { Box(Modifier.padding(it).padding(24.dp)) { Text("Übung nicht gefunden.") } }
        return
    }
    val figure = FigureDefaults.colors()
    ScreenScaffold(e.name, onBack) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AnimationPlayer(e)
            Text(e.summary.trimEnd('.'), style = MaterialTheme.typography.bodyLarge, color = fit.textSecondary)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(e.difficulty.label, difficultyColor(e.difficulty))
                Pill(equipmentText(e), fit.cardMuted)
                Pill(
                    if (e.isTimed) "${e.repRange.first}–${e.repRange.last} Sek." else "${e.repRange.first}–${e.repRange.last} Wdh." + if (e.perSide) " je Seite" else "",
                    fit.cardMuted,
                )
                Pill("${e.restSec} Sek. Pause", fit.cardMuted)
            }

            FitCard {
                Text("So geht's", style = MaterialTheme.typography.titleLarge)
                e.steps.forEachIndexed { i, step ->
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.Top) {
                        Text("${i + 1}", style = FitText.figureSmall, color = fit.muted, modifier = Modifier.width(24.dp).padding(top = 2.dp))
                        Text(step, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    }
                }
            }

            FitCard {
                Text("Typische Fehler", style = MaterialTheme.typography.titleLarge)
                for (m in e.mistakes) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.Top) {
                        Box(Modifier.width(24.dp).padding(top = 4.dp)) { FitIconView(FitIcon.CLOSE, tint = fit.streak, size = 16.dp) }
                        Text(m, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    }
                }
            }

            FitCard {
                Text("Atmung", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text(e.breathing, style = MaterialTheme.typography.bodyLarge)
            }

            FitCard {
                Text("Zielmuskeln", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(4.dp))
                Text(
                    buildString {
                        append(e.primary.joinToString(", ") { it.label })
                        if (e.secondary.isNotEmpty()) append(" · unterstützend: " + e.secondary.joinToString(", ") { it.label })
                    },
                    color = fit.muted,
                )
                Spacer(Modifier.height(12.dp))
                BodyMap(
                    colorFor = { m -> if (m in e.primary) figure.primary else if (m in e.secondary) figure.secondary else null },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 36.dp),
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    LegendDot(figure.primary, "Hauptmuskel")
                    LegendDot(figure.secondary, "unterstützend")
                }
            }

            // Varianten-Leiter: leichter ↔ schwerer
            val ladder = e.ladder?.let { ExerciseLibrary.ladder(it) }.orEmpty()
            if (ladder.size > 1) {
                FitCard {
                    Text("Varianten, leicht bis schwer", style = MaterialTheme.typography.titleLarge)
                    for (v in ladder) {
                        Spacer(Modifier.height(8.dp))
                        Row(
                            Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small)
                                .background(if (v.id == e.id) fit.cardMuted else Color.Transparent)
                                .pressable(enabled = v.id != e.id) { onExercise(v.id) }.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            FigureBox(Modifier.size(52.dp)) { StaticExerciseFigure(v, v.animation.cycleSeconds * 0.3f, Modifier.fillMaxSize().padding(4.dp)) }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(v.name, style = MaterialTheme.typography.titleSmall)
                                Meta("Stufe ${v.ladderRank} · ${v.difficulty.label}")
                            }
                            if (v.id == e.id) PlateMark(fit.accent, size = 12.dp)
                        }
                    }
                }
            }

            // Eigener Verlauf
            val history = remember(data, e.id) { data.history(e.id) }
            if (history.isNotEmpty()) {
                val best = data.best(e.id)
                FitCard {
                    Text("Dein Verlauf", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(4.dp))
                    val weighted = e.load != LoadType.NONE
                    if (best != null) {
                        Text(
                            if (weighted) "Rekord: ${ProgressionEngine.fmtKg(best.maxWeightKg)} kg · geschätztes 1RM ${ProgressionEngine.fmtKg(Math.round(best.best1Rm * 10) / 10.0)} kg"
                            else "Rekord: ${best.maxReps} ${e.unit}",
                            color = fit.textSecondary, style = FitText.meta,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    val fmt = DateTimeFormatter.ofPattern("d.M.")
                    LineChart(
                        values = history.map { if (weighted) it.best1Rm.toFloat() else it.maxReps.toFloat() },
                        startLabel = LocalDate.ofEpochDay(history.first().epochDay).format(fmt),
                        endLabel = LocalDate.ofEpochDay(history.last().epochDay).format(fmt),
                    )
                    Meta(if (weighted) "Geschätztes 1RM in kg pro Training" else "Beste ${e.unit} pro Training")
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
