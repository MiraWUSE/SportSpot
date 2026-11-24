package com.mirawuse.sportspot.presentation.ui.photos

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mirawuse.sportspot.domain.model.auth.AuthState
import com.mirawuse.sportspot.domain.model.photo.PhotoAttachment
import com.mirawuse.sportspot.domain.model.photo.PhotoError
import com.mirawuse.sportspot.domain.model.photo.PhotoException
import com.mirawuse.sportspot.domain.model.photo.PhotoSubmission
import com.mirawuse.sportspot.domain.model.photo.PhotoTarget
import com.mirawuse.sportspot.domain.repository.AuthRepository
import com.mirawuse.sportspot.domain.repository.UserPhotoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

internal data class PhotoHistoryState(
    val items: List<PhotoSubmission> = emptyList(),
    val loading: Boolean = false,
    val error: PhotoError? = null,
)

internal data class PhotoDraftState(
    val attachments: List<PhotoAttachment> = emptyList(),
    val preparing: Boolean = false,
    val sending: Boolean = false,
    val completed: Int = 0,
    val total: Int = 0,
    val error: PhotoError? = null,
) {
    val busy: Boolean get() = preparing || sending
}

@HiltViewModel
internal class PhotosViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val auth: AuthRepository,
        private val repository: UserPhotoRepository,
    ) : ViewModel() {
        private var sessionOwner by mutableStateOf<String?>(null)
        private var draft by mutableStateOf(PhotoDraftState())
        private var savedTarget by mutableStateOf<PhotoTarget?>(null)
        private val history = mutableStateMapOf<PhotoTarget, PhotoHistoryState>()
        private val reads = mutableMapOf<PhotoTarget, Job>()
        private var work: Job? = null

        init {
            viewModelScope.launch {
                auth.authState.collect { state ->
                    val owner = (state as? AuthState.Authenticated)?.profile?.id
                    if (owner != sessionOwner) {
                        sessionOwner = owner
                        work?.cancel()
                        reads.values.forEach(Job::cancel)
                        reads.clear()
                        draft = PhotoDraftState()
                        savedTarget = null
                        history.clear()
                    }
                }
            }
        }

        fun readyFor(owner: String?): Boolean = owner != null && sessionOwner == owner

        private fun current(owner: String): Boolean = readyFor(owner)

        fun draftFor(owner: String): PhotoDraftState = if (current(owner)) draft else PhotoDraftState()

        fun savedFor(owner: String?): PhotoTarget? = savedTarget.takeIf { owner != null && current(owner) }

        fun historyFor(
            owner: String,
            target: PhotoTarget,
        ): PhotoHistoryState = history[target]?.takeIf { current(owner) } ?: PhotoHistoryState()

        fun load(
            owner: String,
            target: PhotoTarget,
        ) {
            if (!current(owner) || reads[target]?.isActive == true) return
            val previous = historyFor(owner, target)
            history[target] = previous.copy(loading = true, error = null)
            reads[target] =
                viewModelScope.launch {
                    try {
                        val items = repository.mine(owner, target)
                        if (current(owner)) history[target] = PhotoHistoryState(items)
                    } catch (error: PhotoException) {
                        if (current(owner)) history[target] = previous.copy(loading = false, error = error.error)
                    }
                }
        }

        fun select(
            owner: String,
            uris: List<Uri>,
            limit: Int,
        ) {
            if (!current(owner) || draft.busy || uris.isEmpty()) return
            if (draft.attachments.size + uris.size > limit) {
                draft = draft.copy(error = PhotoError.LIMIT)
                return
            }
            draft = draft.copy(preparing = true, error = null)
            work =
                viewModelScope.launch {
                    try {
                        for (uri in uris) {
                            val prepared = preparePhoto(context, uri)
                            if (!current(owner)) return@launch
                            draft = draft.copy(attachments = draft.attachments + prepared)
                        }
                    } catch (error: PhotoException) {
                        if (current(owner)) draft = draft.copy(error = error.error)
                    } finally {
                        if (current(owner)) draft = draft.copy(preparing = false)
                    }
                }
        }

        fun discard(
            owner: String,
            id: String,
        ) {
            if (current(owner) && !draft.busy) draft = draft.copy(attachments = draft.attachments.filterNot { it.id == id })
        }

        fun cancel(owner: String) {
            if (current(owner)) work?.cancel()
        }

        fun afterParentSaved(
            owner: String,
            target: PhotoTarget,
        ) {
            if (!current(owner) || savedTarget == target) return
            savedTarget = target
            send(owner, target)
        }

        fun send(
            owner: String,
            target: PhotoTarget,
        ) {
            if (!current(owner) || draft.busy || draft.attachments.isEmpty()) return
            val batch = draft.attachments
            draft = draft.copy(sending = true, completed = 0, total = batch.size, error = null)
            work =
                viewModelScope.launch {
                    try {
                        for (attachment in batch) {
                            val added = repository.upload(owner, target, attachment)
                            if (!current(owner)) return@launch
                            val previous = historyFor(owner, target)
                            history[target] = previous.copy(items = (previous.items.filterNot { it.id == added.id }) + added)
                            draft =
                                draft.copy(attachments = draft.attachments.filterNot { it.id == added.id }, completed = draft.completed + 1)
                        }
                    } catch (error: PhotoException) {
                        if (current(owner)) draft = draft.copy(error = error.error)
                    } finally {
                        if (current(owner)) {
                            draft = draft.copy(sending = false)
                            reads.remove(target)?.cancel()
                            load(owner, target)
                        }
                    }
                }
        }

        fun remove(
            owner: String,
            target: PhotoTarget,
            photo: PhotoSubmission,
        ) {
            if (!current(owner) || draft.busy || photo.ownerId != owner || photo.target != target) return
            draft = draft.copy(sending = true, error = null, total = 0)
            work =
                viewModelScope.launch {
                    try {
                        repository.remove(owner, photo.id)
                        if (current(owner)) {
                            val previous = historyFor(owner, target)
                            history[target] = previous.copy(items = previous.items.filterNot { it.id == photo.id })
                            reads.remove(target)?.cancel()
                            load(owner, target)
                        }
                    } catch (error: PhotoException) {
                        if (current(owner)) draft = draft.copy(error = error.error)
                    } finally {
                        if (current(owner)) draft = draft.copy(sending = false)
                    }
                }
        }

        suspend fun image(
            id: String,
            owner: String?,
        ): ByteArray = repository.image(id, owner)

        suspend fun gallery(target: PhotoTarget): List<String> = repository.gallery(target)

        suspend fun queue(
            owner: String,
            proposalId: String?,
        ): List<PhotoSubmission> = repository.queue(owner, proposalId)

        suspend fun decide(
            owner: String,
            id: String,
            reason: String?,
        ) = repository.decide(owner, id, reason)
    }
