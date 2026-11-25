package com.mirawuse.sportspot.presentation.ui.reviews

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import com.mirawuse.sportspot.domain.model.review.Review
import com.mirawuse.sportspot.domain.result.DomainError
import com.mirawuse.sportspot.domain.validation.FormValidation
import com.mirawuse.sportspot.presentation.R
import com.mirawuse.sportspot.presentation.ui.components.SportSpotButton
import com.mirawuse.sportspot.presentation.ui.components.SpotSearchLoading
import com.mirawuse.sportspot.presentation.ui.forms.ContentErrorText
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
    state: ReviewsState,
    owner: String?,
    busy: Boolean,
    error: DomainError?,
    onBack: () -> Unit,
    onWrite: () -> Unit,
    onDelete: (Review) -> Unit,
    onRetry: () -> Unit,
    reviewPhotos: @Composable (Review) -> Unit = {},
) {
    var confirmDelete by rememberSaveable(owner) { mutableStateOf(false) }
    FormScreen(stringResource(R.string.reviews_title), onBack) {
        spotName?.let { Text(it, style = MaterialTheme.typography.titleLarge) }
        error?.let { ContentErrorText(it) }
        when (state) {
            ReviewsState.Loading -> SpotSearchLoading()
            ReviewsState.Error -> ReviewLoadError(onRetry)
            is ReviewsState.Content -> {
                val ownReview = state.items.firstOrNull { it.authorId == owner }
                if (state.items.isEmpty()) {
                    FormCard(stringResource(R.string.reviews_none)) { }
                } else {
                    ReviewScore(state.items)
                    state.items.forEach { review ->
                        FormCard(
                            stringResource(
                                if (review.authorId ==
                                    owner
                                ) {
                                    R.string.review_own_author
                                } else {
                                    R.string.review_anonymous_author
                                },
                            ),
                        ) {
                            Text("${"★".repeat(review.rating)}${"☆".repeat(5 - review.rating)}", color = reviewRatingColor())
                            review.title?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
                            Text(review.text, style = MaterialTheme.typography.bodyMedium)
                            review.createdAt?.take(10)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                            reviewPhotos(review)
                        }
                    }
                }
                SportSpotButton(
                    text = stringResource(if (ownReview == null) R.string.reviews_write else R.string.review_edit),
                    onClick = onWrite,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (ownReview != null) {
                    TextButton(onClick = { confirmDelete = true }, enabled = !busy) {
                        Text(stringResource(R.string.review_delete))
                    }
                    if (confirmDelete) {
                        AlertDialog(
                            onDismissRequest = { confirmDelete = false },
                            text = { Text(stringResource(R.string.review_delete_question)) },
                            confirmButton = {
                                TextButton(onClick = {
                                    confirmDelete = false
                                    onDelete(ownReview)
                                }, enabled = !busy) {
                                    Text(stringResource(R.string.review_delete))
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.content_cancel)) }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun ReviewFormScreen(
    spotName: String?,
    state: ReviewsState,
    owner: String?,
    restoring: Boolean,
    busy: Boolean,
    error: DomainError?,
    onBack: () -> Unit,
    onOpenAuth: () -> Unit,
    onRetry: () -> Unit,
    onSubmit: (Review?, Int, String, String) -> Unit,
    photoAttachments: @Composable (Review?) -> Unit = {},
) {
    val existing = (state as? ReviewsState.Content)?.items?.firstOrNull { it.authorId == owner }
    FormScreen(stringResource(if (existing == null) R.string.reviews_write else R.string.review_edit), onBack) {
        spotName?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium) }
        when {
            restoring -> SpotSearchLoading()
            owner == null -> {
                Text(stringResource(R.string.review_sign_in))
                SportSpotButton(stringResource(R.string.content_open_auth), onOpenAuth, Modifier.fillMaxWidth())
            }
            state is ReviewsState.Loading -> SpotSearchLoading()
            state is ReviewsState.Error -> ReviewLoadError(onRetry)
            else -> key(owner) { ReviewFields(owner, existing, busy, error, onSubmit, photoAttachments) }
        }
    }
}

@Composable
private fun ReviewFields(
    owner: String,
    existing: Review?,
    busy: Boolean,
    error: DomainError?,
    onSubmit: (Review?, Int, String, String) -> Unit,
    photoAttachments: @Composable (Review?) -> Unit,
) {
    var rating by rememberSaveable(owner) { mutableIntStateOf(existing?.rating ?: 0) }
    var title by rememberSaveable(owner) { mutableStateOf(existing?.title.orEmpty()) }
    var comment by rememberSaveable(owner) { mutableStateOf(existing?.text.orEmpty()) }
    var attempted by rememberSaveable(owner) { mutableStateOf(false) }
    val ratingError = FormValidation.rating(rating)
    val commentError = FormValidation.required(comment)
    val ratingColor = reviewRatingColor()

    Text(stringResource(R.string.review_rating), style = MaterialTheme.typography.titleLarge)
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(SportSpotSpacing.xs)) {
        for (value in 1..5) {
            val label = stringResource(R.string.review_rating_choice, value)
            TextButton(
                onClick = { rating = value },
                enabled = !busy,
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
        onValueChange = { if (!busy) title = it },
        hint = stringResource(R.string.review_title_hint),
    )
    FormField(
        label = stringResource(R.string.review_comment),
        value = comment,
        onValueChange = { if (!busy) comment = it },
        error = if (attempted) commentError else null,
        hint = stringResource(R.string.review_comment_hint),
        singleLine = false,
    )
    error?.let { ContentErrorText(it) }
    photoAttachments(existing)
    SportSpotButton(
        text = stringResource(if (existing == null) R.string.review_publish else R.string.content_save),
        onClick = {
            attempted = true
            if (ratingError == null && commentError == null) onSubmit(existing, rating, comment, title)
        },
        isLoading = busy,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
internal fun ReviewScore(reviews: List<Review>) {
    val label =
        if (reviews.isEmpty()) {
            stringResource(R.string.review_score_empty)
        } else {
            stringResource(R.string.review_score, reviews.map { it.rating }.average(), reviews.size)
        }
    Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ReviewLoadError(onRetry: () -> Unit) {
    ContentErrorText(DomainError.Unavailable)
    TextButton(onClick = onRetry) { Text(stringResource(R.string.home_retry)) }
}

@Composable
internal fun reviewRatingColor() =
    if (MaterialTheme.colorScheme.background.luminance() < 0.5f) SportSpotDarkColors.rating else SportSpotLightColors.rating
