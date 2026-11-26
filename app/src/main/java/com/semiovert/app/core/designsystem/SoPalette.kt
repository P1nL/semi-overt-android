package com.semiovert.app.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

internal object SoPalette {
    val TomatoJam = Color(0xFFC33538)
    val AutumnLeaf = Color(0xFFEB641B)
    val CarrotOrange = Color(0xFFF8961E)
    val Tomato = Color(0xFFE96149)
    val BrightAmber = Color(0xFFFFCF10)
    val Olive = Color(0xFF6A7C20)
    val MintLeaf = Color(0xFF2CB38B)
    val Teal = Color(0xFF118480)
    val Amethyst = Color(0xFF9946A9)
    val Plum = Color(0xFF653563)
    val FrenchBlue = Color(0xFF1F50D5)

    val LightSurface = Color(0xFFFFFDF3)
    val DarkSurface = Color(0xFF191A1C)
    val LightInk = Color(0xFF1B1F2C)
    val DarkInk = Color(0xFFE8E6DE)

    fun inkOn(swatch: Color) =
        if (1.05f / (swatch.luminance() + .05f) >= 4.5f) Color.White else DarkSurface
}
