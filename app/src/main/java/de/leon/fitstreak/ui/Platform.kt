package de.leon.fitstreak.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import de.leon.fitstreak.core.model.Exercise

enum class Haptic { TICK, SUCCESS, ALERT }

enum class Sound { TICK, GO, DONE }

/** Bilder der 3D-Figur (freigestellt, für helles und dunkles Design). */
enum class Art { WELCOME, REST, TIPS, EMPTY }

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

    /** true, wenn das Gerät die 3D-Figur grundsätzlich darstellen kann (für den Schalter in den Einstellungen). */
    fun figure3dSupported(): Boolean = false

    /** true, wenn die 3D-Figur läuft und eingeschaltet ist – sonst zeichnet die App die 2D-Figur. */
    @Composable
    fun figure3dEnabled(): Boolean = false

    /** true, wenn die 3D-Figur auf diesem Gerät zuletzt abgestürzt ist und deshalb pausiert. */
    @Composable
    fun figure3dFailed(): Boolean = false

    /** Nach einem fehlgeschlagenen Start darf die 3D-Figur es noch einmal versuchen (Schalter neu eingeschaltet). */
    fun figure3dRetry() {}

    /**
     * Die Übung als drehbares 3D-Modell. [time] liefert die aktuelle Zeit im Bewegungszyklus (Sekunden);
     * die Uhr selbst bleibt bei der aufrufenden Stelle, damit 2D und 3D exakt gleich laufen.
     */
    @Composable
    fun Figure3D(exercise: Exercise, time: () -> Float, modifier: Modifier) {}

    /** Zeigt eines der mitgelieferten Bilder. Ohne Plattform bleibt der Platz leer. */
    @Composable
    fun ArtImage(art: Art, modifier: Modifier) {}

    /** Öffnet den „Speichern unter“-Dialog und schreibt den von [content] gelieferten Text. */
    @Composable
    fun rememberTextExport(suggestedName: String, content: suspend () -> String, onDone: (Boolean) -> Unit): () -> Unit = { }

    /** Öffnet den Datei-Auswahl-Dialog und liefert den Text der gewählten Datei. */
    @Composable
    fun rememberTextImport(onText: (String) -> Unit): () -> Unit = { }

    object None : Platform
}

val LocalPlatform = staticCompositionLocalOf<Platform> { Platform.None }
