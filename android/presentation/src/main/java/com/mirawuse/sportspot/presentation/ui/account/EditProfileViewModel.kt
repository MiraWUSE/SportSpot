package com.mirawuse.sportspot.presentation.ui.account

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mirawuse.sportspot.domain.repository.AuthRepository
import com.mirawuse.sportspot.domain.repository.UserRepository
import com.mirawuse.sportspot.domain.result.DomainError
import com.mirawuse.sportspot.domain.result.DomainResult
import com.mirawuse.sportspot.domain.usecase.user.UpdateMyProfileUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
internal class EditProfileViewModel
    @Inject
    constructor(
        auth: AuthRepository,
        users: UserRepository,
    ) : ViewModel() {
        private val update = UpdateMyProfileUseCase(auth, users)
        var saving by mutableStateOf(false)
            private set
        var error by mutableStateOf<DomainError?>(null)
            private set

        fun save(
            owner: String,
            name: String,
            onSaved: () -> Unit,
        ) {
            if (saving) return
            saving = true
            error = null
            viewModelScope.launch {
                try {
                    when (val result = update(name, owner)) {
                        is DomainResult.Success -> onSaved()
                        is DomainResult.Failure -> error = result.error
                    }
                } finally {
                    saving = false
                }
            }
        }
    }
