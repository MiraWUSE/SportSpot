package com.mirawuse.sportspot.presentation.ui.proposal

import com.mirawuse.sportspot.domain.model.auth.AuthState
import com.mirawuse.sportspot.domain.model.location.GeoPoint
import com.mirawuse.sportspot.domain.model.proposal.SpotProposal
import com.mirawuse.sportspot.domain.model.spot.SportType
import com.mirawuse.sportspot.domain.model.user.UserProfile
import com.mirawuse.sportspot.domain.model.user.UserRole
import com.mirawuse.sportspot.domain.repository.AuthRepository
import com.mirawuse.sportspot.domain.repository.SpotProposalRepository
import com.mirawuse.sportspot.domain.result.DomainError
import com.mirawuse.sportspot.domain.result.DomainOperationException
import com.mirawuse.sportspot.domain.result.DomainResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProposalsViewModelTest {
    @Test
    fun retryPreservesUserInputAndDuplicateTapsSendOnlyOnce() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val repository = TestProposals()
                val viewModel = ProposalsViewModel(TestAuth, repository)
                val point = GeoPoint(56.3, 44.0)
                viewModel.submit(ALICE, " ", SportType.TENNIS, point, "")
                advanceUntilIdle()
                assertTrue((viewModel.submissionFor(ALICE) as DomainResult.Failure).error is DomainError.Validation)
                assertEquals(0, repository.calls)
                viewModel.submit(ALICE, "Корт", SportType.TENNIS, point, "Лесная, 3")
                viewModel.submit(ALICE, "Корт", SportType.TENNIS, point, "Лесная, 3")
                advanceUntilIdle()
                assertEquals(DomainResult.Failure(DomainError.Unavailable), viewModel.submissionFor(ALICE))
                assertEquals(1, repository.calls)
                repository.online = true
                viewModel.submit(ALICE, "Корт", SportType.TENNIS, point, "Лесная, 3")
                advanceUntilIdle()
                val proposal = (viewModel.submissionFor(ALICE) as DomainResult.Success).value
                assertEquals("Лесная, 3", proposal.address)
                assertEquals(point, proposal.location)
                assertNull(viewModel.submissionFor("another-account"))
                viewModel.submit(ALICE, "Корт", SportType.TENNIS, point, "Лесная, 3")
                advanceUntilIdle()
                assertEquals(2, repository.calls)
            } finally {
                Dispatchers.resetMain()
            }
        }

    private class TestProposals : SpotProposalRepository {
        var online = false
        var calls = 0

        override suspend fun createProposal(
            userId: String,
            name: String,
            sportType: SportType,
            location: GeoPoint,
            address: String?,
        ): SpotProposal {
            calls++
            if (!online) throw DomainOperationException(DomainError.Unavailable)
            return SpotProposal("proposal", userId, name, sportType, location, address = address)
        }

        override suspend fun getMyProposals(userId: String) = emptyList<SpotProposal>()

        override suspend fun getPendingProposals(userId: String) = error("Not used")

        override suspend fun approveProposal(
            userId: String,
            proposalId: String,
        ) = error("Not used")

        override suspend fun rejectProposal(
            userId: String,
            proposalId: String,
            reason: String,
        ) = error("Not used")
    }

    private object TestAuth : AuthRepository {
        override suspend fun getAuthState() = AuthState.Authenticated(UserProfile(ALICE, "Alice", "alice@example.test", UserRole.USER))

        override suspend fun signIn(
            email: String,
            password: String,
        ) = error("Not used")

        override suspend fun signUp(
            name: String,
            email: String,
            password: String,
        ) = error("Not used")

        override suspend fun signOut() = Unit
    }

    private companion object {
        const val ALICE = "alice"
    }
}
