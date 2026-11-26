package de.leon.fitstreak.platform

import android.Manifest
import android.app.ActivityManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import de.leon.fitstreak.AppContainer
import de.leon.fitstreak.BuildConfig
import de.leon.fitstreak.core.model.Exercise
import de.leon.fitstreak.notify.Notifications
import de.leon.fitstreak.ui.Haptic
import de.leon.fitstreak.ui.Platform
import de.leon.fitstreak.ui.Sound
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Android-Implementierung der Plattform-Funktionen für die Oberfläche. */
class AndroidPlatform(private val context: Context, container: AppContainer) : Platform {

    @Volatile
    private var soundOn = true

    @Volatile
    private var hapticsOn = true

    private var tone: ToneGenerator? = null

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    // 3D-Figur: braucht OpenGL ES 3.0. Startet die Engine einmal nicht sauber, bleibt die App bei 2D.
    private val figure3dGuard = Figure3DGuard(context.getSharedPreferences("figure3d", Context.MODE_PRIVATE))
    private val gles3: Boolean = try {
        (context.getSystemService(ActivityManager::class.java)?.deviceConfigurationInfo?.reqGlEsVersion ?: 0) >= 0x30000
    } catch (_: Exception) {
        false
    }
    private val figure3dOn = mutableStateOf(true)
    private val figure3dBroken = mutableStateOf(figure3dGuard.crashedBefore)

    init {
        container.scope.launch {
            container.settings.data.collect {
                soundOn = it.soundEnabled
                hapticsOn = it.hapticsEnabled
                figure3dOn.value = it.figure3d
            }
        }
    }

    override val versionName: String get() = BuildConfig.VERSION_NAME

    override fun haptic(kind: Haptic) {
        if (!hapticsOn) return
        val v = vibrator ?: return
        try {
            if (!v.hasVibrator()) return
            val effect = when (kind) {
                Haptic.TICK -> VibrationEffect.createOneShot(25, VibrationEffect.DEFAULT_AMPLITUDE)
                Haptic.SUCCESS -> VibrationEffect.createOneShot(70, VibrationEffect.DEFAULT_AMPLITUDE)
                Haptic.ALERT -> VibrationEffect.createWaveform(longArrayOf(0, 280, 140, 280), -1)
            }
            v.vibrate(effect)
        } catch (_: Exception) {
            // Vibration ist nie kritisch
        }
    }

    override fun sound(kind: Sound) {
        if (!soundOn) return
        try {
            val generator = tone ?: ToneGenerator(AudioManager.STREAM_MUSIC, 80).also { tone = it }
            when (kind) {
                Sound.TICK -> generator.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
                Sound.GO -> generator.startTone(ToneGenerator.TONE_PROP_BEEP2, 400)
                Sound.DONE -> generator.startTone(ToneGenerator.TONE_PROP_ACK, 400)
            }
        } catch (_: Exception) {
            // ToneGenerator kann auf manchen Geräten fehlschlagen – dann eben still
            tone = null
        }
    }

    override fun notificationsAllowed(): Boolean {
        val enabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return enabled && granted
    }

    override fun batteryOptimized(): Boolean {
        val pm = context.getSystemService(PowerManager::class.java) ?: return false
        return !pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    private fun start(intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }

    private fun openAppDetails() {
        start(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
    }

    override fun openNotificationSettings() {
        val ok = start(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
        if (!ok) openAppDetails()
    }

    override fun openBatterySettings() {
        val ok = start(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        if (!ok) openAppDetails()
    }

    override fun sendTestNotification() {
        if (notificationsAllowed()) {
            Notifications.showTest(context)
        } else {
            toast("Benachrichtigungen sind blockiert – bitte in den Einstellungen erlauben.")
        }
    }

    override fun reducedMotion(): Boolean =
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

    override fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    override fun figure3dSupported(): Boolean = gles3

    @Composable
    override fun figure3dEnabled(): Boolean = gles3 && figure3dOn.value && !figure3dBroken.value

    override fun figure3dRetry() {
        figure3dGuard.reset()
        figure3dBroken.value = false
    }

    @Composable
    override fun Figure3D(exercise: Exercise, time: () -> Float, modifier: Modifier) {
        Figure3DView(exercise, time, modifier, figure3dGuard, onFailed = { figure3dBroken.value = true })
    }

    @Composable
    override fun KeepScreenOn() {
        val view = LocalView.current
        DisposableEffect(view) {
            view.keepScreenOn = true
            onDispose { view.keepScreenOn = false }
        }
    }

    @Composable
    override fun rememberNotificationPermissionRequest(onResult: (Boolean) -> Unit): () -> Unit {
        val callback by rememberUpdatedState(onResult)
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> callback(granted) }
        return {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                callback(true)
            }
        }
    }

    @Composable
    override fun rememberTextExport(suggestedName: String, content: suspend () -> String, onDone: (Boolean) -> Unit): () -> Unit {
        val scope = rememberCoroutineScope()
        val currentContent by rememberUpdatedState(content)
        val currentOnDone by rememberUpdatedState(onDone)
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri != null) {
                scope.launch {
                    val ok = try {
                        val text = currentContent()
                        withContext(Dispatchers.IO) {
                            val stream = context.contentResolver.openOutputStream(uri) ?: error("Datei nicht beschreibbar")
                            stream.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                        }
                        true
                    } catch (_: Exception) {
                        false
                    }
                    currentOnDone(ok)
                }
            }
        }
        return {
            try {
                launcher.launch(suggestedName)
            } catch (_: ActivityNotFoundException) {
                toast("Kein Datei-Manager gefunden.")
            }
        }
    }

    @Composable
    override fun rememberTextImport(onText: (String) -> Unit): () -> Unit {
        val scope = rememberCoroutineScope()
        val currentOnText by rememberUpdatedState(onText)
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                scope.launch {
                    val text = withContext(Dispatchers.IO) {
                        try {
                            context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                        } catch (_: Exception) {
                            null
                        }
                    }
                    if (text != null) currentOnText(text) else toast("Datei konnte nicht gelesen werden.")
                }
            }
        }
        return {
            try {
                launcher.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*"))
            } catch (_: ActivityNotFoundException) {
                toast("Kein Datei-Manager gefunden.")
            }
        }
    }
}
