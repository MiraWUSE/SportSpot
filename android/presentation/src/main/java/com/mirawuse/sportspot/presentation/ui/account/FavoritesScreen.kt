package com.mirawuse.sportspot.presentation.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.mirawuse.sportspot.domain.model.spot.SpotId
import com.mirawuse.sportspot.presentation.R
import com.mirawuse.sportspot.presentation.ui.components.SportSpotButton
import com.mirawuse.sportspot.presentation.ui.components.SpotSearchLoading
import com.mirawuse.sportspot.presentation.ui.components.SpotSearchMessage
import com.mirawuse.sportspot.presentation.ui.forms.FormCard
import com.mirawuse.sportspot.presentation.ui.forms.FormScreen
import com.mirawuse.sportspot.presentation.ui.home.HomeSpotCard
import com.mirawuse.sportspot.presentation.ui.theme.SportSpotSpacing

@Composable
internal fun FavoritesScreen(
    state: FavoritesUiState,
    restoring: Boolean,
    busy: Boolean,
    onOpenAuth: () -> Unit,
    onRetry: () -> Unit,
    onOpenSpot: (SpotId) -> Unit,
    onOpenMap: (SpotId) -> Unit,
    onRemove: (SpotId) -> Unit,
) {
    if (state == FavoritesUiState.Guest && !restoring) {
        FormScreen(stringResource(R.string.tab_favorites)) {
            Text(stringResource(R.string.favorites_saved), style = MaterialTheme.typography.headlineSmall)
            FormCard(stringResource(R.string.favorites_guest_header)) {
                Text(stringResource(R.string.favorites_guest_message), style = MaterialTheme.typography.bodyMedium)
            }
            SportSpotButton(
                text = stringResource(R.string.auth_open),
                onClick = onOpenAuth,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(SportSpotSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(SportSpotSpacing.md),
    ) {
        item {
            Text(stringResource(R.string.tab_favorites), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.favorites_saved), style = MaterialTheme.typography.titleLarge)
        }
        if (restoring || state is FavoritesUiState.Loading) {
            item { SpotSearchLoading() }
            return@LazyColumn
        }
        when (state) {
            is FavoritesUiState.Error ->
                item {
                    SpotSearchMessage(stringResource(R.string.home_error_title), stringResource(state.reason.labelRes()), onRetry)
                }
            is FavoritesUiState.Content -> {
                if (state.list.fromCache) {
                    item {
                        Text(stringResource(R.string.favorites_cached), style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = onRetry, enabled = !busy) { Text(stringResource(R.string.home_retry)) }
                    }
                }
                if (state.list.items.isEmpty()) item { Text(stringResource(R.string.favorites_empty)) }
                items(state.list.items, key = { "${it.spotId.source}:${it.spotId.value}" }) { favorite ->
                    val spot = favorite.spot
                    if (spot == null) {
                        FormCard(stringResource(R.string.favorites_spot_unavailable)) {
                            TextButton(onClick = { onRemove(favorite.spotId) }, enabled = !busy) {
                                Text(stringResource(R.string.favorites_remove))
                            }
                        }
                    } else {
                        Column {
                            HomeSpotCard(
                                spot = spot,
                                onClick = { onOpenSpot(spot.id) },
                                onFavorite = { onRemove(spot.id) },
                                isFavorite = true,
                                favoriteEnabled = !busy,
                                showPhotoLicense = true,
                            )
                            TextButton(onClick = { onOpenMap(spot.id) }) { Text(stringResource(R.string.details_show_map)) }
                        }
                    }
                }
            }
            FavoritesUiState.Guest, is FavoritesUiState.Loading -> Unit
        }
    }
}
