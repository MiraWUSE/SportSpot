package com.mirawuse.sportspot.presentation.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.mirawuse.sportspot.domain.model.spot.SportType
import com.mirawuse.sportspot.domain.model.spot.Spot
import com.mirawuse.sportspot.domain.model.spot.SpotId
import com.mirawuse.sportspot.presentation.R
import com.mirawuse.sportspot.presentation.ui.components.SpotSearchLoading
import com.mirawuse.sportspot.presentation.ui.components.SpotSearchMessage
import com.mirawuse.sportspot.presentation.ui.components.labelRes
import com.mirawuse.sportspot.presentation.ui.forms.canRetryRead
import com.mirawuse.sportspot.presentation.ui.forms.networkRetryReady
import com.mirawuse.sportspot.presentation.ui.forms.userMessage
import com.mirawuse.sportspot.presentation.ui.photos.SpotCoverLoader
import com.mirawuse.sportspot.presentation.ui.search.SearchFilterState
import com.mirawuse.sportspot.presentation.ui.search.SpotFilterEmpty
import com.mirawuse.sportspot.presentation.ui.search.SpotSearchBar
import com.mirawuse.sportspot.presentation.ui.search.SpotSearchCategories
import com.mirawuse.sportspot.presentation.ui.search.rememberFilteredSpots
import com.mirawuse.sportspot.presentation.ui.search.rememberSortedSpots
import com.mirawuse.sportspot.presentation.ui.theme.SportSpotPillShape
import com.mirawuse.sportspot.presentation.ui.theme.SportSpotSpacing

