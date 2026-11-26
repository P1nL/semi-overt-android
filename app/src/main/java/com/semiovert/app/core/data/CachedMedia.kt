package com.semiovert.app.core.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.AtomicFile
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest

internal class CachedMedia(context: Context, private val mediaUrl: (String?) -> String?) {
    private val folder = File(context.cacheDir, "offline-media-v1")
    private val diskLock = Any()
    private val bitmaps = object : LruCache<String, Bitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    fun memory(address: String, maxSide: Int): Bitmap? = bitmaps.get("$maxSide:$address")

    suspend fun seedFromUpload(url: String, source: File) {
        val address = mediaUrl(url) ?: return
        withContext(Dispatchers.IO) {
            val bitmap = BitmapFactory.decodeFile(source.absolutePath) ?: return@withContext
            val image = scaled(bitmap, MAX_SIDE)
            bitmaps.put("$MAX_SIDE:$address", image)
            save(file(address), image)
        }
    }

    suspend fun load(address: String, maxSide: Int): Bitmap? = withContext(Dispatchers.IO) {
        memory(address, maxSide) ?: runCatching {
            val file = file(address)
            val image = cached(file, maxSide) ?: download(address)?.let { bitmap ->
                save(file, bitmap)
                scaled(bitmap, maxSide)
            }
            image?.also { bitmaps.put("$maxSide:$address", it) }
        }.getOrNull()
    }

    suspend fun prefetch(url: String?) {
        val address = mediaUrl(url) ?: return
        withContext(Dispatchers.IO) {
            runCatching {
                val file = file(address)
                if (!file.baseFile.isFile) download(address)?.let { bitmap ->
                    try { save(file, bitmap) } finally { bitmap.recycle() }
                }
            }
        }
    }

    fun open(url: String?): FileInputStream? {
        val address = mediaUrl(url) ?: return null
        val file = file(address)
        return synchronized(diskLock) {
            runCatching { file.openRead().also { file.baseFile.setLastModified(System.currentTimeMillis()) } }.getOrNull()
        }
    }

    private fun file(address: String): AtomicFile {
        val key = MessageDigest.getInstance("SHA-256").digest(address.toByteArray()).toHexString()
        return AtomicFile(File(folder, "$key.png"))
    }

    private fun cached(file: AtomicFile, maxSide: Int): Bitmap? {
        val input = synchronized(diskLock) {
            runCatching { file.openRead().also { file.baseFile.setLastModified(System.currentTimeMillis()) } }.getOrNull()
        } ?: return null
        return input.use { scaled(BitmapFactory.decodeStream(it) ?: return null, maxSide) }
    }

    private fun scaled(bitmap: Bitmap, maxSide: Int): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= maxSide) return bitmap
        val scale = maxSide.toFloat() / longest
        val result = Bitmap.createScaledBitmap(bitmap, (bitmap.width*scale).toInt().coerceAtLeast(1),
            (bitmap.height*scale).toInt().coerceAtLeast(1), true)
        if (result !== bitmap) bitmap.recycle()
        return result
    }

    private fun save(file: AtomicFile, bitmap: Bitmap) = synchronized(diskLock) {
        runCatching {
            val stream = file.startWrite()
            try {
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
                file.finishWrite(stream)
            } catch (error: Exception) {
                file.failWrite(stream)
                throw error
            }
            var remaining = DISK_BYTES
            folder.listFiles { item -> item.isFile && item.extension == "png" }.orEmpty()
                .sortedByDescending(File::lastModified).forEach { candidate ->
                    remaining -= candidate.length()
                    if (remaining < 0) AtomicFile(candidate).delete()
                }
        }
    }

    private fun download(address: String): Bitmap? {
        val connection = URI(address).toURL().openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 7_000
            connection.readTimeout = 12_000
            if (connection.responseCode !in 200..299 || !connection.contentType.orEmpty().startsWith("image/") ||
                connection.contentLengthLong > IMAGE_BYTES) return null
            val bytes = connection.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (output.size() + count > IMAGE_BYTES) return null
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            if (options.outWidth <= 0 || options.outHeight <= 0) return null
            val ratio = (maxOf(options.outWidth, options.outHeight) / MAX_SIDE).coerceAtLeast(1)
            options.inSampleSize = Integer.highestOneBit(ratio)
            options.inJustDecodeBounds = false
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.let { scaled(it, MAX_SIDE) }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val MAX_SIDE = 1200
        const val IMAGE_BYTES = 10 * 1024 * 1024
        const val DISK_BYTES = 64L * 1024 * 1024
    }
}
