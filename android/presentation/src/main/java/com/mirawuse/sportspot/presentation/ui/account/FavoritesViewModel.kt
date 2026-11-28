package com.mirawuse.sportspot.presentation.ui.account

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mirawuse.sportspot.domain.model.auth.AuthException
import com.mirawuse.sportspot.domain.model.auth.AuthState
import com.mirawuse.sportspot.domain.model.favorite.FavoriteException
import com.mirawuse.sportspot.domain.model.favorite.FavoriteFailure
import com.mirawuse.sportspot.domain.model.favorite.FavoriteList
import com.mirawuse.sportspot.domain.model.spot.Spot
import com.mirawuse.sportspot.domain.model.spot.SpotId
import com.mirawuse.sportspot.domain.repository.AuthRepository
import com.mirawuse.sportspot.domain.repository.FavoriteRepository
import com.mirawuse.sportspot.domain.result.DomainError
import com.mirawuse.sportspot.domain.result.DomainResult
import com.mirawuse.sportspot.domain.result.NetworkIssue
import com.mirawuse.sportspot.domain.usecase.favorite.AddFavoriteUseCase
import com.mirawuse.sportspot.domain.usecase.favorite.GetFavoritesUseCase
import com.mirawuse.sportspot.domain.usecase.favorite.RemoveFavoriteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

internal sealed interface FavoritesUiState {
    val owner: String?

    data object Guest : FavoritesUiState {
        override val owner: String? = null
    }

    data class Loading(
        override val owner: String,
    ) : FavoritesUiState

    data class Content(
        override val owner: String,
        val list: FavoriteList,
    ) : FavoritesUiState

    data class Error(
        override val owner: String,
        val reason: FavoriteFailure,
        val error: DomainError = reason.asDomainError(),
    ) : FavoritesUiState
}

internal fun FavoritesUiState.visibleFor(auth: AuthState): FavoritesUiState {
    val uid = (auth as? AuthState.Authenticated)?.profile?.id ?: return FavoritesUiState.Guest
    return if (owner == uid) this else FavoritesUiState.Loading(uid)
}

@HiltViewModel
internal class FavoritesViewModel
    @Inject
    constructor(
        auth: AuthRepository,
        favorites: FavoriteRepository,
    ) : ViewModel() {
        private val get = GetFavoritesUseCase(auth, favorites)
        private val add = AddFavoriteUseCase(auth, favorites)
        private val remove = RemoveFavoriteUseCase(auth, favorites)
        private var loadJob: Job? = null
        private var actionJob: Job? = null

        var state by mutableStateOf<FavoritesUiState>(FavoritesUiState.Guest)
            private set
        var busySpot by mutableStateOf<SpotId?>(null)
            private set
        var actionError by mutableStateOf<DomainError?>(null)
            private set

        init {
            viewModelScope.launch {
                auth.authState.map { (it as? AuthState.Authenticated)?.profile?.id }.distinctUntilChanged().collect { userId ->
                    loadJob?.cancel()
                    actionJob?.cancel()
                    loadJob = null
                    busySpot = null
                    actionError = null
                    state = if (userId == null) FavoritesUiState.Guest else FavoritesUiState.Loading(userId)
                    if (userId != null) retry()
                }
            }
        }

        fun retry() {
            val owner = state.owner ?: return
            if (loadJob?.isActive == true || busySpot != null) return
            state = FavoritesUiState.Loading(owner)
            loadJob =
                viewModelScope.launch {
                    val loaded =
                        try {
                            val list = get(owner).favoriteValue()
                            FavoritesUiState.Content(owner, list)
                        } catch (failure: FavoriteException) {
                            FavoritesUiState.Error(owner, failure.failure, failure.detail ?: failure.failure.asDomainError())
                        } catch (failure: AuthException) {
                            FavoritesUiState.Error(owner, FavoriteFailure.UNAVAILABLE, failure.detail ?: DomainError.Unauthorized)
                        }
                    if (state.owner == owner) state = loaded
                }
        }

        fun toggle(spot: Spot) {
            val current = state as? FavoritesUiState.Content ?: return retry()
            if (current.list.items.any { it.spotId == spot.id }) {
                removeSpot(spot.id)
            } else {
                mutate(spot.id) { owner, list ->
                    val saved = add(spot, owner).favoriteValue()
                    list.copy(items = listOf(saved.copy(spot = saved.spot ?: spot)) + list.items.filterNot { it.spotId == spot.id })
                }
            }
        }

        fun removeSpot(spotId: SpotId) =
            mutate(spotId) { owner, list ->
                remove(spotId, owner).favoriteValue()
                list.copy(items = list.items.filterNot { it.spotId == spotId })
            }

        fun clearActionError() {
            actionError = null
        }

        private fun mutate(
            spotId: SpotId,
            action: suspend (String, FavoriteList) -> FavoriteList,
        ) {
            val current = state as? FavoritesUiState.Content ?: return
            if (busySpot != null) return
            busySpot = spotId
            actionError = null
            actionJob =
                viewModelScope.launch {
                    try {
                        val list = action(current.owner, current.list)
                        if (state.owner == current.owner) state = current.copy(list = list)
                    } catch (failure: FavoriteException) {
                        if (state.owner == current.owner) actionError = failure.detail ?: failure.failure.asDomainError()
                    } catch (failure: AuthException) {
                        if (state.owner == current.owner) actionError = failure.detail ?: DomainError.Unauthorized
                    } finally {
                        if (state.owner == current.owner) busySpot = null
                    }
                }
        }
    }

private fun <T> DomainResult<T>.favoriteValue(): T =
    when (this) {
        is DomainResult.Success -> value
        is DomainResult.Failure -> throw FavoriteException(FavoriteFailure.SESSION_EXPIRED)
    }

private fun FavoriteFailure.asDomainError(): DomainError =
    when (this) {
        FavoriteFailure.OFFLINE -> DomainError.Network(NetworkIssue.OFFLINE)
        FavoriteFailure.RATE_LIMITED -> DomainError.Network(NetworkIssue.RATE_LIMITED)
        FavoriteFailure.SESSION_EXPIRED -> DomainError.Unauthorized
        FavoriteFailure.UNAVAILABLE -> DomainError.Unavailable
    }
