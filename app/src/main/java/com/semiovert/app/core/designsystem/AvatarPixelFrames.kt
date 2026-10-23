package com.semiovert.app.core.designsystem

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap

/** Prepare once off the UI thread; each animation frame only selects a cached resolution. */
internal class AvatarPixelFrames(source: ImageBitmap,private val size: Int=AvatarPixelMotion.OPENING_SIZE) {
    private val full: ImageBitmap
    private val levels=mutableMapOf<Int,ImageBitmap>()

    init {
        val original=source.asAndroidBitmap()
        val readable=if(original.config==Bitmap.Config.HARDWARE)original.copy(Bitmap.Config.ARGB_8888,false) else original
        // Web drawImage center-crops the original image to a square before downsampling.
        val side=minOf(readable.width,readable.height)
        val square=Bitmap.createBitmap(readable,(readable.width-side)/2,(readable.height-side)/2,side,side)
        full=square.asImageBitmap()
        var cells=4
        while(cells<size) {
            levels[cells]=Bitmap.createScaledBitmap(square,cells,cells,true).asImageBitmap()
            cells*=2
        }
    }

    fun frame(progress: Float): ImageBitmap {
        val cells=AvatarPixelMotion.resolution(progress,size)
        return if(cells==size)full else levels.getValue(cells)
    }
}