@Composable
internal fun HomeScreen(
    state: HomeUiState,
    location: SearchLocationState,
    onLocate: (Boolean) -> Unit,
    onPermissionDenied: () -> Unit,
    onCancelLocation: () -> Unit,
    onRetry: () -> Unit,
    onOpenSpot: (SpotId) -> Unit,
    onOpenMap: () -> Unit,
    filter: SearchFilterState,
    onSearchQuery: (String) -> Unit,
    onSearchSport: (SportType?) -> Unit,
    onResetSearch: () -> Unit,
    onApplySearch: (SearchFilterState) -> Unit,
    signedIn: Boolean = false,
    savedSpotIds: Set<SpotId> = emptySet(),
    favoriteEnabled: Boolean = true,
    onFavorite: (Spot) -> Unit = {},
    onOpenAuth: () -> Unit = {},
    coverLoader: SpotCoverLoader? = null,
    backgroundRefresh: (@Composable () -> Unit)? = null,
) {
    var favoritesInfo by rememberSaveable { mutableStateOf(false) }
    val filtered = rememberFilteredSpots((state as? HomeUiState.Content)?.spots.orEmpty(), filter)
    val sorted = rememberSortedSpots(filtered.orEmpty(), filter.sort, location.center)
    // Keep permission callbacks and cancellation alive when the header scrolls off screen.
    val requestLocation = rememberDeviceLocationRequest(onLocate, onPermissionDenied, onCancelLocation)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding =
            PaddingValues(
                start = SportSpotSpacing.xl,
                end = SportSpotSpacing.xl,
                top = SportSpotSpacing.xs,
                bottom = SportSpotSpacing.lg,
            ),
        verticalArrangement = Arrangement.spacedBy(SportSpotSpacing.md),
    ) {
        item {
            HomeHeader(filter, onSearchQuery, onSearchSport, onApplySearch, onResetSearch) {
                DeviceLocationControl(location, requestLocation)
            }
        }
        if (backgroundRefresh != null) item(key = "background-refresh") { backgroundRefresh() }
        when (state) {
            HomeUiState.Loading -> item { SpotSearchLoading() }
            HomeUiState.Empty ->
                item {
                    SpotSearchMessage(
                        stringResource(R.string.home_empty_title),
                        stringResource(R.string.home_empty_message),
                        onRetry,
                    )
                }
            is HomeUiState.Error ->
                item {
                    SpotSearchMessage(
                        stringResource(R.string.home_error_title),
                        state.error.userMessage(),
                        onRetry.takeIf { state.error.canRetryRead() },
                        retryEnabled = networkRetryReady(state.error),
                    )
                }
            is HomeUiState.Content -> {
                state.fallback?.let { error ->
                    item {
                        SpotSearchMessage(
                            stringResource(R.string.network_cached_title),
                            error.userMessage(),
                            onRetry.takeIf { error.canRetryRead() },
                            retryEnabled = networkRetryReady(error),
                        )
                    }
                }
                item { HomeMapPreview(filtered.orEmpty(), location.center, resultsPending = filtered == null, onOpenMap = onOpenMap) }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.home_spots_heading),
                            modifier = Modifier.weight(1f).semantics { heading() },
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        TextButton(onClick = onOpenMap, contentPadding = PaddingValues(horizontal = SportSpotSpacing.xs)) {
                            Text(
                                text = stringResource(R.string.home_all_spots),
                                color = homeAccentText(),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                        if (state.fallback == null) {
                            TextButton(onClick = onRetry, contentPadding = PaddingValues(horizontal = SportSpotSpacing.xs)) {
                                Text(stringResource(R.string.content_refresh), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
                if (filtered == null || sorted == null) {
                    item { Text(stringResource(R.string.search_filters_updating), style = MaterialTheme.typography.bodyMedium) }
                } else if (filtered.isEmpty()) {
                    item {
                        if (state.spots.isEmpty()) {
                            Text(stringResource(R.string.home_empty_message))
                        } else {
                            SpotFilterEmpty(stringResource(R.string.home_no_matches), onResetSearch)
                        }
                    }
                }
                items(sorted.orEmpty(), key = { "${it.id.source.length}:${it.id.source}${it.id.value}" }) { spot ->
                    HomeSpotCard(
                        spot,
                        onClick = { onOpenSpot(spot.id) },
                        onFavorite = { if (signedIn) onFavorite(spot) else favoritesInfo = true },
                        isFavorite = spot.id in savedSpotIds,
                        favoriteEnabled = favoriteEnabled,
                        coverLoader = coverLoader,
                        showUnknownAddress = true,
                    )
                }
            }
        }
    }
    if (favoritesInfo) {
        AlertDialog(
            onDismissRequest = { favoritesInfo = false },
            title = { Text(stringResource(R.string.tab_favorites)) },
            text = { Text(stringResource(R.string.favorites_guest_message)) },
            confirmButton = {
                TextButton(onClick = {
                    favoritesInfo = false
                    onOpenAuth()
                }) { Text(stringResource(R.string.auth_sign_in)) }
            },
            dismissButton = {
                TextButton(onClick = { favoritesInfo = false }) { Text(stringResource(R.string.home_close)) }
            },
        )
    }
}

@Composable
private fun HomeHeader(
    filter: SearchFilterState,
    onQuery: (String) -> Unit,
    onSport: (SportType?) -> Unit,
    onApply: (SearchFilterState) -> Unit,
    onReset: () -> Unit,
    locationContent: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val sportPrefix = stringResource(R.string.home_brand_prefix)
    val sportSuffix = stringResource(R.string.home_brand_suffix)
    Column(verticalArrangement = Arrangement.spacedBy(SportSpotSpacing.lg)) {
        Column {
            Text(
                text =
                    buildAnnotatedString {
                        append(sportPrefix)
                        withStyle(SpanStyle(color = colors.primary)) { append(sportSuffix) }
                    },
                style = MaterialTheme.typography.headlineLarge,
            )
            Text(stringResource(R.string.home_tagline), color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        locationContent()
        Column(verticalArrangement = Arrangement.spacedBy(SportSpotSpacing.xs)) {
            SpotSearchBar(filter, onQuery, onApply, onReset, showRadius = true)
            Text(
                stringResource(R.string.home_search_scope),
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(SportSpotSpacing.sm),
        ) {
            SpotSearchCategories.forEach { category ->
                val selected = category == filter.sport
                Text(
                    text = stringResource(category?.labelRes() ?: R.string.home_filter_all),
                    modifier =
                        Modifier
                            .clip(SportSpotPillShape)
                            .background(if (selected) colors.primary else colors.surfaceVariant)
                            .selectable(selected = selected, role = Role.RadioButton, onClick = { onSport(category) })
                            .padding(horizontal = SportSpotSpacing.lg, vertical = 10.dp),
                    color = if (selected) colors.onPrimary else colors.onSurface,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun homeAccentText(): Color {
    val colors = MaterialTheme.colorScheme
    return if (colors.background.luminance() < 0.5f) colors.primary else lerp(colors.primary, colors.onBackground, 0.58f)
}
