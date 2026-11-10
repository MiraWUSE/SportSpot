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

class GetMyProposalsUseCaseTest {
    @Test
    fun `пользователь получает список своих предложений`() =
        runTest {
            val proposals =
                listOf(
                    createProposal("proposal-1", ProposalStatus.PENDING),
                    createProposal("proposal-2", ProposalStatus.APPROVED),
                )

            val repository = FakeSpotProposalRepository(proposals)
            val useCase =
                GetMyProposalsUseCase(
                    FakeAuthRepository(authenticated(UserRole.USER)),
                    repository,
                )

            val result = useCase()

            assertEquals(proposals, result)
            assertEquals(2, result.size)
            assertEquals(1, repository.getMyProposalsCalls)
        }

    @Test
    fun `возвращает пустой список если предложений нет`() =
        runTest {
            val repository = FakeSpotProposalRepository(emptyList())
            val useCase =
                GetMyProposalsUseCase(
                    FakeAuthRepository(authenticated(UserRole.USER)),
                    repository,
                )

            val result = useCase()

            assertTrue(result.isEmpty())
            assertEquals(1, repository.getMyProposalsCalls)
        }

    @Test
    fun `модератор может просматривать свои предложения`() =
        runTest {
            val proposals = listOf(createProposal("proposal-1", ProposalStatus.PENDING))
            val repository = FakeSpotProposalRepository(proposals)
            val useCase =
                GetMyProposalsUseCase(
                    FakeAuthRepository(authenticated(UserRole.MODERATOR)),
                    repository,
                )

            val result = useCase()

            assertEquals(proposals, result)
            assertEquals(1, repository.getMyProposalsCalls)
        }

    @Test
    fun `гость не может получить список своих предложений`() =
        runTest {
            val repository = FakeSpotProposalRepository(emptyList())
            val useCase =
                GetMyProposalsUseCase(
                    FakeAuthRepository(AuthState.Guest),
                    repository,
                )

            val result = runCatching { useCase() }

            assertTrue(result.exceptionOrNull() is IllegalStateException)
            assertEquals(0, repository.getMyProposalsCalls)
        }

    private fun createProposal(
        id: String,
        status: ProposalStatus,
    ): SpotProposal =
        SpotProposal(
            id = id,
            authorId = "user-123",
            name = "Спортивная площадка",
            sportType = SportType.FOOTBALL,
            location = GeoPoint(56.3269, 44.0059),
            status = status,
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
        var getMyProposalsCalls = 0

        override suspend fun getMyProposals(): List<SpotProposal> {
            getMyProposalsCalls++
            return proposals
        }

        override suspend fun getPendingProposals(): List<SpotProposal> = error("Метод не используется в тесте")

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
