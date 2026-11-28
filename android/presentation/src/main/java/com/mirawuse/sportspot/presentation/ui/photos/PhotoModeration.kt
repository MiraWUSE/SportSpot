package com.mirawuse.sportspot.presentation.ui.photos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
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
import com.mirawuse.sportspot.presentation.ui.forms.networkRetryReady
import com.mirawuse.sportspot.presentation.ui.moderation.ModerationDecisionButtons
import com.mirawuse.sportspot.presentation.ui.moderation.ModerationField
import com.mirawuse.sportspot.presentation.ui.moderation.ModerationHeading
import com.mirawuse.sportspot.presentation.ui.theme.SportSpotSpacing

@Composable
internal fun PhotoModeration(
    owner: String,
    model: PhotosViewModel,
) {
    val ready = model.readyFor(owner)
    LaunchedEffect(owner, ready) { if (ready) model.loadModeration(owner) }
    val queue = model.moderationFor(owner)
    ModerationHeading(stringResource(R.string.moderation_photo_queue)) {
        val error = queue.error
        if (error == null || error.canRetryPhotoRead()) {
            val label = if (error == null) R.string.photos_refresh else R.string.home_retry
            PhotoActionButton(label, enabled = ready && !queue.loading && networkRetryReady(queue.detail)) { model.loadModeration(owner) }
        }
    }
    if (!ready || queue.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
    queue.error?.let { PhotoErrorText(it, queue.detail) }
    if (ready && !queue.loading && queue.items.isEmpty() && queue.error == null) {
        Text(stringResource(R.string.photos_queue_empty))
    }
    for ((index, photo) in queue.items.withIndex()) {
        key(owner, photo.id) {
            PhotoModerationCard(owner, photo, index + 1, model, enabled = !queue.loading)
        }
    }
}

@Composable
private fun PhotoModerationCard(
    owner: String,
    photo: PhotoSubmission,
    number: Int,
    model: PhotosViewModel,
    enabled: Boolean,
) {
    var reject by rememberSaveable { mutableStateOf(false) }
    var reason by rememberSaveable { mutableStateOf("") }
    var preview by rememberSaveable { mutableStateOf(false) }
    val targetLabel =
        when (photo.target.type) {
            PhotoTargetType.SPOT -> R.string.photos_target_spot
            PhotoTargetType.REVIEW -> R.string.photos_target_review
            PhotoTargetType.PROPOSAL -> R.string.photos_target_proposal
        }
    FormCard(stringResource(R.string.moderation_photo_title, stringResource(targetLabel), number)) {
        PhotoStatusBadge(photo.status)
        ModerationField(stringResource(R.string.moderation_target_id), photo.target.id)
        ModerationField(stringResource(R.string.moderation_author_id), photo.ownerId)
        PhotoVisibilityButton(preview) { preview = !preview }
        if (preview) RemotePhoto(photo.id, model, owner)
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
        ModerationDecisionButtons(
            enabled = enabled,
            onApprove = { model.moderate(owner, photo.id, null) },
            onReject = { reject = true },
        )
    }
    if (reject) {
        AlertDialog(
            onDismissRequest = { reject = false },
            title = { Text(stringResource(R.string.moderation_reject_photo_title)) },
            text = {
                OutlinedTextField(
                    value = reason,
                    onValueChange = { if (it.length <= 500) reason = it },
                    label = { Text(stringResource(R.string.moderation_reason)) },
                )
            },
            confirmButton = {
                PhotoActionButton(R.string.moderation_confirm_rejection, enabled = enabled && reason.isNotBlank(), danger = true) {
                    reject = false
                    model.moderate(owner, photo.id, reason.trim())
                }
            },
            dismissButton = { PhotoActionButton(R.string.content_cancel) { reject = false } },
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
        val ready = model.readyFor(owner)
        var expanded by rememberSaveable { mutableStateOf(false) }
        var attempt by rememberSaveable { mutableIntStateOf(0) }
        val result by produceState<Result<List<PhotoSubmission>>?>(null, ready, expanded, attempt) {
            if (ready && expanded) {
                value = null
                value =
                    try {
                        Result.success(model.queue(owner, proposalId))
                    } catch (error: PhotoException) {
                        Result.failure(error)
                    }
            }
        }
        if (ready) {
            val photos = result?.getOrNull()
            val error = result?.exceptionOrNull() as? PhotoException
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                Column(
                    modifier = Modifier.padding(SportSpotSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(SportSpotSpacing.sm),
                ) {
                    ModerationHeading(stringResource(R.string.photos_proposal_evidence), R.drawable.ic_photo_image) {
                        if (photos?.isNotEmpty() == true) {
                            PhotoVisibilityButton(expanded) { expanded = !expanded }
                        } else if (!expanded) {
                            PhotoActionButton(R.string.moderation_check_attachments) { expanded = true }
                        }
                    }
                    when {
                        !expanded -> Unit
                        error != null -> {
                            PhotoErrorText(error.error, error.detail)
                            if (error.error.canRetryPhotoRead()) {
                                PhotoActionButton(R.string.home_retry, enabled = networkRetryReady(error.detail)) { attempt++ }
                            }
                        }
                        photos == null -> LinearProgressIndicator(Modifier.fillMaxWidth())
                        photos.isEmpty() -> Text(stringResource(R.string.moderation_no_attachments))
                        else ->
                            photos.forEachIndexed { index, photo ->
                                key(photo.id) {
                                    if (index > 0) HorizontalDivider()
                                    Text(
                                        stringResource(R.string.moderation_photo_number, index + 1),
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    PhotoStatusBadge(photo.status)
                                    RemotePhoto(photo.id, model, owner)
                                }
                            }
                    }
                }
            }
        }
    }
}
