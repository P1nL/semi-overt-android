package com.semiovert.app.feature.home

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.ImageBitmap

/** Last settled home frame, without the main-circle overlay. Kept only for this app session. */
@Stable
class HomeSnapshotState {
    var image by mutableStateOf<ImageBitmap?>(null)
}

val LocalHomeSnapshot=staticCompositionLocalOf<HomeSnapshotState?> {null}
