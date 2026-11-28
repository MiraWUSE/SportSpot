package com.mirawuse.sportspot.presentation.ui.account

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mirawuse.sportspot.domain.model.auth.AuthException
import com.mirawuse.sportspot.domain.model.auth.AuthFailure
import com.mirawuse.sportspot.domain.model.auth.AuthState
import com.mirawuse.sportspot.domain.repository.AuthRepository
import com.mirawuse.sportspot.domain.result.DomainResult
import com.mirawuse.sportspot.domain.usecase.auth.GetAuthStateUseCase
import com.mirawuse.sportspot.domain.usecase.auth.SignInUseCase
import com.mirawuse.sportspot.domain.usecase.auth.SignOutUseCase
import com.mirawuse.sportspot.domain.usecase.auth.SignUpUseCase
import com.mirawuse.sportspot.presentation.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
internal class AuthViewModel
    @Inject
    constructor(
        repository: AuthRepository,
    ) : ViewModel() {
        private val getAuthState = GetAuthStateUseCase(repository)
        private val signIn = SignInUseCase(repository)
        private val signUp = SignUpUseCase(repository)
        private val signOut = SignOutUseCase(repository)
        private var restoreJob: Job? = null

        val authState = repository.authState.stateIn(viewModelScope, SharingStarted.Eagerly, AuthState.Guest)

        var restoring by mutableStateOf(true)
            private set
        var submitting by mutableStateOf(false)
            private set
        var confirmationPending by mutableStateOf(false)
            private set
        var error by mutableStateOf<AuthException?>(null)
            private set
        var restoreFailed by mutableStateOf(false)
            private set

        init {
            restore()
        }

        fun restore() {
            if (restoreJob?.isActive == true || submitting) return
            restoring = true
            restoreFailed = false
            error = null
            restoreJob =
                viewModelScope.launch {
                    try {
                        getAuthState()
                    } catch (failure: AuthException) {
                        error = failure
                        restoreFailed = true
                    } finally {
                        restoring = false
                    }
                }
        }

        fun clearNotice() {
            confirmationPending = false
            error = null
            restoreFailed = false
        }

        fun submit(
            name: String?,
            email: String,
            password: String,
        ) = runOperation {
            val result = if (name == null) signIn(email, password) else signUp(name, email, password)
            if (result is DomainResult.Failure) {
                error = AuthException(AuthFailure.UNAVAILABLE, result.error)
            } else if (name != null) {
                confirmationPending = getAuthState() == AuthState.Guest
            }
        }

        fun logout() = runOperation { signOut() }

        private fun runOperation(operation: suspend () -> Unit) {
            if (submitting || restoring) return
            submitting = true
            clearNotice()
            viewModelScope.launch {
                try {
                    operation()
                } catch (failure: AuthException) {
                    error = failure
                } finally {
                    submitting = false
                }
            }
        }
    }

internal fun AuthFailure.labelRes(): Int =
    when (this) {
        AuthFailure.INVALID_CREDENTIALS -> R.string.auth_invalid_credentials
        AuthFailure.EMAIL_UNCONFIRMED -> R.string.auth_email_unconfirmed
        AuthFailure.WEAK_PASSWORD -> R.string.auth_weak_password
        AuthFailure.RATE_LIMITED -> R.string.auth_rate_limited
        AuthFailure.OFFLINE -> R.string.auth_offline
        AuthFailure.SESSION_EXPIRED -> R.string.auth_session_expired
        AuthFailure.UNAVAILABLE -> R.string.auth_unavailable
    }
