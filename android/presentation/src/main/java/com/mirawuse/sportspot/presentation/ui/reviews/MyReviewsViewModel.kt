package com.mirawuse.sportspot.presentation.ui.reviews

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mirawuse.sportspot.domain.model.review.Review
import com.mirawuse.sportspot.domain.repository.AuthRepository
import com.mirawuse.sportspot.domain.repository.ReviewRepository
import com.mirawuse.sportspot.domain.result.DomainResult
import com.mirawuse.sportspot.domain.usecase.review.GetMyReviewsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
internal class MyReviewsViewModel
    @Inject
    constructor(
        auth: AuthRepository,
        repository: ReviewRepository,
    ) : ViewModel() {
        private val getReviews = GetMyReviewsUseCase(auth, repository)
        private var loadJob: Job? = null
        private var result by mutableStateOf<Pair<String, DomainResult<List<Review>>>?>(null)

        fun resultFor(owner: String?): DomainResult<List<Review>>? = result?.takeIf { it.first == owner }?.second

        fun load(owner: String?) {
            clear()
            if (owner == null) return
            loadJob =
                viewModelScope.launch {
                    val reviews = getReviews(owner)
                    ensureActive()
                    result = owner to reviews
                }
        }

        fun clear() {
            loadJob?.cancel()
            loadJob = null
            result = null
        }
    }
