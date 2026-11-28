package com.mirawuse.sportspot.presentation.ui.photos

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mirawuse.sportspot.domain.model.photo.PhotoException
import com.mirawuse.sportspot.domain.model.photo.PhotoTarget
import com.mirawuse.sportspot.domain.model.photo.PhotoTargetType
import com.mirawuse.sportspot.presentation.R
import com.mirawuse.sportspot.presentation.ui.theme.SportSpotSpacing

@Composable
internal fun PhotoGallery(
    target: PhotoTarget,
    model: PhotosViewModel,
    collapsible: Boolean = false,
    showTitle: Boolean = !collapsible,
    refreshButton: @Composable (label: Int, enabled: Boolean, onRefresh: () -> Unit) -> Unit = { label, enabled, onRefresh ->
        PhotoActionButton(label, enabled = enabled, onClick = onRefresh)
    },
) {
    key(target) {
        var expanded by rememberSaveable { mutableStateOf(!collapsible) }
        if (collapsible) {
            if (expanded) {
                PhotoVisibilityButton(true) { expanded = false }
            } else {
                PhotoActionButton(R.string.photos_review_gallery, icon = R.drawable.ic_photo_visibility) { expanded = true }
            }
        }
        if (expanded) GalleryContent(target, model, showTitle, refreshButton)
    }
}

@Composable
private fun GalleryContent(
    target: PhotoTarget,
    model: PhotosViewModel,
    showTitle: Boolean,
    refreshButton: @Composable (label: Int, enabled: Boolean, onRefresh: () -> Unit) -> Unit,
) {
    var attempt by rememberSaveable { mutableIntStateOf(0) }
    var opened by rememberSaveable { mutableStateOf<String?>(null) }
    val spotCovers = model.spotCovers.takeIf { target.type == PhotoTargetType.SPOT }
    val gallery =
        key(attempt, spotCovers?.revision(target)) {
            val result by produceState<Result<List<String>>?>(null) {
                value =
                    try {
                        Result.success(model.gallery(target))
                    } catch (error: PhotoException) {
                        Result.failure(error)
                    }
            }
            result
        }
    val images = gallery?.getOrNull()
    val error = gallery?.exceptionOrNull() as? PhotoException
    Column(verticalArrangement = Arrangement.spacedBy(SportSpotSpacing.sm)) {
        if (showTitle) Text(stringResource(R.string.photos_gallery), style = MaterialTheme.typography.titleMedium)
        when {
            error != null -> PhotoErrorText(error.error)
            images == null -> LinearProgressIndicator(Modifier.fillMaxWidth())
            images.isEmpty() -> Text(stringResource(R.string.photos_empty_gallery))
            else ->
                LazyRow(horizontalArrangement = Arrangement.spacedBy(SportSpotSpacing.sm)) {
                    items(images, key = { it }) { id ->
                        RemotePhoto(
                            id,
                            model,
                            modifier = Modifier.width(132.dp).height(104.dp).clickable { opened = id },
                            scale = ContentScale.Crop,
                        )
                    }
                }
        }
        refreshButton(if (error != null) R.string.home_retry else R.string.photos_refresh, gallery != null) {
            if (spotCovers != null) spotCovers.invalidate(target) else attempt++
        }
    }
    opened?.takeIf { images?.contains(it) == true }?.let { id ->
        AlertDialog(
            onDismissRequest = { opened = null },
            text = { RemotePhoto(id, model, modifier = Modifier.fillMaxWidth().height(280.dp)) },
            confirmButton = { PhotoActionButton(R.string.content_cancel) { opened = null } },
        )
    }
}
