package com.semiovert.app.feature.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.semiovert.app.core.designsystem.SoPalette
import com.semiovert.app.core.designsystem.soColorScheme

internal data class EditorPalette(
    val name: String,
    val background: Int,
    val foreground: Int,
    val waveStart: Int,
    val waveEnd: Int,
) {
    companion object {
        val Light = create(false, SoPalette.BrightAmber, SoPalette.AutumnLeaf)
        val Dark = create(true, SoPalette.Teal, SoPalette.FrenchBlue)

        fun forTheme(dark: Boolean) = if (dark) Dark else Light

        private fun create(dark: Boolean, waveStart: Color, waveEnd: Color): EditorPalette {
            val colors = soColorScheme(dark)
            return EditorPalette(
                if (dark) "dark" else "light", colors.background.toArgb(), colors.onBackground.toArgb(),
                waveStart.toArgb(), waveEnd.toArgb(),
            )
        }
    }
}
