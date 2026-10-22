package com.semiovert.app.navigation

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.abs

internal object SwipeBackGesture {
    fun acceptsDirection(delta: Offset)=delta.x>0f&&delta.x>abs(delta.y)*1.4f
    fun commits(distance: Float,velocity: Float,width: Float,density: Float)=
        distance>=maxOf(80f*density,width*.22f)||(distance>=32f*density&&velocity>=900f*density)
}

/** An anywhere-on-page gesture. Children keep priority over text selection and existing drags. */
@Composable
internal fun SwipeBackPage(enabled: Boolean,onBack: ()->Unit,content: @Composable ()->Unit) {
    val currentBack by rememberUpdatedState(onBack)
    val density=LocalDensity.current.density
    Box(Modifier.fillMaxSize().pointerInput(enabled,density) {
        if(!enabled)return@pointerInput
        awaitEachGesture {
            val down=awaitFirstDown()
            val tracker=VelocityTracker().apply {addPosition(down.uptimeMillis,down.position)}
            var acquired=false
            var distance=0f
            while(true) {
                val change=awaitPointerEvent().changes.firstOrNull {it.id==down.id}?:break
                tracker.addPosition(change.uptimeMillis,change.position)
                val delta=change.position-down.position
                if(change.isConsumed)break
                if(!change.pressed) {
                    if(acquired&&SwipeBackGesture.commits(distance,tracker.calculateVelocity().x,size.width.toFloat(),density))currentBack()
                    break
                }
                if(!acquired&&delta.getDistance()>viewConfiguration.touchSlop) {
                    if(!SwipeBackGesture.acceptsDirection(delta))break
                    acquired=true
                }
                if(acquired){distance=delta.x;change.consume()}
            }
        }
    }) {content()}
}
