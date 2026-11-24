package com.mirawuse.sportspot.presentation.ui.reviews

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mirawuse.sportspot.domain.model.review.Review
import com.mirawuse.sportspot.domain.model.spot.SpotId
import com.mirawuse.sportspot.domain.repository.AuthRepository
import com.mirawuse.sportspot.domain.repository.ReviewRepository
import com.mirawuse.sportspot.domain.result.DomainError
import com.mirawuse.sportspot.domain.result.DomainOperationException
import com.mirawuse.sportspot.domain.result.DomainResult
import com.mirawuse.sportspot.domain.usecase.review.CreateReviewUseCase
import com.mirawuse.sportspot.domain.usecase.review.DeleteReviewUseCase
import com.mirawuse.sportspot.domain.usecase.review.GetReviewsBySpotUseCase
import com.mirawuse.sportspot.domain.usecase.review.UpdateReviewUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
internal class ReviewsViewModel
    @Inject
    constructor(
        auth: AuthRepository,
        repository: ReviewRepository,
    ) : ViewModel() {
        private val getReviews = GetReviewsBySpotUseCase(repository)
        private val createReview = CreateReviewUseCase(auth, repository)
        private val updateReview = UpdateReviewUseCase(auth, repository)
        private val deleteReview = DeleteReviewUseCase(auth, repository)
        private var loadJob: Job? = null
        private var activeSpot by mutableStateOf<SpotId?>(null)

        private var list by mutableStateOf<ReviewsState>(ReviewsState.Loading)
        var busy by mutableStateOf(false)
            private set
        private var actionError by mutableStateOf<Pair<String, DomainError>?>(null)

        fun stateFor(spotId: SpotId): ReviewsState = if (spotId == activeSpot) list else ReviewsState.Loading

        fun errorFor(owner: String?): DomainError? = actionError?.takeIf { it.first == owner }?.second

        fun clearError() {
            actionError = null
        }

        fun load(
            spotId: SpotId,
            refresh: Boolean = false,
        ) {
            if (!refresh && activeSpot == spotId) return
            loadJob?.cancel()
            if (activeSpot != spotId || list !is ReviewsState.Content) list = ReviewsState.Loading
            activeSpot = spotId
            loadJob =
                viewModelScope.launch {
                    list =
                        try {
                            ReviewsState.Content(getReviews(spotId))
                        } catch (_: DomainOperationException) {
                            (list as? ReviewsState.Content) ?: ReviewsState.Error
                        }
                }
        }

        fun save(
            spotId: SpotId,
            owner: String,
            existing: Review?,
            rating: Int,
            text: String,
            title: String,
            onSaved: (Review) -> Unit,
        ) {
            mutate(owner, spotId, onSuccess = { saved ->
                updateContent(spotId) { items ->
                    if (items.none { it.id == saved.id }) listOf(saved) + items else items.map { if (it.id == saved.id) saved else it }
                }
                onSaved(saved)
            }) {
                if (existing == null) {
                    createReview(spotId, rating, text, title, owner)
                } else {
                    updateReview(existing.id, rating, text, title, owner)
                }
            }
        }

        fun delete(
            owner: String,
            review: Review,
        ) {
            mutate(owner, review.spotId, onSuccess = {
                updateContent(review.spotId) { items -> items.filterNot { it.id == review.id } }
            }) { deleteReview(review.id, owner) }
        }

        private fun updateContent(
            spotId: SpotId,
            update: (List<Review>) -> List<Review>,
        ) {
            if (activeSpot != spotId) return
            (list as? ReviewsState.Content)?.let { list = ReviewsState.Content(update(it.items)) }
            load(spotId, refresh = true)
        }

        private fun <T> mutate(
            owner: String,
            spotId: SpotId,
            onSuccess: (T) -> Unit,
            operation: suspend () -> DomainResult<T>,
        ) {
            if (busy) return
            busy = true
            clearError()
            viewModelScope.launch {
                try {
                    when (val result = operation()) {
                        is DomainResult.Success -> onSuccess(result.value)
                        is DomainResult.Failure -> {
                            actionError = owner to result.error
                            if (activeSpot == spotId && result.error in setOf(DomainError.Conflict, DomainError.NotFound)) {
                                load(spotId, refresh = true)
                            }
                        }
                    }
                } finally {
                    busy = false
                }
            }
        }
    }

internal sealed interface ReviewsState {
    data object Loading : ReviewsState

    data object Error : ReviewsState

    data class Content(
        val items: List<Review>,
    ) : ReviewsState
}
