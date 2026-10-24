package com.semiovert.app.core.designsystem

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Rect
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.*

internal data class LiquidBridge(
    val mainA: Offset,val mainHandleA: Offset,val smallHandleA: Offset,val smallA: Offset,
    val smallB: Offset,val smallHandleB: Offset,val mainHandleB: Offset,val mainB: Offset,
)

internal data class LiquidShadowPart(val center: Offset,val radius: Float,val stretch: Float,val bridge: LiquidBridge?)

/** One occluding silhouette: no internal circle rim is allowed to cast its own shadow. */
internal fun liquidFusionOutline(main: Offset,mainRadius: Float,parts: List<LiquidShadowPart>): Path {
    var union=Path().apply {addOval(Rect(main,mainRadius))}
    for(part in parts) {
        val outline=LiquidCircleShape(main-part.center,part.stretch).createOutline(
            Size(part.radius*2f,part.radius*2f),LayoutDirection.Ltr,Density(1f))
        val small=Path().apply {addOutline(outline);translate(part.center-Offset(part.radius,part.radius))}
        union=Path.combine(PathOperation.Union,union,small)
        part.bridge?.let {union=Path.combine(PathOperation.Union,union,it.toPath())}
    }
    return union
}

/** Tangential metaball necks; both separation and reunion use the actual circle positions. */
internal object LiquidCircleGeometry {
    fun facingRadius(radius: Float,stretch: Float)=radius*(1f+.8f*stretch.coerceIn(-.18f,1.1f))
    fun detachDistance(mainRadius: Float,smallRadius: Float)=mainRadius+smallRadius+min(mainRadius,smallRadius)*1.25f

    /** Keep a droplet the source's color until it has completely pinched off. */
    fun materialProgress(distance: Float,mainRadius: Float,smallRadius: Float): Float {
        val raw=((distance-detachDistance(mainRadius,smallRadius))/(smallRadius*.65f)).coerceIn(0f,1f)
        return raw*raw*(3f-2f*raw)
    }

    fun bridge(main: Offset,mainRadius: Float,small: Offset,smallRadius: Float,
        maxDistance: Float=detachDistance(mainRadius,smallRadius),reveal: Float=1f): LiquidBridge? {
        val delta=small-main
        val distance=delta.getDistance()
        val detach=maxDistance
        if(distance<=abs(mainRadius-smallRadius)||distance>=detach||reveal<=0f)return null
        val sum=mainRadius+smallRadius
        val overlapMain=if(distance<sum)acos(((mainRadius*mainRadius+distance*distance-smallRadius*smallRadius)/(2f*mainRadius*distance)).coerceIn(-1f,1f)) else 0f
        val overlapSmall=if(distance<sum)acos(((smallRadius*smallRadius+distance*distance-mainRadius*mainRadius)/(2f*smallRadius*distance)).coerceIn(-1f,1f)) else 0f
        val remaining=((detach-distance)/(detach-sum)).coerceIn(0f,1f)
        // Grow the neck from a pinched contact, shared by its fill and exterior shadow.
        val strength=.55f*remaining*remaining*(3f-2f*remaining)*reveal.coerceIn(0f,1f)
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

/** Keep the far edge and side diameter stable; only the main-facing side is pulled. */
internal class LiquidCircleShape(private val towardMain: Offset,private val stretch: Float): Shape {
    override fun createOutline(size: Size,layoutDirection: LayoutDirection,density: Density): Outline {
        if(abs(stretch)<.001f)return CircleShape.createOutline(size,layoutDirection,density)
        val radius=min(size.width,size.height)/2f
        val center=Offset(size.width/2f,size.height/2f)
        val direction=if(towardMain.getDistance()>.001f)towardMain/towardMain.getDistance() else Offset(1f,0f)
        val normal=Offset(-direction.y,direction.x)
        val front=LiquidCircleGeometry.facingRadius(radius,stretch)
        val k=.55228475f
        fun point(x: Float,y: Float)=center+direction*x+normal*y
        val path=Path()
        fun curve(ax: Float,ay: Float,bx: Float,by: Float,x: Float,y: Float) {
            val a=point(ax,ay);val b=point(bx,by);val end=point(x,y)
            path.cubicTo(a.x,a.y,b.x,b.y,end.x,end.y)
        }
        val start=point(front,0f);path.moveTo(start.x,start.y)
        curve(front,k*radius,k*front,radius,0f,radius)
        curve(-k*radius,radius,-radius,k*radius,-radius,0f)
        curve(-radius,-k*radius,-k*radius,-radius,0f,-radius)
        curve(k*front,-radius,front,-k*radius,front,0f)
        path.close()
        return Outline.Generic(path)
    }
}

internal fun DrawScope.drawLiquidBridge(bridge: LiquidBridge,color: Color)=drawLiquidBridge(bridge,SolidColor(color))

internal fun DrawScope.drawLiquidBridge(bridge: LiquidBridge,brush: Brush)=drawPath(bridge.toPath(),brush)

private fun LiquidBridge.toPath()=Path().apply {
    moveTo(mainA.x,mainA.y)
    cubicTo(mainHandleA.x,mainHandleA.y,smallHandleA.x,smallHandleA.y,smallA.x,smallA.y)
    lineTo(smallB.x,smallB.y)
    cubicTo(smallHandleB.x,smallHandleB.y,mainHandleB.x,mainHandleB.y,mainB.x,mainB.y)
    close()
}
