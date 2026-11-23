package com.semiovert.app.feature.home

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import com.semiovert.app.core.designsystem.DrawFrameCapture

/** The home content just before its last departure. */
@Stable
class HomeSnapshotState {
    private val frame=DrawFrameCapture()
    var image by mutableStateOf<ImageBitmap?>(null)
        private set

    internal suspend fun captureDeparture(graphics: GraphicsContext,onRecorded: ()->Unit) {
        // The transition starts after the outgoing frame is recorded; readback can finish later.
        image=frame.capture(graphics,onRecorded)
    }

    internal fun drawHome(scope: ContentDrawScope)=frame.draw(scope)
}

val LocalHomeSnapshot=staticCompositionLocalOf<HomeSnapshotState?> {null}
