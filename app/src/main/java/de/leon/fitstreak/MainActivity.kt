package de.leon.fitstreak

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableIntStateOf
import de.leon.fitstreak.ui.FitStreakRoot
import de.leon.fitstreak.ui.theme.FitTheme

class MainActivity : ComponentActivity() {
    /** Zählt hoch, wenn die App über eine Benachrichtigung/das Widget geöffnet wird → „Heute“ anzeigen. */
    private val openToday = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null && intent?.getBooleanExtra(EXTRA_OPEN_TODAY, false) == true) openToday.intValue++
        val container = (application as FitStreakApp).container
        setContent {
            FitTheme {
                FitStreakRoot(container, openToday.intValue)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_TODAY, false)) openToday.intValue++
    }

    companion object {
        const val EXTRA_OPEN_TODAY = "open_today"
    }
}
