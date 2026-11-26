package com.semiovert.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.semiovert.app.core.designsystem.SoPalette
import com.semiovert.app.core.designsystem.accentInk
import com.semiovert.app.core.designsystem.soColorScheme
import com.semiovert.app.navigation.Destination
import com.semiovert.app.navigation.HomeDraftAction
import com.semiovert.app.navigation.circleColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SoPaletteTest {
    @Test fun navigationAndDraftActionsUseTheirPaletteSwatches() {
        assertEquals(SoPalette.Teal, Destination.HOME.circleColors.background)
        assertEquals(SoPalette.CarrotOrange, Destination.CATEGORY.circleColors.background)
        assertEquals(Color(0xFF1F50D5), SoPalette.FrenchBlue)
        assertEquals(SoPalette.FrenchBlue, Destination.SEARCH.circleColors.background)
        assertEquals(SoPalette.Amethyst, Destination.PROFILE.circleColors.background)
        assertEquals(SoPalette.BrightAmber, HomeDraftAction.CREATE.colors.background)
        assertEquals(SoPalette.MintLeaf, HomeDraftAction.BOX.colors.background)
    }

    @Test fun lightAndDarkThemeRolesHaveReadableForegrounds() {
        for (dark in listOf(false, true)) {
            val colors = soColorScheme(dark)
            assertEquals(if (dark) SoPalette.MintLeaf else SoPalette.Teal, colors.primary)
            assertEquals(if (dark) SoPalette.Tomato else SoPalette.TomatoJam, colors.error)
            listOf(
                colors.background to colors.onBackground,
                colors.surfaceVariant to colors.onSurfaceVariant,
                colors.surface to colors.accentInk, colors.surface to colors.tertiary,
                colors.primary to colors.onPrimary, colors.primaryContainer to colors.onPrimaryContainer,
                colors.secondary to colors.onSecondary, colors.secondaryContainer to colors.onSecondaryContainer,
                colors.tertiary to colors.onTertiary, colors.tertiaryContainer to colors.onTertiaryContainer,
                colors.error to colors.onError, colors.errorContainer to colors.onErrorContainer,
            ).forEach { (fill, ink) -> assertReadable(fill, ink) }
        }
    }

    private fun assertReadable(fill: Color, ink: Color) {
        val background = fill.luminance()
        val foreground = ink.luminance()
        assertTrue("$ink on $fill",(maxOf(background, foreground) + .05f) / (minOf(background, foreground) + .05f) >= 4.5f)
    }
}
