package com.mirawuse.sportspot.presentation.ui.reviews

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.mirawuse.sportspot.domain.model.spot.SpotId
import com.mirawuse.sportspot.domain.result.DomainResult
import com.mirawuse.sportspot.presentation.R
import com.mirawuse.sportspot.presentation.ui.components.SportSpotButton
import com.mirawuse.sportspot.presentation.ui.components.SportSpotButtonVariant
import com.mirawuse.sportspot.presentation.ui.components.SpotSearchLoading
import com.mirawuse.sportspot.presentation.ui.forms.ContentReadError
import com.mirawuse.sportspot.presentation.ui.forms.FormCard
import com.mirawuse.sportspot.presentation.ui.forms.FormScreen
import com.mirawuse.sportspot.presentation.ui.theme.SportSpotSpacing

@Composable
internal fun MyReviewsScreen(
    owner: String?,
    restoring: Boolean,
    viewModel: MyReviewsViewModel,
    spotNames: Map<SpotId, String>,
    onBack: () -> Unit,
    onOpenAuth: () -> Unit,
    onSearch: () -> Unit,
    reviewRevision: Int,
    onOpenReviews: (SpotId) -> Unit,
) {
    LifecycleResumeEffect(owner, restoring, reviewRevision) {
        if (!restoring) viewModel.load(owner)
        onPauseOrDispose { viewModel.clear() }
    }
    val state = viewModel.resultFor(owner)
    FormScreen(stringResource(R.string.profile_my_reviews), onBack, scrollable = false) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(SportSpotSpacing.md),
            contentPadding = PaddingValues(SportSpotSpacing.xl),
        ) {
            when {
                restoring -> item { SpotSearchLoading() }
                owner == null ->
                    item {
                        Text(stringResource(R.string.profile_reviews_guest))
                        SportSpotButton(stringResource(R.string.content_open_auth), onOpenAuth, Modifier.fillMaxWidth())
                    }
                state == null -> item { SpotSearchLoading() }
                state is DomainResult.Failure ->
                    item {
                        ContentReadError(state.error) { viewModel.load(owner) }
                    }
                state is DomainResult.Success && state.value.isEmpty() ->
                    item {
                        Text(stringResource(R.string.my_reviews_empty))
                        SportSpotButton(
                            stringResource(R.string.my_reviews_search),
                            onSearch,
                            Modifier.fillMaxWidth(),
                            variant = SportSpotButtonVariant.SECONDARY,
                        )
                    }
                state is DomainResult.Success ->
                    items(state.value, key = { it.id }) { review ->
                        val spotName =
                            spotNames[review.spotId] ?: viewModel.cachedSpotNames[review.spotId] ?: stringResource(
                                R.string.my_reviews_spot,
                                review.spotId.source,
                                review.spotId.value,
                            )
                        val open = stringResource(R.string.my_reviews_open_spot)
                        FormCard(
                            title = spotName,
                            modifier =
                                Modifier.clip(MaterialTheme.shapes.large).clickable(role = Role.Button, onClickLabel = open) {
                                    onOpenReviews(review.spotId)
                                },
                        ) {
                            Text(stringResource(R.string.review_rating_choice, review.rating), color = reviewRatingColor())
                            review.title?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
                            Text(review.text, style = MaterialTheme.typography.bodyMedium)
                            review.createdAt?.take(10)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                            Text(open, style = MaterialTheme.typography.labelLarge)
                        }
                    }
            }
        }
    }
}
