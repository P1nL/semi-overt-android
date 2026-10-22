package com.semiovert.app.core.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Same four-cubic sun/moon contour as the web ThemeMorphIcon, with a native touch target. */
@Composable
fun ThemeToggleButton(onClick: ()->Unit,modifier: Modifier=Modifier) {
    val reveal=LocalThemeReveal.current
    var center by remember {mutableStateOf(Offset.Zero)}
    val dark=MaterialTheme.colorScheme.background.luminance()<.5f
    val moon by animateFloatAsState(if(dark)1f else 0f,
        tween(if(LocalMotionEnabled.current)220 else 0,easing=SoSceneEase),label="theme-icon")
    val ink=MaterialTheme.colorScheme.onSurfaceVariant
    IconButton(onClick={if(reveal!=null)reveal.switchAt(center,onClick) else onClick()},
        modifier=modifier.size(48.dp).testTag("home-theme-toggle").onGloballyPositioned {center=it.boundsInRoot().center}
        .semantics {contentDescription=if(dark)"切换到浅色模式" else "切换到深色模式"}) {
        Canvas(Modifier.size(24.dp)) {
            val r=4.1f;val k=r*.55228475f;val lo=12f-r;val hi=12f+r
            val sun=floatArrayOf(12f,lo,12f-k,lo,lo,12f-k,lo,12f,lo,12f+k,12f-k,hi,12f,hi,
                12f+k,hi,hi,12f+k,hi,12f,hi,12f-k,12f+k,lo,12f,lo)
            val night=floatArrayOf(9f,3f,5.5f,4f,3f,7.6f,3f,12f,3f,17f,7f,21f,12f,21f,
                15.8f,21f,19.1f,18.6f,20.4f,15.2f,12.6f,16.4f,7.6f,10.3f,9f,3f)
            val points=sun.indices.map {sun[it]+(night[it]-sun[it])*moon}
            scale(size.width/24f,size.height/24f,pivot=Offset.Zero) {
                val outline=Path().apply {
                    moveTo(points[0],points[1])
                    for(i in 2 until points.size step 6)cubicTo(points[i],points[i+1],points[i+2],points[i+3],points[i+4],points[i+5])
                    close()
                }
                drawPath(outline,ink,style=Stroke(1.9f,cap=StrokeCap.Round))
                val rays=((1f-moon-.35f)/.65f).coerceIn(0f,1f)
                repeat(8) {i->rotate(i*45f,Offset(12f,12f)) {
                    drawLine(ink,Offset(12f,7.9f-5.2f*rays),Offset(12f,7.9f-3f*rays),1.9f,StrokeCap.Round,alpha=rays)
                }}
                fun star(x: Float,y: Float,radius: Float) {
                    val inner=radius*.26f
                    val path=Path().apply {
                        moveTo(x,y-radius);lineTo(x+inner,y-inner);lineTo(x+radius,y);lineTo(x+inner,y+inner)
                        lineTo(x,y+radius);lineTo(x-inner,y+inner);lineTo(x-radius,y);lineTo(x-inner,y-inner);close()
                    }
                    drawPath(path,ink,alpha=moon)
                }
                star(17f,6f,3.5f);star(21f,10.5f,2f)
            }
        }
    }
}
