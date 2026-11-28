package com.mirawuse.sportspot.presentation.ui.photos

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.collection.LruCache
import androidx.compose.runtime.mutableStateMapOf
import com.mirawuse.sportspot.domain.model.photo.PhotoError
import com.mirawuse.sportspot.domain.model.photo.PhotoException
import com.mirawuse.sportspot.domain.model.photo.PhotoTarget
import com.mirawuse.sportspot.domain.repository.UserPhotoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

internal enum class SpotCoverSize(
    val maxSide: Int,
) {
    THUMBNAIL(480),
    DETAIL(1200),
}

/** Short-lived display cache. Only public gallery and image operations are used. */
@Singleton
internal class SpotCoverLoader(
    private val readGallery: suspend (PhotoTarget) -> List<String>,
    private val readImage: suspend (String) -> ByteArray,
    private val nowMillis: () -> Long = { System.nanoTime() / 1_000_000L },
) {
    @Inject
    constructor(repository: UserPhotoRepository) : this(repository::gallery, { repository.image(it) })

    private val revisions = mutableStateMapOf<PhotoTarget, Int>()
    private val permits = Semaphore(2)
    private val galleryLocks = List(16) { Mutex() }
    private val galleries = LruCache<PhotoTarget, Cached<List<String>>>(128)
    private val imageLocks = List(16) { Mutex() }
    private val images =
        object : LruCache<CoverKey, Cached<Bitmap?>>(8 * 1024 * 1024) {
            override fun sizeOf(
                key: CoverKey,
                value: Cached<Bitmap?>,
            ): Int = value.result.getOrNull()?.allocationByteCount ?: 1024
        }

    fun revision(target: PhotoTarget): Int = revisions[target] ?: 0

    fun invalidate(target: PhotoTarget) {
        revisions[target] = revision(target) + 1
        galleries.remove(target)
        for (key in images.snapshot().keys) {
            if (key.target == target) images.remove(key)
        }
    }

    suspend fun gallery(target: PhotoTarget): List<String> =
        galleryLocks[lockIndex(target)].withLock {
            val revision = revision(target)
            galleries.get(target)?.takeIf { it.expiresAt > nowMillis() }?.let { return@withLock it.result.getOrThrow() }
            val result = capturePhotoFailure { permits.withPermit { readGallery(target) } }
            currentCoroutineContext().ensureActive()
            if (revision == revision(target)) galleries.put(target, Cached(result, expiresAt(result.isSuccess)))
            result.getOrThrow()
        }

    suspend fun cover(
        target: PhotoTarget,
        size: SpotCoverSize,
    ): Bitmap? {
        val revision = revision(target)
        val ordered = capturePhotoFailure { gallery(target) }.getOrNull() ?: return null
        // Only a missing published image advances to the next candidate. Keep work bounded during an outage.
        for (id in ordered.take(2)) {
            val result = image(CoverKey(target, id, size), revision)
            if ((result.exceptionOrNull() as? PhotoException)?.error != PhotoError.NOT_FOUND) return result.getOrNull()
            discardMissing(target, id, revision)
        }
        return null
    }

    private suspend fun image(
        key: CoverKey,
        revision: Int,
    ): Result<Bitmap?> =
        imageLocks[lockIndex(key.target)].withLock {
            if (revision != revision(key.target)) return@withLock Result.success(null)
            images.get(key)?.takeIf { it.expiresAt > nowMillis() }?.let { return@withLock it.result }
            val result =
                capturePhotoFailure {
                    permits.withPermit {
                        val bytes = readImage(key.photoId)
                        withContext(Dispatchers.Default) { decodeCover(bytes, key.size.maxSide) }
                    }
                }
            currentCoroutineContext().ensureActive()
            if (revision != revision(key.target)) return@withLock Result.success(null)
            images.put(key, Cached(result, expiresAt(result.getOrNull() != null)))
            result
        }

    private fun discardMissing(
        target: PhotoTarget,
        id: String,
        revision: Int,
    ) {
        if (revision != revision(target)) return
        val cached = galleries.get(target) ?: return
        val ids = cached.result.getOrNull() ?: return
        galleries.put(target, cached.copy(result = Result.success(ids.filterNot { it == id })))
        for (size in SpotCoverSize.entries) images.remove(CoverKey(target, id, size))
    }

    private fun expiresAt(success: Boolean): Long = nowMillis() + if (success) 45_000L else 8_000L

    private fun lockIndex(target: PhotoTarget): Int = (target.hashCode() and Int.MAX_VALUE) % galleryLocks.size

    private data class Cached<T>(
        val result: Result<T>,
        val expiresAt: Long,
    )

    private data class CoverKey(
        val target: PhotoTarget,
        val photoId: String,
        val size: SpotCoverSize,
    )
}

private suspend fun <T> capturePhotoFailure(block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (failure: PhotoException) {
        Result.failure(failure)
    }

private fun decodeCover(
    bytes: ByteArray,
    maxSide: Int,
): Bitmap? {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    if (options.outWidth <= 0 || options.outHeight <= 0) return null
    val longestSide = maxOf(options.outWidth, options.outHeight)
    var sample = 1
    while (longestSide / sample > maxSide) sample *= 2
    options.inJustDecodeBounds = false
    options.inSampleSize = sample
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
}
