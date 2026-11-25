package de.leon.fitstreak

import android.content.Context
import androidx.room.withTransaction
import de.leon.fitstreak.data.AppHooks
import de.leon.fitstreak.data.FitRepository
import de.leon.fitstreak.data.db.AppDatabase
import de.leon.fitstreak.data.settings.DataStoreSettingsStore
import de.leon.fitstreak.notify.ReminderScheduler
import de.leon.fitstreak.widget.StreakWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Manuelle Dependency Injection: eine Instanz pro App-Prozess. */
class AppContainer(context: Context) {
    private val appContext: Context = context.applicationContext

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val db: AppDatabase by lazy { AppDatabase.create(appContext) }

    val settings: DataStoreSettingsStore by lazy { DataStoreSettingsStore(appContext) }

    private val hooks = object : AppHooks {
        override fun remindersChanged() {
            scope.launch { scheduleReminders() }
        }

        override fun dataChanged() {
            scope.launch { runCatching { StreakWidget.refresh(appContext) } }
        }
    }

    val repo: FitRepository by lazy {
        FitRepository(
            dao = db.dao(),
            settingsStore = settings,
            hooks = hooks,
            transaction = { block -> db.withTransaction { block() } },
        )
    }

    suspend fun scheduleReminders() {
        runCatching { ReminderScheduler.scheduleAll(appContext, settings.data.first()) }
    }
}
