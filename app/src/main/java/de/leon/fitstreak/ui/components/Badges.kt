package de.leon.fitstreak.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import de.leon.fitstreak.core.game.Achievement
import de.leon.fitstreak.core.game.AchievementIcon
import de.leon.fitstreak.ui.theme.Brand
import de.leon.fitstreak.ui.theme.fit

private fun AchievementIcon.toFitIcon(): FitIcon = when (this) {
    AchievementIcon.START -> FitIcon.FLAG
    AchievementIcon.FLAMME -> FitIcon.FLAME
    AchievementIcon.LIEGESTUETZ -> FitIcon.PUSHUP
    AchievementIcon.POKAL -> FitIcon.TROPHY
    AchievementIcon.KALENDER -> FitIcon.CALENDAR
    AchievementIcon.KOERPER -> FitIcon.BODY
    AchievementIcon.GEWICHT -> FitIcon.WEIGHT
}

private fun AchievementIcon.color(): Color = when (this) {
    AchievementIcon.START -> Brand.Green
    AchievementIcon.FLAMME -> Brand.Red
    AchievementIcon.LIEGESTUETZ -> Brand.Blue
    AchievementIcon.POKAL -> Brand.Yellow
    AchievementIcon.KALENDER -> Brand.Green
    AchievementIcon.KOERPER -> Color(0xFF8F6BFF)
    AchievementIcon.GEWICHT -> Brand.Orange
}

/** Selbst gezeichnetes Abzeichen: Scheibe mit Rand, Icon und optionaler Zahl. */
@Composable
fun AchievementBadge(achievement: Achievement, unlocked: Boolean, modifier: Modifier = Modifier, size: Dp = 64.dp) {
    val color = if (unlocked) achievement.icon.color() else fit.muted.copy(alpha = 0.45f)
    val plate = if (unlocked) color.copy(alpha = if (fit.isDark) 0.28f else 0.16f) else fit.cardMuted
    val icon = achievement.icon.toFitIcon()
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = this.size.minDimension / 2
            val c = Offset(this.size.width / 2, this.size.height / 2)
            drawCircle(plate, r * 0.96f, c)
            drawCircle(color, r * 0.92f, c, style = Stroke(r * 0.09f))
            if (unlocked) {
                drawCircle(
                    Brush.radialGradient(listOf(Color.White.copy(alpha = 0.35f), Color.Transparent), Offset(c.x - r * 0.3f, c.y - r * 0.4f), r * 0.9f),
                    r * 0.86f, c,
                )
            }
        }
        FitIconView(
            icon, tint = color, size = size * (if (achievement.badge > 0) 0.38f else 0.48f),
            modifier = Modifier.align(Alignment.Center).padding(bottom = if (achievement.badge > 0) size * 0.2f else 0.dp),
        )
        if (achievement.badge > 0) {
            Text(
                achievement.badge.toString(),
                color = color,
                fontWeight = FontWeight.Black,
                fontSize = (size.value * 0.22f).sp,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = size * 0.12f),
            )
        }
    }
}

/** Meilenstein-Popup: Abzeichen springt mit Strahlenkranz ins Bild. */
@Composable
fun MilestoneDialog(
    title: String,
    subtitle: String,
    onDismiss: () -> Unit,
    badge: @Composable () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        val pop = remember { Animatable(0.3f) }
        LaunchedEffect(Unit) {
            pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
        }
        val spin by rememberInfiniteTransition(label = "burst").animateFloat(
            0f, 360f, infiniteRepeatable(tween(14000, easing = LinearEasing), RepeatMode.Restart), label = "spin",
        )
        Surface(shape = MaterialTheme.shapes.extraLarge, color = fit.card) {
            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(180.dp), contentAlignment = Alignment.Center) {
                    Sunburst(Modifier.fillMaxSize(), rotation = spin)
                    Box(Modifier.scale(pop.value)) { badge() }
                }
                Spacer(Modifier.height(8.dp))
                Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                Spacer(Modifier.height(4.dp))
                Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = fit.muted, textAlign = TextAlign.Center)
                Spacer(Modifier.height(20.dp))
                PrimaryButton("Weiter", onDismiss)
            }
        }
    }
}
