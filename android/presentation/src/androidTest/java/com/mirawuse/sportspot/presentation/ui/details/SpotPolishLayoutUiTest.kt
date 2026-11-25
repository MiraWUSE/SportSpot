package com.mirawuse.sportspot.presentation.ui.details

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.mirawuse.sportspot.domain.model.favorite.Favorite
import com.mirawuse.sportspot.domain.model.favorite.FavoriteList
import com.mirawuse.sportspot.domain.model.location.GeoPoint
import com.mirawuse.sportspot.domain.model.review.Review
import com.mirawuse.sportspot.domain.model.spot.SportType
import com.mirawuse.sportspot.domain.model.spot.Spot
import com.mirawuse.sportspot.domain.model.spot.SpotId
import com.mirawuse.sportspot.domain.model.spot.SpotSport
import com.mirawuse.sportspot.presentation.R
import com.mirawuse.sportspot.presentation.ui.account.FavoritesUiState
import com.mirawuse.sportspot.presentation.ui.components.SportSpotButton
import com.mirawuse.sportspot.presentation.ui.home.HomeSpotCard
import com.mirawuse.sportspot.presentation.ui.home.HomeUiState
import com.mirawuse.sportspot.presentation.ui.map.VisibleMapSpotRow
import com.mirawuse.sportspot.presentation.ui.navigation.favoriteDestinationState
import com.mirawuse.sportspot.presentation.ui.reviews.ReviewScore
import com.mirawuse.sportspot.presentation.ui.theme.SportSpotSpacing
import com.mirawuse.sportspot.presentation.ui.theme.SportSpotTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

class SpotPolishLayoutUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun cardsAndDetailsRemainUsableInBothThemesAndWithLargerText() {
        val spot =
            Spot(
                id = SpotId("osm", "node:11"),
                name = "Многофункциональная спортивная площадка на набережной",
                sportType = SportType.OTHER,
                location = GeoPoint(56.31, 44.05),
                address = "Нижне-Волжская набережная, 24",
                sports = listOf(SpotSport.TABLE_TENNIS, SpotSport.ICE_HOCKEY),
                surface = " artificial_turf ",
            )
        val mapSpot = spot.copy(id = SpotId("community", spot.id.value), name = "Теннисный корт в парке", address = null)
        val review = Review("review-1", spot.id, "author-1", 5, "Удобная площадка")
        val dark = mutableStateOf(false)
        val wide = mutableStateOf(false)
        val showingDetails = mutableStateOf(false)
        val detailsState = mutableStateOf<HomeUiState>(HomeUiState.Content(listOf(mapSpot, spot)))
        val actions = mutableListOf<Pair<String, SpotId>>()
        val routeDestinations = mutableListOf<GeoPoint>()
        var backCount = 0
        var retries = 0

