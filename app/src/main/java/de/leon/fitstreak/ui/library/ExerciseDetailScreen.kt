package de.leon.fitstreak.ui.library

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
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
import de.leon.fitstreak.ui.components.Pill
import de.leon.fitstreak.ui.components.ScreenScaffold
import de.leon.fitstreak.ui.components.StaticExerciseFigure
import de.leon.fitstreak.ui.theme.Brand
import de.leon.fitstreak.ui.theme.fit
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Große Animation mit Phasen-Beschriftung, Pause und Zeitlupe. */
@Composable
fun AnimationPlayer(e: Exercise, modifier: Modifier = Modifier) {
    var playing by remember(e.id) { mutableStateOf(true) }
    var slow by remember(e.id) { mutableStateOf(false) }
    var phase by remember(e.id) { mutableIntStateOf(0) }
    Column(modifier) {
        FigureBox(Modifier.fillMaxWidth().height(280.dp)) {
            ExerciseFigure(e, Modifier.fillMaxSize().padding(10.dp), playing = playing, speed = if (slow) 0.35f else 1f, onPhase = { phase = it })
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Phasen der Bewegung – die aktuelle ist hervorgehoben
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val labels = e.animation.phases.map { it.label }
                val current = labels.getOrNull(phase) ?: ""
                Box(Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primary).padding(horizontal = 14.dp, vertical = 8.dp)) {
                    Text(current, color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                }
            }
            ControlButton(if (slow) "Zeitlupe an" else "Zeitlupe", FitIcon.SNAIL, active = slow) { slow = !slow }
            Spacer(Modifier.width(8.dp))
            ControlButton(if (playing) "Pause" else "Weiter", if (playing) FitIcon.PAUSE else FitIcon.PLAY, active = !playing) { playing = !playing }
        }
    }
}

@Composable
private fun ControlButton(label: String, icon: FitIcon, active: Boolean, onClick: () -> Unit) {
    val bg = if (active) Brand.Orange else fit.cardMuted
    val fg = if (active) Color.White else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(bg).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FitIconView(icon, tint = fg, size = 16.dp)
        Spacer(Modifier.width(6.dp))
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
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            AnimationPlayer(e)
            Text(e.summary, style = MaterialTheme.typography.bodyLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(e.difficulty.label, difficultyColor(e.difficulty))
                Pill(equipmentText(e), fit.cardMuted, contentColor = MaterialTheme.colorScheme.onSurface)
                Pill(
                    if (e.isTimed) "${e.repRange.first}–${e.repRange.last} Sek." else "${e.repRange.first}–${e.repRange.last} Wdh." + if (e.perSide) " je Seite" else "",
                    fit.cardMuted, contentColor = MaterialTheme.colorScheme.onSurface,
                )
                Pill("${e.restSec} Sek. Pause", fit.cardMuted, contentColor = MaterialTheme.colorScheme.onSurface)
            }

            FitCard {
                Text("So geht's", style = MaterialTheme.typography.titleLarge)
                e.steps.forEachIndexed { i, step ->
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.Top) {
                        Box(Modifier.size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                            Text("${i + 1}", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(step, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(top = 2.dp))
                    }
                }
            }

            FitCard {
                Text("Typische Fehler", style = MaterialTheme.typography.titleLarge)
                for (m in e.mistakes) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.Top) {
                        Box(Modifier.padding(top = 2.dp).size(22.dp).clip(CircleShape).background(fit.streak.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                            Text("✕", color = fit.streak, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(m, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    }
                }
            }

            FitCard {
                Text("Atmung", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(6.dp))
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
                Spacer(Modifier.height(10.dp))
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
                    Text("Varianten: leichter ↔ schwerer", style = MaterialTheme.typography.titleLarge)
                    for (v in ladder) {
                        Spacer(Modifier.height(8.dp))
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                                .background(if (v.id == e.id) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                .clickable(enabled = v.id != e.id) { onExercise(v.id) }.padding(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            FigureBox(Modifier.size(52.dp)) { StaticExerciseFigure(v, v.animation.cycleSeconds * 0.3f, Modifier.fillMaxSize().padding(3.dp)) }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(v.name, style = MaterialTheme.typography.titleSmall)
                                Text("Stufe ${v.ladderRank} · ${v.difficulty.label}", color = fit.muted, style = MaterialTheme.typography.bodySmall)
                            }
                            if (v.id == e.id) Pill("diese", MaterialTheme.colorScheme.primary)
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
                            color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    val fmt = DateTimeFormatter.ofPattern("d.M.")
                    LineChart(
                        values = history.map { if (weighted) it.best1Rm.toFloat() else it.maxReps.toFloat() },
                        startLabel = LocalDate.ofEpochDay(history.first().epochDay).format(fmt),
                        endLabel = LocalDate.ofEpochDay(history.last().epochDay).format(fmt),
                    )
                    Text(if (weighted) "Geschätztes 1RM (kg) pro Training" else "Beste ${e.unit} pro Training", color = fit.muted, style = MaterialTheme.typography.labelMedium)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
