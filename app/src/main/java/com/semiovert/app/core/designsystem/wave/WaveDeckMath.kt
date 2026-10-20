package com.semiovert.app.core.designsystem.wave

import androidx.compose.animation.core.spring
import kotlin.math.*

data class WaveDeckGeometry(val width: Float,val height: Float,val cardSize: Float,val frontTop: Float,val stackStep: Float,
    val uniformTopGap: Float?=null) {
    val frontHeight: Float get()=height-frontTop
    val frontY: Float get()=frontTop+frontHeight/2f
    val outsideY: Float get()=height+frontHeight/2f+24f
    val scrollStep: Float get()=(outsideY-frontY)/WaveDeckMath.EXIT_FRACTION
}

data class WaveCardPose(val article: Int,val centerX: Float,val centerY: Float,val scale: Float,val layer: Float,
    val height: Float,val alpha: Float=1f,val outgoing: Boolean=false,val returning: Boolean=false,
    val waveOffset: Float=0f)

/** A reversible conveyor coordinate. Only the foreground leg leaves the viewport;
 * the hidden transfer reconnects behind the stack after the entire card has left. */
object WaveDeckMath {
    const val RETURN_FRACTION=.14f
    const val EXIT_FRACTION=1f-RETURN_FRACTION
    const val DOWNWARD_GAIN=1.25f
    const val SEARCH_VISIBLE=4
    const val CATEGORY_VISIBLE=7
    const val PROFILE_VISIBLE=5
    const val RESTING_VISIBLE_FRACTION=.7f
    const val TOP_INSET=16f
    const val BOTTOM_OVERFLOW=160f
    const val MAX_ELASTIC_PULL=120f

    fun index(page: Int,count: Int)=((page%count)+count)%count
    fun nearestPage(article: Int,position: Float,count: Int): Int {
        val half=count/2f
        return (position+((article-position+half)%count+count)%count-half).roundToInt()
    }
    private fun wrap(value: Float,count: Int)=((value%count)+count)%count
    private fun smooth(value: Float): Float {val t=value.coerceIn(0f,1f);return t*t*(3f-2f*t)}
    private fun verticalReference(width: Float,height: Float)=min(width*.82f,min(380f,height*.65f))
    // Preserve the established top-edge spacing without narrowing cards farther back.
    private fun stackTop(depth: Float,g: WaveDeckGeometry)=
        g.uniformTopGap?.let {g.frontTop-depth*it}
            ?: (g.frontTop-depth*g.stackStep+verticalReference(g.width,g.height)*min(depth*.05f,.28f)/2f)

    fun geometry(width: Float,height: Float,count: Int): WaveDeckGeometry {
        val card=min(width*.90f,min(420f,height*.65f))
        val reference=verticalReference(width,height)
        val previousOrigin=max(reference/2f+24f,min(height*.60f,height-reference/2f-96f))
        val step=if(count>1)min(62f,(previousOrigin-reference/2f-24f)/(count-1)) else 0f
        // Keep every original top edge; grow the card below it instead of translating the deck.
        return WaveDeckGeometry(width,height,card,previousOrigin-reference/2f,step)
    }

    fun dragDelta(deltaPx: Float,stepPx: Float)=deltaPx/stepPx*(if(deltaPx>0f)DOWNWARD_GAIN else 1f)
    /** Match the reference category's three-card top gap, regardless of result count or crop. */
    fun embeddedGeometry(width: Float,viewportHeight: Float,count: Int,referenceHeight: Float,frontTop: Float?=null,visibleLimit: Int=count): WaveDeckGeometry {
        val reference=geometry(width,referenceHeight,3)
        val gap=stackTop(0f,reference)-stackTop(1f,reference)
        // Reserve space for the visible window, not for every article in the data set.
        val visible=count.coerceAtMost(visibleLimit.coerceAtLeast(1))
        val top=max(frontTop ?: (reference.frontTop-(referenceHeight-viewportHeight)),TOP_INSET+gap*(visible-1).coerceAtLeast(0))
        return reference.copy(height=max(top+reference.frontHeight,viewportHeight),frontTop=top,
            stackStep=gap,uniformTopGap=gap)
    }
    /** Expose the shared resting fraction without shortening the card itself. */
    fun restingFrontTop(width: Float,viewportHeight: Float,referenceHeight: Float): Float =
        viewportHeight-geometry(width,referenceHeight,3).frontHeight*RESTING_VISIBLE_FRACTION

