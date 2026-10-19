package com.semiovert.app.core.designsystem.arc

import kotlin.math.*

/** Positions are in dp. Cards remain upright; only position, scale and depth change. */
data class ArcDeckGeometry(val width: Float,val height: Float,val cardWidth: Float,val cardHeight: Float,
    val focusX: Float,val focusY: Float,val radiusX: Float,val radiusY: Float) {
    val scrollStep: Float get()=radiusY*ArcDeckMath.ANGLE_STEP
}

data class ArcCardPose(val article: Int,val distance: Float,val centerX: Float,val centerY: Float,val scale: Float,val alpha: Float,
    val waveOffset: Float=0f) {
    // Later articles stay above earlier articles; selection never changes this order.
    val layer: Float get()=distance
}

object ArcDeckMath {
    const val ANGLE_STEP=.30f
    const val VISIBLE_DISTANCE=4.65f
    const val FOCUS_THRESHOLD=.56f
    const val CARD_GAP=12f

    fun geometry(width: Float,height: Float): ArcDeckGeometry {
        val cardWidth=min(width*.70f,min(360f,height*.53f))
        return ArcDeckGeometry(width,height,cardWidth,cardWidth,width*.56f,height*.46f,width*.68f,height*.40f)
    }
    fun index(page: Int,count: Int)=((page%count)+count)%count
    fun distance(article: Int,position: Float,count: Int): Float {
        val half=count/2f
        return (((article-position+half)%count)+count)%count-half
    }
    fun focus(position: Float,previousPage: Int)=if(abs(position-previousPage)>FOCUS_THRESHOLD)position.roundToInt() else previousPage
    fun target(position: Float,screenVelocity: Float,scrollStep: Float)=
        (position+(-screenVelocity/scrollStep*.12f).coerceIn(-1.4f,1.4f)).roundToInt()
    private fun waveGate(distance: Float): Float {
        // Wider than the focus hysteresis: even a spring tail cannot move or
        // cover the selected card. Both sides open outwards along the arc.
        val t=((abs(distance)-.65f)/.35f).coerceIn(0f,1f)
        return t*t*(3f-2f*t)
    }
    private fun waveMagnitude(distance: Float,amount: Float)=
        amount.coerceAtLeast(0f)*waveGate(distance)*(if(distance<0f)-1f else 1f)
    fun waveX(distance: Float,amount: Float)=waveMagnitude(distance,amount)*sin(abs(distance)*ANGLE_STEP)
    fun waveY(distance: Float,amount: Float)=waveMagnitude(distance,amount)*cos(abs(distance)*ANGLE_STEP)

    fun frames(count: Int,position: Float,g: ArcDeckGeometry): List<ArcCardPose> = (0 until count).mapNotNull {article ->
        val d=distance(article,position,count)
        if(abs(d)>VISIBLE_DISTANCE)return@mapNotNull null
        val angle=d*ANGLE_STEP
        val scale=1f-.11f*abs(d).coerceAtMost(4f)
        // Open a full-card window in the middle instead of raising the selected
        // card's z-index. Outside it, compact rows still expose both ordered stacks.
        val room=min(g.focusY-g.cardHeight/2f,g.height-g.focusY-g.cardHeight/2f)
        val stackStep=minOf(g.height*.045f,g.cardHeight*.055f+CARD_GAP,(room-CARD_GAP)*.28f)
        val t=abs(d).coerceAtMost(1f)
        val opening=t*t*(3f-2f*t)
        val separation=(g.cardHeight*(1f+scale)/2f+CARD_GAP+(abs(d)-1f)*stackStep)*opening
        val side=if(d<0f)-1f else 1f
        val pulse=sin((position-floor(position))*PI.toFloat()).coerceAtLeast(0f)
        // Same .25 / .10 neighbour falloff as the category and web home rail.
        val influence=.25f*.4f.pow((abs(d)-1f).coerceAtLeast(0f))
        val waveOffset=g.cardWidth*.6f*influence*pulse*waveGate(d)
        ArcCardPose(article,d,g.focusX+side*g.radiusX*(1f-cos(angle)),g.focusY+side*separation,
            scale,((VISIBLE_DISTANCE-abs(d))/.65f).coerceIn(0f,1f),waveOffset)
    }
}
