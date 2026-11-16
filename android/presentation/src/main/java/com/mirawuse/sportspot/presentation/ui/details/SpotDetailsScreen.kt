package com.mirawuse.sportspot.presentation.ui.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.mirawuse.sportspot.domain.model.spot.SpotId
import com.mirawuse.sportspot.presentation.R
import com.mirawuse.sportspot.presentation.ui.components.SpotSearchLoading
import com.mirawuse.sportspot.presentation.ui.components.SpotSearchMessage
import com.mirawuse.sportspot.presentation.ui.components.SpotSummary
import com.mirawuse.sportspot.presentation.ui.home.HomeUiState
import com.mirawuse.sportspot.presentation.ui.theme.SportSpotSpacing

@Composable
internal fun SpotDetailsScreen(
    spotId: SpotId,
    state: HomeUiState,
    onRetry: () -> Unit,
    onBack: () -> Unit,
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
                spot != null -> SpotSummary(spot)
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
