package com.semiovert.app.core.designsystem

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import kotlinx.coroutines.CompletableDeferred

/** Keeps the frame's layer alive through readback, even if its content leaves composition. */
@Stable
internal class DrawFrameCapture {
    private class Frame(val layer: GraphicsLayer) {
        val recorded=CompletableDeferred<Unit>()
    }

    private var pending by mutableStateOf<Frame?>(null)

    suspend fun capture(graphics: GraphicsContext,onRecorded: ()->Unit={}): ImageBitmap {
        val frame=Frame(graphics.createGraphicsLayer())
        pending=frame
        try {
            frame.recorded.await()
            pending=null
            onRecorded()
            return frame.layer.toImageBitmap()
        } finally {
            if(pending===frame)pending=null
            graphics.releaseGraphicsLayer(frame.layer)
        }
    }

    fun draw(scope: ContentDrawScope)=with(scope) {
        val frame=pending
        if(frame==null||frame.recorded.isCompleted)drawContent()
        else {
            frame.layer.record {scope.drawContent()}
            drawLayer(frame.layer)
            frame.recorded.complete(Unit)
        }
    }
}
