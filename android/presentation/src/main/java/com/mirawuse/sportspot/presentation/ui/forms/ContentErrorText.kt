package com.mirawuse.sportspot.presentation.ui.forms

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.ui.res.stringResource
import com.mirawuse.sportspot.domain.result.DomainError
import com.mirawuse.sportspot.domain.result.DomainFailure
import com.mirawuse.sportspot.domain.result.NetworkIssue
import com.mirawuse.sportspot.presentation.R
import com.mirawuse.sportspot.presentation.ui.components.SportSpotButton
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import java.net.SocketTimeoutException

internal fun Throwable.readError(): DomainError =
    when (this) {
        is DomainFailure -> domainError
        is TimeoutCancellationException, is SocketTimeoutException -> DomainError.Network(NetworkIssue.TIMEOUT)
        else -> DomainError.Unexpected
    }

@Composable
internal fun ContentErrorText(error: DomainError) {
    Text(error.userMessage(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
}

@Composable
internal fun DomainError.userMessage(): String =
    when (this) {
        DomainError.Unauthorized -> stringResource(R.string.content_sign_in_again)
        DomainError.Forbidden -> stringResource(R.string.content_forbidden)
        DomainError.Conflict -> stringResource(R.string.content_conflict)
        DomainError.NotFound -> stringResource(R.string.content_not_found)
        DomainError.Unavailable -> stringResource(R.string.content_unavailable)
        DomainError.Unexpected -> stringResource(R.string.content_unexpected)
        is DomainError.Validation -> message.ifBlank { stringResource(R.string.content_invalid_request) }
        is DomainError.Network ->
            stringResource(
                when (issue) {
                    NetworkIssue.OFFLINE -> R.string.network_offline
                    NetworkIssue.CONNECTION -> R.string.network_connection
                    NetworkIssue.TIMEOUT -> R.string.network_timeout
                    NetworkIssue.SERVER -> R.string.network_server
                    NetworkIssue.RATE_LIMITED -> R.string.network_rate_limited
                    NetworkIssue.INVALID_RESPONSE -> R.string.content_unexpected
                },
            )
    }

internal fun DomainError.canRetryRead(): Boolean =
    this is DomainError.Network || this == DomainError.Unavailable || this == DomainError.Unexpected

internal fun DomainError.mayHaveApplied(): Boolean =
    this is DomainError.Network &&
        issue in setOf(NetworkIssue.TIMEOUT, NetworkIssue.CONNECTION, NetworkIssue.SERVER, NetworkIssue.INVALID_RESPONSE)

@Composable
internal fun ContentMutationError(error: DomainError) {
    ContentErrorText(error)
    if (error.mayHaveApplied()) Text(stringResource(R.string.network_check_before_resubmit), style = MaterialTheme.typography.bodyMedium)
}

@Composable
internal fun networkRetryReady(error: DomainError?): Boolean {
    val remainingUntil = (error as? DomainError.Network)?.retryAtMillis ?: return true
    if (remainingUntil <= System.currentTimeMillis()) return true
    return key(remainingUntil) {
        val ready by produceState(false) {
            delay((remainingUntil - System.currentTimeMillis()).coerceAtLeast(0L))
            value = true
        }
        ready
    }
}

@Composable
internal fun ContentReadError(
    error: DomainError,
    onRetry: () -> Unit,
) {
    ContentErrorText(error)
    if (error.canRetryRead()) {
        SportSpotButton(text = stringResource(R.string.home_retry), enabled = networkRetryReady(error), onClick = onRetry)
    }
}
