package com.mirawuse.sportspot.presentation.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.mirawuse.sportspot.domain.model.spot.SportType
import com.mirawuse.sportspot.domain.model.spot.Spot
import com.mirawuse.sportspot.domain.model.spot.SpotId
import com.mirawuse.sportspot.presentation.R
import com.mirawuse.sportspot.presentation.ui.components.SpotSearchLoading
import com.mirawuse.sportspot.presentation.ui.components.SpotSearchMessage
import com.mirawuse.sportspot.presentation.ui.components.labelRes
import com.mirawuse.sportspot.presentation.ui.components.sportLabel
import com.mirawuse.sportspot.presentation.ui.theme.SportSpotPillShape
import com.mirawuse.sportspot.presentation.ui.theme.SportSpotSpacing

private val HomeSports = listOf(null, SportType.FOOTBALL, SportType.BASKETBALL, SportType.WORKOUT)

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
) {
    var query by rememberSaveable { mutableStateOf("") }
    var sport by rememberSaveable { mutableStateOf<SportType?>(null) }
    var favoritesInfo by rememberSaveable { mutableStateOf(false) }
    val resources = LocalResources.current
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
            HomeHeader(query, sport, onQuery = { query = it }, onSport = { sport = it }) {
                DeviceLocationControl(location, requestLocation)
            }
        }
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
                    SpotSearchMessage(stringResource(R.string.home_error_title), state.message, onRetry)
                }
            is HomeUiState.Content -> {
                val filtered = filterHomeSpots(state.spots, query, sport) { it.sportLabel(resources::getString) }
                item { HomeMapPreview(filtered, location.center, onOpenMap) }
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
                    }
                }
                if (filtered.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.home_no_matches),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                items(filtered) { spot ->
                    HomeSpotCard(spot, onClick = { onOpenSpot(spot.id) }, onFavorite = { favoritesInfo = true })
                }
            }
        }
    }
    if (favoritesInfo) {
        AlertDialog(
            onDismissRequest = { favoritesInfo = false },
            title = { Text(stringResource(R.string.tab_favorites)) },
            text = { Text(stringResource(R.string.home_favorites_unavailable)) },
            confirmButton = {
                TextButton(onClick = { favoritesInfo = false }) { Text(stringResource(R.string.home_close)) }
            },
        )
    }
}

@Composable
private fun HomeHeader(
    query: String,
    sport: SportType?,
    onQuery: (String) -> Unit,
    onSport: (SportType?) -> Unit,
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
        HomeSearchBar(query, onQuery, onSport)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(SportSpotSpacing.sm),
        ) {
            val categories = if (sport in HomeSports) HomeSports else HomeSports + sport
            categories.forEach { category ->
                val selected = category == sport
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
private fun HomeSearchBar(
    query: String,
    onQuery: (String) -> Unit,
    onSport: (SportType?) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val theme = MaterialTheme.colorScheme
    val searchLabel = stringResource(R.string.home_search)
    val textStyle = MaterialTheme.typography.bodySmall
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(if (theme.background.luminance() < 0.5f) theme.surfaceVariant else theme.surface)
                .padding(start = SportSpotSpacing.lg, end = SportSpotSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(SportSpotSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_home_search), contentDescription = null, tint = theme.onSurfaceVariant)
        BasicTextField(
            value = query,
            onValueChange = onQuery,
            modifier = Modifier.weight(1f).semantics { contentDescription = searchLabel }.padding(vertical = SportSpotSpacing.lg),
            textStyle = textStyle.copy(color = theme.onSurface),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            cursorBrush = SolidColor(theme.onSurface),
            decorationBox = { input ->
                Box {
                    if (query.isEmpty()) Text(searchLabel, color = theme.onSurfaceVariant, style = textStyle)
                    input()
                }
            },
        )
        Box {
            Box(
                modifier =
                    Modifier
                        .size(48.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .clickable(role = Role.Button) { menuOpen = true },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier.size(40.dp).background(theme.primary, MaterialTheme.shapes.medium),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(R.drawable.ic_home_filters),
                        contentDescription = stringResource(R.string.home_filters),
                        tint = theme.onPrimary,
                    )
                }
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                (listOf(null) + SportType.entries).forEach { category ->
                    DropdownMenuItem(
                        text = { Text(stringResource(category?.labelRes() ?: R.string.home_filter_all)) },
                        onClick = {
                            onSport(category)
                            menuOpen = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun homeAccentText(): Color {
    val colors = MaterialTheme.colorScheme
    return if (colors.background.luminance() < 0.5f) colors.primary else lerp(colors.primary, colors.onBackground, 0.58f)
}

internal fun filterHomeSpots(
    spots: List<Spot>,
    query: String,
    sport: SportType?,
    sportLabel: (Spot) -> String,
): List<Spot> {
    val text = query.trim()
    return spots.filter { spot ->
        (sport == null || spot.supportsSport(sport)) &&
            (spot.name.contains(text, ignoreCase = true) || sportLabel(spot).contains(text, ignoreCase = true))
    }
}
