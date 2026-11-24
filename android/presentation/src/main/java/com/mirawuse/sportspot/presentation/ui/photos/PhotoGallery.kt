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
import com.mirawuse.sportspot.presentation.R
import com.mirawuse.sportspot.presentation.ui.theme.SportSpotSpacing

@Composable
internal fun PhotoGallery(
    target: PhotoTarget,
    model: PhotosViewModel,
    collapsible: Boolean = false,
) {
    key(target) {
        var expanded by rememberSaveable { mutableStateOf(!collapsible) }
        if (collapsible) {
            PhotoTextButton(if (expanded) R.string.photos_hide_preview else R.string.photos_review_gallery) { expanded = !expanded }
        }
        if (expanded) GalleryContent(target, model, showTitle = !collapsible)
    }
}

@Composable
private fun GalleryContent(
    target: PhotoTarget,
    model: PhotosViewModel,
    showTitle: Boolean,
) {
    var attempt by rememberSaveable { mutableIntStateOf(0) }
    var opened by rememberSaveable { mutableStateOf<String?>(null) }
    val gallery by produceState<Result<List<String>>?>(null, attempt) {
        value = null
        value =
            try {
                Result.success(model.gallery(target))
            } catch (error: PhotoException) {
                Result.failure(error)
            }
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
        PhotoTextButton(if (error != null) R.string.home_retry else R.string.photos_refresh, enabled = gallery != null) { attempt++ }
    }
    opened?.takeIf { images?.contains(it) == true }?.let { id ->
        AlertDialog(
            onDismissRequest = { opened = null },
            text = { RemotePhoto(id, model, modifier = Modifier.fillMaxWidth().height(280.dp)) },
            confirmButton = { PhotoTextButton(R.string.content_cancel) { opened = null } },
        )
    }
}