    /** Personal-page scrolling uses the same visible fraction as category and search. */
    fun profileViewportHeight(width: Float,referenceHeight: Float,count: Int): Float {
        val deck=embeddedGeometry(width,0f,count,referenceHeight,76f,PROFILE_VISIBLE)
        // Elastic reserve stays offscreen and does not extend page scrolling.
        return deck.frontTop+deck.frontHeight*RESTING_VISIBLE_FRACTION
    }

    /** A long upward pull stretches the whole deck, with resistance and a finite reserve. */
    fun elasticPull(dragDp: Float,viewportHeight: Float): Float {
        val excess=(-dragDp-viewportHeight*.45f).coerceAtLeast(0f)
        return -MAX_ELASTIC_PULL*excess/(MAX_ELASTIC_PULL+excess)
    }
    fun focus(position: Float,previous: Int)=if(abs(position-previous)>.56f)position.roundToInt() else previous
    fun target(position: Float,velocity: Float,stepPx: Float)=
        (position+(dragDelta(velocity,stepPx)*.10f).coerceIn(-.8f,.8f)).roundToInt()

    fun frames(count: Int,position: Float,g: WaveDeckGeometry,visibleLimit: Int=count): List<WaveCardPose> {
        if(count==0)return emptyList()
        val poses=(0 until count).map {article ->
        val distance=wrap(article-position,count)
        if(distance<=count-1f) {
            val slot=floor(distance).toInt().coerceAtMost(max(0,count-2))
            val fraction=distance-slot
            // Every rear card responds immediately. A depth-dependent wave bends
            // its progress rather than delaying its start. Endpoint slopes stay
            // equal across slots, so passing an article boundary stays continuous.
            val lag=min(.10f+.035f*slot,.28f)
            val wave=sin(PI.toFloat()*fraction)
            val shift=fraction+lag*wave*wave
            val depth=if(count==1)0f else slot+shift
            val top=stackTop(depth,g)
            // HomeShowcaseRail.vue: first/second neighbours receive .25/.10
            // of the crest. Continue that falloff for the taller mobile stack.
            val influence=if(slot==0).25f else .10f*.4f.pow(slot-1)
            val crest=min(g.cardSize*.6f,g.height*.30f)
            WaveCardPose(article,g.width/2f,(g.height+top)/2f,1f,count-depth,g.height-top,
                waveOffset=crest*influence*wave*wave)
        } else {
            val loop=distance-(count-1f)
            if(loop>=RETURN_FRACTION) {
                val travel=(1f-loop)/EXIT_FRACTION
                WaveCardPose(article,g.width/2f,g.frontY+(g.outsideY-g.frontY)*travel,1f,count+1f,g.frontHeight,outgoing=true)
            } else {
                // Position/depth are reset only between a fully offscreen pose and an invisible one.
                val retreat=smooth(loop/RETURN_FRACTION)
                val top=stackTop(count-1f,g)+g.stackStep*1.3f*retreat
                WaveCardPose(article,g.width/2f,(g.height+top)/2f,1f,1f-.5f*retreat,
                    g.height-top,1f-retreat,returning=true)
            }
        }
        }
        val visible=visibleLimit.coerceIn(1,count)
        if(visible==count)return poses
        // The outgoing foreground occupies a slot until it leaves. Its replacement
        // enters at the rear only during the hidden-transfer phase, without a pop.
        return poses.filter { !it.returning&&(it.outgoing||wrap(it.article-position,count)<visible) }
            .sortedByDescending {it.layer}.take(visible).map {pose ->
                val depth=wrap(pose.article-position,count)
                if(!pose.outgoing&&depth>visible-1f)
                    pose.copy(alpha=smooth((visible-depth-EXIT_FRACTION)/RETURN_FRACTION))
                else pose
            }
    }
}

internal object WaveDeckMotion {
    fun spec()=spring<Float>(dampingRatio=.86f,stiffness=95f,visibilityThreshold=.0015f)
    // Web spring k=120, damping=25, mass=1 -> damping ratio approximately 1.14.
    fun followSpec()=spring<Float>(dampingRatio=1.14f,stiffness=120f,visibilityThreshold=.05f)
    fun releaseVelocity(velocity: Float,stepPx: Float)=WaveDeckMath.dragDelta(velocity,stepPx).coerceIn(-1.6f,1.6f)
}
