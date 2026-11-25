package com.mirawuse.sportspot.domain.usecase.review

import com.mirawuse.sportspot.domain.model.auth.AuthState
import com.mirawuse.sportspot.domain.model.review.Review
import com.mirawuse.sportspot.domain.model.spot.SpotId
import com.mirawuse.sportspot.domain.model.user.UserProfile
import com.mirawuse.sportspot.domain.model.user.UserRole
import com.mirawuse.sportspot.domain.repository.AuthRepository
import com.mirawuse.sportspot.domain.repository.ReviewRepository
import com.mirawuse.sportspot.domain.result.DomainError
import com.mirawuse.sportspot.domain.result.DomainOperationException
import com.mirawuse.sportspot.domain.result.DomainResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class GetMyReviewsUseCaseTest {
    private val profile = UserProfile("alice", "Алиса", "alice@example.test", UserRole.USER)
    private val review = Review("review", SpotId("osm", "way:42"), profile.id, 5, "Открытая площадка")
    private var currentState: AuthState = AuthState.Guest
    private var requested: String? = null
    private var unavailable = false
    private val useCase =
        GetMyReviewsUseCase(
            object : AuthRepository {
                override suspend fun getAuthState() = currentState

                override suspend fun signIn(
                    email: String,
                    password: String,
                ): UserProfile = error("Unexpected sign-in")

                override suspend fun signUp(
                    name: String,
                    email: String,
                    password: String,
                ) = error("Unexpected sign-up")

                override suspend fun signOut() = error("Unexpected sign-out")
            },
            object : ReviewRepository {
                override suspend fun getReviewsByUser(userId: String): List<Review> {
                    requested = userId
                    if (unavailable) throw DomainOperationException(DomainError.Unavailable)
                    return listOf(review)
                }

                override suspend fun getReviewsBySpot(spotId: SpotId): List<Review> = error("Unexpected public read")

                override suspend fun createReview(
                    userId: String,
                    spotId: SpotId,
                    rating: Int,
                    text: String,
                    title: String?,
                ): Review = error("Unexpected creation")

                override suspend fun updateReview(
                    userId: String,
                    reviewId: String,
                    rating: Int,
                    text: String,
                    title: String?,
                ): Review = error("Unexpected update")

                override suspend fun deleteReview(
                    userId: String,
                    reviewId: String,
                ) = error("Unexpected deletion")
            },
        )

    @Test
    fun rejectsGuestAndDifferentAccountWithoutReadingReviews() =
        runTest {
            assertEquals(DomainResult.Failure(DomainError.Unauthorized), useCase(profile.id))
            currentState = AuthState.Authenticated(profile)
            assertEquals(DomainResult.Failure(DomainError.Unauthorized), useCase("bob"))
            assertEquals(null, requested)
        }

    @Test
    fun usesAuthenticatedOwnerAndPropagatesContentFailure() =
        runTest {
            currentState = AuthState.Authenticated(profile)
            assertEquals(DomainResult.Success(listOf(review)), useCase(profile.id))
            assertEquals(profile.id, requested)
            unavailable = true
            assertEquals(DomainResult.Failure(DomainError.Unavailable), useCase(profile.id))
        }
}
