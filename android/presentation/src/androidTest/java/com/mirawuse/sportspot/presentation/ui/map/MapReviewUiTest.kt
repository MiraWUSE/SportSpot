package com.mirawuse.sportspot.presentation.ui.map

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import com.mirawuse.sportspot.domain.model.location.GeoPoint
import com.mirawuse.sportspot.domain.model.spot.SportType
import com.mirawuse.sportspot.domain.model.spot.Spot
import com.mirawuse.sportspot.domain.model.spot.SpotId
import com.mirawuse.sportspot.presentation.ui.home.HomeMapPreview
import com.mirawuse.sportspot.presentation.ui.home.HomeUiState
import com.mirawuse.sportspot.presentation.ui.theme.SportSpotTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.maplibre.android.MapLibre

class MapReviewUiTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()
    private val showing = mutableStateOf(true)
    private val center = GeoPoint(56.3269, 44.0059)
    private val spots =
        List(56) { index ->
            Spot(
                SpotId(if (index == 55) "sportspot" else "osm", if (index == 55) "node:1" else "node:${index + 1}"),
                "Площадка",
                if (index == 55) SportType.FOOTBALL else SportType.BASKETBALL,
                center.copy(latitude = center.latitude + index * 0.00001),
            )
        }

    @Before
    fun disableExternalTiles() {
        compose.activityRule.scenario.onActivity {
            OsmMapTiles.initialize(it)
            MapLibre.setConnected(false)
        }
    }

    @After
    fun disposeBeforeRestoringConnectivity() {
        compose.runOnIdle { showing.value = false }
        compose.waitForIdle()
        compose.activityRule.scenario.onActivity { MapLibre.setConnected(null) }
    }

    @Test
    fun pickerScrollsDuplicateNamesAndSelectsFullSpotIdForTheCardAndDetails() {
        var opened: SpotId? = null
        compose.setContent {
            SportSpotTheme {
                if (showing.value) {
                    MapScreen(HomeUiState.Content(spots), center, "Нижний Новгород", SpotId("osm", "missing"), {}, {
                        opened =
                            it
                    })
                }
            }
        }
        compose.onNodeWithText("Площадка отсутствует в текущих результатах. Можно выбрать другую.").assertIsDisplayed()
        compose.onNodeWithText("Выбрать площадку").assertHasClickAction().performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(55)
        compose
            .onNode(hasText("56. Площадка") and hasText("Футбол"))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
            .assertHasClickAction()
            .performClick()
        compose.onNodeWithText("Футбол").assertIsDisplayed()
        compose.onNodeWithText("Площадка отсутствует в текущих результатах. Можно выбрать другую.").assertDoesNotExist()
        compose.onNodeWithText("Подробнее").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(SpotId("sportspot", "node:1"), opened) }
        compose
            .onNodeWithText("Выбрать площадку")
            .performScrollTo()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Выбрана площадка: 56. Площадка"))
            .performClick()
        compose.onNodeWithText("56. Площадка").assertIsSelected()
        compose.onNodeWithText("Закрыть").performClick()
    }

    @Test
    fun emptyResultsCannotOpenThePicker() {
        compose.setContent {
            SportSpotTheme {
                if (showing.value) MapScreen(HomeUiState.Empty, center, "Нижний Новгород", null, {}, {})
            }
        }
        compose.onNodeWithText("Выбрать площадку").assertIsNotEnabled()
        compose.onNode(hasScrollToIndexAction()).assertDoesNotExist()
    }

    @Test
    fun previewKeepsTheFullResultCountAndTheMapAction() {
        var opened = 0
        compose.setContent {
            SportSpotTheme {
                if (showing.value) HomeMapPreview(spots, center) { opened++ }
            }
        }
        compose.onNodeWithText("Найдено 56").assertIsDisplayed()
        compose.onNodeWithText("Карта →").assertHasClickAction().performClick()
        compose.runOnIdle { assertEquals(1, opened) }
    }
}
