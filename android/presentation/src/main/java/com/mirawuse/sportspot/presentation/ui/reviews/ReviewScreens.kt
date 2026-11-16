package com.mirawuse.sportspot.presentation.ui.reviews

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.mirawuse.sportspot.domain.validation.FormValidation
import com.mirawuse.sportspot.presentation.R
import com.mirawuse.sportspot.presentation.ui.components.SportSpotButton
import com.mirawuse.sportspot.presentation.ui.forms.FormCard
import com.mirawuse.sportspot.presentation.ui.forms.FormErrorText
import com.mirawuse.sportspot.presentation.ui.forms.FormField
import com.mirawuse.sportspot.presentation.ui.forms.FormScreen
import com.mirawuse.sportspot.presentation.ui.theme.SportSpotDarkColors
import com.mirawuse.sportspot.presentation.ui.theme.SportSpotLightColors
import com.mirawuse.sportspot.presentation.ui.theme.SportSpotSpacing

@Composable
internal fun ReviewsScreen(
    spotName: String?,
    onBack: () -> Unit,
    onWrite: () -> Unit,
) {
    FormScreen(stringResource(R.string.reviews_title), onBack) {
        if (spotName != null) {
            Text(spotName, style = MaterialTheme.typography.titleLarge)
        }
        FormCard(stringResource(R.string.reviews_unavailable)) {
            Text(stringResource(R.string.reviews_read_unavailable), style = MaterialTheme.typography.bodyMedium)
        }
        SportSpotButton(
            text = stringResource(R.string.reviews_write),
            onClick = onWrite,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
internal fun ReviewFormScreen(
    spotName: String?,
    onBack: () -> Unit,
) {
    var rating by rememberSaveable { mutableIntStateOf(0) }
    var title by rememberSaveable { mutableStateOf("") }
    var comment by rememberSaveable { mutableStateOf("") }
    var attempted by rememberSaveable { mutableStateOf(false) }
    val ratingError = FormValidation.rating(rating)
    val commentError = FormValidation.required(comment)
    val ratingColor =
        if (MaterialTheme.colorScheme.background.luminance() < 0.5f) SportSpotDarkColors.rating else SportSpotLightColors.rating

    FormScreen(stringResource(R.string.reviews_write), onBack) {
        if (spotName != null) {
            Text(spotName, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
        Text(stringResource(R.string.review_rating), style = MaterialTheme.typography.titleLarge)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(SportSpotSpacing.xs)) {
            for (value in 1..5) {
                val label = stringResource(R.string.review_rating_choice, value)
                TextButton(
                    onClick = { rating = value },
                    modifier =
                        Modifier.weight(1f).semantics {
                            contentDescription = label
                            selected = rating == value
                        },
                ) {
                    Text(if (value <= rating) "★" else "☆", color = ratingColor, style = MaterialTheme.typography.headlineMedium)
                }
            }
        }
        if (attempted && ratingError != null) FormErrorText(ratingError)

        FormField(
            label = stringResource(R.string.review_title_optional),
            value = title,
            onValueChange = { title = it },
            hint = stringResource(R.string.review_title_hint),
        )
        FormField(
            label = stringResource(R.string.review_comment),
            value = comment,
            onValueChange = { comment = it },
            error = if (attempted) commentError else null,
            hint = stringResource(R.string.review_comment_hint),
            singleLine = false,
        )
        SportSpotButton(
            text = stringResource(R.string.review_validate),
            onClick = { attempted = true },
            modifier = Modifier.fillMaxWidth(),
        )
        if (attempted && ratingError == null && commentError == null) {
            Text(stringResource(R.string.review_send_unavailable), style = MaterialTheme.typography.bodyMedium)
        }
    }
}
