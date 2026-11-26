package de.leon.fitstreak.ui.profile

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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.leon.fitstreak.core.game.Achievement
import de.leon.fitstreak.core.game.Achievements
import de.leon.fitstreak.core.game.Level
import de.leon.fitstreak.core.game.levelFor
import de.leon.fitstreak.ui.AppViewModel
import de.leon.fitstreak.ui.components.AchievementBadge
import de.leon.fitstreak.ui.components.FitCard
import de.leon.fitstreak.ui.components.FitIcon
import de.leon.fitstreak.ui.components.FitIconView
import de.leon.fitstreak.ui.components.Meta
import de.leon.fitstreak.ui.components.MilestoneDialog
import de.leon.fitstreak.ui.components.PlateMark
import de.leon.fitstreak.ui.components.ProgressBar
import de.leon.fitstreak.ui.components.StatStrip
import de.leon.fitstreak.ui.components.ThinDivider
import de.leon.fitstreak.ui.components.WindowInsetsCompat
import de.leon.fitstreak.ui.components.pressable
import de.leon.fitstreak.ui.theme.FitText
import de.leon.fitstreak.ui.theme.fit
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
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column(Modifier.padding(WindowInsetsCompat.statusBars())) {
                Text("Profil", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Level als Scheibe
                    Box(Modifier.size(56.dp).clip(CircleShape).background(fit.text), contentAlignment = Alignment.Center) {
                        Text(level.number.toString(), style = FitText.figure, color = fit.bg)
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(data.settings.name.ifBlank { "Athlet" }, style = MaterialTheme.typography.headlineSmall)
                        Text("Level ${level.number} · ${level.level.title}", color = fit.textSecondary, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Spacer(Modifier.height(16.dp))
                ProgressBar(level.progress, color = fit.xp, height = 4.dp)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Meta(level.next?.let { "${level.xp} XP · noch ${it.minXp - level.xp} bis ${it.title}" } ?: "${level.xp} XP · höchstes Level", Modifier.weight(1f))
                    for (l in Level.entries) {
                        Spacer(Modifier.width(4.dp))
                        PlateMark(if (l.ordinal <= level.level.ordinal) fit.xp else fit.muted, filled = l.ordinal <= level.level.ordinal, size = 12.dp)
                    }
                }
            }
        }
        item {
            StatStrip(
                listOf(
                    stats.workouts.toString() to "Trainings",
                    maxOf(stats.bestStreak, data.settings.streakCurrent).toString() to "Rekord-Streak",
                    (if (stats.totalVolumeKg >= 1000) String.format("%.1f t", stats.totalVolumeKg / 1000).replace('.', ',') else "${stats.totalVolumeKg.roundToInt()} kg") to "bewegt",
                ),
            )
        }
        item {
            FitCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Erfolge", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    Meta("${unlocked.size}/${Achievements.all.size}")
                }
                Spacer(Modifier.height(8.dp))
                for (row in Achievements.all.chunked(3)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (a in row) {
                            Column(
                                Modifier.weight(1f).clip(MaterialTheme.shapes.small).pressable { shown = a }.padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                AchievementBadge(a, unlocked = a.id in unlocked, size = 64.dp)
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    a.title, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 2,
                                    color = if (a.id in unlocked) fit.text else fit.muted,
                                )
                            }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        item {
            FitCard(padding = 0.dp) {
                MenuRow(FitIcon.CALENDAR, "Wochenplan", "Workouts ansehen, Übungen tauschen, eigene Workouts", onPlan)
                ThinDivider(Modifier.padding(start = 52.dp))
                MenuRow(FitIcon.BULB, "Tipps", "Muskelaufbau, Technik, Ernährung, Erholung", onTips)
                ThinDivider(Modifier.padding(start = 52.dp))
                MenuRow(FitIcon.GEAR, "Einstellungen", "Erinnerungen, Ziele & Ausrüstung, Backup", onSettings)
            }
        }
    }

    shown?.let { a ->
        val entry = unlocked[a.id]
        val subtitle = if (entry != null) {
            val date = java.time.Instant.ofEpochMilli(entry.unlockedAt).atZone(ZoneId.systemDefault()).toLocalDate()
            "${a.description} · freigeschaltet am ${date.format(DateTimeFormatter.ofPattern("d.M.yyyy"))}"
        } else "${a.description} · noch nicht freigeschaltet"
        MilestoneDialog(a.title, subtitle, { shown = null }) { AchievementBadge(a, unlocked = entry != null, size = 112.dp) }
    }
}

@Composable
private fun MenuRow(icon: FitIcon, title: String, subtitle: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().pressable(onClick = onClick).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(36.dp)) { FitIconView(icon, tint = fit.textSecondary, size = 20.dp) }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, color = fit.muted, style = MaterialTheme.typography.bodySmall)
        }
        FitIconView(FitIcon.CHEVRON, tint = fit.muted, size = 16.dp)
    }
}
