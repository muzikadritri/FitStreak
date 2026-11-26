package de.leon.fitstreak.platform

import android.content.SharedPreferences
import android.util.Log
import android.view.TextureView
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import de.leon.fitstreak.core.model.Exercise
import de.leon.fitstreak.figure3d.Figure3Colors
import de.leon.fitstreak.figure3d.Figure3DRuntime
import de.leon.fitstreak.figure3d.FigureRenderer
import de.leon.fitstreak.ui.components.FigureDefaults

/** Merkt sich über Abstürze hinweg, ob die 3D-Engine auf diesem Gerät sauber startet. */
internal class Figure3DGuard(private val prefs: SharedPreferences) {
    /** true, wenn die App beim letzten Mal mitten in der 3D-Ansicht beendet wurde (z. B. Treiber-Absturz). */
    val crashedBefore: Boolean = prefs.getBoolean(PENDING, false)
    private var verified = false

    fun beforeStart() {
        if (!verified) prefs.edit().putBoolean(PENDING, true).commit()
    }

    /** Die Ansicht hat eine Weile sauber gezeichnet: Die Engine läuft auf diesem Gerät. */
    fun succeeded() {
        if (!verified) {
            verified = true
            prefs.edit().putBoolean(PENDING, false).apply()
            Log.i(Figure3DRuntime.TAG, "3D-Ansicht läuft stabil")
        }
    }

    /** Die Ansicht wurde regulär geschlossen (oder kam gar nicht erst zustande) – kein Absturz. */
    fun reset() {
        if (!verified) prefs.edit().putBoolean(PENDING, false).apply()
    }

    /** Neuer Versuch nach einem vermerkten Absturz. */
    fun retry() {
        prefs.edit().putBoolean(PENDING, false).apply()
    }

    private companion object {
        const val PENDING = "start_pending"
    }
}

private fun colorsFor(dark: Boolean): Figure3Colors {
    val base = if (dark) FigureDefaults.Dark else FigureDefaults.Light
    fun rgb(c: androidx.compose.ui.graphics.Color) = c.toArgb() and 0xFFFFFF
    return Figure3Colors(
        body = if (dark) 0xD9D9DE else 0xE9E9ED,
        joint = if (dark) 0x4A4A50 else 0x2B2B30,
        primary = rgb(base.primary),
        secondary = rgb(base.secondary),
        mat = if (dark) 0x38383C else 0xD6D6DB,
        steel = if (dark) 0xB4B4BA else 0x9A9AA2,
        dark = if (dark) 0x5A5A61 else 0x3C3C42,
        band = rgb(base.band),
    )
}

private class RendererHolder {
    var renderer: FigureRenderer? = null
}

/**
 * 3D-Ansicht einer Übung. Die Figur lässt sich mit einem seitlichen Wisch drehen, Doppeltipp stellt
 * die Startansicht wieder her. Senkrechte Wischgesten bleiben beim Scrollen der Seite.
 */
@Composable
internal fun Figure3DView(
    exercise: Exercise,
    time: () -> Float,
    modifier: Modifier,
    guard: Figure3DGuard,
    onFailed: () -> Unit,
) {
    val colors = colorsFor(isSystemInDarkTheme())
    val holder = remember { RendererHolder() }
    var yaw by remember { mutableFloatStateOf(0f) }
    // neue Übung = wieder die Startansicht
    LaunchedEffect(exercise.id) { yaw = 0f }
    val currentTime by rememberUpdatedState(time)
    val currentExercise by rememberUpdatedState(exercise)
    val currentColors by rememberUpdatedState(colors)
    val currentOnFailed by rememberUpdatedState(onFailed)
    val density = LocalDensity.current.density

    AndroidView(
        factory = { context ->
            TextureView(context).also { view ->
                view.isOpaque = false
                // unsichtbar starten und mit dem ersten fertigen Bild einblenden – kein Aufblitzen
                view.alpha = 0.01f
                try {
                    guard.beforeStart()
                    val runtime = Figure3DRuntime.get(context) ?: error("3D-Engine startet nicht")
                    holder.renderer = FigureRenderer(runtime, view).also { renderer ->
                        renderer.onFirstFrame = {
                            Log.i(Figure3DRuntime.TAG, "erstes 3D-Bild gezeichnet")
                            view.animate().alpha(1f).setDuration(220).start()
                        }
                        renderer.onStable = { guard.succeeded() }
                    }
                } catch (t: Throwable) {
                    Log.w(Figure3DRuntime.TAG, "3D-Ansicht nicht verfügbar, nutze 2D", t)
                    guard.reset()
                    view.post { currentOnFailed() }
                }
            }
        },
        modifier = modifier
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = { Log.i(Figure3DRuntime.TAG, "gedreht auf ${yaw.toInt()} Grad") },
                ) { change, dragAmount ->
                    change.consume()
                    yaw -= dragAmount / density * 0.45f
                }
            }
            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { yaw = 0f }) },
    )

    DisposableEffect(Unit) {
        onDispose {
            holder.renderer?.destroy()
            holder.renderer = null
            guard.reset()
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { frameTime ->
                val renderer = holder.renderer ?: return@withFrameNanos
                val e = currentExercise
                try {
                    renderer.setColors(currentColors)
                    renderer.setExercise(e.animation, e.primaryParts, e.secondaryParts)
                    renderer.setView(yaw, 0f)
                    renderer.frame(currentTime(), frameTime)
                } catch (t: Throwable) {
                    Log.w(Figure3DRuntime.TAG, "3D-Zeichnen fehlgeschlagen, nutze 2D", t)
                    holder.renderer = null
                    try { renderer.destroy() } catch (_: Throwable) { }
                    currentOnFailed()
                }
            }
        }
    }
}
