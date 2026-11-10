
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

class CreateProposalUseCaseTest {
    private val location = GeoPoint(56.3269, 44.0059)

    @Test
    fun `пользователь может предложить новую площадку`() =
        runTest {
            val repository = FakeSpotProposalRepository()
            val useCase = createUseCase(authenticated(UserRole.USER), repository)

            val result =
                useCase(
                    name = "Футбольная площадка",
                    sportType = SportType.FOOTBALL,
                    location = location,
                )

            assertEquals("Футбольная площадка", result.name)
            assertEquals(SportType.FOOTBALL, result.sportType)
            assertEquals(location, result.location)
            assertEquals(ProposalStatus.PENDING, result.status)
            assertEquals(1, repository.createCalls)
        }

    @Test
    fun `модератор может предложить новую площадку`() =
        runTest {
            val repository = FakeSpotProposalRepository()
            val useCase = createUseCase(authenticated(UserRole.MODERATOR), repository)

            val result =
                useCase(
                    name = "Баскетбольная площадка",
                    sportType = SportType.BASKETBALL,
                    location = location,
                )

            assertEquals("Баскетбольная площадка", result.name)
            assertEquals(ProposalStatus.PENDING, result.status)
            assertEquals(1, repository.createCalls)
        }

    @Test
    fun `пробелы вокруг названия удаляются`() =
        runTest {
            val repository = FakeSpotProposalRepository()
            val useCase = createUseCase(authenticated(UserRole.USER), repository)

            val result =
                useCase(
                    name = "   Теннисный корт   ",
                    sportType = SportType.TENNIS,
                    location = location,
                )

            assertEquals("Теннисный корт", result.name)
            assertEquals("Теннисный корт", repository.lastName)
        }

    @Test
    fun `пустое название запрещено`() =
        runTest {
            val repository = FakeSpotProposalRepository()
            val useCase = createUseCase(authenticated(UserRole.USER), repository)

            val result =
                runCatching {
                    useCase(
                        name = "   ",
                        sportType = SportType.FOOTBALL,
                        location = location,
                    )
                }

            assertTrue(result.exceptionOrNull() is IllegalArgumentException)
            assertEquals(0, repository.createCalls)
        }

    @Test
    fun `гость не может предложить площадку`() =
        runTest {
            val repository = FakeSpotProposalRepository()
            val useCase = createUseCase(AuthState.Guest, repository)

            val result =
                runCatching {
                    useCase(
                        name = "Футбольная площадка",
                        sportType = SportType.FOOTBALL,
                        location = location,
                    )
                }

            assertTrue(result.exceptionOrNull() is IllegalStateException)
            assertEquals(0, repository.createCalls)
        }

    private fun createUseCase(
        state: AuthState,
        repository: SpotProposalRepository,
    ): CreateProposalUseCase =
        CreateProposalUseCase(
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
        var createCalls = 0
        var lastName: String? = null

        override suspend fun getMyProposals(): List<SpotProposal> = error("Метод не используется в тесте")

        override suspend fun getPendingProposals(): List<SpotProposal> = error("Метод не используется в тесте")

        override suspend fun createProposal(
            name: String,
            sportType: SportType,
            location: GeoPoint,
        ): SpotProposal {
            createCalls++
            lastName = name

            return SpotProposal(
                id = "proposal-123",
                authorId = "user-123",
                name = name,
                sportType = sportType,
                location = location,
                status = ProposalStatus.PENDING,
            )
        }

        override suspend fun approveProposal(proposalId: String): SpotProposal = error("Метод не используется в тесте")

        override suspend fun rejectProposal(
            proposalId: String,
            reason: String,
        ): SpotProposal = error("Метод не используется в тесте")
    }
}
