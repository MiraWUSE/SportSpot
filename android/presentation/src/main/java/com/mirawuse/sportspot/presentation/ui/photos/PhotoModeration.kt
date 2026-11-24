package com.mirawuse.sportspot.presentation.ui.photos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.mirawuse.sportspot.domain.model.photo.PhotoException
import com.mirawuse.sportspot.domain.model.photo.PhotoSubmission
import com.mirawuse.sportspot.domain.model.photo.PhotoTargetType
import com.mirawuse.sportspot.presentation.R
import com.mirawuse.sportspot.presentation.ui.forms.FormCard
import com.mirawuse.sportspot.presentation.ui.theme.SportSpotSpacing

@Composable
internal fun PhotoModeration(
    owner: String,
    model: PhotosViewModel,
) {
    val ready = model.readyFor(owner)
    LaunchedEffect(owner, ready) { if (ready) model.loadModeration(owner) }
    val queue = model.moderationFor(owner)
    if (!ready || queue.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
    queue.error?.let { PhotoErrorText(it) }
    if (ready && !queue.loading && queue.items.isEmpty() && queue.error == null) {
        Text(stringResource(R.string.photos_queue_empty))
    }
    for (photo in queue.items) {
        key(owner, photo.id) {
            PhotoModerationCard(owner, photo, model, enabled = !queue.loading)
        }
    }
    PhotoTextButton(R.string.photos_refresh, enabled = ready && !queue.loading) { model.loadModeration(owner) }
}

@Composable
private fun PhotoModerationCard(
    owner: String,
    photo: PhotoSubmission,
    model: PhotosViewModel,
    enabled: Boolean,
) {
    var reject by rememberSaveable { mutableStateOf(false) }
    var reason by rememberSaveable { mutableStateOf("") }
    val label =
        when (photo.target.type) {
            PhotoTargetType.SPOT -> R.string.photos_target_spot
            PhotoTargetType.REVIEW -> R.string.photos_target_review
            PhotoTargetType.PROPOSAL -> R.string.photos_target_proposal
        }
    FormCard(stringResource(label)) {
        Text(stringResource(R.string.photos_target_id, photo.target.id))
        Text(stringResource(R.string.photos_author_id, photo.ownerId))
        PrivatePreview(owner, photo.id, model)
        Row(horizontalArrangement = Arrangement.spacedBy(SportSpotSpacing.sm)) {
            PhotoTextButton(R.string.moderation_approve, enabled = enabled) { model.moderate(owner, photo.id, null) }
            PhotoTextButton(R.string.moderation_reject, enabled = enabled) { reject = true }
        }
    }
    if (reject) {
        AlertDialog(
            onDismissRequest = { reject = false },
            title = { Text(stringResource(R.string.moderation_reject)) },
            text = {
                OutlinedTextField(
                    value = reason,
                    onValueChange = { if (it.length <= 500) reason = it },
                    label = { Text(stringResource(R.string.moderation_reason)) },
                )
            },
            confirmButton = {
                PhotoTextButton(R.string.moderation_confirm_rejection, enabled = enabled && reason.isNotBlank()) {
                    reject = false
                    model.moderate(owner, photo.id, reason.trim())
                }
            },
            dismissButton = { PhotoTextButton(R.string.content_cancel) { reject = false } },
        )
    }
}

@Composable
internal fun ProposalPhotoEvidence(
    owner: String,
    proposalId: String,
    model: PhotosViewModel,
) {
    key(owner, proposalId) {
        var expanded by rememberSaveable { mutableStateOf(false) }
        PhotoTextButton(
            if (expanded) R.string.photos_hide_preview else R.string.photos_proposal_evidence,
            enabled = model.readyFor(owner),
        ) { expanded = !expanded }
        if (expanded && model.readyFor(owner)) {
            val result by produceState<Result<List<PhotoSubmission>>?>(null) {
                value =
                    try {
                        Result.success(model.queue(owner, proposalId))
                    } catch (error: PhotoException) {
                        Result.failure(error)
                    }
            }
            val photos = result?.getOrNull()
            val error = result?.exceptionOrNull() as? PhotoException
            when {
                error != null -> PhotoErrorText(error.error)
                photos == null -> LinearProgressIndicator(Modifier.fillMaxWidth())
                photos.isEmpty() -> Text(stringResource(R.string.photos_queue_empty))
                else ->
                    photos.forEach { photo ->
                        key(photo.id) {
                            Text(stringResource(photo.status.labelRes()))
                            PrivatePreview(owner, photo.id, model)
                        }
                    }
            }
        }
    }
}

@Composable
private fun PrivatePreview(
    owner: String,
    id: String,
    model: PhotosViewModel,
) {
    var preview by rememberSaveable { mutableStateOf(false) }
    PhotoTextButton(if (preview) R.string.photos_hide_preview else R.string.photos_preview) { preview = !preview }
    if (preview) RemotePhoto(id, model, owner)
}
