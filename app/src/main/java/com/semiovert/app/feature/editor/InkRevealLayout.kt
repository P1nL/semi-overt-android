package com.semiovert.app.feature.editor

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.view.MotionEvent
import android.widget.FrameLayout

/** Clips the native WebView, including its background, without transforming its text or caret. */
internal class InkRevealLayout(
    context: Context,
    private val pointDelays: Array<FloatArray> = LiquidRevealMotion.newPointDelays(),
) : FrameLayout(context) {
    private val leadingContour = Path()
    private val editorContour = Path()
    private val leadingWave = Paint(Paint.ANTI_ALIAS_FLAG)
    var palette = EditorPalette.Dark
        set(value) {
            if (field == value) return
            field = value
            setBackgroundColor(value.background)
            updateGradient()
            invalidate()
        }
    var revealProgress = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }
    var documentReady = false

    init {
        setBackgroundColor(palette.background)
        clipChildren = false
        clipToPadding = false
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateGradient()
    }

    private fun updateGradient() {
        if (height > 0) leadingWave.shader = LinearGradient(
            0f, 0f, 0f, height.toFloat(),
            palette.waveStart, palette.waveEnd, Shader.TileMode.CLAMP,
        )
    }

    override fun draw(canvas: Canvas) {
        if (revealProgress >= 1f) {
            super.draw(canvas)
            return
        }
        if (width == 0 || height == 0) return
        trace(leadingContour, 0)
        trace(editorContour, 1)
        canvas.drawPath(leadingContour, leadingWave)
        val saved = canvas.save()
        canvas.clipPath(editorContour)
        super.draw(canvas)
        canvas.restoreToCount(saved)
    }

    private fun trace(path: Path, layer: Int) {
        path.reset()
        val delays = pointDelays[layer]
        val viewportWidth = width.toFloat()
        val viewportHeight = height.toFloat()
        path.moveTo(0f, LiquidRevealMotion.pointY(0, layer, revealProgress, viewportWidth, viewportHeight, delays))
        for (index in 0 until LiquidRevealMotion.POINT_COUNT - 1) {
            val segment = LiquidRevealMotion.segment(index, layer, revealProgress, viewportWidth, viewportHeight, delays)
            path.cubicTo(segment.first.x, segment.first.y, segment.second.x, segment.second.y, segment.end.x, segment.end.y)
        }
        path.lineTo(viewportWidth, viewportHeight)
        path.lineTo(0f, viewportHeight)
        path.close()
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean =
        if (!documentReady || revealProgress < 1f) true else super.dispatchTouchEvent(event)
}
