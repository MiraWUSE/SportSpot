package com.mirawuse.sportspot.presentation.ui.account

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.mirawuse.sportspot.domain.model.auth.AuthState
import com.mirawuse.sportspot.domain.model.user.UserProfile
import com.mirawuse.sportspot.domain.model.user.UserRole
import com.mirawuse.sportspot.domain.repository.AuthRepository
import com.mirawuse.sportspot.domain.repository.UserRepository
import com.mirawuse.sportspot.domain.result.DomainError
import com.mirawuse.sportspot.domain.result.DomainOperationException
import com.mirawuse.sportspot.presentation.ui.theme.SportSpotTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class EditProfileUiTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()
    private val user = UserProfile("alice", "Алиса", "alice@example.test", UserRole.MODERATOR)
    private val repository = TestProfile(user)

    @Test
    fun editValidatesAndDisplaysServerErrorsThenUpdatesTheProfileInPlace() {
        setContent(dark = false)
        openEditor()
        val name = compose.onNodeWithContentDescription("Имя")
        val save = compose.onNodeWithText("Сохранить")
        name.assertTextContains(user.name)
        compose.onNodeWithText(user.email).performScrollTo().assertIsDisplayed()

        name.performScrollTo().performTextReplacement(" ")
        save.performScrollTo().performClick()
        compose.onNodeWithText("Заполните это поле").performScrollTo().assertIsDisplayed()

        name.performScrollTo().performTextReplacement("Я".repeat(81))
        save.performScrollTo().performClick()
        compose.onNodeWithText("Имя должно содержать не больше 80 символов").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, repository.calls) }

        name.performScrollTo().performTextReplacement("  Новое имя  ")
        compose.runOnIdle { repository.offline = true }
        save.performScrollTo().performClick()
        compose.onNodeWithText("Не удалось связаться с сервером. Попробуйте ещё раз.").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { assertEquals(user, repository.getProfile()) }

        val completion = CompletableDeferred<Unit>()
        compose.runOnIdle {
            repository.offline = false
            repository.completion = completion
        }
        save.performScrollTo().performClick().assertIsNotEnabled()
        compose.onNodeWithText("Отмена").performScrollTo().assertIsNotEnabled()
        name.assertIsNotEnabled()
        compose.runOnIdle { completion.complete(Unit) }
        compose.onNodeWithText("Новое имя").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(2, repository.calls)
            assertEquals(user.copy(name = "Новое имя"), repository.getProfile())
        }
    }

    @Test
    fun cancelDiscardsDraftAndGuestHasNoEditAction() {
        setContent(dark = true)
        openEditor()
        compose.onNodeWithContentDescription("Имя").performTextReplacement("Черновик")
        compose.onNodeWithText("Отмена").performScrollTo().performClick()
        compose.onNodeWithText(user.name).assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(0, repository.calls)
            repository.authState.value = AuthState.Guest
        }
        compose.onNodeWithText("Редактировать профиль").assertDoesNotExist()
    }

    private fun openEditor() {
        compose.onNodeWithText("Редактировать профиль").performScrollTo().performClick()
    }

    private fun setContent(dark: Boolean) {
        val auth = AuthViewModel(repository)
        val edit = EditProfileViewModel(repository, repository)
        compose.setContent {
            val state by auth.authState.collectAsState()
            var editing by remember { mutableStateOf(false) }
            val profile = (state as? AuthState.Authenticated)?.profile
            SportSpotTheme(darkTheme = dark) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.5f)) {
                    Box(Modifier.width(320.dp)) {
                        if (editing && profile != null) {
                            EditProfileScreen(profile, edit) { editing = false }
                        } else {
                            ProfileScreen(state, auth, {}, {}, {}, {}, { editing = true })
                        }
                    }
                }
            }
        }
    }

    private class TestProfile(
        profile: UserProfile,
    ) : AuthRepository,
        UserRepository {
        override val authState = MutableStateFlow<AuthState>(AuthState.Authenticated(profile))
        var offline = false
        var calls = 0
        var completion: CompletableDeferred<Unit>? = null

        fun getProfile(): UserProfile = (authState.value as AuthState.Authenticated).profile

        override suspend fun getAuthState(): AuthState = authState.value

        override suspend fun getMyProfile(): UserProfile = getProfile()

        override suspend fun updateMyProfile(
            userId: String,
            name: String,
        ): UserProfile {
            calls++
            if (offline) throw DomainOperationException(DomainError.Unavailable)
            completion?.await()
            val profile = getProfile()
            check(userId == profile.id)
            return profile.copy(name = name).also { authState.value = AuthState.Authenticated(it) }
        }

        override suspend fun signIn(
            email: String,
            password: String,
        ): UserProfile = error("Unexpected sign-in")

        override suspend fun signUp(
            name: String,
            email: String,
            password: String,
        ) = error("Unexpected sign-up")

        override suspend fun signOut() {
            authState.value = AuthState.Guest
        }
    }
}
