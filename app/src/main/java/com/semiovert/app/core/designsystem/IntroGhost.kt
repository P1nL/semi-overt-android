package com.semiovert.app.core.designsystem

import android.content.Context
import android.graphics.ColorFilter
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.airbnb.lottie.LottieComposition
import com.airbnb.lottie.LottieCompositionFactory
import com.airbnb.lottie.LottieDrawable
import com.airbnb.lottie.LottieFeatureFlag
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.RenderMode
import com.airbnb.lottie.model.KeyPath
import com.airbnb.lottie.value.LottieValueCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class IntroGhost private constructor(composition: LottieComposition) {
    val durationNanos=(composition.duration*1_000_000.0).toLong()
    private val viewport=composition.bounds
    private val drawable=LottieDrawable().apply {
        enableFeatureFlag(LottieFeatureFlag.MergePathsApi19,true)
        setComposition(composition)
        setRenderMode(RenderMode.HARDWARE)
        setBounds(0,0,viewport.width(),viewport.height())
    }
    private var tint: Int?=null

    private fun setTint(ink: Color) {
        val color=ink.toArgb()
        if(tint!=color) {
            tint=color
            drawable.addValueCallback(KeyPath("**"),LottieProperty.COLOR_FILTER,
                LottieValueCallback<ColorFilter>(PorterDuffColorFilter(color,PorterDuff.Mode.SRC_IN)))
        }
    }

    fun draw(scope: DrawScope,bounds: Rect,ink: Color,progress: Float) {
        setTint(ink)
        drawable.progress=progress
        scope.drawIntoCanvas {
            val canvas=it.nativeCanvas
            val saved=canvas.save()
            canvas.translate(bounds.left,bounds.top)
            canvas.scale(bounds.width/viewport.width(),bounds.height/viewport.height())
            drawable.draw(canvas)
            canvas.restoreToCount(saved)
        }
    }

    companion object {
        suspend fun load(context: Context,ink: Color): IntroGhost? =
            withContext(Dispatchers.IO) {
                LottieCompositionFactory.fromAssetSync(context,"identity/startup-ghost.json").value
                    ?.let {IntroGhost(it).apply {setTint(ink)}}
            }
    }
}
