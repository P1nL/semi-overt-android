package com.semiovert.app.core.designsystem.arc

import com.semiovert.app.core.designsystem.FlowPhase
import com.semiovert.app.core.designsystem.SceneMotion
import com.semiovert.app.core.designsystem.SoSceneEase
import kotlin.math.*

/** Base ring positions in dp; only the selected card rises, without resizing the ring. */
data class ArcDeckGeometry(val width: Float,val height: Float,val cardWidth: Float,val cardHeight: Float,
    val focusX: Float,val focusY: Float,val radiusX: Float,val radiusY: Float) {
    val scrollStep: Float get()=radiusY*ArcDeckMath.ANGLE_STEP
}

data class ArcCardPose(val article: Int,val distance: Float,val centerX: Float,val centerY: Float,val scale: Float,val alpha: Float,
    val waveOffset: Float=0f) {
    // Selection never changes the article order; the active card rises out of the stack.
    val layer: Float get()=distance
}

object ArcDeckMath {
    const val SIDE_MARGIN=16f
    private const val PULL_GAP=16f
    const val ANGLE_STEP=.30f
    const val VISIBLE_DISTANCE=4.65f
    const val EDGE_START_DISTANCE=4f
    const val EDGE_CLEAR_DISTANCE=5f
    const val FOCUS_THRESHOLD=.56f
    const val FOCUS_ELEVATION=10f
    /** Back/upper cards pitch away; front/lower cards pitch toward the viewer, about the bottom edge. */
    fun neighbourTilt(relativePage: Float): Float = -sign(relativePage)*when {
        abs(relativePage)<.5f -> 0f
        abs(relativePage)<1.5f -> 20f
        abs(relativePage)<2.5f -> 6f
        else -> 0f
    }

    // Clear the following card by motion, not by changing the article's drawing order.
    fun pullLift(distance: Float,g: ArcDeckGeometry): Float {
        val here=atDistance(0,distance,0f,g).centerY
        val next=atDistance(0,distance+1f,0f,g).centerY
        // Include the projected top edge of the tilted foreground neighbour.
        return max(min(64f,g.cardHeight*.18f),g.cardHeight-(next-here)+PULL_GAP)
    }
    fun pulledLeft(g: ArcDeckGeometry)=g.width-SIDE_MARGIN-g.cardWidth
    fun canPull(g: ArcDeckGeometry,safeTop: Float,safeBottom: Float)=
        g.height>=440f&&safeBottom-safeTop>=g.cardHeight

    /** Project onto the nearest legal arc position, never onto the centre slot. */
    fun pullPosition(article: Int,position: Float,count: Int,g: ArcDeckGeometry,safeTop: Float,safeBottom: Float): Float {
        val d=distance(article,position,count)
        val minY=safeTop+g.cardHeight/2f
        val maxY=safeBottom-g.cardHeight/2f
        fun pulledY(distance: Float)=atDistance(article,distance,0f,g).centerY-pullLift(distance,g)
        val y=pulledY(d)
        if(y in minY..maxY)return position
        val targetY=y.coerceIn(minY,maxY)
        var low=-EDGE_CLEAR_DISTANCE-1f
        var high=EDGE_CLEAR_DISTANCE+1f
        repeat(24) {
            val mid=(low+high)/2f
            if(pulledY(mid)<targetY)low=mid else high=mid
        }
        return position+d-(low+high)/2f
    }

    fun geometry(width: Float,height: Float): ArcDeckGeometry {
        val cardWidth=min(width*.70f,min(360f,height*.53f))
        return ArcDeckGeometry(width,height,cardWidth,cardWidth,width*.48f,height*.53f,width*.30f,height*.34f)
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
        // Keep the existing neighbour response without opening a centre window.
        val t=((abs(distance)-.65f)/.35f).coerceIn(0f,1f)
        return t*t*(3f-2f*t)
    }
    private fun waveMagnitude(distance: Float,amount: Float)=
        amount.coerceAtLeast(0f)*waveGate(distance)*(if(distance<0f)-1f else 1f)
    fun waveX(distance: Float,amount: Float)=-abs(waveMagnitude(distance,amount))*sin(abs(distance)*ANGLE_STEP)
    fun waveY(distance: Float,amount: Float)=waveMagnitude(distance,amount)*cos(abs(distance)*ANGLE_STEP)

