package com.mirawuse.sportspot.domain.model.photo

import com.mirawuse.sportspot.domain.model.spot.SpotId

const val MAX_USER_PHOTO_BYTES = 5 * 1024 * 1024
private val uuid = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
private val osmId = Regex("osm:(node|way|relation):(0|-?[1-9][0-9]{0,18})")

enum class PhotoTargetType(
    val value: String,
    val limit: Int,
) {
    SPOT("spot", 5),
    REVIEW("review", 3),
    PROPOSAL("proposal", 3),
}

data class PhotoTarget(
    val type: PhotoTargetType,
    val id: String,
) {
    init {
        require(
            when (type) {
                PhotoTargetType.SPOT ->
                    if (id.startsWith("community:")) {
                        uuid.matches(id.removePrefix("community:"))
                    } else {
                        osmId.matches(id) && id.substringAfterLast(':').toLongOrNull() != null
                    }
                PhotoTargetType.REVIEW, PhotoTargetType.PROPOSAL -> uuid.matches(id)
            },
        ) { "Invalid photo target" }
    }

    companion object {
        fun spot(id: SpotId): PhotoTarget = PhotoTarget(PhotoTargetType.SPOT, "${id.source}:${id.value}")

        fun review(id: String): PhotoTarget = PhotoTarget(PhotoTargetType.REVIEW, id)

        fun proposal(id: String): PhotoTarget = PhotoTarget(PhotoTargetType.PROPOSAL, id)
    }
}

class PhotoAttachment(
    val id: String,
    val jpeg: ByteArray,
) {
    init {
        require(uuid.matches(id))
        require(jpeg.size in 1..MAX_USER_PHOTO_BYTES)
    }
}

enum class PhotoStatus { UPLOADING, PENDING, APPROVED, REJECTED }

data class PhotoSubmission(
    val id: String,
    val ownerId: String,
    val target: PhotoTarget,
    val status: PhotoStatus,
    val rejectionReason: String?,
) {
    init {
        require(uuid.matches(id) && uuid.matches(ownerId))
    }
}

enum class PhotoError { INVALID, TOO_LARGE, OFFLINE, UNAUTHORIZED, FORBIDDEN, NOT_FOUND, CONFLICT, LIMIT, RATE_LIMITED, UNAVAILABLE }

class PhotoException(
    val error: PhotoError,
) : Exception(error.name)
