package com.semiovert.app.core.designsystem.arc

import kotlin.math.abs
import kotlin.math.round

internal data class ArcAuthorAvatarPlacement(val left: Float,val top: Float,val diameter: Float,val hiddenOffset: Float)

/** One confirmation window after release; once ready, spring-tail noise cannot hide it again. */
internal class ArcAvatarRestGate {
    private var articleId: String?=null
    private var anchor=0f
    private var since=0L
    private var ready=false

    fun sample(position: Float,focusedArticleId: String,settling: Boolean,nowMillis: Long): Boolean {
        if(ready)return true
        if(settling&&abs(position-round(position))>ArcAuthorAvatarMotion.SETTLED_SLOT_DISTANCE) {
            articleId=null
            return false
        }
        if(articleId!=focusedArticleId||abs(position-anchor)>ArcAuthorAvatarMotion.STABLE_POSITION_WINDOW) {
            articleId=focusedArticleId;anchor=position;since=nowMillis
            return false
        }
        ready=nowMillis-since>=ArcAuthorAvatarMotion.STABLE_MILLIS
        return ready
    }
}

internal object ArcAuthorAvatarMotion {
    const val HIDE_MILLIS=140
    const val ENTER_MILLIS=280
    const val STABLE_MILLIS=120L
    const val STABLE_POSITION_WINDOW=.025f
    const val SETTLED_SLOT_DISTANCE=.08f
    const val EXPANDED_ENTER_MILLIS=280
    const val EXPANDED_REVEAL_PROGRESS=.95f
    const val EXPANDED_GAP=14f

    fun placement(g: ArcDeckGeometry): ArcAuthorAvatarPlacement {
        val left=g.focusX+g.cardWidth/2f
        val diameter=(g.width-left-12f).coerceIn(24f,76f)
        return ArcAuthorAvatarPlacement(left,g.focusY-diameter/2f,diameter,g.width-left+1f)
    }

    /** Use the final lifted card bounds; never move or resize the card to fit an avatar. */
    fun expandedPlacement(g: ArcDeckGeometry,cardTop: Float,safeTop: Float,safeBottom: Float): ArcAuthorAvatarPlacement? {
        val diameter=placement(g).diameter
        val above=cardTop-EXPANDED_GAP-diameter
        val below=cardTop+g.cardHeight+EXPANDED_GAP
        val top=when {
            above>=safeTop -> above
            below+diameter<=safeBottom -> below
            else -> return null // A short viewport must not put the avatar over navigation/content.
        }
        val left=ArcDeckMath.pulledLeft(g)+g.cardWidth-diameter
        return ArcAuthorAvatarPlacement(left,top,diameter,g.width-left+1f)
    }

}
