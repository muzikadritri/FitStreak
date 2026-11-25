package de.leon.fitstreak.ui.profile

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import de.leon.fitstreak.core.game.Achievement
import de.leon.fitstreak.core.game.Achievements
import de.leon.fitstreak.core.game.Level
import de.leon.fitstreak.core.game.levelFor
import de.leon.fitstreak.ui.AppViewModel
import de.leon.fitstreak.ui.components.AchievementBadge
import de.leon.fitstreak.ui.components.FitCard
import de.leon.fitstreak.ui.components.FitIcon
import de.leon.fitstreak.ui.components.FitIconView
import de.leon.fitstreak.ui.components.MilestoneDialog
import de.leon.fitstreak.ui.components.ProgressBar
import de.leon.fitstreak.ui.components.WindowInsetsCompat
import de.leon.fitstreak.ui.theme.Brand
import de.leon.fitstreak.ui.theme.fit
import de.leon.fitstreak.ui.workout.StatTile
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@Composable
fun ProfileScreen(vm: AppViewModel, onPlan: () -> Unit, onTips: () -> Unit, onSettings: () -> Unit) {
    val data by vm.data.collectAsState()
    val level = levelFor(data.settings.xp)
    val stats = remember(data) { data.achievementStats() }
    val unlocked = remember(data) { data.achievements.associateBy { it.id } }
    var shown by remember { mutableStateOf<Achievement?>(null) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Box(
                Modifier.padding(WindowInsetsCompat.statusBars()).fillMaxWidth().clip(MaterialTheme.shapes.extraLarge)
                    .background(Brush.linearGradient(listOf(fit.heroStart, fit.heroEnd))).padding(18.dp),
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(64.dp).clip(CircleShape).background(fit.xp), contentAlignment = Alignment.Center) {
                            Text(level.number.toString(), fontSize = 32.sp, fontWeight = FontWeight.Black, color = fit.onXp)
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(data.settings.name.ifBlank { "Athlet" }, color = fit.onHero, style = MaterialTheme.typography.headlineSmall)
                            Text("Level ${level.number} · ${level.level.title}", color = fit.onHero.copy(alpha = 0.85f), style = MaterialTheme.typography.titleMedium)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    ProgressBar(level.progress, color = fit.xp, track = Color.White.copy(alpha = 0.2f))
                    Spacer(Modifier.height(6.dp))
                    Text(
                        level.next?.let { "${level.xp} XP · noch ${it.minXp - level.xp} bis „${it.title}“" } ?: "${level.xp} XP · höchstes Level erreicht",
                        color = fit.onHero.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (l in Level.entries) {
                            val reached = l.ordinal <= level.level.ordinal
                            Text(
                                l.title, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 1,
                                color = if (reached) fit.onXp else fit.onHero.copy(alpha = 0.6f),
                                modifier = Modifier.weight(1f).clip(RoundedCornerShape(50))
                                    .background(if (reached) fit.xp else Color.White.copy(alpha = 0.12f)).padding(vertical = 4.dp),
                            )
                        }
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(stats.workouts.toString(), "Trainings", Modifier.weight(1f))
                StatTile(maxOf(stats.bestStreak, data.settings.streakCurrent).toString(), "Rekord-Streak", Modifier.weight(1f))
                StatTile(if (stats.totalVolumeKg >= 1000) String.format("%.1f t", stats.totalVolumeKg / 1000).replace('.', ',') else "${stats.totalVolumeKg.roundToInt()} kg", "bewegt", Modifier.weight(1f))
            }
        }
        item {
            FitCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Erfolge", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    Text("${unlocked.size} / ${Achievements.all.size}", color = fit.muted, style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(10.dp))
                for (row in Achievements.all.chunked(3)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (a in row) {
                            Column(
                                Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).clickable { shown = a }.padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                AchievementBadge(a, unlocked = a.id in unlocked, size = 68.dp)
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    a.title, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 2,
                                    color = if (a.id in unlocked) MaterialTheme.colorScheme.onSurface else fit.muted,
                                )
                            }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        item { MenuRow(FitIcon.CALENDAR, Brand.Blue, "Wochenplan", "Workouts ansehen, Übungen tauschen, eigene Workouts", onPlan) }
        item { MenuRow(FitIcon.BULB, Brand.Yellow, "Tipps", "Kurz erklärt: Muskelaufbau, Technik, Ernährung, Erholung", onTips) }
        item { MenuRow(FitIcon.TIMER, Brand.Green, "Einstellungen", "Erinnerungen, Ziele & Ausrüstung, Backup", onSettings) }
    }

    shown?.let { a ->
        val entry = unlocked[a.id]
        val subtitle = if (entry != null) {
            val date = java.time.Instant.ofEpochMilli(entry.unlockedAt).atZone(ZoneId.systemDefault()).toLocalDate()
            "${a.description}\nFreigeschaltet am ${date.format(DateTimeFormatter.ofPattern("d.M.yyyy"))}"
        } else "${a.description}\nNoch nicht freigeschaltet."
        MilestoneDialog(a.title, subtitle, { shown = null }) { AchievementBadge(a, unlocked = entry != null, size = 110.dp) }
    }
}

@Composable
private fun MenuRow(icon: FitIcon, color: Color, title: String, subtitle: String, onClick: () -> Unit) {
    FitCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(color.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                FitIconView(icon, tint = color, size = 22.dp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, color = fit.muted, style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null, tint = fit.muted)
        }
    }
}
