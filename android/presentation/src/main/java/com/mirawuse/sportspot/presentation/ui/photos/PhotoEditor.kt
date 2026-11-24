package com.mirawuse.sportspot.presentation.ui.photos

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mirawuse.sportspot.domain.model.photo.PhotoError
import com.mirawuse.sportspot.domain.model.photo.PhotoException
import com.mirawuse.sportspot.domain.model.photo.PhotoStatus
import com.mirawuse.sportspot.domain.model.photo.PhotoTarget
import com.mirawuse.sportspot.presentation.R
import com.mirawuse.sportspot.presentation.ui.components.SportSpotButton
import com.mirawuse.sportspot.presentation.ui.theme.SportSpotSpacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun PhotoEditor(
    owner: String,
    limit: Int,
    model: PhotosViewModel,
    target: PhotoTarget? = null,
    allowUpload: Boolean = true,
    enabled: Boolean = true,
) {
    val state = model.draftFor(owner)
    val editable = enabled && model.readyFor(owner)
    val contract = remember(limit) { ActivityResultContracts.PickMultipleVisualMedia(limit) }
    val picker = rememberLauncherForActivityResult(contract) { images -> model.select(owner, images, limit) }
    Column(verticalArrangement = Arrangement.spacedBy(SportSpotSpacing.sm)) {
        Text(stringResource(R.string.photos_add), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.photos_limits, limit), style = MaterialTheme.typography.bodySmall)
        for (attachment in state.attachments) {
            key(attachment.id) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PhotoBitmap(attachment.jpeg, Modifier.size(64.dp))
                    PhotoTextButton(R.string.photos_discard, enabled = !state.busy && editable) { model.discard(owner, attachment.id) }
                }
            }
        }
        if (state.preparing) Text(stringResource(R.string.photos_preparing))
        if (state.sending && state.total > 0) {
            LinearProgressIndicator(progress = { state.completed.toFloat() / state.total }, modifier = Modifier.fillMaxWidth())
            Text(stringResource(R.string.photos_progress, state.completed, state.total))
        }
        if (state.busy && state.total != 0 || state.preparing) {
            PhotoTextButton(R.string.content_cancel) { model.cancel(owner) }
        }
        state.error?.let { PhotoErrorText(it) }
        PhotoTextButton(R.string.photos_choose, enabled = editable && !state.busy && state.attachments.size < limit) {
            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        if (target != null && allowUpload && state.attachments.isNotEmpty()) {
            SportSpotButton(
                text = stringResource(if (state.error == null) R.string.photos_send else R.string.home_retry),
                onClick = { model.send(owner, target) },
                enabled = editable && !state.busy,
            )
        }
        if (target != null) PhotoHistory(owner, target, model)
    }
}

@Composable
internal fun PhotoHistory(
    owner: String,
    target: PhotoTarget,
    model: PhotosViewModel,
) {
    val ready = model.readyFor(owner)
    LaunchedEffect(owner, target, ready) { if (ready) model.load(owner, target) }
    val history = model.historyFor(owner, target)
    val busy = model.draftFor(owner).busy
    if (history.items.isNotEmpty() || history.loading || history.error != null) {
        Text(stringResource(R.string.photos_mine), style = MaterialTheme.typography.titleMedium)
        if (history.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        history.error?.let { PhotoErrorText(it) }
        for (item in history.items) {
            key(owner, item.id) {
                var preview by remember { mutableStateOf(false) }
                Text(stringResource(item.status.labelRes()))
                item.rejectionReason?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                if (item.status == PhotoStatus.PENDING || item.status == PhotoStatus.APPROVED) {
                    PhotoTextButton(if (preview) R.string.photos_hide_preview else R.string.photos_preview) { preview = !preview }
                    if (preview) RemotePhoto(item.id, model, owner)
                }
                PhotoTextButton(R.string.photos_remove, enabled = !busy) { model.remove(owner, target, item) }
            }
        }
        PhotoTextButton(R.string.photos_refresh, enabled = !history.loading) { model.load(owner, target) }
    }
}

@Composable
internal fun RemotePhoto(
    id: String,
    model: PhotosViewModel,
    owner: String? = null,
) {
    // Scope the state itself to the account. produceState keys alone retain the old value during a reload.
    key(owner, id) {
        val result by produceState<Result<ByteArray>?>(null) {
            value =
                try {
                    Result.success(model.image(id, owner))
                } catch (error: PhotoException) {
                    Result.failure(error)
                }
        }
        val error = result?.exceptionOrNull() as? PhotoException
        val bytes = result?.getOrNull()
        when {
            bytes != null -> PhotoBitmap(bytes, Modifier.fillMaxWidth().height(160.dp))
            error != null -> PhotoErrorText(error.error)
            else -> CircularProgressIndicator(Modifier.size(24.dp))
        }
    }
}

@Composable
private fun PhotoBitmap(
    bytes: ByteArray,
    modifier: Modifier,
) {
    val bitmap by produceState<ImageBitmap?>(null, bytes) {
        value =
            withContext(Dispatchers.Default) {
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = 2 })?.asImageBitmap()
            }
    }
    Box(modifier.clip(MaterialTheme.shapes.medium), contentAlignment = Alignment.Center) {
        bitmap?.let { image ->
            Image(image, stringResource(R.string.photos_preview), Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        }
    }
}

@Composable
internal fun PhotoTextButton(
    label: Int,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
    ) {
        Text(stringResource(label))
    }
}

@Composable
internal fun PhotoErrorText(error: PhotoError) {
    Text(stringResource(error.labelRes()), color = MaterialTheme.colorScheme.error)
}

private fun PhotoStatus.labelRes(): Int =
    when (this) {
        PhotoStatus.UPLOADING -> R.string.photos_incomplete
        PhotoStatus.PENDING -> R.string.photos_pending
        PhotoStatus.APPROVED -> R.string.photos_approved
        PhotoStatus.REJECTED -> R.string.photos_rejected
    }

private fun PhotoError.labelRes(): Int =
    when (this) {
        PhotoError.INVALID -> R.string.photos_invalid
        PhotoError.TOO_LARGE -> R.string.photos_too_large
        PhotoError.OFFLINE -> R.string.photos_offline
        PhotoError.UNAUTHORIZED -> R.string.photos_sign_in
        PhotoError.FORBIDDEN -> R.string.photos_forbidden
        PhotoError.NOT_FOUND -> R.string.photos_missing
        PhotoError.CONFLICT -> R.string.photos_conflict
        PhotoError.LIMIT -> R.string.photos_limit_reached
        PhotoError.RATE_LIMITED -> R.string.photos_rate_limited
        PhotoError.UNAVAILABLE -> R.string.photos_unavailable
    }
