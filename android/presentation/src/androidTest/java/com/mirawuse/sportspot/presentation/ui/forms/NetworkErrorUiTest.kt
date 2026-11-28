package com.mirawuse.sportspot.presentation.ui.forms

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import com.mirawuse.sportspot.domain.repository.SpotRefreshPhase
import com.mirawuse.sportspot.domain.repository.SpotRefreshStatus
import com.mirawuse.sportspot.domain.result.DomainError
import com.mirawuse.sportspot.domain.result.NetworkIssue
import com.mirawuse.sportspot.presentation.ui.home.SpotBackgroundRefresh
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

    @Test
    fun backgroundStatusAndActionsRemainReadableInBothThemes() {
        val status = mutableStateOf<SpotRefreshStatus?>(null)
        val dark = mutableStateOf(false)
        var scheduled = 0
        var cancelled = 0
        compose.setContent {
            SportSpotTheme(darkTheme = dark.value) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = 1.4f)) {
                    SpotBackgroundRefresh(status.value, { scheduled++ }, { cancelled++ })
                }
            }
        }
        compose.onNodeWithText("Обновить в фоне").performClick()
        compose.runOnIdle {
            assertEquals(1, scheduled)
            status.value = SpotRefreshStatus("one", SpotRefreshPhase.QUEUED)
        }
        compose.onNodeWithText("Запланировано.", substring = true).assertIsDisplayed()
        compose.runOnIdle {
            dark.value = true
            status.value = SpotRefreshStatus("one", SpotRefreshPhase.RUNNING)
        }
        compose.onNodeWithText("Обновляем в фоне…").assertIsDisplayed()
        compose.onNodeWithText("Отменить").performClick()
        compose.runOnIdle {
            assertEquals(1, cancelled)
            status.value = SpotRefreshStatus("one", SpotRefreshPhase.CANCELLED)
        }
        compose.onNodeWithText("Фоновое обновление отменено").assertIsDisplayed()
        compose.runOnIdle {
            status.value = SpotRefreshStatus("two", SpotRefreshPhase.FAILED, error = DomainError.Network(NetworkIssue.SERVER))
        }
        compose.onNodeWithText("Не удалось обновить площадки в фоне.").assertIsDisplayed()
        compose.onNodeWithText("Повторить").performClick()
        compose.runOnIdle {
            assertEquals(2, scheduled)
            status.value = SpotRefreshStatus("three", SpotRefreshPhase.SUCCEEDED)
        }
        compose.onNodeWithText("Площадки обновлены").assertIsDisplayed()
        compose.onNodeWithText("Отменить").assertDoesNotExist()
    }
}
