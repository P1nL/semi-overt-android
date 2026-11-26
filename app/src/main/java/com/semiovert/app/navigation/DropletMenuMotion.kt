package com.semiovert.app.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.util.lerp
import com.semiovert.app.core.designsystem.LiquidCircleGeometry
import com.semiovert.app.core.designsystem.LiquidShadowPart
import com.semiovert.app.core.designsystem.drawLiquidBridge
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow

internal object DropletMenuMotion {
    private val springOut=Easing {fraction ->
        if(fraction==0f||fraction==1f)fraction
        else (1.0-2.0.pow(-10.0*fraction)*cos(4.0*PI*fraction)).toFloat()
    }
    private val acceleratingIn=Easing {fraction ->fraction*fraction*fraction*fraction}
    fun stagger(rank: Int,open: Boolean)=rank*(if(open)50L else 35L)
    fun travel(open: Boolean)=if(open)tween<Float>(950,easing=springOut) else tween(500,easing=acceleratingIn)
    fun icon(open: Boolean)=if(open)tween<Float>(300,delayMillis=140,easing=LinearEasing) else tween(180,easing=LinearEasing)
    fun scale(progress: Float)=lerp(.55f,1f,progress.coerceAtLeast(0f))

    fun coreCenter(density: Float)=Offset(28f,28f)*density

    fun join(offset: Offset,scale: Float,density: Float,color: Color,stretch: Float=0f,elasticStretch: Float=0f): MenuDropJoin? {
        val main=coreCenter(density)
        val small=main+offset*density
        val radius=NavigationCircleSize.value*.5f*scale*density
        val mainRadius=28f*density
        val bridge=LiquidCircleGeometry.bridge(main,mainRadius,small,radius,mainRadius+radius+16f*density)?:return null
        return MenuDropJoin(LiquidShadowPart(small,radius,stretch,elasticStretch,bridge),color)
    }
}

internal class MenuDropletState(initial: Float=0f) {
    val travel=Animatable(initial)
    val icon=Animatable(initial)

    suspend fun snapTo(value: Float) {
        travel.snapTo(value)
        icon.snapTo(value)
    }

    suspend fun animateTo(open: Boolean,rank: Int,motion: Boolean,onTravel: ((Float)->Unit)?=null) {
        val target=if(open)1f else 0f
        if(!motion) {
            snapTo(target)
            onTravel?.invoke(target)
        } else if(travel.value!=target||icon.value!=target) {
            delay(DropletMenuMotion.stagger(rank,open))
            coroutineScope {
                launch {icon.animateTo(target,DropletMenuMotion.icon(open))}
                travel.animateTo(target,DropletMenuMotion.travel(open)) {onTravel?.invoke(value)}
            }
        }
    }
}

internal data class MenuDropJoin(val silhouette: LiquidShadowPart,val color: Color)

@Composable
internal fun MenuDropletBridges(mainCenter: Offset,mainColor: Color,joins: Collection<MenuDropJoin>,modifier: Modifier) {
    if(joins.isEmpty())return
    Canvas(modifier) {
        val main=DropletMenuMotion.coreCenter(density)
        val shift=mainCenter*density-main
        translate(shift.x,shift.y) {
            for(join in joins) {
                val bridge=checkNotNull(join.silhouette.bridge)
                if(join.color==mainColor)drawLiquidBridge(bridge,mainColor)
                else drawLiquidBridge(bridge,Brush.linearGradient(listOf(mainColor,join.color),main,join.silhouette.center))
            }
        }
    }
}
