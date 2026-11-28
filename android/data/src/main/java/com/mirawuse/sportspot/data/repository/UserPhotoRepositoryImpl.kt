package com.mirawuse.sportspot.data.repository

import com.mirawuse.sportspot.data.remote.RequestSession
import com.mirawuse.sportspot.data.remote.api.PhotoRow
import com.mirawuse.sportspot.data.remote.api.UserPhotoApi
import com.mirawuse.sportspot.data.remote.error.NetworkCall
import com.mirawuse.sportspot.data.remote.error.NetworkError
import com.mirawuse.sportspot.data.remote.error.NetworkException
import com.mirawuse.sportspot.domain.model.auth.AuthException
import com.mirawuse.sportspot.domain.model.photo.MAX_USER_PHOTO_BYTES
import com.mirawuse.sportspot.domain.model.photo.PhotoAttachment
import com.mirawuse.sportspot.domain.model.photo.PhotoError
import com.mirawuse.sportspot.domain.model.photo.PhotoException
import com.mirawuse.sportspot.domain.model.photo.PhotoStatus
import com.mirawuse.sportspot.domain.model.photo.PhotoSubmission
import com.mirawuse.sportspot.domain.model.photo.PhotoTarget
import com.mirawuse.sportspot.domain.model.photo.PhotoTargetType
import com.mirawuse.sportspot.domain.repository.UserPhotoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject

internal class UserPhotoRepositoryImpl
    @Inject
    constructor(
        private val api: UserPhotoApi,
        private val auth: AuthRepositoryImpl,
        private val network: NetworkCall,
    ) : UserPhotoRepository {
        override suspend fun upload(
            ownerId: String,
            target: PhotoTarget,
            attachment: PhotoAttachment,
        ): PhotoSubmission =
            call(ownerId, retryUnauthorized = false) { session ->
                network
                    .body(api.upload(attachment.id, target.type.value, target.id, attachment.jpeg.toRequestBody(jpeg), session))
                    .toDomain()
                    .also { check(it.ownerId == ownerId && it.target == target && it.id == attachment.id) }
            }

        override suspend fun mine(
            ownerId: String,
            target: PhotoTarget,
        ): List<PhotoSubmission> =
            call(ownerId) { session ->
                network.body(api.submissions("mine", target.type.value, target.id, session)).map { row ->
                    row.toDomain().also { check(it.ownerId == ownerId && it.target == target) }
                }
            }

        override suspend fun remove(
            ownerId: String,
            photoId: String,
        ) = mutate(ownerId, photoId, "remove")

        override suspend fun gallery(target: PhotoTarget): List<String> =
            call { session -> network.body(api.gallery(target.type.value, target.id, session)) }

        override suspend fun image(
            photoId: String,
            previewOwnerId: String?,
        ): ByteArray =
            call(previewOwnerId) { session ->
                val action = if (previewOwnerId == null) "image" else "preview"
                network.body(api.image(action, photoId, session)).use { body ->
                    if (body.contentType() != jpeg) throw PhotoException(PhotoError.INVALID)
                    val source = body.source()
                    if (body.contentLength() > MAX_USER_PHOTO_BYTES || source.request(MAX_USER_PHOTO_BYTES.toLong() + 1)) {
                        throw PhotoException(PhotoError.TOO_LARGE)
                    }
                    source.readByteArray()
                }
            }

        override suspend fun queue(
            moderatorId: String,
            proposalId: String?,
        ): List<PhotoSubmission> =
            call(moderatorId) { session ->
                val action = if (proposalId == null) "queue" else "proposal_queue"
                network.body(api.submissions(action, null, proposalId, session)).map(PhotoRow::toDomain)
            }

        override suspend fun decide(
            moderatorId: String,
            photoId: String,
            rejectionReason: String?,
        ) = if (rejectionReason == null) {
            mutate(moderatorId, photoId, "approve")
        } else {
            mutate(moderatorId, photoId, "reject", mapOf("reason" to rejectionReason))
        }

        private suspend fun mutate(
            owner: String,
            id: String,
            action: String,
            payload: Map<String, String> = emptyMap(),
        ) {
            call(owner, retryUnauthorized = false) { session -> network.body(api.mutate(action, id, payload, session)).close() }
        }

        private suspend fun <T> call(
            owner: String? = null,
            retryUnauthorized: Boolean = true,
            block: suspend (RequestSession) -> T,
        ): T =
            try {
                if (owner == null) {
                    withContext(Dispatchers.IO) { network.execute { block(RequestSession(null)) } }
                } else {
                    auth.withAccount(owner, retryUnauthorized) { session -> network.execute { block(session) } }
                }
            } catch (failure: AuthException) {
                throw PhotoException(PhotoError.UNAUTHORIZED, failure.detail)
            } catch (failure: NetworkException) {
                val localError = failure.cause as? PhotoException
                throw localError ?: PhotoException(failure.error.toPhotoError(), failure.domainError)
            }

        private companion object {
            val jpeg = "image/jpeg".toMediaType()
        }
    }

private fun PhotoRow.toDomain(): PhotoSubmission =
    PhotoSubmission(
        id = requireNotNull(id),
        ownerId = requireNotNull(ownerId),
        target = PhotoTarget(PhotoTargetType.entries.first { it.value == targetType }, requireNotNull(targetId)),
        status = PhotoStatus.valueOf(requireNotNull(status).uppercase()),
        rejectionReason = rejectionReason,
    )

internal fun NetworkError.toPhotoError(): PhotoError =
    when (this) {
        NetworkError.Offline, NetworkError.Connection, NetworkError.Timeout, NetworkError.Dns -> PhotoError.OFFLINE
        NetworkError.InvalidRequest -> PhotoError.INVALID
        is NetworkError.Http ->
            when (status) {
                400, 415 -> PhotoError.INVALID
                401 -> PhotoError.UNAUTHORIZED
                403 -> PhotoError.FORBIDDEN
                404 -> PhotoError.NOT_FOUND
                409 -> if (serverCode == "PHOTO_LIMIT") PhotoError.LIMIT else PhotoError.CONFLICT
                413 -> PhotoError.TOO_LARGE
                429 -> PhotoError.RATE_LIMITED
                else -> PhotoError.UNAVAILABLE
            }
        else -> PhotoError.UNAVAILABLE
    }
