package com.mirawuse.sportspot.presentation.ui.account

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.mirawuse.sportspot.domain.model.favorite.Favorite
import com.mirawuse.sportspot.domain.model.favorite.FavoriteList
import com.mirawuse.sportspot.domain.model.location.GeoPoint
import com.mirawuse.sportspot.domain.model.spot.SportType
import com.mirawuse.sportspot.domain.model.spot.Spot
import com.mirawuse.sportspot.domain.model.spot.SpotId
import com.mirawuse.sportspot.presentation.ui.theme.SportSpotTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class FavoritesUiTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun guestCanOpenAuthAndOwnerCanOpenRemoveOrLocateACachedFavorite() {
        val state = mutableStateOf<FavoritesUiState>(FavoritesUiState.Guest)
        val spot = Spot(SpotId("osm", "node:42"), "Сохранённая площадка", SportType.FOOTBALL, GeoPoint(56.31, 44.05))
        val actions = mutableListOf<String>()
        compose.setContent {
            SportSpotTheme {
                FavoritesScreen(
                    state = state.value,
                    restoring = false,
                    busy = false,
                    onOpenAuth = { actions.add("auth") },
                    onRetry = {},
                    onOpenSpot = { actions.add("details:${it.value}") },
                    onOpenMap = { actions.add("map:${it.value}") },
                    onRemove = { actions.add("remove:${it.value}") },
                )
            }
        }
        compose.onNodeWithText("Для доступа к избранному нужен аккаунт.").assertIsDisplayed()
        compose.onNodeWithText("К авторизации").performClick()
        compose.onNodeWithText(spot.name).assertDoesNotExist()

        compose.runOnIdle {
            val favorite = Favorite("owner", spot.id, "2026-11-24", spot)
            state.value = FavoritesUiState.Content("owner", FavoriteList(listOf(favorite), fromCache = true))
        }
        compose.onNodeWithText("Показан сохранённый список. Не удалось обновить данные.").assertIsDisplayed()
        compose.onNodeWithText(spot.name).performClick()
        compose.onNodeWithText("Показать на карте").performClick()
        compose.onNodeWithContentDescription("Удалить из избранного").performClick()
        compose.runOnIdle { assertEquals(listOf("auth", "details:node:42", "map:node:42", "remove:node:42"), actions) }
    }
}
