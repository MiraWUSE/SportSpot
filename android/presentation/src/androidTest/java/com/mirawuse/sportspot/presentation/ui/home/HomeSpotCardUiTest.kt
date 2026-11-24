package com.mirawuse.sportspot.presentation.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.mirawuse.sportspot.domain.model.location.GeoPoint
import com.mirawuse.sportspot.domain.model.spot.SportType
import com.mirawuse.sportspot.domain.model.spot.Spot
import com.mirawuse.sportspot.domain.model.spot.SpotId
import com.mirawuse.sportspot.presentation.ui.theme.SportSpotTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class HomeSpotCardUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun cardAndHeartHaveSeparateTouchTargetsAndAddressIsOptional() {
        val spot =
            Spot(
                id = SpotId("osm", "node:9"),
                name = "Открытая спортивная площадка у школы",
                sportType = SportType.VOLLEYBALL,
                location = GeoPoint(56.31, 44.05),
                address = "Школьная улица, 12",
            )
        val current = mutableStateOf(spot)
        val saved = mutableStateOf(false)
        val enabled = mutableStateOf(true)
        var opens = 0
        var toggles = 0
        compose.setContent {
            SportSpotTheme {
                Box(Modifier.width(312.dp)) {
                    HomeSpotCard(
                        spot = current.value,
                        onClick = { opens++ },
                        onFavorite = {
                            toggles++
                            saved.value = !saved.value
                        },
                        isFavorite = saved.value,
                        favoriteEnabled = enabled.value,
                    )
                }
            }
        }
        compose.onNodeWithText("Школьная улица, 12").assertIsDisplayed()
        compose.onNodeWithText("Волейбол").assertIsDisplayed()
        compose
            .onNodeWithContentDescription("Добавить в избранное")
            .assertWidthIsEqualTo(48.dp)
            .assertHeightIsEqualTo(48.dp)
            .performTouchInput { click() }
        compose.onNodeWithContentDescription("Удалить из избранного").assertIsSelected()
        compose.runOnIdle {
            assertEquals(0, opens)
            assertEquals(1, toggles)
            current.value = spot.copy(address = "  ")
            enabled.value = false
        }
        compose.onNodeWithText("Школьная улица, 12").assertDoesNotExist()
        compose.onNodeWithText("Адрес не указан").assertDoesNotExist()
        compose.onNodeWithContentDescription("Фото площадки «${spot.name}»").assertDoesNotExist()
        compose.onNodeWithContentDescription("Удалить из избранного").assertIsNotEnabled()
        compose.onNodeWithText(spot.name).performTouchInput { click() }
        compose.runOnIdle {
            assertEquals(1, opens)
            assertEquals(1, toggles)
        }
    }
}
