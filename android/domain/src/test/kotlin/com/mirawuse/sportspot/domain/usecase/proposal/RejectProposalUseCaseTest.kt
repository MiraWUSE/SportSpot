
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

class RejectProposalUseCaseTest {
    @Test
    fun `модератор может отклонить предложение`() =
        runTest {
            val repository = FakeSpotProposalRepository()
            val useCase = createUseCase(authenticated(UserRole.MODERATOR), repository)

            val result = useCase("proposal-123", "Площадка уже существует")

            assertEquals("proposal-123", result.id)
            assertEquals(ProposalStatus.REJECTED, result.status)
            assertEquals("Площадка уже существует", result.rejectReason)
            assertEquals(1, repository.rejectCalls)
        }

    @Test
    fun `пробелы вокруг идентификатора и причины удаляются`() =
        runTest {
            val repository = FakeSpotProposalRepository()
            val useCase = createUseCase(authenticated(UserRole.MODERATOR), repository)

            val result =
                useCase(
                    "  proposal-123  ",
                    "  Неверные координаты  ",
                )

            assertEquals("proposal-123", repository.lastProposalId)
            assertEquals("Неверные координаты", repository.lastReason)
            assertEquals("Неверные координаты", result.rejectReason)
            assertEquals(1, repository.rejectCalls)
        }

    @Test
    fun `обычный пользователь не может отклонить предложение`() =
        runTest {
            val repository = FakeSpotProposalRepository()
            val useCase = createUseCase(authenticated(UserRole.USER), repository)

            val result =
                runCatching {
                    useCase("proposal-123", "Причина отклонения")
                }

            assertTrue(result.exceptionOrNull() is IllegalStateException)
            assertEquals(0, repository.rejectCalls)
        }

    @Test
    fun `гость не может отклонить предложение`() =
        runTest {
            val repository = FakeSpotProposalRepository()
            val useCase = createUseCase(AuthState.Guest, repository)

            val result =
                runCatching {
                    useCase("proposal-123", "Причина отклонения")
                }

            assertTrue(result.exceptionOrNull() is IllegalStateException)
            assertEquals(0, repository.rejectCalls)
        }

    @Test
    fun `пустой идентификатор запрещен`() =
        runTest {
            val repository = FakeSpotProposalRepository()
            val useCase = createUseCase(authenticated(UserRole.MODERATOR), repository)

            val result =
                runCatching {
                    useCase("", "Причина отклонения")
                }

            assertTrue(result.exceptionOrNull() is IllegalArgumentException)
            assertEquals(0, repository.rejectCalls)
        }

    @Test
    fun `идентификатор из пробелов запрещен`() =
        runTest {
            val repository = FakeSpotProposalRepository()
            val useCase = createUseCase(authenticated(UserRole.MODERATOR), repository)

            val result =
                runCatching {
                    useCase("   ", "Причина отклонения")
                }

            assertTrue(result.exceptionOrNull() is IllegalArgumentException)
            assertEquals(0, repository.rejectCalls)
        }

    @Test
    fun `пустая причина отклонения запрещена`() =
        runTest {
            val repository = FakeSpotProposalRepository()
            val useCase = createUseCase(authenticated(UserRole.MODERATOR), repository)

            val result =
                runCatching {
                    useCase("proposal-123", "")
                }

            assertTrue(result.exceptionOrNull() is IllegalArgumentException)
            assertEquals(0, repository.rejectCalls)
        }

    @Test
    fun `причина отклонения из пробелов запрещена`() =
        runTest {
            val repository = FakeSpotProposalRepository()
            val useCase = createUseCase(authenticated(UserRole.MODERATOR), repository)

            val result =
                runCatching {
                    useCase("proposal-123", "   ")
                }

            assertTrue(result.exceptionOrNull() is IllegalArgumentException)
            assertEquals(0, repository.rejectCalls)
        }

    private fun createUseCase(
        state: AuthState,
        repository: SpotProposalRepository,
    ): RejectProposalUseCase =
        RejectProposalUseCase(
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

    private class FakeSpotProposalRepository : SpotProposalRepository {
        var rejectCalls = 0
        var lastProposalId: String? = null
        var lastReason: String? = null

        override suspend fun getMyProposals(): List<SpotProposal> = error("Метод не используется в тесте")

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
        ): SpotProposal {
            rejectCalls++
            lastProposalId = proposalId
            lastReason = reason

            val proposal =
                SpotProposal(
                    id = proposalId,
                    authorId = "user-123",
                    name = "Футбольная площадка",
                    sportType = SportType.FOOTBALL,
                    location = GeoPoint(56.3269, 44.0059),
                    status = ProposalStatus.PENDING,
                )

            return proposal.reject(reason)
        }
    }
}
