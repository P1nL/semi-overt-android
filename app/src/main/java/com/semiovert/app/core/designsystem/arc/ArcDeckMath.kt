package com.semiovert.app.core.designsystem.arc

import com.semiovert.app.core.designsystem.FlowPhase
import com.semiovert.app.core.designsystem.SceneMotion
import com.semiovert.app.core.designsystem.SoSceneEase
import kotlin.math.*

/** Base ring positions in dp; only the selected card rises, without resizing the ring. */
data class ArcDeckGeometry(val width: Float,val height: Float,val cardWidth: Float,val cardHeight: Float,
    val focusX: Float,val focusY: Float,val radiusX: Float,val radiusY: Float) {
    val scrollStep=radiusY*ArcDeckMath.ANGLE_STEP
    internal val edgeShift=min(48f,cardHeight*.15f)
    internal val edgeTravel=focusX-radiusX*(1f-cos(ArcDeckMath.VISIBLE_DISTANCE*ArcDeckMath.ANGLE_STEP))+cardWidth/2f+24f
}

data class ArcCardPose(val article: Int,val distance: Float,val centerX: Float,val centerY: Float,val scale: Float,val alpha: Float,
    val waveOffset: Float=0f) {
    // Selection never changes the article order; the active card rises out of the stack.
    val layer: Float get()=distance
}

internal data class ArcCardCopies(val queue: ArcCardPose,val lifted: ArcCardPose?,val queueVisible: Boolean)

object ArcDeckMath {
    const val SIDE_MARGIN=16f
    private const val PULL_GAP=16f
    const val ANGLE_STEP=.30f
    const val STACK_VERTICAL_SPACING=.80f
    const val VISIBLE_DISTANCE=4.65f
    const val EDGE_START_DISTANCE=4f
    const val LOWER_EXIT_START_DISTANCE=2.5f
    const val UPPER_ENTER_START_DISTANCE=LOWER_EXIT_START_DISTANCE
    const val EDGE_CLEAR_DISTANCE=5.4f
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

    fun isVisible(pose: ArcCardPose,g: ArcDeckGeometry): Boolean =
        pose.centerX+g.cardWidth*pose.scale/2f>=0f&&pose.centerX-g.cardWidth*pose.scale/2f<=g.width&&
            pose.centerY+g.cardHeight*pose.scale/2f>=0f&&pose.centerY-g.cardHeight*pose.scale/2f<=g.height

    fun frame(article: Int,count: Int,position: Float,g: ArcDeckGeometry): ArcCardPose {
        val d=distance(article,position,count)
        return atDistance(article,d,sin((position-floor(position))*PI.toFloat()).coerceAtLeast(0f),g)
    }

    // The native deck draws the base arc; it does not use the optional wave offset.
    internal fun displayPose(article: Int,count: Int,position: Float,g: ArcDeckGeometry): ArcCardPose =
        atDistance(article,distance(article,position,count),0f,g)

    fun frames(count: Int,position: Float,g: ArcDeckGeometry,includeOffscreen: Boolean=false): List<ArcCardPose> = (0 until count).mapNotNull {article ->
        val pose=frame(article,count,position,g)
        if(includeOffscreen||isVisible(pose,g))pose else null
    }

    /** A lifted card keeps its current loop until its return finishes. */
    fun continuePose(pose: ArcCardPose,previousDistance: Float,count: Int,g: ArcDeckGeometry): ArcCardPose {
        val d=pose.distance+((previousDistance-pose.distance)/count).roundToInt()*count
        return if(d==pose.distance)pose else atDistance(pose.article,d,0f,g).copy(waveOffset=pose.waveOffset)
    }

    /** The returning copy stays on its old lap; the queue never waits for its spring to finish. */
    internal fun copies(pose: ArcCardPose,previousDistance: Float,count: Int,g: ArcDeckGeometry,hasLiftedCopy: Boolean): ArcCardCopies {
        val lifted=if(hasLiftedCopy)continuePose(pose,previousDistance,count,g) else null
        return ArcCardCopies(pose,lifted,lifted==null||lifted.distance!=pose.distance)
    }

    private fun atDistance(article: Int,d: Float,pulse: Float,g: ArcDeckGeometry): ArcCardPose {
        val arcDistance=d.coerceIn(-VISIBLE_DISTANCE,VISIBLE_DISTANCE)
        val angle=arcDistance*ANGLE_STEP
        val scale=1f
        // Both ends share edge travel across neighbouring cards, preserving the stack.
        val ringY=g.focusY+g.radiusY*sin(angle)
        val beyond=(abs(d)-VISIBLE_DISTANCE).coerceAtLeast(0f)
        val direction=sign(d)
        val edgeStart=if(d<0f)UPPER_ENTER_START_DISTANCE else LOWER_EXIT_START_DISTANCE
        val edge=(abs(d)-edgeStart).coerceAtLeast(0f)
        val span=EDGE_CLEAR_DISTANCE-edgeStart
        val t=(edge/span).coerceIn(0f,1f)
        // Retain an extra card at each end before clearing the 11-card loop boundary.
        val edgeProgress=t*t
        val y=if(edge==0f)ringY else {
            // Clear the left edge near the queue, rather than travelling to the top or bottom.
            val shift=g.edgeShift*edgeProgress+
                (edge-span).coerceAtLeast(0f)*g.scrollStep
            ringY+direction*shift
        }
        // Same .25 / .10 neighbour falloff as the category and web home rail.
        val waveOffset=if(pulse==0f)0f else {
            val influence=.25f*.4f.pow((abs(d)-1f).coerceAtLeast(0f))
            g.cardWidth*.6f*influence*pulse*waveGate(d)
        }
        val x=g.focusX-g.radiusX*(1f-cos(angle))-beyond*g.width*.04f-g.edgeTravel*edgeProgress
        // Tighten both halves around the focus without changing drag distance or side clearance.
        val stackedY=g.focusY+(y-g.focusY)*STACK_VERTICAL_SPACING
        return ArcCardPose(article,d,x,stackedY,scale,1f,waveOffset)
    }

