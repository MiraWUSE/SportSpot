package com.mirawuse.sportspot.domain.repository

import com.mirawuse.sportspot.domain.model.photo.PhotoAttachment
import com.mirawuse.sportspot.domain.model.photo.PhotoSubmission
import com.mirawuse.sportspot.domain.model.photo.PhotoTarget

interface UserPhotoRepository {
    suspend fun upload(
        ownerId: String,
        target: PhotoTarget,
        attachment: PhotoAttachment,
    ): PhotoSubmission

    suspend fun mine(
        ownerId: String,
        target: PhotoTarget,
    ): List<PhotoSubmission>

    suspend fun remove(
        ownerId: String,
        photoId: String,
    )

    suspend fun gallery(target: PhotoTarget): List<String>

    suspend fun image(
        photoId: String,
        previewOwnerId: String? = null,
    ): ByteArray

    suspend fun queue(
        moderatorId: String,
        proposalId: String? = null,
    ): List<PhotoSubmission>

    suspend fun decide(
        moderatorId: String,
        photoId: String,
        rejectionReason: String?,
    )
}
