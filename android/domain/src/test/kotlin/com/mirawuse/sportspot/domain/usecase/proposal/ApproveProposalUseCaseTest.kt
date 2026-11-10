
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

class ApproveProposalUseCaseTest {
    @Test
    fun `модератор успешно одобряет предложение`() =
        runTest {
            val repository = FakeSpotProposalRepository()
            val useCase = createUseCase(authenticated(UserRole.MODERATOR), repository)

            val result = useCase("proposal-123")

            assertTrue(result is DomainResult.Success)
            assertEquals(ProposalStatus.APPROVED, repository.lastApprovedProposal?.status)
            assertEquals("proposal-123", repository.lastProposalId)
            assertEquals(1, repository.approveCalls)
        }

    @Test
    fun `пробелы вокруг идентификатора удаляются`() =
        runTest {
            val repository = FakeSpotProposalRepository()
            val useCase = createUseCase(authenticated(UserRole.MODERATOR), repository)

            val result = useCase("  proposal-123  ")

            assertTrue(result is DomainResult.Success)
            assertEquals("proposal-123", repository.lastProposalId)
            assertEquals(1, repository.approveCalls)
        }

    @Test
    fun `обычный пользователь получает Forbidden`() =
        runTest {
            val repository = FakeSpotProposalRepository()
            val useCase = createUseCase(authenticated(UserRole.USER), repository)

            val result = useCase("proposal-123")

            assertEquals(
                DomainResult.Failure(DomainError.Forbidden),
                result,
            )
            assertEquals(0, repository.approveCalls)
        }

    @Test
    fun `гость получает Unauthorized`() =
        runTest {
            val repository = FakeSpotProposalRepository()
            val useCase = createUseCase(AuthState.Guest, repository)

            val result = useCase("proposal-123")

            assertEquals(
                DomainResult.Failure(DomainError.Unauthorized),
                result,
            )
            assertEquals(0, repository.approveCalls)
        }

    @Test
    fun `пустой идентификатор возвращает Validation`() =
        runTest {
            val repository = FakeSpotProposalRepository()
            val useCase = createUseCase(authenticated(UserRole.MODERATOR), repository)

            val result = useCase("")

            assertEquals(
                DomainResult.Failure(
                    DomainError.Validation(
                        "Идентификатор предложения не может быть пустым",
                    ),
                ),
                result,
            )
            assertEquals(0, repository.approveCalls)
        }

    @Test
    fun `идентификатор из пробелов возвращает Validation`() =
        runTest {
            val repository = FakeSpotProposalRepository()
            val useCase = createUseCase(authenticated(UserRole.MODERATOR), repository)

            val result = useCase("   ")

            assertEquals(
                DomainResult.Failure(
                    DomainError.Validation(
                        "Идентификатор предложения не может быть пустым",
                    ),
                ),
                result,
            )
            assertEquals(0, repository.approveCalls)
        }

    @Test
    fun `непредвиденная ошибка репозитория не скрывается`() =
        runTest {
            val failure = IllegalStateException("Ошибка репозитория")
            val repository = FakeSpotProposalRepository(failure)
            val useCase = createUseCase(authenticated(UserRole.MODERATOR), repository)

            val result = runCatching { useCase("proposal-123") }

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() === failure)
            assertEquals(1, repository.approveCalls)
        }

    private fun createUseCase(
        state: AuthState,
        repository: SpotProposalRepository,
    ): ApproveProposalUseCase =
        ApproveProposalUseCase(
            authRepository = FakeAuthRepository(state),
            proposalRepository = repository,
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
        private val failure: Throwable? = null,
    ) : SpotProposalRepository {
        var approveCalls = 0
        var lastProposalId: String? = null
        var lastApprovedProposal: SpotProposal? = null

        override suspend fun getMyProposals(): List<SpotProposal> = error("Метод не используется в тесте")

        override suspend fun getPendingProposals(): List<SpotProposal> = error("Метод не используется в тесте")

        override suspend fun createProposal(
            name: String,
            sportType: SportType,
            location: GeoPoint,
        ): SpotProposal = error("Метод не используется в тесте")

        override suspend fun approveProposal(proposalId: String): SpotProposal {
            approveCalls++
            lastProposalId = proposalId

            failure?.let { throw it }

            val proposal =
                SpotProposal(
                    id = proposalId,
                    authorId = "user-123",
                    name = "Футбольная площадка",
                    sportType = SportType.FOOTBALL,
                    location = GeoPoint(56.3269, 44.0059),
                    status = ProposalStatus.PENDING,
                )

            return proposal.approve().also {
                lastApprovedProposal = it
            }
        }

        override suspend fun rejectProposal(
            proposalId: String,
            reason: String,
        ): SpotProposal = error("Метод не используется в тесте")
    }
}
