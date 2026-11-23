package com.semiovert.app.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.semiovert.app.R
import com.semiovert.app.core.designsystem.CircleColors
import com.semiovert.app.core.designsystem.SoEase
import com.semiovert.app.core.designsystem.SoGreen
import com.semiovert.app.core.designsystem.SoPalette
import com.semiovert.app.core.designsystem.motionMillis

internal fun paletteCircleColors(swatch: Color)=CircleColors(swatch,SoPalette.inkOn(swatch))

private val HomeCircleColors=paletteCircleColors(SoGreen)
private val CategoryCircleColors=paletteCircleColors(SoPalette.CarrotOrange)
private val SearchCircleColors=paletteCircleColors(SoPalette.FrenchBlue)
private val ProfileCircleColors=paletteCircleColors(SoPalette.Amethyst)

internal val Destination.circleColors: CircleColors get()=when(this) {
    Destination.HOME -> HomeCircleColors
    Destination.CATEGORY -> CategoryCircleColors
    Destination.SEARCH -> SearchCircleColors
    Destination.PROFILE -> ProfileCircleColors
}

internal val NavigationLabelFont=FontFamily(Font(R.font.navigation_label_rounded,FontWeight.Bold))
internal val NavigationCircleSize=52.dp
internal val NavigationCircleIconSize=24.dp

@Composable
internal fun animatedCircleColors(target: CircleColors,label: String): CircleColors {
    val duration=motionMillis(180)
    return CircleColors(
        animateColorAsState(target.background,tween(duration,easing=SoEase),label="$label-background").value,
        animateColorAsState(target.foreground,tween(duration,easing=SoEase),label="$label-foreground").value,
    )
}
