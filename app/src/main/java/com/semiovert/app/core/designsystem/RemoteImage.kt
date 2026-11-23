package com.semiovert.app.core.designsystem

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.asImageBitmap
import com.semiovert.app.core.data.LocalBusiness
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI

private val imageCache = object : LruCache<String, Bitmap>(16 * 1024 * 1024) {
    override fun sizeOf(key: String, value: Bitmap) = value.byteCount
}

@Composable
internal fun rememberRemoteImage(url: String?, maxSide: Int = 1200): ProfilePhoto {
    val address = LocalBusiness.current?.mediaUrl(url) ?: return ProfilePhoto(loaded = true)
    val cacheKey = "$maxSide:$address"
    return key(cacheKey) {
        val cached = imageCache.get(cacheKey)
        produceState(ProfilePhoto(cached?.asImageBitmap(), cached != null), cacheKey) {
            if (cached == null) {
                val image = withContext(Dispatchers.IO) {
                    runCatching { loadImage(address, maxSide) }.getOrNull()?.also { imageCache.put(cacheKey, it) }
                }
                value = ProfilePhoto(image?.asImageBitmap(), loaded = true)
            }
        }.value
    }
}

private fun loadImage(address: String, maxSide: Int): Bitmap? {
    val connection = URI(address).toURL().openConnection() as HttpURLConnection
    val limit = 10 * 1024 * 1024
    try {
        connection.instanceFollowRedirects = false
        connection.connectTimeout = 7_000
        connection.readTimeout = 12_000
        if (connection.responseCode !in 200..299 || !connection.contentType.orEmpty().startsWith("image/") ||
            connection.contentLengthLong > limit) return null
        val bytes = connection.inputStream.use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (output.size() + read > limit) return null
                output.write(buffer, 0, read)
            }
            output.toByteArray()
        }
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        if (options.outWidth <= 0 || options.outHeight <= 0) return null
        val ratio = (maxOf(options.outWidth, options.outHeight) / maxSide).coerceAtLeast(1)
        options.inSampleSize = Integer.highestOneBit(ratio)
        options.inJustDecodeBounds = false
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    } finally {
        connection.disconnect()
    }
}
