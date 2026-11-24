package com.mirawuse.sportspot.presentation.ui.reviews

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.mirawuse.sportspot.domain.model.auth.AuthState
import com.mirawuse.sportspot.domain.model.location.GeoPoint
import com.mirawuse.sportspot.domain.model.proposal.SpotProposal
import com.mirawuse.sportspot.domain.model.review.Review
import com.mirawuse.sportspot.domain.model.spot.SportType
import com.mirawuse.sportspot.domain.model.spot.SpotId
import com.mirawuse.sportspot.domain.model.user.UserProfile
import com.mirawuse.sportspot.domain.model.user.UserRole
import com.mirawuse.sportspot.domain.repository.AuthRepository
import com.mirawuse.sportspot.domain.repository.ReviewRepository
import com.mirawuse.sportspot.domain.result.DomainResult
import com.mirawuse.sportspot.presentation.ui.moderation.ModerationScreen
import com.mirawuse.sportspot.presentation.ui.proposal.ProposalScreen
import com.mirawuse.sportspot.presentation.ui.theme.SportSpotTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class CommunityContentUiTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun reviewRatingAndTitleArePublicButTheEditActionBelongsToItsAuthor() {
        val owner = mutableStateOf<String?>(null)
        val id = SpotId("community", "33333333-3333-4333-8333-333333333333")
        val review = Review("review", id, "alice", 4, "Есть сетка", title = "Хороший корт")
        val model = ReviewsViewModel(GuestAuth, PublicReviews(review))
        compose.setContent {
            SportSpotTheme(darkTheme = true) {
                LaunchedEffect(id) { model.load(id) }
                ReviewsScreen("Корт", model.stateFor(id), owner.value, false, null, {}, {}, {}, {})
            }
        }
        compose.onNodeWithText("Отзывов: 1", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Хороший корт").assertIsDisplayed()
        compose.onNodeWithText("Пользователь").assertIsDisplayed()
        compose.onNodeWithText("Удалить отзыв").assertDoesNotExist()
        compose.runOnIdle { owner.value = "alice" }
        compose.onNodeWithText("Редактировать отзыв").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Вы").assertIsDisplayed()
    }

    private object GuestAuth : AuthRepository {
        override suspend fun getAuthState() = AuthState.Guest

        override suspend fun signIn(
            email: String,
            password: String,
        ) = error("Guest read")

        override suspend fun signUp(
            name: String,
            email: String,
            password: String,
        ) = error("Guest read")

        override suspend fun signOut() = Unit
    }

    private class PublicReviews(
        private val review: Review,
    ) : ReviewRepository {
        override suspend fun getReviewsBySpot(spotId: SpotId): List<Review> = listOf(review).filter { it.spotId == spotId }

        override suspend fun createReview(
            userId: String,
            spotId: SpotId,
            rating: Int,
            text: String,
            title: String?,
        ) = error("Guest read")

        override suspend fun updateReview(
            userId: String,
            reviewId: String,
            rating: Int,
            text: String,
            title: String?,
        ) = error("Guest read")

        override suspend fun deleteReview(
            userId: String,
            reviewId: String,
        ) = error("Guest read")
    }

    @Test
    fun guestProposalOffersSignInAndNoSubmission() {
        var openedAuth = false
        compose.setContent {
            SportSpotTheme(darkTheme = false) {
                ProposalScreen(null, false, false, null, {}, { openedAuth = true }, {}, { _, _, _, _ -> })
            }
        }
        compose.onNodeWithText("Отправить на проверку").assertDoesNotExist()
        compose.onNodeWithText("Войдите, чтобы отправлять предложения и следить за их статусом.").assertIsDisplayed()
        compose.onNodeWithText("Войти или зарегистрироваться").performClick()
        compose.runOnIdle { assertEquals(true, openedAuth) }
    }

    @Test
    fun moderatorQueueFitsNarrowScreenAndKeepsProposalActions() {
        val regular = UserProfile("reviewer", "Спортсмен", "reviewer@example.test", UserRole.USER)
        val auth = mutableStateOf(AuthState.Authenticated(regular))
        val busy = mutableStateOf(false)
        val title = "Будущий теннисный корт рядом с городским парком"
        val description = "Две площадки для одиночной и парной игры. Вход со стороны центральной аллеи."
        val proposal = SpotProposal("proposal", "alice", title, SportType.TENNIS, GeoPoint(56.3, 44.0), description = description)
        var refreshes = 0
        var approval: String? = null
        var rejection: Pair<String, String>? = null
        compose.setContent {
            SportSpotTheme(darkTheme = false) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.6f)) {
                    Box(Modifier.width(300.dp)) {
                        ModerationScreen(
                            auth = auth.value,
                            restoring = false,
                            pending = DomainResult.Success(listOf(proposal)),
                            busy = busy.value,
                            actionError = null,
                            onBack = {},
                            onRetry = { refreshes++ },
                            onApprove = { approval = it },
                            onReject = { id, reason -> rejection = id to reason },
                        )
                    }
                }
            }
        }
        compose.onNodeWithText(title).assertDoesNotExist()
        compose.onNodeWithText("Раздел открыт только модератору.").assertIsDisplayed()
        compose.runOnIdle { auth.value = AuthState.Authenticated(regular.copy(role = UserRole.MODERATOR)) }
        compose.onNodeWithText(title).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Обновить").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, refreshes) }
        compose.onNodeWithText(description).performScrollTo().assertIsDisplayed()
        compose.runOnIdle { busy.value = true }
        compose.onNodeWithText("Одобрить").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Отклонить").performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { busy.value = false }
        compose.onNodeWithText("Одобрить").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("proposal", approval) }
        compose.onNodeWithText("Отклонить").performScrollTo().performClick()
        compose.onNodeWithText("Подтвердить отклонение").performScrollTo().performClick()
        compose.runOnIdle { assertNull(rejection) }
        compose.onNodeWithContentDescription("Причина отклонения").performScrollTo().performTextInput("Уточните адрес")
        compose.onNodeWithText("Подтвердить отклонение").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("proposal" to "Уточните адрес", rejection) }
    }
}
