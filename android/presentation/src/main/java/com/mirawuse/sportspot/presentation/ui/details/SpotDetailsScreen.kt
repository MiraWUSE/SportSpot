package com.mirawuse.sportspot.presentation.ui.details

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.mirawuse.sportspot.domain.model.spot.Spot
import com.mirawuse.sportspot.domain.model.spot.SpotId
import com.mirawuse.sportspot.presentation.R
import com.mirawuse.sportspot.presentation.ui.components.SportSpotButton
import com.mirawuse.sportspot.presentation.ui.components.SportSpotButtonVariant
import com.mirawuse.sportspot.presentation.ui.components.SpotPhotoImage
import com.mirawuse.sportspot.presentation.ui.components.SpotSearchLoading
import com.mirawuse.sportspot.presentation.ui.components.SpotSearchMessage
import com.mirawuse.sportspot.presentation.ui.components.sportLabel
import com.mirawuse.sportspot.presentation.ui.home.HomeUiState
import com.mirawuse.sportspot.presentation.ui.theme.SportSpotSpacing

@Composable
internal fun SpotDetailsScreen(
    spotId: SpotId,
    state: HomeUiState,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onOpenReviews: (SpotId) -> Unit,
    onShowMap: (SpotId) -> Unit,
    favoriteAction: (@Composable (Spot) -> Unit)? = null,
) {
    val backLabel = stringResource(R.string.details_back)
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.padding(horizontal = SportSpotSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.semantics { contentDescription = backLabel },
            ) {
                Text(text = "‹", style = MaterialTheme.typography.headlineLarge)
            }
            Text(text = stringResource(R.string.details_title), style = MaterialTheme.typography.headlineSmall)
        }
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(SportSpotSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(SportSpotSpacing.lg),
        ) {
            val spot = (state as? HomeUiState.Content)?.findSpot(spotId)
            when {
                state is HomeUiState.Loading -> SpotSearchLoading()
                state is HomeUiState.Error ->
                    SpotSearchMessage(stringResource(R.string.home_error_title), state.message, onRetry)
                spot != null -> DetailsContent(spot, onShowMap, onOpenReviews, favoriteAction)
                else ->
                    SpotSearchMessage(
                        title = stringResource(R.string.details_not_found),
                        message = stringResource(R.string.details_not_found_message),
                        onRetry = onRetry,
                    )
            }
        }
    }
}

@Composable
private fun DetailsContent(
    spot: Spot,
    onShowMap: (SpotId) -> Unit,
    onOpenReviews: (SpotId) -> Unit,
    favoriteAction: (@Composable (Spot) -> Unit)?,
) {
    val uriHandler = LocalUriHandler.current
    SpotPhotoImage(
        spot = spot,
        illustration = R.drawable.spot_court_illustration,
        modifier = Modifier.fillMaxWidth().aspectRatio(347f / 218f).clip(MaterialTheme.shapes.large),
    ) { photo ->
        Text(
            text = stringResource(R.string.spot_photo_credit, photo.author, photo.license),
            modifier =
                Modifier
                    .clickable(role = Role.Button) { runCatching { uriHandler.openUri(photo.licenseUrl) } }
                    .padding(top = SportSpotSpacing.sm),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            text = stringResource(R.string.spot_photo_crop_notice),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            text = stringResource(R.string.spot_photo_source),
            modifier =
                Modifier
                    .clickable(role = Role.Button) { runCatching { uriHandler.openUri(photo.sourceUrl) } }
                    .padding(vertical = SportSpotSpacing.sm),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelMedium,
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(SportSpotSpacing.xs)) {
        Text(text = spot.name, style = MaterialTheme.typography.headlineSmall)
        Text(
            text = spot.address?.takeIf { it.isNotBlank() } ?: stringResource(R.string.details_no_address),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    DetailsSection(stringResource(R.string.details_about)) {
        Text(
            text = spot.sportLabel(),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    DetailsSection(stringResource(R.string.details_traits)) {
        val surface =
            when (val value = spot.surface?.trim()?.takeIf { it.isNotEmpty() }) {
                null -> stringResource(R.string.details_unknown)
                "asphalt" -> stringResource(R.string.details_surface_asphalt)
                "concrete" -> stringResource(R.string.details_surface_concrete)
                "grass" -> stringResource(R.string.details_surface_grass)
                "artificial_turf" -> stringResource(R.string.details_surface_artificial_turf)
                "rubber" -> stringResource(R.string.details_surface_rubber)
                else -> value
            }
        Text(text = stringResource(R.string.details_surface, surface), style = MaterialTheme.typography.bodyMedium)
        Text(
            text = stringResource(R.string.details_lighting, stringResource(lightingLabelRes(spot.lighting))),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = stringResource(R.string.details_equipment, equipmentLabel(spot.equipment)),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    favoriteAction?.invoke(spot)
    SportSpotButton(
        text = stringResource(R.string.details_show_map),
        onClick = { onShowMap(spot.id) },
        modifier = Modifier.fillMaxWidth(),
    )
    SportSpotButton(
        text = stringResource(R.string.reviews_title),
        onClick = { onOpenReviews(spot.id) },
        modifier = Modifier.fillMaxWidth(),
        variant = SportSpotButtonVariant.OUTLINED,
    )
}

@Composable
private fun DetailsSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(SportSpotSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(SportSpotSpacing.sm),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleLarge)
            content()
        }
    }
}

@Composable
private fun equipmentLabel(equipment: String?): String =
    equipment
        .orEmpty()
        .split(';')
        .map(String::trim)
        .filter(String::isNotEmpty)
        .distinct()
        .map { code ->
            when (code) {
                "horizontal_bar" -> stringResource(R.string.details_equipment_horizontal_bar)
                "parallel_bars" -> stringResource(R.string.details_equipment_parallel_bars)
                "horizontal_ladder" -> stringResource(R.string.details_equipment_horizontal_ladder)
                "wall_bars" -> stringResource(R.string.details_equipment_wall_bars)
                else -> code
            }
        }.joinToString()
        .ifEmpty { stringResource(R.string.details_unknown) }

internal fun lightingLabelRes(lighting: Boolean?): Int =
    when (lighting) {
        true -> R.string.details_lighting_yes
        false -> R.string.details_lighting_no
        null -> R.string.details_unknown
    }
