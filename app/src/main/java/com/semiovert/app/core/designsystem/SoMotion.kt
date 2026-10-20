package com.semiovert.app.core.designsystem

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalContext
import kotlin.math.hypot

val SoEase = CubicBezierEasing(.22f,1f,.36f,1f)
/** Scene travel uses the whole interval instead of spending its last half nearly stationary. */
val SoSceneEase = CubicBezierEasing(.42f,0f,.58f,1f)
val LocalMotionEnabled = staticCompositionLocalOf { true }
/** One shared flow coordinate: 0 is resting, 1 is rolled out below the viewport. */
val LocalFlowRoll = compositionLocalOf { 0f }

@Composable
fun MotionEnvironment(content: @Composable ()->Unit) {
    val resolver=LocalContext.current.contentResolver
    fun animationsEnabled()=Settings.Global.getFloat(resolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)>0f
    var enabled by remember {mutableStateOf(animationsEnabled())}
    DisposableEffect(resolver) {
        val observer=object: ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {enabled=animationsEnabled()}
        }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),false,observer)
        onDispose {resolver.unregisterContentObserver(observer)}
    }
    CompositionLocalProvider(LocalMotionEnabled provides enabled,content=content)
}

@Composable
fun motionMillis(value: Int)=if(LocalMotionEnabled.current)value else 0

fun Modifier.circleReveal(progress: Float,origin: Offset)=drawWithContent {
    if(progress>=.999f) drawContent()
    else if(progress>0f) {
        val center=Offset(size.width*origin.x,size.height*origin.y)
        val radius=hypot(maxOf(center.x,size.width-center.x),maxOf(center.y,size.height-center.y))*progress
        clipPath(Path().apply {addOval(androidx.compose.ui.geometry.Rect(center,radius))}) {this@drawWithContent.drawContent()}
    }
}
