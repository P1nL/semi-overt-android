package com.semiovert.app.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.semiovert.app.R
import com.semiovert.app.core.designsystem.CircleColors
import com.semiovert.app.core.designsystem.SoEase
import com.semiovert.app.core.designsystem.motionMillis

private val HomeCircleColors=CircleColors(Color(0xFF00C98D),Color(0xFF12251E))
private val CategoryCircleColors=CircleColors(Color(0xFFFF7A00),Color(0xFF291A0D))
private val SearchCircleColors=CircleColors(Color(0xFF2962FF),Color.White)
private val ProfileCircleColors=CircleColors(Color(0xFFA12BFF),Color.White)

internal val Destination.circleColors: CircleColors get()=when(this) {
    Destination.HOME -> HomeCircleColors
    Destination.CATEGORY -> CategoryCircleColors
    Destination.SEARCH -> SearchCircleColors
    Destination.PROFILE -> ProfileCircleColors
}

internal val NavigationLabelFont=FontFamily(Font(R.font.navigation_label_rounded,FontWeight.Bold))

@Composable
internal fun animatedCircleColors(target: CircleColors,label: String): CircleColors {
    val duration=motionMillis(180)
    return CircleColors(
        animateColorAsState(target.background,tween(duration,easing=SoEase),label="$label-background").value,
        animateColorAsState(target.foreground,tween(duration,easing=SoEase),label="$label-foreground").value,
    )
}
