package com.mirawuse.sportspot.presentation.ui.forms

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.mirawuse.sportspot.domain.result.DomainError
import com.mirawuse.sportspot.presentation.R

@Composable
internal fun ContentErrorText(error: DomainError) {
    val message =
        when (error) {
            DomainError.Unauthorized -> stringResource(R.string.content_sign_in_again)
            DomainError.Forbidden -> stringResource(R.string.content_forbidden)
            DomainError.Conflict -> stringResource(R.string.content_conflict)
            DomainError.NotFound -> stringResource(R.string.content_not_found)
            DomainError.Unavailable -> stringResource(R.string.content_unavailable)
            DomainError.Unexpected -> stringResource(R.string.content_unexpected)
            is DomainError.Validation -> error.message
        }
    Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
}
