package de.leon.fitstreak.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import de.leon.fitstreak.data.ReminderTimes
import de.leon.fitstreak.data.settings.Settings
import java.time.ZonedDateTime

/**
 * Plant die Erinnerungen mit AlarmManager.setAndAllowWhileIdle – ohne Exact-Alarm-Berechtigung.
 * Jeder Alarm plant sich nach dem Auslösen selbst für den nächsten Tag neu (siehe [ReminderReceiver]).
 */
object ReminderScheduler {
    const val ACTION_DAILY = "de.leon.fitstreak.action.DAILY_REMINDER"
    const val ACTION_SAVER = "de.leon.fitstreak.action.STREAK_SAVER"
    const val ACTION_SNOOZE = "de.leon.fitstreak.action.SNOOZE"
    const val ACTION_SNOOZED = "de.leon.fitstreak.action.SNOOZED_REMINDER"

    private const val SNOOZE_MILLIS = 60 * 60 * 1000L

    private fun requestCode(action: String): Int = when (action) {
        ACTION_DAILY -> 1
        ACTION_SAVER -> 2
        ACTION_SNOOZE -> 3
        else -> 4
    }

    fun pendingIntent(context: Context, action: String): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).setAction(action)
        return PendingIntent.getBroadcast(context, requestCode(action), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun scheduleAll(context: Context, settings: Settings) {
        val active = settings.onboardingDone
        schedule(context, ACTION_DAILY, active && settings.reminderEnabled, settings.reminderMinutes)
        schedule(context, ACTION_SAVER, active && settings.saverEnabled, settings.saverMinutes)
    }

    private fun schedule(context: Context, action: String, enabled: Boolean, minutesOfDay: Int) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = pendingIntent(context, action)
        alarms.cancel(pi)
        if (!enabled) return
        val at = ReminderTimes.nextTrigger(minutesOfDay, ZonedDateTime.now()).toInstant().toEpochMilli()
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    }

    fun snooze(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + SNOOZE_MILLIS, pendingIntent(context, ACTION_SNOOZED))
    }
}
