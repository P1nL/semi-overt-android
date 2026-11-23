package com.semiovert.app.core.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

internal object ProfileImages {
    private const val MAX_SIDE = 2048

    suspend fun importPhoto(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        val image = runCatching { decode(context, uri) }.getOrNull() ?: return@withContext null
        try {
            write(context, image)
        } finally {
            image.recycle()
        }
    }

    suspend fun load(context: Context, name: String): Bitmap? = withContext(Dispatchers.IO) {
        file(context, name)?.let { BitmapFactory.decodeFile(it.absolutePath) }
    }

    fun delete(context: Context, name: String?) {
        name?.let { file(context, it)?.delete() }
    }

    suspend fun save(context: Context, render: () -> Bitmap): String? = withContext(Dispatchers.IO) {
        val image = render()
        try {
            write(context, image)
        } finally {
            image.recycle()
        }
    }

    private fun directory(context: Context) = File(context.filesDir, "profile-media")

    private fun file(context: Context, name: String): File? =
        if (name.startsWith("photo-") && name == File(name).name) File(directory(context), name) else null

    fun uploadFile(context: Context, name: String): File? = file(context, name)?.takeIf { it.isFile }

    private fun write(context: Context, image: Bitmap): String? {
        var output: File? = null
        return try {
            val folder = directory(context).apply { mkdirs() }
            val alpha = image.hasAlpha()
            val target = File.createTempFile("photo-", if (alpha) ".png" else ".jpg", folder)
            output = target
            val format = if (alpha) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
            if (!target.outputStream().use { image.compress(format, 94, it) }) throw IOException()
            target.name
        } catch (_: IOException) {
            output?.delete()
            null
        }
    }

    private fun decode(context: Context, uri: Uri): Bitmap? {
        val resolver = context.contentResolver
        if (Build.VERSION.SDK_INT >= 28) {
            return ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val longest = maxOf(info.size.width, info.size.height)
                if (longest > MAX_SIDE) {
                    val scale = MAX_SIDE.toFloat() / longest
                    decoder.setTargetSize((info.size.width * scale).roundToInt().coerceAtLeast(1),
                        (info.size.height * scale).roundToInt().coerceAtLeast(1))
                }
            }
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        if (longest <= 0) return null
        val options = BitmapFactory.Options().apply {
            inSampleSize = Integer.highestOneBit((longest - 1) / MAX_SIDE).coerceAtLeast(1)
        }
        val source = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) } ?: return null
        val orientation = runCatching { resolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } }.getOrNull()
        val transform = Matrix().apply {
            if (orientation in listOf(2, 4, 5, 7)) postScale(-1f, 1f)
            val degrees = when (orientation) {
                3, 4 -> 180f
                6, 7 -> 90f
                5, 8 -> 270f
                else -> 0f
            }
            if (degrees != 0f) postRotate(degrees)
            val scale = MAX_SIDE.toFloat() / maxOf(source.width, source.height)
            if (scale < 1f) postScale(scale, scale)
        }
        if (transform.isIdentity) return source
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, transform, true)
            .also { if (it !== source) source.recycle() }
    }
}
