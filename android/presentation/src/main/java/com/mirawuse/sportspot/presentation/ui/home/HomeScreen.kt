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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.mirawuse.sportspot.domain.model.spot.SpotId
import com.mirawuse.sportspot.presentation.R
import com.mirawuse.sportspot.presentation.ui.components.SpotSearchLoading
import com.mirawuse.sportspot.presentation.ui.components.SpotSearchMessage
import com.mirawuse.sportspot.presentation.ui.components.SpotSummary
import com.mirawuse.sportspot.presentation.ui.theme.SportSpotSpacing

@Composable
internal fun HomeScreen(
    state: HomeUiState,
    onRetry: () -> Unit,
    onOpenSpot: (SpotId) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(SportSpotSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(SportSpotSpacing.md),
    ) {
        item { HomeHeader() }

        when (state) {
            HomeUiState.Loading -> item { SpotSearchLoading() }

            is HomeUiState.Content ->
                items(state.spots) { spot ->
                    Card(
                        onClick = { onOpenSpot(spot.id) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        SpotSummary(spot, Modifier.padding(SportSpotSpacing.lg))
                    }
                }

            HomeUiState.Empty ->
                item {
                    SpotSearchMessage(
                        title = stringResource(R.string.home_empty_title),
                        message = stringResource(R.string.home_empty_message),
                        onRetry = onRetry,
                    )
                }

            is HomeUiState.Error ->
                item {
                    SpotSearchMessage(
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
