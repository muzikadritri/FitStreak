package de.leon.fitstreak.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable

/** Kleine Hülle um die System-Abstände (Edge-to-Edge). */
object WindowInsetsCompat {
    @Composable
    fun statusBars(): PaddingValues = WindowInsets.statusBars.asPaddingValues()
}
