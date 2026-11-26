package de.leon.fitstreak.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Farben der Hantelscheiben – jede hat genau eine Bedeutung:
 * Blau = Aktion/Auswahl, Rot = Streak, Gelb = XP, Grün = erledigt.
 */
object Brand {
    val Blue = Color(0xFF1746C7)
    val Red = Color(0xFFDC2626)
    val Yellow = Color(0xFFE0A100)
    val Green = Color(0xFF16A34A)
}

@Immutable
data class FitColors(
    val bg: Color,
    val card: Color,
    /** Leicht angehobene Fläche: Eingaben, aktive Zeilen, Figuren-Hintergrund. */
    val cardMuted: Color,
    val hairline: Color,
    val text: Color,
    val textSecondary: Color,
    val muted: Color,
    val accent: Color,
    val onAccent: Color,
    /** Akzent als Textfarbe auf Flächen. */
    val accentInk: Color,
    val streak: Color,
    val xp: Color,
    val xpInk: Color,
    val onXp: Color,
    val done: Color,
    val bodyBase: Color,
    val isDark: Boolean,
)

private val LightFit = FitColors(
    bg = Color(0xFFFAFAFA),
    card = Color(0xFFFFFFFF),
    cardMuted = Color(0xFFF4F4F5),
    hairline = Color(0x14171717),
    text = Color(0xFF171717),
    textSecondary = Color(0xFF525252),
    muted = Color(0xFF737373),
    accent = Brand.Blue,
    onAccent = Color.White,
    accentInk = Brand.Blue,
    streak = Brand.Red,
    xp = Brand.Yellow,
    xpInk = Color(0xFF8A6100),
    onXp = Color(0xFF171717),
    done = Brand.Green,
    bodyBase = Color(0xFFE4E4E7),
    isDark = false,
)

private val DarkFit = FitColors(
    bg = Color(0xFF141415),
    card = Color(0xFF19191B),
    cardMuted = Color(0xFF232325),
    hairline = Color(0x14EDEDED),
    text = Color(0xFFEDEDED),
    textSecondary = Color(0xFFA1A1A6),
    muted = Color(0xFF85858B),
    accent = Color(0xFF3A63E6),
    onAccent = Color.White,
    accentInk = Color(0xFF8FA8FF),
    streak = Color(0xFFE5484D),
    xp = Color(0xFFF2B600),
    xpInk = Color(0xFFF2B600),
    onXp = Color(0xFF171717),
    done = Color(0xFF4CC38A),
    bodyBase = Color(0xFF303033),
    isDark = true,
)

val LocalFitColors = staticCompositionLocalOf { LightFit }

private fun scheme(c: FitColors) = if (c.isDark) {
    darkColorScheme(
        primary = c.accent, onPrimary = c.onAccent,
        primaryContainer = c.cardMuted, onPrimaryContainer = c.text,
        secondary = c.accentInk, onSecondary = c.bg,
        secondaryContainer = c.cardMuted, onSecondaryContainer = c.text,
        tertiary = c.streak, onTertiary = Color.White,
        background = c.bg, onBackground = c.text,
        surface = c.card, onSurface = c.text,
        surfaceVariant = c.cardMuted, onSurfaceVariant = c.textSecondary,
        surfaceTint = c.card,
        outline = Color(0xFF5A5A5F), outlineVariant = Color(0xFF2A2A2C),
        error = Color(0xFFE5484D), onError = Color.White,
    )
} else {
    lightColorScheme(
        primary = c.accent, onPrimary = c.onAccent,
        primaryContainer = c.cardMuted, onPrimaryContainer = c.text,
        secondary = c.accentInk, onSecondary = Color.White,
        secondaryContainer = c.cardMuted, onSecondaryContainer = c.text,
        tertiary = c.streak, onTertiary = Color.White,
        background = c.bg, onBackground = c.text,
        surface = c.card, onSurface = c.text,
        surfaceVariant = c.cardMuted, onSurfaceVariant = c.textSecondary,
        surfaceTint = c.card,
        outline = Color(0xFFA3A3A3), outlineVariant = Color(0xFFE5E5E5),
        error = Color(0xFFDC2626), onError = Color.White,
    )
}

private fun sans(size: Int, line: Int, weight: FontWeight = FontWeight.Normal, spacing: Double = 0.0) = TextStyle(
    fontFamily = SansFamily, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight, letterSpacing = spacing.sp,
)

/** Hierarchie über Größe und Farbe, nicht über Fettung: nur 400 und 500, selten 600. */
private val FitTypography = Typography(
    displayLarge = sans(72, 72, FontWeight.Medium, -1.6),
    displayMedium = sans(56, 60, FontWeight.Medium, -1.2),
    displaySmall = sans(40, 44, FontWeight.Medium, -0.8),
    headlineLarge = sans(32, 38, FontWeight.Medium, -0.4),
    headlineMedium = sans(28, 34, FontWeight.Medium, -0.3),
    headlineSmall = sans(22, 28, FontWeight.Medium),
    titleLarge = sans(18, 24, FontWeight.Medium),
    titleMedium = sans(16, 22, FontWeight.Medium),
    titleSmall = sans(14, 20, FontWeight.Medium),
    bodyLarge = sans(16, 24),
    bodyMedium = sans(14, 20),
    bodySmall = sans(13, 18),
    labelLarge = sans(14, 20, FontWeight.Medium),
    labelMedium = sans(12, 16, FontWeight.Medium),
    labelSmall = sans(12, 16),
)

/** Zahlen und Meta-Angaben in Mono mit Tabellenziffern. */
object FitText {
    val meta = TextStyle(fontFamily = MonoFamily, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal)
    val figureSmall = TextStyle(fontFamily = MonoFamily, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
    val figure = TextStyle(fontFamily = MonoFamily, fontSize = 24.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium)
    val figureLarge = TextStyle(fontFamily = MonoFamily, fontSize = 44.sp, lineHeight = 48.sp, fontWeight = FontWeight.Medium, letterSpacing = (-1).sp)
    val hero = TextStyle(fontFamily = SansFamily, fontSize = 88.sp, lineHeight = 88.sp, fontWeight = FontWeight.Medium, letterSpacing = (-3).sp, fontFeatureSettings = "tnum")
}

private val FitShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp),
)

/** Eine Kurve, wenige Dauern – überall in der App. */
object Motion {
    /** Kräftiges Ease-out für alles, was erscheint oder reagiert. */
    val EaseOut = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

    /** Für Bewegung, die auf dem Bildschirm bleibt. */
    val EaseInOut = CubicBezierEasing(0.77f, 0f, 0.175f, 1f)

    const val PRESS = 120
    const val FAST = 160
    const val BASE = 220
    const val ENTER = 300

    fun <T> press(): FiniteAnimationSpec<T> = tween(PRESS, easing = EaseOut)
    fun <T> fast(): FiniteAnimationSpec<T> = tween(FAST, easing = EaseOut)
    fun <T> base(): FiniteAnimationSpec<T> = tween(BASE, easing = EaseOut)
    fun <T> enter(delay: Int = 0): FiniteAnimationSpec<T> = tween(ENTER, delayMillis = delay, easing = EaseOut)
}

@Composable
fun FitTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (dark) DarkFit else LightFit
    CompositionLocalProvider(LocalFitColors provides colors) {
        MaterialTheme(colorScheme = scheme(colors), typography = FitTypography, shapes = FitShapes) {
            CompositionLocalProvider(LocalContentColor provides colors.text, content = content)
        }
    }
}

val fit: FitColors
    @Composable get() = LocalFitColors.current