    /** Enter into the queue; exit follows the scrolling arc through its lower edge without looping. */
    fun scenePose(pose: ArcCardPose,g: ArcDeckGeometry,hidden: Float,phase: FlowPhase): ArcCardPose {
        if(hidden<=0f||phase==FlowPhase.IDLE)return pose
        val order=((VISIBLE_DISTANCE-pose.distance)/(2f*VISIBLE_DISTANCE)).coerceIn(0f,1f)
        if(phase==FlowPhase.EXIT)return exitPose(pose,g,hidden*SceneMotion.HOME_EXIT_MS,order)
        val duration=SceneMotion.HOME_ENTER_MS
        val span=SceneMotion.DECK_STAGGER_MS.toFloat()
        val elapsed=(1f-hidden)*duration
        val q=SoSceneEase.transform(((elapsed-order*span)/(duration-span)).coerceIn(0f,1f))
        return entrancePose(pose,g,q)
    }

    private fun exitPose(pose: ArcCardPose,g: ArcDeckGeometry,elapsed: Float,order: Float): ArcCardPose {
        // One continuous curve per card: start together, finish front-to-back, never hand off.
        val duration=SceneMotion.HOME_EXIT_CARD_MS+order*SceneMotion.HOME_EXIT_STAGGER_MS
        val departure=SoSceneEase.transform(SceneMotion.segment(elapsed,0f,duration))
        val remaining=(EDGE_CLEAR_DISTANCE-pose.distance).coerceAtLeast(0f)
        val path=atDistance(pose.article,pose.distance+remaining*departure,0f,g)
        return pose.copy(centerX=path.centerX,centerY=path.centerY)
    }

    private fun entrancePose(pose: ArcCardPose,g: ArcDeckGeometry,progress: Float): ArcCardPose {
        val joinDistance=min(pose.distance,-EDGE_START_DISTANCE)
        val join=atDistance(pose.article,joinDistance,0f,g)
        val startX=-g.cardWidth/2f-24f
        val startY=join.centerY-min(96f,g.cardHeight*.30f)
        val firstX=startX+(join.centerX-startX)*.40f
        val firstY=startY+(join.centerY-startY)*.20f
        val angle=joinDistance.coerceIn(-VISIBLE_DISTANCE,VISIBLE_DISTANCE)*ANGLE_STEP
        // Keep the page's diagonal arrival tangent to the extended upper queue.
        val span=EDGE_CLEAR_DISTANCE-UPPER_ENTER_START_DISTANCE
        val edgeProgress=((-joinDistance-UPPER_ENTER_START_DISTANCE)/span).coerceIn(0f,1f)
        val edgeSlope=2f*edgeProgress/span
        val onRing=abs(joinDistance)<VISIBLE_DISTANCE
        val tangentX=(if(onRing)-g.radiusX*sin(angle)*ANGLE_STEP else g.width*.04f)+
            g.edgeTravel*edgeSlope
        val tangentY=((if(onRing)g.radiusY*cos(angle)*ANGLE_STEP else 0f)+
            g.edgeShift*edgeSlope)*STACK_VERTICAL_SPACING
        // Keep both control points ordered as the diagonal joins the shared edge path.
        val approach=min(3f,min((join.centerX-firstX)*3f/tangentX,(join.centerY-firstY)*3f/tangentY))
        val distance=joinDistance-approach+(pose.distance-joinDistance+approach)*progress
        if(distance>=joinDistance) {
            val position=atDistance(pose.article,distance,0f,g)
            return pose.copy(centerX=position.centerX,centerY=position.centerY)
        }
        val lastX=join.centerX-tangentX*approach/3f
        val lastY=join.centerY-tangentY*approach/3f
        val t=((distance-joinDistance+approach)/approach).coerceIn(0f,1f)
        val u=1f-t
        fun coordinate(start: Float,first: Float,last: Float,end: Float)=
            u*u*u*start+3f*u*u*t*first+3f*u*t*t*last+t*t*t*end
        return pose.copy(
            centerX=coordinate(startX,firstX,lastX,join.centerX),
            centerY=coordinate(startY,firstY,lastY,join.centerY),
        )
    }
}