        compose.setContent {
            SportSpotTheme(darkTheme = dark.value) {
                val deviceDensity = LocalDensity.current.density
                val layoutDensity = if (wide.value) Density(deviceDensity * 0.6f, 1.3f) else Density(deviceDensity, 1.5f)
                CompositionLocalProvider(LocalDensity provides layoutDensity) {
                    Surface(
                        modifier = Modifier.size(if (wide.value) 600.dp else 360.dp, 640.dp).testTag("spot-polish-layout"),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        if (showingDetails.value) {
                            SpotDetailsScreen(
                                spotId = spot.id,
                                state = detailsState.value,
                                onRetry = { retries++ },
                                onBack = {
                                    backCount++
                                    showingDetails.value = false
                                },
                                onOpenReviews = { actions.add("reviews" to it) },
                                onShowMap = { actions.add("map" to it) },
                                favoriteAction = { current ->
                                    SportSpotButton(
                                        text = stringResource(R.string.favorites_add),
                                        onClick = { actions.add("favorite" to current.id) },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                },
                                reviewSummary = { ReviewScore(listOf(review)) },
                                onBuildRoute = { routeDestinations.add(it) },
                            )
                        } else {
                            Column(
                                modifier = Modifier.padding(SportSpotSpacing.xl),
                                verticalArrangement = Arrangement.spacedBy(SportSpotSpacing.md),
                            ) {
                                HomeSpotCard(spot, { showingDetails.value = true }, { actions.add("card favorite" to spot.id) })
                                VisibleMapSpotRow(
                                    spot = mapSpot,
                                    selected = true,
                                    onSelect = { actions.add("select" to mapSpot.id) },
                                    onOpen = { actions.add("details" to mapSpot.id) },
                                )
                            }
                        }
                    }
                }
            }
        }

        for (darkTheme in listOf(false, true)) {
            compose.runOnIdle { dark.value = darkTheme }
            val themeLabel = if (darkTheme) "dark" else "light"
            compose.onNodeWithText(spot.name).assertIsDisplayed()
            compose
                .onNodeWithText(checkNotNull(spot.address), useUnmergedTree = true)
                .assertIsDisplayed()
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { readLayout ->
                    val layouts = mutableListOf<TextLayoutResult>()
                    check(readLayout(layouts))
                    assertFalse(layouts.single().hasVisualOverflow)
                }
            compose.onNodeWithContentDescription("Добавить в избранное").assertIsDisplayed()
            compose.onNodeWithContentDescription("Подробнее о площадке «${mapSpot.name}»").assertIsDisplayed()
            capture("cards-$themeLabel.png")

            compose.onNodeWithText(spot.name).performClick()
            compose.onNodeWithText("Покрытие: искусственный газон").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Освещение:", substring = true).assertDoesNotExist()
            capture("details-$themeLabel.png")
            compose.onNodeWithText("из 5", substring = true).performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Показать на карте").performScrollTo().performClick()
            compose.onNodeWithText("Построить маршрут").performScrollTo().performClick()
            compose.onNodeWithText("Отзывы").performScrollTo().performClick()
            compose.onNodeWithText("Добавить в избранное").performScrollTo().performClick()
            compose.onNodeWithContentDescription("Назад").performClick()
        }
        compose.runOnIdle {
            assertEquals(List(2) { listOf("map" to spot.id, "reviews" to spot.id, "favorite" to spot.id) }.flatten(), actions)
            assertEquals(listOf(spot.location, spot.location), routeDestinations)
            assertEquals(2, backCount)
            wide.value = true
            detailsState.value = HomeUiState.Loading
        }
        compose.onNodeWithText(spot.name).assertIsDisplayed().performClick()
        compose.onNodeWithText("Загружаем площадки…").assertIsDisplayed()
        compose.runOnIdle { detailsState.value = HomeUiState.Error("Нет сети") }
        compose.onNodeWithText("Нет сети").assertIsDisplayed()
        compose.onNodeWithText("Повторить").performClick()
        compose.runOnIdle { detailsState.value = HomeUiState.Content(listOf(mapSpot)) }
        compose.onNodeWithText("Площадка не найдена").assertIsDisplayed()
        compose.onNodeWithText(mapSpot.name).assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(1, retries)
            detailsState.value = HomeUiState.Content(listOf(spot))
        }
        compose.onNodeWithText(checkNotNull(spot.address)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Показать на карте").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun buildsTheRouteFromTheSavedCommunitySpotCoordinates() {
        val owner = "owner"
        val spot =
            Spot(
                id = SpotId("community", "99ffb368-7b27-4a0d-bd15-3e91375426d1"),
                name = "Корт сообщества",
                sportType = SportType.TENNIS,
                location = GeoPoint(56.39, 44.15),
            )
        val favorites = FavoritesUiState.Content(owner, FavoriteList(listOf(Favorite(owner, spot.id, "2026-11-25", spot))))
        val state = favoriteDestinationState(favorites, spot.id, "Ошибка")
        var opened: GeoPoint? = null
        compose.setContent {
            SportSpotTheme {
                SpotDetailsScreen(spot.id, state, {}, {}, {}, {}, onBuildRoute = { opened = it })
            }
        }

        compose.onNodeWithText("Построить маршрут").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(spot.location, opened) }
    }

    private fun capture(fileName: String) {
        if (InstrumentationRegistry.getArguments().getString("spotPolishScreenshots") != "true") return
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val bitmap = compose.onNodeWithTag("spot-polish-layout").captureToImage().asAndroidBitmap()
        target.filesDir
            .resolve(fileName)
            .outputStream()
            .use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
