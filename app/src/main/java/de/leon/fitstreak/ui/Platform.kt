package de.leon.fitstreak.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

enum class Haptic { TICK, SUCCESS, ALERT }

enum class Sound { TICK, GO, DONE }

/**
 * Alles, was die Oberfläche vom Android-System braucht. Die Implementierung liegt in `platform/`,
 * so bleiben die Screens reines Compose.
 */
interface Platform {
    fun haptic(kind: Haptic) {}
    fun sound(kind: Sound) {}
    fun notificationsAllowed(): Boolean = true
    fun batteryOptimized(): Boolean = false
    fun openNotificationSettings() {}
    fun openBatterySettings() {}
    fun sendTestNotification() {}
    fun toast(message: String) {}

    /** true, wenn in den System-Einstellungen Animationen abgeschaltet sind. */
    fun reducedMotion(): Boolean = false
    val versionName: String get() = ""

    /** Hält den Bildschirm an, solange das Composable sichtbar ist. */
    @Composable
    fun KeepScreenOn() {}

    /** Fragt die Benachrichtigungs-Berechtigung an (Android 13+). */
    @Composable
    fun rememberNotificationPermissionRequest(onResult: (Boolean) -> Unit): () -> Unit = { onResult(true) }

    /** Öffnet den „Speichern unter“-Dialog und schreibt den von [content] gelieferten Text. */
    @Composable
    fun rememberTextExport(suggestedName: String, content: suspend () -> String, onDone: (Boolean) -> Unit): () -> Unit = { }

    /** Öffnet den Datei-Auswahl-Dialog und liefert den Text der gewählten Datei. */
    @Composable
    fun rememberTextImport(onText: (String) -> Unit): () -> Unit = { }

    object None : Platform
}

val LocalPlatform = staticCompositionLocalOf<Platform> { Platform.None }
