package com.semiovert.app.feature.profile

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.tanh

internal const val AVATAR_OPENING = .86f
internal const val AVATAR_MAX_ZOOM = 3f

internal data class AvatarCrop(val zoom: Float = 1f, val offset: Offset = Offset.Zero) {
    fun transform(zoomFactor: Float, focus: Offset = Offset.Zero, pan: Offset = Offset.Zero): AvatarCrop {
        val nextZoom = (zoom * zoomFactor).coerceIn(1f, AVATAR_MAX_ZOOM)
        return AvatarCrop(nextZoom, focus + (offset - focus) * (nextZoom / zoom) + pan)
    }
}

internal class AvatarCropGeometry(width: Int, height: Int) {
    private val fitted = Offset(width.toFloat(), height.toFloat()) / minOf(width, height).toFloat()

    fun imageRect(crop: AvatarCrop): Rect {
        val half = fitted * (crop.zoom / 2f)
        val center = Offset(.5f, .5f) + crop.offset
        return Rect(center - half, center + half)
    }

    fun clamp(crop: AvatarCrop): AvatarCrop {
        val limit = (fitted * crop.zoom - Offset(AVATAR_OPENING, AVATAR_OPENING)) / 2f
        return crop.copy(offset = Offset(crop.offset.x.coerceIn(-limit.x, limit.x), crop.offset.y.coerceIn(-limit.y, limit.y)))
    }

    fun resisted(crop: AvatarCrop): AvatarCrop {
        val edge = clamp(crop).offset
        val excess = crop.offset - edge
        val travel = .12f
        return crop.copy(offset = edge + Offset(tanh(excess.x / travel), tanh(excess.y / travel)) * travel)
    }

    fun render(source: Bitmap, crop: AvatarCrop, width: Int = 512): Bitmap {
        val bounds = imageRect(clamp(crop))
        val inset = (1f - AVATAR_OPENING) / 2f
        val scale = width / AVATAR_OPENING
        val destination = RectF((bounds.left - inset) * scale, (bounds.top - inset) * scale,
            (bounds.right - inset) * scale, (bounds.bottom - inset) * scale)
        return Bitmap.createBitmap(width, width, Bitmap.Config.ARGB_8888).apply {
            Canvas(this).drawBitmap(source, null, destination, Paint(Paint.FILTER_BITMAP_FLAG))
        }
    }
}
