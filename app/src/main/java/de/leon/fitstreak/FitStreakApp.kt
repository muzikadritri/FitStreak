package de.leon.fitstreak

import android.app.Application
import de.leon.fitstreak.notify.Notifications
import kotlinx.coroutines.launch

class FitStreakApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifications.ensureChannel(this)
        // Bei jedem App-Start sicherstellen, dass die Erinnerungen geplant sind.
        container.scope.launch { container.scheduleReminders() }
    }
}
