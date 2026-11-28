package com.mirawuse.sportspot.presentation.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.mirawuse.sportspot.domain.repository.SpotRefreshPhase
import com.mirawuse.sportspot.domain.repository.SpotRefreshStatus
import com.mirawuse.sportspot.presentation.R
import com.mirawuse.sportspot.presentation.ui.components.SportSpotButton
import com.mirawuse.sportspot.presentation.ui.components.SportSpotButtonVariant
import com.mirawuse.sportspot.presentation.ui.forms.networkRetryReady
import com.mirawuse.sportspot.presentation.ui.forms.userMessage
import com.mirawuse.sportspot.presentation.ui.theme.SportSpotSpacing

@Composable
internal fun SpotBackgroundRefresh(
    status: SpotRefreshStatus?,
    onRefresh: () -> Unit,
    onCancel: () -> Unit,
    matchesArea: Boolean = true,
) {
    Column(verticalArrangement = Arrangement.spacedBy(SportSpotSpacing.xs)) {
        SportSpotButton(
            text = stringResource(if (status?.phase == SpotRefreshPhase.FAILED) R.string.home_retry else R.string.background_refresh),
            enabled = networkRetryReady(status?.error),
            onClick = onRefresh,
            modifier = Modifier.fillMaxWidth(),
            variant = SportSpotButtonVariant.SECONDARY,
        )
        if (status == null) return@Column
        val label =
            when (status.phase) {
                SpotRefreshPhase.QUEUED -> R.string.background_queued
                SpotRefreshPhase.RUNNING -> R.string.background_running
                SpotRefreshPhase.SUCCEEDED -> if (matchesArea) R.string.background_succeeded else R.string.background_other_area
                SpotRefreshPhase.FAILED -> R.string.background_failed
                SpotRefreshPhase.CANCELLED -> R.string.background_cancelled
            }
        Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
            Text(stringResource(label), style = MaterialTheme.typography.bodyMedium)
            status.error?.let {
                Text(
                    it.userMessage(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        if (status.active) {
            SportSpotButton(
                text = stringResource(R.string.background_cancel),
                onClick = onCancel,
                variant = SportSpotButtonVariant.OUTLINED,
            )
        }
    }
}