    fun frames(count: Int,position: Float,g: ArcDeckGeometry): List<ArcCardPose> = (0 until count).mapNotNull {article ->
        val d=distance(article,position,count)
        val pose=atDistance(article,d,sin((position-floor(position))*PI.toFloat()).coerceAtLeast(0f),g)
        // Keep the original visible queue. Retain only the still-visible part of an edge exit.
        if(abs(d)>VISIBLE_DISTANCE&&(pose.centerY+g.cardHeight/2f<0f||pose.centerY-g.cardHeight/2f>g.height))null else pose
    }

    private fun atDistance(article: Int,d: Float,pulse: Float,g: ArcDeckGeometry): ArcCardPose {
        val arcDistance=d.coerceIn(-VISIBLE_DISTANCE,VISIBLE_DISTANCE)
        val angle=arcDistance*ANGLE_STEP
        val scale=1f
        // Every resting slot keeps the approved ring; only the moving edge slot is eased out.
        val ringY=g.focusY+g.radiusY*sin(angle)
        val beyond=(abs(d)-VISIBLE_DISTANCE).coerceAtLeast(0f)
        val edge=(abs(d)-EDGE_START_DISTANCE).coerceAtLeast(0f)
        val y=if(edge==0f)ringY else {
            // Spread edge travel across a full slot, not the final .35 of one slot.
            // Smoothstep joins with zero extra velocity, and recycling stays offscreen.
            val clearY=if(d>0f)g.height+g.cardHeight/2f+24f else -g.cardHeight/2f-24f
            val edgeY=g.focusY+sign(d)*g.radiusY*sin(VISIBLE_DISTANCE*ANGLE_STEP)
            val travel=abs(clearY-edgeY)
            val span=EDGE_CLEAR_DISTANCE-EDGE_START_DISTANCE
            val t=(edge/span).coerceIn(0f,1f)
            val shift=travel*t*t*(3f-2f*t)+(edge-span).coerceAtLeast(0f)*g.scrollStep
            ringY+sign(d)*shift
        }
        // Same .25 / .10 neighbour falloff as the category and web home rail.
        val influence=.25f*.4f.pow((abs(d)-1f).coerceAtLeast(0f))
        val waveOffset=g.cardWidth*.6f*influence*pulse*waveGate(d)
        val x=g.focusX-g.radiusX*(1f-cos(angle))-beyond*g.width*.04f
        return ArcCardPose(article,d,x,y,scale,1f,waveOffset)
    }

    /** Entrance and exit travel down the same conveyor as manual scrolling. */
    fun scenePose(pose: ArcCardPose,g: ArcDeckGeometry,hidden: Float,phase: FlowPhase): ArcCardPose {
        if(hidden<=0f||phase==FlowPhase.IDLE)return pose
        val entering=phase==FlowPhase.ENTER
        val duration=if(entering)SceneMotion.HOME_ENTER_MS else SceneMotion.HOME_EXIT_MS
        val span=if(entering)SceneMotion.DECK_STAGGER_MS.toFloat() else 220f
        val order=((VISIBLE_DISTANCE-pose.distance)/(2f*VISIBLE_DISTANCE)).coerceIn(0f,1f)
        val elapsed=(if(entering)1f-hidden else hidden)*duration
        val q=SoSceneEase.transform(((elapsed-order*span)/(duration-span)).coerceIn(0f,1f))
        val start=-(VISIBLE_DISTANCE+1f)
        val travel=2f*(VISIBLE_DISTANCE+1f)
        val d=if(entering)start+(pose.distance-start)*q else pose.distance+travel*q
        val result=atDistance(pose.article,d,0f,g)
        return result.copy(alpha=pose.alpha)
    }
}
