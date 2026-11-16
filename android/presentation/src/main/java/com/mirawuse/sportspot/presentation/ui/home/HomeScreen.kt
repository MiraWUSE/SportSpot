package com.mirawuse.sportspot.presentation.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mirawuse.sportspot.domain.model.spot.SportType
import com.mirawuse.sportspot.domain.model.spot.Spot
import com.mirawuse.sportspot.presentation.R
import com.mirawuse.sportspot.presentation.ui.components.SportSpotButton
import com.mirawuse.sportspot.presentation.ui.theme.SportSpotSpacing

@Composable
internal fun HomeRoute(viewModel: HomeViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HomeScreen(state = state, onRetry = viewModel::retry)
}

@Composable
internal fun HomeScreen(
    state: HomeUiState,
    onRetry: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(SportSpotSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(SportSpotSpacing.md),
    ) {
        item { HomeHeader() }

        when (state) {
            HomeUiState.Loading ->
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = SportSpotSpacing.xxxl),
                        verticalArrangement = Arrangement.spacedBy(SportSpotSpacing.md),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(text = stringResource(R.string.home_loading), style = MaterialTheme.typography.bodyMedium)
                    }
                }

            is HomeUiState.Content ->
                items(state.spots) { spot ->
                    SpotCard(spot)
                }

            HomeUiState.Empty ->
                item {
                    HomeMessage(
                        title = stringResource(R.string.home_empty_title),
                        message = stringResource(R.string.home_empty_message),
                        onRetry = onRetry,
                    )
                }

            is HomeUiState.Error ->
                item {
                    HomeMessage(
                        title = stringResource(R.string.home_error_title),
                        message = state.message,
                        onRetry = onRetry,
                    )
                }
        }
    }
}

@Composable
private fun HomeHeader() {
    Column(verticalArrangement = Arrangement.spacedBy(SportSpotSpacing.lg)) {
        Text(
            text = stringResource(R.string.home_brand),
            style = MaterialTheme.typography.headlineLarge,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(SportSpotSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_demo_location),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.home_demo_location),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Text(
            text = stringResource(R.string.home_title),
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.headlineSmall,
        )
    }
}

@Composable
private fun SpotCard(spot: Spot) {
    val sport =
        when (spot.sportType) {
            SportType.FOOTBALL -> R.string.sport_football
            SportType.BASKETBALL -> R.string.sport_basketball
            SportType.VOLLEYBALL -> R.string.sport_volleyball
            SportType.TENNIS -> R.string.sport_tennis
            SportType.WORKOUT -> R.string.sport_workout
            SportType.OTHER -> R.string.sport_other
        }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(SportSpotSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(SportSpotSpacing.xs),
        ) {
            Text(text = spot.name, style = MaterialTheme.typography.titleLarge)
            Text(
                text = stringResource(sport),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun HomeMessage(
    title: String,
    message: String,
    onRetry: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(SportSpotSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(SportSpotSpacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = title, textAlign = TextAlign.Center, style = MaterialTheme.typography.titleLarge)
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
            )
            SportSpotButton(text = stringResource(R.string.home_retry), onClick = onRetry)
        }
    }
}
