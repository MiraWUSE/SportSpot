package com.mirawuse.sportspot.presentation.ui.forms

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.mirawuse.sportspot.domain.result.DomainError
import com.mirawuse.sportspot.domain.result.NetworkIssue
import com.mirawuse.sportspot.presentation.ui.theme.SportSpotTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class NetworkErrorUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun offlineAndServerErrorHaveDifferentMessagesAndCanBeRetried() {
        val error = mutableStateOf<DomainError>(DomainError.Network(NetworkIssue.OFFLINE))
        val dark = mutableStateOf(false)
        var attempts = 0
        compose.setContent {
            SportSpotTheme(darkTheme = dark.value) {
                ContentReadError(error.value) { attempts++ }
            }
        }
        compose.onNodeWithText("Нет подключения к интернету.", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Повторить").performClick()
        compose.runOnIdle {
            assertEquals(1, attempts)
            error.value = DomainError.Network(NetworkIssue.SERVER)
            dark.value = true
        }
        compose.onNodeWithText("Нет подключения к интернету.", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Сервис временно недоступен из-за ошибки сервера.", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Повторить").performClick()
        compose.runOnIdle {
            assertEquals(2, attempts)
            error.value = DomainError.Forbidden
        }
        compose.onNodeWithText("Повторить").assertDoesNotExist()
    }
}
