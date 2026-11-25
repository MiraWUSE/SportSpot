package com.mirawuse.sportspot.presentation.ui.reviews

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.mirawuse.sportspot.domain.model.auth.AuthState
import com.mirawuse.sportspot.domain.model.review.Review
import com.mirawuse.sportspot.domain.model.spot.SpotId
import com.mirawuse.sportspot.domain.model.user.UserProfile
import com.mirawuse.sportspot.domain.model.user.UserRole
import com.mirawuse.sportspot.domain.repository.AuthRepository
import com.mirawuse.sportspot.domain.repository.ReviewRepository
import com.mirawuse.sportspot.domain.result.DomainError
import com.mirawuse.sportspot.domain.result.DomainOperationException
import com.mirawuse.sportspot.presentation.ui.navigation.ReviewFormRoute
import com.mirawuse.sportspot.presentation.ui.navigation.ReviewsRoute
import com.mirawuse.sportspot.presentation.ui.theme.SportSpotTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MyReviewsUiTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()
    private val spot = SpotId("osm", "way:42")
    private val alice = UserProfile("alice", "Алиса", "alice@example.test", UserRole.USER)
    private val bob = UserProfile("bob", "Боб", "bob@example.test", UserRole.USER)
    private val repository =
        TestReviews(Review("review", spot, alice.id, 5, "Есть сетка", title = "Теннис", createdAt = "2026-11-24T09:00:00Z"))
    private var profile by mutableStateOf<UserProfile?>(alice)
    private val auth =
        object : AuthRepository {
            override suspend fun getAuthState(): AuthState = profile?.let { AuthState.Authenticated(it) } ?: AuthState.Guest

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
        }

    @Test
    fun returnsToRefreshedListAfterEditingAndDeletingAnOsmReviewWithoutAKnownName() {
        setContent()
        compose.onNodeWithText("Площадка (osm:way:42)").assertIsDisplayed()
        compose.onNodeWithText("Оценка 5 из 5").assertIsDisplayed()
        compose.onNodeWithText("2026-11-24").assertIsDisplayed()
        compose.onNodeWithText("Теннис").performClick()

        compose.onNodeWithText("Редактировать отзыв").performScrollTo().performClick()
        val comment = compose.onNodeWithContentDescription("Комментарий")
        comment.performScrollTo().assertTextContains("Есть сетка")
        comment.performTextReplacement("Заменили сетку")
        compose.onNodeWithText("Сохранить").performScrollTo().performClick()
        compose.onNodeWithText("Заменили сетку").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Назад").performClick()
        compose.onNodeWithText("Заменили сетку").assertIsDisplayed()
        compose.onNodeWithText("Есть сетка").assertDoesNotExist()

        compose.onNodeWithText("Теннис").performClick()
        compose.onNodeWithText("Удалить отзыв").performScrollTo().performClick()
        compose.onNodeWithText("Удалить ваш отзыв об этой площадке?").assertIsDisplayed()
        compose.onNode(hasText("Удалить отзыв") and hasAnyAncestor(isDialog())).performClick()
        compose.onNodeWithContentDescription("Назад").performClick()
        compose.onNodeWithText("Вы пока не оставили отзывов").assertIsDisplayed()
        compose.onNodeWithText("Найти площадку").assertIsDisplayed()
    }

    @Test
    fun switchingAccountsHidesPreviousReviewsAndRetryDistinguishesErrorFromEmpty() {
        setContent()
        compose.onNodeWithText("Есть сетка").assertIsDisplayed()
        compose.runOnIdle {
            repository.offline = true
            profile = bob
        }
        compose.onNodeWithText("Не удалось связаться с сервером. Попробуйте ещё раз.").assertIsDisplayed()
        compose.onNodeWithText("Есть сетка").assertDoesNotExist()
        compose.onNodeWithText("Вы пока не оставили отзывов").assertDoesNotExist()
        compose.runOnIdle { repository.offline = false }
        compose.onNodeWithText("Повторить").performClick()
        compose.onNodeWithText("Вы пока не оставили отзывов").assertIsDisplayed()
        compose.runOnIdle { profile = null }
        compose.onNodeWithText("Войдите, чтобы посмотреть свои отзывы.").assertIsDisplayed()
        compose.onNodeWithText("Войти или зарегистрироваться").assertIsDisplayed()
        compose.onNodeWithText("Есть сетка").assertDoesNotExist()
    }

    private fun setContent() {
        val myReviews = MyReviewsViewModel(auth, repository)
        val reviews = ReviewsViewModel(auth, repository)
        compose.setContent {
            val controller = rememberNavController()
            val owner = profile?.id

            fun back() {
                controller.popBackStack()
            }
            SportSpotTheme(darkTheme = true) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.5f)) {
                    Box(Modifier.width(320.dp)) {
                        NavHost(controller, startDestination = "my") {
                            composable("my") {
                                MyReviewsScreen(owner, false, myReviews, emptyMap(), ::back, {}, {}) {
                                    controller.navigate(ReviewsRoute(it.source, it.value))
                                }
                            }
                            composable<ReviewsRoute> { entry ->
                                val route = entry.toRoute<ReviewsRoute>()
                                assertEquals(spot, route.spotId)
                                LaunchedEffect(route.spotId) { reviews.load(route.spotId) }
                                ReviewsScreen(
                                    null,
                                    reviews.stateFor(route.spotId),
                                    owner,
                                    reviews.busy,
                                    reviews.errorFor(owner),
                                    ::back,
                                    onWrite = { controller.navigate(ReviewFormRoute(route.source, route.value)) },
                                    onDelete = { reviews.delete(checkNotNull(owner), it) },
                                    onRetry = { reviews.load(route.spotId, refresh = true) },
                                )
                            }
                            composable<ReviewFormRoute> { entry ->
                                val route = entry.toRoute<ReviewFormRoute>()
                                ReviewFormScreen(
                                    null,
                                    reviews.stateFor(route.spotId),
                                    owner,
                                    false,
                                    reviews.busy,
                                    reviews.errorFor(owner),
                                    ::back,
                                    {},
                                    onRetry = { reviews.load(route.spotId, refresh = true) },
                                    onSubmit = { existing, rating, text, title ->
                                        reviews.save(route.spotId, checkNotNull(owner), existing, rating, text, title) { back() }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private class TestReviews(
        private var review: Review?,
    ) : ReviewRepository {
        var offline = false

        override suspend fun getReviewsByUser(userId: String): List<Review> {
            if (offline) throw DomainOperationException(DomainError.Unavailable)
            return listOfNotNull(review?.takeIf { it.authorId == userId })
        }

        override suspend fun getReviewsBySpot(spotId: SpotId): List<Review> = listOfNotNull(review?.takeIf { it.spotId == spotId })

        override suspend fun createReview(
            userId: String,
            spotId: SpotId,
            rating: Int,
            text: String,
            title: String?,
        ): Review = error("Expected update to the existing review")

        override suspend fun updateReview(
            userId: String,
            reviewId: String,
            rating: Int,
            text: String,
            title: String?,
        ): Review = owned(userId, reviewId).copy(rating = rating, text = text, title = title).also { review = it }

        override suspend fun deleteReview(
            userId: String,
            reviewId: String,
        ) {
            owned(userId, reviewId)
            review = null
        }

        private fun owned(
            userId: String,
            reviewId: String,
        ): Review = checkNotNull(review?.takeIf { it.authorId == userId && it.id == reviewId })
    }
}
