package com.semiovert.app.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.asImageBitmap
import com.semiovert.app.core.data.LocalBusiness

@Composable
internal fun rememberRemoteImage(url: String?, maxSide: Int = 1200): ProfilePhoto {
    val business = LocalBusiness.current ?: return ProfilePhoto(loaded = true)
    val address = business.mediaUrl(url) ?: return ProfilePhoto(loaded = true)
    return key(business, address, maxSide) {
        val cached = business.media.memory(address, maxSide)
        produceState(ProfilePhoto(cached?.asImageBitmap(), cached != null), address, maxSide) {
            if (cached == null) {
                val image = business.media.load(address, maxSide)
                value = ProfilePhoto(image?.asImageBitmap(), loaded = true)
            }
        }.value
    }
}
