package com.semiovert.app.core.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*

@Composable
internal fun rememberResultsEntrance(request: Any?,ready: Boolean,durationMillis: Int): State<Float> {
    val motion=LocalMotionEnabled.current&&LocalArticleExpansion.current?.returning!=true
    val roll=remember(request) {Animatable(if(request!=null)1f else 0f)}
    LaunchedEffect(roll,ready,motion) {
        if(ready&&roll.value>0f) {
            if(motion)roll.animateTo(0f,tween(durationMillis,easing=LinearEasing))
            else roll.snapTo(0f)
        }
    }
    return roll.asState()
}
