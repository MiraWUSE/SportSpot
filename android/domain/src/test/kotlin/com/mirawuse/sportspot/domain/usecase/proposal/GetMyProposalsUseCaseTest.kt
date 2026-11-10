
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
import com.mirawuse.sportspot.domain.result.DomainError
import com.mirawuse.sportspot.domain.result.DomainResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GetMyProposalsUseCaseTest {
    @Test
    fun `пользователь успешно получает свои предложения`() =
        runTest {
            val proposals =
                listOf(
                    createProposal("proposal-1", ProposalStatus.PENDING),
                    createProposal("proposal-2", ProposalStatus.APPROVED),
                )

            val repository = FakeSpotProposalRepository(proposals)
            val useCase = createUseCase(authenticated(UserRole.USER), repository)

            val result = useCase()

            assertEquals(DomainResult.Success(proposals), result)
            assertEquals(2, proposals.size)
            assertEquals(1, repository.getMyProposalsCalls)
        }

    @Test
    fun `пустой список предложений считается успехом`() =
        runTest {
            val repository = FakeSpotProposalRepository(emptyList())
            val useCase = createUseCase(authenticated(UserRole.USER), repository)

            val result = useCase()

            assertEquals(
                DomainResult.Success(emptyList<SpotProposal>()),
                result,
            )
            assertEquals(1, repository.getMyProposalsCalls)
        }

    @Test
    fun `модератор успешно получает свои предложения`() =
        runTest {
            val proposals =
                listOf(
                    createProposal("proposal-1", ProposalStatus.PENDING),
                )

            val repository = FakeSpotProposalRepository(proposals)
            val useCase = createUseCase(authenticated(UserRole.MODERATOR), repository)

            val result = useCase()

            assertEquals(DomainResult.Success(proposals), result)
            assertEquals(1, repository.getMyProposalsCalls)
        }

    @Test
    fun `гость получает Unauthorized`() =
        runTest {
            val repository = FakeSpotProposalRepository(emptyList())
            val useCase = createUseCase(AuthState.Guest, repository)

            val result = useCase()

            assertEquals(
                DomainResult.Failure(DomainError.Unauthorized),
                result,
            )
            assertEquals(0, repository.getMyProposalsCalls)
        }

    @Test
    fun `непредвиденная ошибка репозитория не скрывается`() =
        runTest {
            val failure = IllegalStateException("Ошибка загрузки предложений")
            val repository = FakeSpotProposalRepository(emptyList(), failure)
            val useCase = createUseCase(authenticated(UserRole.USER), repository)

            val result = runCatching { useCase() }

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() === failure)
            assertEquals(1, repository.getMyProposalsCalls)
        }

    private fun createUseCase(
        state: AuthState,
        repository: SpotProposalRepository,
    ): GetMyProposalsUseCase =
        GetMyProposalsUseCase(
            authRepository = FakeAuthRepository(state),
            proposalRepository = repository,
        )

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
        private val failure: Throwable? = null,
    ) : SpotProposalRepository {
        var getMyProposalsCalls = 0

        override suspend fun getMyProposals(): List<SpotProposal> {
            getMyProposalsCalls++
            failure?.let { throw it }
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
