package com.semiovert.app.core.designsystem

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import kotlin.math.ceil

/** Black on the light canvas; invert only for dark-theme contrast. No colored backing. */
internal fun guestIdentityInk(background: Color)=if(background.luminance()>.5f)Color.Black else Color.White

/** One vector renderer for the intro and its resting avatar, so the landing cannot swap glyphs. */
internal class GuestIdentityPainter {
    // M19 21v-2a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v2
    private val body=Path().apply {
        moveTo(19f,21f);lineTo(19f,19f)
        // A circular quarter-arc, represented with the standard cubic approximation.
        cubicTo(19f,16.790861f,17.209139f,15f,15f,15f)
        lineTo(9f,15f)
        cubicTo(6.790861f,15f,5f,16.790861f,5f,19f)
        lineTo(5f,21f)
    }
    private val clip=Path()
    private val strokePaint=Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color=android.graphics.Color.BLACK
        style=Paint.Style.STROKE
        strokeCap=Paint.Cap.ROUND
        strokeJoin=Paint.Join.ROUND
    }
    private val imagePaint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val circle=RectF()
    private val destination=RectF()
    private var mask: Bitmap?=null
    private var maskCanvas: Canvas?=null
    private var filtered: Bitmap?=null
    private var filteredCanvas: Canvas?=null
    private val sampleSource=android.graphics.Rect()
    private val filteredSource=android.graphics.Rect()
    private var sampleDiameter=0
    private var lastRing=Float.NaN
    private var lastRise=Float.NaN
    private var lastBounce=Float.NaN
    private var lastInk=0
    private var lastOutputDiameter=0

    fun draw(scope: DrawScope,rect: Rect,ink: Color,ring: Float=1f,rise: Float=1f,bounce: Float=1f) {
        if(ring<=0f||rect.width<=0f)return
        // Supersample only this glyph. Reuse both buffers through the handoff.
        val outputDiameter=ceil(rect.width).toInt()
        val requiredDiameter=outputDiameter*2
        if(requiredDiameter>sampleDiameter) {
            sampleDiameter=requiredDiameter
            mask=Bitmap.createBitmap(sampleDiameter+8,sampleDiameter+8,Bitmap.Config.ARGB_8888)
            maskCanvas=Canvas(mask!!)
            filtered=Bitmap.createBitmap(sampleDiameter/2+4,sampleDiameter/2+4,Bitmap.Config.ARGB_8888)
            filteredCanvas=Canvas(filtered!!)
            lastRing=Float.NaN
        }
        val bitmap=mask!!
        val color=ink.toArgb()
        if(ring!=lastRing||rise!=lastRise||bounce!=lastBounce||color!=lastInk||outputDiameter!=lastOutputDiameter) {
            bitmap.eraseColor(android.graphics.Color.TRANSPARENT)
            renderMask(maskCanvas!!,requiredDiameter.toFloat(),ring,rise,bounce,color)
            // Resolve the exact 2:1 sample grid before applying fractional placement.
            // A single GPU bilinear lookup at an arbitrary phase can skip samples
            // while shrinking, even with FILTER_BITMAP_FLAG enabled.
            sampleSource.set(0,0,requiredDiameter+8,requiredDiameter+8)
            filteredSource.set(0,0,outputDiameter+4,outputDiameter+4)
            filtered!!.eraseColor(android.graphics.Color.TRANSPARENT)
            filteredCanvas!!.drawBitmap(bitmap,sampleSource,filteredSource,imagePaint)
            lastRing=ring;lastRise=rise;lastBounce=bounce;lastInk=color;lastOutputDiameter=outputDiameter
        }
        // Two transparent output pixels protect the outer AA fringe from clipping.
        val padding=2f*rect.width/outputDiameter
        destination.set(rect.left-padding,rect.top-padding,rect.right+padding,rect.bottom+padding)
        scope.drawIntoCanvas {it.nativeCanvas.drawBitmap(filtered!!,filteredSource,destination,imagePaint)}
    }

    private fun renderMask(canvas: Canvas,diameter: Float,ring: Float,rise: Float,bounce: Float,ink: Int) {
        strokePaint.color=ink
        val center=diameter/2f+4f
        val stroke=diameter*GuestIdentityMotion.RING_STROKE_FRACTION
        val radius=(diameter-stroke)/2f
        circle.set(center-radius,center-radius,center+radius,center+radius)
        if(ring>=1f) {
            clip.reset();clip.addOval(circle,Path.Direction.CW)
            canvas.save()
            canvas.clipPath(clip)
            val iconSize=diameter*GuestIdentityMotion.ICON_FRACTION
            canvas.translate(center-iconSize/2f,center-iconSize/2f+diameter*GuestIdentityMotion.riseOffset(rise))
            canvas.scale(iconSize/24f,iconSize/24f)
            strokePaint.strokeWidth=2f
            // Both parts rise together; after arrival, only the head bounces.
            canvas.drawPath(body,strokePaint)
            canvas.translate(0f,GuestIdentityMotion.headOffset(bounce))
            canvas.drawCircle(12f,7f,4f,strokePaint)
            canvas.restore()
            strokePaint.strokeWidth=stroke
            canvas.drawCircle(center,center,radius,strokePaint)
        } else {
            strokePaint.strokeWidth=stroke
            canvas.drawArc(circle,-90f,360f*ring,false,strokePaint)
        }
    }
}
