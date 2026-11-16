package com.mirawuse.sportspot.presentation.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.mirawuse.sportspot.domain.model.spot.SportType
import com.mirawuse.sportspot.domain.model.spot.Spot
import com.mirawuse.sportspot.presentation.R
import com.mirawuse.sportspot.presentation.ui.theme.SportSpotSpacing

internal fun SportType.labelRes(): Int =
    when (this) {
        SportType.FOOTBALL -> R.string.sport_football
        SportType.BASKETBALL -> R.string.sport_basketball
        SportType.VOLLEYBALL -> R.string.sport_volleyball
        SportType.TENNIS -> R.string.sport_tennis
        SportType.WORKOUT -> R.string.sport_workout
        SportType.OTHER -> R.string.sport_other
    }

@Composable
internal fun SpotSummary(
    spot: Spot,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(SportSpotSpacing.xs)) {
        Text(text = spot.name, style = MaterialTheme.typography.titleLarge)
        Text(
            text = stringResource(spot.sportType.labelRes()),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
internal fun SpotSearchLoading() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = SportSpotSpacing.xxxl),
        verticalArrangement = Arrangement.spacedBy(SportSpotSpacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = stringResource(R.string.home_loading), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun SpotSearchMessage(
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
