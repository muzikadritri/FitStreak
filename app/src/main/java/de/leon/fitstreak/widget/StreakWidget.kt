package de.leon.fitstreak.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import de.leon.fitstreak.FitStreakApp
import de.leon.fitstreak.MainActivity
import de.leon.fitstreak.R
import de.leon.fitstreak.data.AppData

/** Home-Screen-Widget: Streak + heutiges Workout. Tippen öffnet die App auf „Heute“. */
class StreakWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repo = (context.applicationContext as FitStreakApp).container.repo
        val open = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_TODAY, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        provideContent {
            val data by repo.data.collectAsState(initial = AppData())
            val today = repo.today()
            val streak = if (data.loaded && data.settings.onboardingDone) data.streak(today).display else 0
            val workout = data.workoutFor(today)
            val trained = data.trainedOn(today)
            val (line1, line2) = when {
                !data.loaded || !data.settings.onboardingDone -> "FitStreak" to "Tippen zum Starten"
                workout == null -> "Ruhetag" to "Erholung zählt für die Streak"
                trained -> workout.template.name to "Erledigt – stark!"
                else -> workout.template.name to "Heute dran – tippen zum Starten"
            }
            WidgetContent(streak, line1, line2, trained, GlanceModifier.clickable(actionStartActivity(open)))
        }
    }

    companion object {
        suspend fun refresh(context: Context) {
            StreakWidget().updateAll(context)
        }
    }
}

@Composable
private fun WidgetContent(streak: Int, line1: String, line2: String, done: Boolean, clickable: GlanceModifier) {
    val white = ColorProvider(Color.White)
    val soft = ColorProvider(Color(0xFFD6E0FF))
    Column(
        modifier = GlanceModifier.fillMaxSize().background(ImageProvider(R.drawable.widget_background)).padding(14.dp).then(clickable),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Image(provider = ImageProvider(R.drawable.ic_widget_flame), contentDescription = "Streak", modifier = GlanceModifier.size(40.dp))
            Spacer(GlanceModifier.width(8.dp))
            Text(streak.toString(), style = TextStyle(color = white, fontSize = 36.sp, fontWeight = FontWeight.Bold))
            Spacer(GlanceModifier.width(6.dp))
            Text(if (streak == 1) "Tag" else "Tage", style = TextStyle(color = soft, fontSize = 15.sp, fontWeight = FontWeight.Medium))
        }
        Spacer(GlanceModifier.height(6.dp))
        Text(line1, style = TextStyle(color = white, fontSize = 16.sp, fontWeight = FontWeight.Bold), maxLines = 1)
        Text(line2, style = TextStyle(color = if (done) ColorProvider(Color(0xFF8CF0B4)) else soft, fontSize = 12.sp), maxLines = 1)
    }
}

class StreakWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StreakWidget()
}
