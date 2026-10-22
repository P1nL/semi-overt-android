package com.semiovert.app.core.designsystem

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.*

internal data class LiquidBridge(
    val mainA: Offset,val mainHandleA: Offset,val smallHandleA: Offset,val smallA: Offset,
    val smallB: Offset,val smallHandleB: Offset,val mainHandleB: Offset,val mainB: Offset,
)

/** Tangential metaball necks; both separation and reunion use the actual circle positions. */
internal object LiquidCircleGeometry {
    fun detachDistance(mainRadius: Float,smallRadius: Float)=mainRadius+smallRadius+min(mainRadius,smallRadius)*1.25f

    /** Keep a droplet the source's color until it has completely pinched off. */
    fun materialProgress(distance: Float,mainRadius: Float,smallRadius: Float): Float {
        val raw=((distance-detachDistance(mainRadius,smallRadius))/(smallRadius*.65f)).coerceIn(0f,1f)
        return raw*raw*(3f-2f*raw)
    }

    fun bridge(main: Offset,mainRadius: Float,small: Offset,smallRadius: Float): LiquidBridge? {
        val delta=small-main
        val distance=delta.getDistance()
        val detach=detachDistance(mainRadius,smallRadius)
        if(distance<=abs(mainRadius-smallRadius)||distance>=detach)return null
        val sum=mainRadius+smallRadius
        val overlapMain=if(distance<sum)acos(((mainRadius*mainRadius+distance*distance-smallRadius*smallRadius)/(2f*mainRadius*distance)).coerceIn(-1f,1f)) else 0f
        val overlapSmall=if(distance<sum)acos(((smallRadius*smallRadius+distance*distance-mainRadius*mainRadius)/(2f*smallRadius*distance)).coerceIn(-1f,1f)) else 0f
        val remaining=((detach-distance)/(detach-sum)).coerceIn(0f,1f)
        val strength=.55f*remaining*remaining*(3f-2f*remaining)
        val axis=atan2(delta.y,delta.x)
        val tangent=acos(((mainRadius-smallRadius)/distance).coerceIn(-1f,1f))
        val pi=PI.toFloat()
        val angleMainA=axis+overlapMain+(tangent-overlapMain)*strength
        val angleMainB=axis-overlapMain-(tangent-overlapMain)*strength
        val angleSmallA=axis+pi-overlapSmall-(pi-overlapSmall-tangent)*strength
        val angleSmallB=axis-pi+overlapSmall+(pi-overlapSmall-tangent)*strength
        fun polar(radius: Float,angle: Float)=Offset(cos(angle)*radius,sin(angle)*radius)
        val mainA=main+polar(mainRadius,angleMainA)
        val mainB=main+polar(mainRadius,angleMainB)
        val smallA=small+polar(smallRadius,angleSmallA)
        val smallB=small+polar(smallRadius,angleSmallB)
        val handle=min(strength*2.4f,(mainA-smallA).getDistance()/sum)*min(1f,distance*2f/sum)
        return LiquidBridge(
            mainA,mainA+polar(mainRadius*handle,angleMainA-pi/2f),
            smallA+polar(smallRadius*handle,angleSmallA+pi/2f),smallA,
            smallB,smallB+polar(smallRadius*handle,angleSmallB-pi/2f),
            mainB+polar(mainRadius*handle,angleMainB+pi/2f),mainB,
        )
    }
}

internal fun DrawScope.drawLiquidBridge(bridge: LiquidBridge,color: Color) {
    val path=Path().apply {
        moveTo(bridge.mainA.x,bridge.mainA.y)
        cubicTo(bridge.mainHandleA.x,bridge.mainHandleA.y,bridge.smallHandleA.x,bridge.smallHandleA.y,bridge.smallA.x,bridge.smallA.y)
        lineTo(bridge.smallB.x,bridge.smallB.y)
        cubicTo(bridge.smallHandleB.x,bridge.smallHandleB.y,bridge.mainHandleB.x,bridge.mainHandleB.y,bridge.mainB.x,bridge.mainB.y)
        close()
    }
    drawPath(path,color)
}
