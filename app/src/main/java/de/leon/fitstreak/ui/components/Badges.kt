package de.leon.fitstreak.ui.components

import androidx.compose.animation.core.Animatable
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.background
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import de.leon.fitstreak.core.game.Achievement
import de.leon.fitstreak.core.game.AchievementIcon
import de.leon.fitstreak.ui.theme.FitText
import de.leon.fitstreak.ui.theme.Motion
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

/**
 * Abzeichen als Hantelscheibe: freigeschaltet = massive Scheibe mit eingeprägtem Icon,
 * gesperrt = nur der Umriss.
 */
@Composable
fun AchievementBadge(achievement: Achievement, unlocked: Boolean, modifier: Modifier = Modifier, size: Dp = 64.dp) {
    val plate = fit.text
    val outline = fit.muted.copy(alpha = 0.5f)
    val ink = if (unlocked) fit.bg else fit.muted
    val rim = if (unlocked) fit.bg.copy(alpha = 0.35f) else outline
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = this.size.minDimension / 2
            val c = Offset(this.size.width / 2, this.size.height / 2)
            if (unlocked) drawCircle(plate, r, c) else drawCircle(outline, r - 1f, c, style = Stroke(1.5.dp.toPx()))
            // Rille der Scheibe
            drawCircle(rim, r * 0.82f, c, style = Stroke(1.dp.toPx()))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            FitIconView(achievement.icon.toFitIcon(), tint = ink, size = size * (if (achievement.badge > 0) 0.34f else 0.42f))
            if (achievement.badge > 0) {
                Text(achievement.badge.toString(), color = ink, style = FitText.meta.copy(fontSize = (size.value * 0.19f).sp, lineHeight = (size.value * 0.22f).sp))
            }
        }
    }
}

/** Meilenstein: das Abzeichen kommt ruhig ins Bild (kurzes Einblenden mit leichtem Anheben). */
@Composable
fun MilestoneDialog(
    title: String,
    subtitle: String,
    onDismiss: () -> Unit,
    badge: @Composable () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        val enter = remember { Animatable(0f) }
        LaunchedEffect(Unit) { enter.animateTo(1f, Motion.enter(delay = 80)) }
        val shape = MaterialTheme.shapes.extraLarge
        Column(
            Modifier.fillMaxWidth().clip(shape).background(fit.card).hairline(shape).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier.graphicsLayer {
                    val v = enter.value
                    alpha = v
                    scaleX = 0.92f + 0.08f * v
                    scaleY = 0.92f + 0.08f * v
                    translationY = (1f - v) * 12.dp.toPx()
                },
            ) { badge() }
            Spacer(Modifier.height(20.dp))
            Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, color = fit.text)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = fit.textSecondary, textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            PrimaryButton("Weiter", onDismiss)
        }
    }
}
