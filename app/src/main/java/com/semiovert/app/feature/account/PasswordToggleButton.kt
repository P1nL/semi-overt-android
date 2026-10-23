package com.semiovert.app.feature.account

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.semiovert.app.core.designsystem.LocalMotionEnabled
import com.semiovert.app.core.designsystem.SoEase
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.random.Random

/** Native version of the web PasswordToggleButton: same lids/pupil, without pointer tracking. */
@Composable
internal fun PasswordToggleButton(visible: Boolean,onVisibleChange: (Boolean)->Unit,modifier: Modifier=Modifier) {
    val motion=LocalMotionEnabled.current
    val lid=remember {Animatable(if(visible)1f else 0f)}
    LaunchedEffect(visible,motion) {
        if(!motion){lid.snapTo(if(visible)1f else 0f);return@LaunchedEffect}
        lid.animateTo(if(visible)1f else 0f,tween(125,easing=SoEase))
        if(!visible)while(isActive) {
            delay(Random.nextLong(2000,8001))
            lid.animateTo(1f,tween(75,easing=SoEase))
            lid.animateTo(0f,tween(75,easing=SoEase))
        }
    }
    val ink=MaterialTheme.colorScheme.onSurfaceVariant
    val glint=MaterialTheme.colorScheme.background
    val spark=com.semiovert.app.core.designsystem.rememberSparkControl()
    IconToggleButton(checked=visible,onCheckedChange=onVisibleChange,interactionSource=spark.source,modifier=modifier.size(48.dp).then(spark.modifier)
        .clip(RoundedCornerShape(12.dp)).semantics {contentDescription=if(visible)"隐藏密码" else "显示密码"}) {
        Canvas(Modifier.size(24.dp)) {
            scale(size.width/24f,size.height/24f,pivot=Offset.Zero) {
                val upperY=4f+16f*lid.value
                val upper=Path().apply {
                    moveTo(1f,12f);cubicTo(1f,12f,5f,upperY,12f,upperY);cubicTo(19f,upperY,23f,12f,23f,12f)
                }
                val lower=Path().apply {
                    moveTo(1f,12f);cubicTo(1f,12f,5f,20f,12f,20f);cubicTo(19f,20f,23f,12f,23f,12f)
                }
                val aperture=Path().apply {
                    addPath(upper)
                    cubicTo(23f,12f,19f,20f,12f,20f);cubicTo(5f,20f,1f,12f,1f,12f);close()
                }
                clipPath(aperture) {
                    drawCircle(ink,4f,Offset(12f,12f))
                    drawCircle(glint,1f,Offset(13f,11f))
                }
                val outline=Stroke(1.5f,cap=StrokeCap.Round,join=StrokeJoin.Round)
                drawPath(upper,ink,style=outline)
                drawPath(lower,ink,style=outline)
            }
        }
    }
}
