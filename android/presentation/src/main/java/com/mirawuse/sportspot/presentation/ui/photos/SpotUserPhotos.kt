package com.mirawuse.sportspot.presentation.ui.photos

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mirawuse.sportspot.domain.model.photo.PhotoTarget
import com.mirawuse.sportspot.presentation.R
import com.mirawuse.sportspot.presentation.ui.theme.SportSpotSpacing

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SpotUserPhotos(
    owner: String?,
    target: PhotoTarget,
    model: PhotosViewModel,
    onOpenAuth: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = colors.surface,
        contentColor = colors.onSurface,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, colors.outline.copy(alpha = 0.32f)),
    ) {
        Column(
            modifier = Modifier.padding(SportSpotSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(SportSpotSpacing.md),
        ) {
            Text(
                stringResource(R.string.photos_gallery),
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.headlineSmall,
            )
            PhotoGallery(target, model, showTitle = false) { label, canRefresh, refreshGallery ->
                if (owner == null) {
                    PhotoActionButton(
                        label = if (label == R.string.home_retry) label else R.string.photos_refresh_gallery,
                        enabled = canRefresh,
                        icon = R.drawable.ic_action_refresh,
                        onClick = refreshGallery,
                    )
                } else {
                    val history = model.historyFor(owner, target)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalArrangement = Arrangement.spacedBy(SportSpotSpacing.xs),
                        itemVerticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.photos_my_uploads),
                            modifier = Modifier.padding(end = SportSpotSpacing.sm).semantics { heading() },
                            style = MaterialTheme.typography.titleMedium,
                        )
                        PhotoActionButton(R.string.photos_refresh, enabled = canRefresh && model.readyFor(owner) && !history.loading) {
                            refreshGallery()
                            model.load(owner, target)
                        }
                    }
                    PhotoHistory(owner, target, model, insideSpotDetails = true)
                }
            }
            HorizontalDivider(Modifier.padding(vertical = SportSpotSpacing.xs), color = colors.outline.copy(alpha = 0.35f))
            if (owner != null) {
                PhotoEditor(owner, target.type.limit, model, target, insideSpotDetails = true)
            } else {
                SpotPhotoUploadHeading(target.type.limit)
                Text(stringResource(R.string.photos_sign_in), style = MaterialTheme.typography.bodyMedium)
                PhotoActionButton(R.string.content_open_auth, onClick = onOpenAuth)
            }
        }
    }
}

@Composable
internal fun SpotPhotoUploadHeading(limit: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(SportSpotSpacing.xs)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(SportSpotSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(R.drawable.ic_photo_image), contentDescription = null)
            Text(
                stringResource(R.string.photos_add),
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleLarge,
            )
        }
        Text(
            stringResource(R.string.photos_limits, limit),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
