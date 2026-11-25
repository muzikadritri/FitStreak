package de.leon.fitstreak.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Farben wie Hantelscheiben: Blau (Hauptfarbe), Rot (Streak), Gelb (XP), Grün (erledigt). */
object Brand {
    val Blue = Color(0xFF1746C7)
    val BlueDeep = Color(0xFF0E2A7A)
    val BlueBright = Color(0xFF2F66F2)
    val Red = Color(0xFFE8382D)
    val Orange = Color(0xFFFF8A1F)
    val Yellow = Color(0xFFFFC21A)
    val Green = Color(0xFF1FA85A)
}

@Immutable
data class FitColors(
    val streak: Color,
    val xp: Color,
    val onXp: Color,
    val done: Color,
    val card: Color,
    val cardMuted: Color,
    val muted: Color,
    val heroStart: Color,
    val heroEnd: Color,
    val onHero: Color,
    val bodyBase: Color,
    val isDark: Boolean,
)

private val LightFit = FitColors(
    streak = Brand.Red,
    xp = Brand.Yellow,
    onXp = Color(0xFF3A2A00),
    done = Brand.Green,
    card = Color.White,
    cardMuted = Color(0xFFEDF1FA),
    muted = Color(0xFF6A7389),
    heroStart = Brand.BlueBright,
    heroEnd = Brand.BlueDeep,
    onHero = Color.White,
    bodyBase = Color(0xFFD3D9E6),
    isDark = false,
)

private val DarkFit = FitColors(
    streak = Color(0xFFFF5A4D),
    xp = Brand.Yellow,
    onXp = Color(0xFF3A2A00),
    done = Color(0xFF3DCB7C),
    card = Color(0xFF181E30),
    cardMuted = Color(0xFF222A40),
    muted = Color(0xFF98A2B8),
    heroStart = Color(0xFF2452D6),
    heroEnd = Color(0xFF0B1F5C),
    onHero = Color.White,
    bodyBase = Color(0xFF39425A),
    isDark = true,
)

val LocalFitColors = staticCompositionLocalOf { LightFit }

private val LightScheme = lightColorScheme(
    primary = Brand.Blue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE6FF),
    onPrimaryContainer = Color(0xFF001A5C),
    secondary = Color(0xFF3F5AA9),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE3E9FA),
    onSecondaryContainer = Color(0xFF14204A),
    tertiary = Brand.Red,
    onTertiary = Color.White,
    background = Color(0xFFF4F6FB),
    onBackground = Color(0xFF151B2B),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF151B2B),
    surfaceVariant = Color(0xFFE7EBF5),
    onSurfaceVariant = Color(0xFF50596E),
    outline = Color(0xFF8D96AB),
    outlineVariant = Color(0xFFD5DAE6),
    error = Color(0xFFBA1A1A),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF9DB6FF),
    onPrimary = Color(0xFF00227A),
    primaryContainer = Color(0xFF1E3FA8),
    onPrimaryContainer = Color(0xFFDCE6FF),
    secondary = Color(0xFFB4C4F2),
    onSecondary = Color(0xFF1B2B5E),
    secondaryContainer = Color(0xFF2A3760),
    onSecondaryContainer = Color(0xFFDDE4FA),
    tertiary = Color(0xFFFF8A80),
    onTertiary = Color(0xFF5A0A05),
    background = Color(0xFF0E1220),
    onBackground = Color(0xFFE8ECF6),
    surface = Color(0xFF141929),
    onSurface = Color(0xFFE8ECF6),
    surfaceVariant = Color(0xFF232A3F),
    onSurfaceVariant = Color(0xFFA9B2C7),
    outline = Color(0xFF6E7890),
    outlineVariant = Color(0xFF2F374D),
    error = Color(0xFFFFB4AB),
)

private val base = Typography()

private val FitTypography = Typography(
    displayLarge = base.displayLarge.copy(fontWeight = FontWeight.Black),
    displayMedium = base.displayMedium.copy(fontWeight = FontWeight.Black),
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Black),
    headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.ExtraBold),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.Bold),
    titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
)

private val FitShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun FitTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalFitColors provides if (dark) DarkFit else LightFit) {
        MaterialTheme(
            colorScheme = if (dark) DarkScheme else LightScheme,
            typography = FitTypography,
            shapes = FitShapes,
            content = content,
        )
    }
}

val fit: FitColors
    @Composable get() = LocalFitColors.current
