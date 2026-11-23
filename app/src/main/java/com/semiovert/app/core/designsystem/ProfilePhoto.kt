package com.semiovert.app.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import com.semiovert.app.core.data.ProfileImages

internal data class ProfilePhoto(val bitmap: ImageBitmap? = null, val loaded: Boolean = false)

@Composable
internal fun rememberProfilePhoto(name: String?): ProfilePhoto {
    val context = LocalContext.current.applicationContext
    if (name == null) return ProfilePhoto(loaded = true)
    if (name.startsWith("https://") || name.startsWith("http://")) return rememberRemoteImage(name)
    return key(name) {
        produceState(ProfilePhoto(), context, name) {
            value = ProfilePhoto(ProfileImages.load(context, name)?.asImageBitmap(), loaded = true)
        }.value
    }
}
