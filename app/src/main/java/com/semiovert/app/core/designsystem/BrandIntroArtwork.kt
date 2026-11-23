package com.semiovert.app.core.designsystem

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

private const val STAR_START_PROGRESS=.015f

/** Paths and segment buffers are retained across frames; no bitmap or WebView is used. */
internal class BrandIntroArtwork {
    private class DrawnPath(data: String) {
        val path=PathParser().parsePathString(data).toPath()
        val measure=PathMeasure().apply {setPath(path,false)}
        val segment=Path()

        fun at(progress: Float): Path {
            if(progress>=1f)return path
            segment.reset()
            measure.getSegment(0f,measure.length*progress,segment,true)
            return segment
        }
    }

    private val starEdges=arrayOf(
        DrawnPath("M206 58 C218 143 243 168 328 180"),
        DrawnPath("M328 180 C243 192 218 217 206 302"),
        DrawnPath("M206 302 C194 217 169 192 84 180"),
        DrawnPath("M84 180 C169 168 194 143 206 58"),
    )
    private val aperture=PathParser().parsePathString(
        "M206 58 C218 143 243 168 328 180 C243 192 218 217 206 302 "+
            "C194 217 169 192 84 180 C169 168 194 143 206 58 Z"
    ).toPath()
    private val word=DrawnPath(
        "M50 180 H362 Q390 180 374.36 203.22 L258 376 C239.56 403.38 229 447 244 449 "+
            "C260.05 451.14 317.87 394.44 346 366 L497 219 C517 200 548 203 556 186 "+
            "C567 163 547 153 530 169 C510 188 510 222 526 239 C542 256 566 249 582 232 "+
            "C598 215 609 192 620 174 Q627 162 632 170 Q634 175 630 185 "+
            "L610 235 C620 212 636 186 652 186 Q664 186 672 197 Q680 209 690 195 "+
            "L697 187 Q704 180 716 180 H807 Q829 180 817.73 161.11 "+
            "L804.3 138.61 Q792 118 780.09 138.84 L696 286 C681.12 312.04 683 322 704 317 "+
            "C716.6 314 725.38 308.11 738 300 L792.86 264.73 Q808 255 826 255 H892"
    )
    private val vStart=BrandIntroMotion.vStartMillis(word.measure.length)
    private val stroke=Stroke(width=12f,cap=StrokeCap.Round,join=StrokeJoin.Round)

    fun draw(scope: DrawScope,time: Float,background: Color,ink: Color)=with(scope) {
        if(size.minDimension<=0f)return@with
        val unitScale=min(size.width/1_100f,size.height/620f)
        val center=Offset(size.width*.5f,size.height*BrandIntroMotion.CENTER_Y_FRACTION)
        val shift=BrandIntroMotion.leftShift(time,vStart)*unitScale
        // The 40-unit disk is inside the outline even after allowing for its stroke.
        // Fit that disk to the farthest screen corner so portrait screens fully clear.
        val targetZoom=max(40f,hypot(center.x,max(center.y,size.height-center.y))/(40f*unitScale))
        val starScale=unitScale*BrandIntroMotion.portalZoom(time,targetZoom)
        val opacity=BrandIntroMotion.homeOpacity(time)
        if(opacity<=0f)drawRect(background)
        else {
            val viewportTopLeft=Offset(206f-center.x/starScale,180f-center.y/starScale)
            val viewportSize=Size(size.width/starScale,size.height/starScale)
            withTransform({
                translate(center.x,center.y)
                scale(starScale,starScale,pivot=Offset.Zero)
                translate(-206f,-180f)
            }) {
                clipPath(aperture,ClipOp.Difference) {
                    drawRect(background,viewportTopLeft,viewportSize)
                }
                if(opacity<1f)clipPath(aperture) {
                    drawRect(background,viewportTopLeft,viewportSize,alpha=1f-opacity)
                }
            }
        }
        val starProgress=STAR_START_PROGRESS+(1f-STAR_START_PROGRESS)*BrandIntroMotion.starProgress(time)
        withTransform({
            translate(center.x-shift,center.y)
            scale(starScale,starScale,pivot=Offset.Zero)
            translate(-206f,-180f)
        }) {
            for(edge in starEdges)drawPath(edge.at(starProgress),ink,style=stroke)
        }
        val wordProgress=BrandIntroMotion.wordProgress(time)
        if(wordProgress>0f)withTransform({
            translate(center.x-shift,center.y)
            scale(unitScale,unitScale,pivot=Offset.Zero)
            translate(-206f,-180f)
        }) {
            drawPath(word.at(wordProgress),ink,style=stroke)
        }
    }
}
