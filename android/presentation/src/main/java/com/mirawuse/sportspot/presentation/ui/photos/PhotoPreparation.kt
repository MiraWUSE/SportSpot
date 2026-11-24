package com.mirawuse.sportspot.presentation.ui.photos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import com.mirawuse.sportspot.domain.model.photo.MAX_USER_PHOTO_BYTES
import com.mirawuse.sportspot.domain.model.photo.PhotoAttachment
import com.mirawuse.sportspot.domain.model.photo.PhotoError
import com.mirawuse.sportspot.domain.model.photo.PhotoException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlin.math.max

private const val MAX_SOURCE_BYTES = 25 * 1024 * 1024
private const val MAX_SIDE = 1600
private val supported = setOf("image/jpeg", "image/png", "image/webp")

internal suspend fun preparePhoto(
    context: Context,
    uri: Uri,
): PhotoAttachment =
    withContext(Dispatchers.IO) {
        val file = File.createTempFile("spot-photo-", ".tmp", context.cacheDir)
        try {
            val contextForCancellation = currentCoroutineContext()
            val input = context.contentResolver.openInputStream(uri) ?: throw PhotoException(PhotoError.INVALID)
            input.use { stream ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var size = 0
                    while (true) {
                        contextForCancellation.ensureActive()
                        val count = stream.read(buffer)
                        if (count < 0) break
                        size += count
                        if (size > MAX_SOURCE_BYTES) throw PhotoException(PhotoError.TOO_LARGE)
                        output.write(buffer, 0, count)
                    }
                }
            }
            PhotoAttachment(UUID.randomUUID().toString(), encodePhoto(file))
        } catch (_: IOException) {
            throw PhotoException(PhotoError.INVALID)
        } catch (_: SecurityException) {
            throw PhotoException(PhotoError.INVALID)
        } finally {
            file.delete()
        }
    }

private fun encodePhoto(file: File): ByteArray {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    if (bounds.outMimeType !in supported || bounds.outWidth < 1 || bounds.outHeight < 1) throw PhotoException(PhotoError.INVALID)
    if (bounds.outWidth.toLong() * bounds.outHeight > 100_000_000) throw PhotoException(PhotoError.TOO_LARGE)
    var sampleSize = 1
    while (max(bounds.outWidth, bounds.outHeight) / sampleSize > MAX_SIDE * 2) sampleSize *= 2
    val orientation =
        try {
            ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } catch (_: IOException) {
            ExifInterface.ORIENTATION_NORMAL
        }
    val bitmap =
        BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sampleSize })
            ?: throw PhotoException(PhotoError.INVALID)
    try {
        val scale = minOf(1f, MAX_SIDE.toFloat() / max(bitmap.width, bitmap.height))
        val transform =
            Matrix().apply {
                when (orientation) {
                    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                    ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
                    ExifInterface.ORIENTATION_TRANSPOSE -> {
                        setRotate(90f)
                        postScale(-1f, 1f)
                    }
                    ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
                    ExifInterface.ORIENTATION_TRANSVERSE -> {
                        setRotate(270f)
                        postScale(-1f, 1f)
                    }
                    ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(270f)
                }
                postScale(scale, scale)
            }
        val normalized = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, transform, true)
        try {
            return ByteArrayOutputStream().use { output ->
                if (!normalized.compress(Bitmap.CompressFormat.JPEG, 85, output)) throw PhotoException(PhotoError.INVALID)
                if (output.size() > MAX_USER_PHOTO_BYTES) throw PhotoException(PhotoError.TOO_LARGE)
                output.toByteArray()
            }
        } finally {
            if (normalized !== bitmap) normalized.recycle()
        }
    } finally {
        bitmap.recycle()
    }
}
