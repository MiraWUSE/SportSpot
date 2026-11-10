
package com.mirawuse.sportspot.domain.usecase.proposal

import com.mirawuse.sportspot.domain.model.auth.AuthState
import com.mirawuse.sportspot.domain.model.location.GeoPoint
import com.mirawuse.sportspot.domain.model.proposal.ProposalStatus
import com.mirawuse.sportspot.domain.model.proposal.SpotProposal
import com.mirawuse.sportspot.domain.model.spot.SportType
import com.mirawuse.sportspot.domain.model.user.UserProfile
import com.mirawuse.sportspot.domain.model.user.UserRole
import com.mirawuse.sportspot.domain.repository.AuthRepository
import com.mirawuse.sportspot.domain.repository.SpotProposalRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GetPendingProposalsUseCaseTest {
    @Test
    fun `модератор получает список ожидающих предложений`() =
        runTest {
            val proposals =
                listOf(
                    createProposal("proposal-1"),
                    createProposal("proposal-2"),
                )

            val repository = FakeSpotProposalRepository(proposals)
            val useCase =
                GetPendingProposalsUseCase(
                    FakeAuthRepository(authenticated(UserRole.MODERATOR)),
                    repository,
                )

            val result = useCase()

            assertEquals(proposals, result)
            assertEquals(2, result.size)
            assertTrue(result.all { it.status == ProposalStatus.PENDING })
            assertEquals(1, repository.getPendingCalls)
        }

    @Test
    fun `модератор получает пустой список если заявок нет`() =
        runTest {
            val repository = FakeSpotProposalRepository(emptyList())
            val useCase =
                GetPendingProposalsUseCase(
                    FakeAuthRepository(authenticated(UserRole.MODERATOR)),
                    repository,
                )

            val result = useCase()

            assertTrue(result.isEmpty())
            assertEquals(1, repository.getPendingCalls)
        }

    @Test
    fun `обычный пользователь не может просматривать очередь модерации`() =
        runTest {
            val repository = FakeSpotProposalRepository(emptyList())
            val useCase =
                GetPendingProposalsUseCase(
                    FakeAuthRepository(authenticated(UserRole.USER)),
                    repository,
                )

            val result = runCatching { useCase() }

            assertTrue(result.exceptionOrNull() is IllegalStateException)
            assertEquals(0, repository.getPendingCalls)
        }

    @Test
    fun `гость не может просматривать очередь модерации`() =
        runTest {
            val repository = FakeSpotProposalRepository(emptyList())
            val useCase =
                GetPendingProposalsUseCase(
                    FakeAuthRepository(AuthState.Guest),
                    repository,
                )

            val result = runCatching { useCase() }

            assertTrue(result.exceptionOrNull() is IllegalStateException)
            assertEquals(0, repository.getPendingCalls)
        }

    private fun createProposal(id: String): SpotProposal =
        SpotProposal(
            id = id,
            authorId = "user-123",
            name = "Футбольная площадка",
            sportType = SportType.FOOTBALL,
            location = GeoPoint(56.3269, 44.0059),
            status = ProposalStatus.PENDING,
        )

    private fun authenticated(role: UserRole): AuthState =
        AuthState.Authenticated(
            UserProfile(
                id = "user-123",
                name = "Иван",
                email = "ivan@example.com",
                role = role,
            ),
        )

    private class FakeAuthRepository(
        private val state: AuthState,
    ) : AuthRepository {
        override suspend fun getAuthState(): AuthState = state

        override suspend fun signIn(
            email: String,
            password: String,
        ): UserProfile = error("Метод не используется в тесте")

        override suspend fun signUp(
            name: String,
            email: String,
            password: String,
        ) {
            error("Метод не используется в тесте")
        }

        override suspend fun signOut() {
            error("Метод не используется в тесте")
        }
    }

    private class FakeSpotProposalRepository(
        private val proposals: List<SpotProposal>,
    ) : SpotProposalRepository {
        var getPendingCalls = 0

        override suspend fun getMyProposals(): List<SpotProposal> = error("Метод не используется в тесте")

        override suspend fun getPendingProposals(): List<SpotProposal> {
            getPendingCalls++
            return proposals
        }

        override suspend fun createProposal(
            name: String,
            sportType: SportType,
            location: GeoPoint,
        ): SpotProposal = error("Метод не используется в тесте")

        override suspend fun approveProposal(proposalId: String): SpotProposal = error("Метод не используется в тесте")

        override suspend fun rejectProposal(
            proposalId: String,
            reason: String,
        ): SpotProposal = error("Метод не используется в тесте")
    }
}
