package de.leon.fitstreak.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import de.leon.fitstreak.FitStreakApp
import de.leon.fitstreak.data.ReminderTexts
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val app = context.applicationContext as? FitStreakApp ?: return
        val pending = goAsync()
        app.container.scope.launch {
            try {
                withTimeoutOrNull(8_000) { handle(app, action) }
            } catch (_: Exception) {
                // Eine fehlgeschlagene Erinnerung darf die App nie zum Absturz bringen
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun handle(app: FitStreakApp, action: String) {
        val container = app.container
        val repo = container.repo
        when (action) {
            ReminderScheduler.ACTION_DAILY, ReminderScheduler.ACTION_SAVER -> {
                // zuerst für morgen neu planen, dann benachrichtigen
                container.scheduleReminders()
                repo.refreshStreak()
                val data = repo.snapshot()
                val today = repo.today()
                val message = if (action == ReminderScheduler.ACTION_DAILY) ReminderTexts.daily(data, today) else ReminderTexts.saver(data, today)
                if (message != null) Notifications.show(app, Notifications.ID_REMINDER, message.title, message.text, message.withSnooze)
            }
            ReminderScheduler.ACTION_SNOOZE -> {
                Notifications.cancel(app, Notifications.ID_REMINDER)
                ReminderScheduler.snooze(app)
            }
            ReminderScheduler.ACTION_SNOOZED -> {
                repo.refreshStreak()
                val data = repo.snapshot()
                val today = repo.today()
                val message = ReminderTexts.saver(data, today) ?: ReminderTexts.daily(data, today)?.takeIf { it.withSnooze }
                if (message != null) Notifications.show(app, Notifications.ID_REMINDER, message.title, message.text, withSnooze = true)
            }
        }
    }
}

/** Plant die Erinnerungen nach Neustart, App-Update und Zeit-/Zeitzonenwechsel neu. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val allowed = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            "android.intent.action.QUICKBOOT_POWERON",
        )
        if (intent.action !in allowed) return
        val app = context.applicationContext as? FitStreakApp ?: return
        val pending = goAsync()
        app.container.scope.launch {
            try {
                withTimeoutOrNull(8_000) { app.container.scheduleReminders() }
            } finally {
                pending.finish()
            }
        }
    }
}
