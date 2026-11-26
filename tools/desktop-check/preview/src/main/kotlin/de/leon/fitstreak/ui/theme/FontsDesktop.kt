// Desktop-Gegenstück zu app/.../ui/theme/Fonts.kt: lädt dieselben TTF-Dateien direkt aus res/font.
package de.leon.fitstreak.ui.theme

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import java.io.File

private fun font(name: String, weight: FontWeight) =
    Font(File(System.getProperty("fitstreak.fonts", "../../../app/src/main/res/font"), "$name.ttf"), weight)

val SansFamily = FontFamily(
    font("ibm_plex_sans_regular", FontWeight.Normal),
    font("ibm_plex_sans_medium", FontWeight.Medium),
    font("ibm_plex_sans_semibold", FontWeight.SemiBold),
)

val MonoFamily = FontFamily(
    font("ibm_plex_mono_regular", FontWeight.Normal),
    font("ibm_plex_mono_medium", FontWeight.Medium),
)
