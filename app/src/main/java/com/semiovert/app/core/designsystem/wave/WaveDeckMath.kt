package com.semiovert.app.core.designsystem.wave

import androidx.compose.animation.core.spring
import kotlin.math.*

data class WaveDeckGeometry(val width: Float,val height: Float,val cardSize: Float,val frontTop: Float,val stackStep: Float) {
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
        g.frontTop-depth*g.stackStep+verticalReference(g.width,g.height)*min(depth*.05f,.28f)/2f

    fun geometry(width: Float,height: Float,count: Int): WaveDeckGeometry {
        val card=min(width*.90f,min(420f,height*.65f))
        val reference=verticalReference(width,height)
        val previousOrigin=max(reference/2f+24f,min(height*.60f,height-reference/2f-96f))
        val step=if(count>1)min(62f,(previousOrigin-reference/2f-24f)/(count-1)) else 0f
        // Keep every original top edge; grow the card below it instead of translating the deck.
        return WaveDeckGeometry(width,height,card,previousOrigin-reference/2f,step)
    }

    fun dragDelta(deltaPx: Float,stepPx: Float)=deltaPx/stepPx*(if(deltaPx>0f)DOWNWARD_GAIN else 1f)
    fun focus(position: Float,previous: Int)=if(abs(position-previous)>.56f)position.roundToInt() else previous
    fun target(position: Float,velocity: Float,stepPx: Float)=
        (position+(dragDelta(velocity,stepPx)*.10f).coerceIn(-.8f,.8f)).roundToInt()

    fun frames(count: Int,position: Float,g: WaveDeckGeometry): List<WaveCardPose> = (0 until count).map {article ->
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
}

internal object WaveDeckMotion {
    fun spec()=spring<Float>(dampingRatio=.86f,stiffness=95f,visibilityThreshold=.0015f)
    // Web spring k=120, damping=25, mass=1 -> damping ratio approximately 1.14.
    fun followSpec()=spring<Float>(dampingRatio=1.14f,stiffness=120f,visibilityThreshold=.05f)
    fun releaseVelocity(velocity: Float,stepPx: Float)=WaveDeckMath.dragDelta(velocity,stepPx).coerceIn(-1.6f,1.6f)
}
