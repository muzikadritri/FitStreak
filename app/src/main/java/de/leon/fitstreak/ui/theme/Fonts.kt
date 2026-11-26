package de.leon.fitstreak.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import de.leon.fitstreak.R

/** IBM Plex (SIL Open Font License, siehe licenses/IBM-Plex-OFL.txt) – in der App eingebettet, kein Download. */
val SansFamily = FontFamily(
    Font(R.font.ibm_plex_sans_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_sans_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_sans_semibold, FontWeight.SemiBold),
)

/** Für Zahlen und Meta-Angaben (Sätze, Gewichte, Timer). */
val MonoFamily = FontFamily(
    Font(R.font.ibm_plex_mono_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_mono_medium, FontWeight.Medium),
)
