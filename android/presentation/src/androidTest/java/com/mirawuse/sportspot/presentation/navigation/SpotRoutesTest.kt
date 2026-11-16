package com.mirawuse.sportspot.presentation.navigation

import android.os.Bundle
import androidx.navigation.NavHostController
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.composable
import androidx.navigation.createGraph
import androidx.navigation.toRoute
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mirawuse.sportspot.domain.model.spot.SpotId
import com.mirawuse.sportspot.presentation.ui.navigation.MapRoute
import com.mirawuse.sportspot.presentation.ui.navigation.ReviewFormRoute
import com.mirawuse.sportspot.presentation.ui.navigation.ReviewsRoute
import com.mirawuse.sportspot.presentation.ui.navigation.SpotDetailsRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.FutureTask

@RunWith(AndroidJUnit4::class)
class SpotRoutesTest {
    @Test
    fun detailsRoutePreservesOsmTypesAndCommunityUuid() =
        onMain {
            val nav = controller()
            val ids =
                listOf(
                    SpotId("osm", "node:123"),
                    SpotId("osm", "way:123"),
                    SpotId("osm", "relation:123"),
                    SpotId("community", "33333333-3333-4333-8333-333333333333"),
                )
            for (id in ids) {
                nav.navigate(SpotDetailsRoute(id.source, id.value))

                assertEquals(id, nav.currentBackStackEntry?.toRoute<SpotDetailsRoute>()?.spotId)
                assertTrue(nav.popBackStack())
                assertEquals("home", nav.currentDestination?.route)
            }
        }

    @Test
    fun mapRouteCanOpenWithoutFocusAndKeepFocusOnBack() =
        onMain {
            val nav = controller()
            nav.navigate(MapRoute())
            assertNull(nav.currentBackStackEntry?.toRoute<MapRoute>()?.spotId)
            nav.popBackStack()

            val id = SpotId("osm", "way:456")
            nav.navigate(MapRoute(id.source, id.value))
            nav.navigate(SpotDetailsRoute(id.source, id.value))

            assertTrue(nav.popBackStack())
            assertEquals(id, nav.currentBackStackEntry?.toRoute<MapRoute>()?.spotId)
        }

    @Test
    fun restoringNavigationStateKeepsTheDetailsAndMapIds() =
        onMain {
            val nav = controller()
            val id = SpotId("osm", "node:123")
            nav.navigate(MapRoute(id.source, id.value))
            nav.navigate(SpotDetailsRoute(id.source, id.value))

            val restored = controller(savedState = nav.saveState())

            assertEquals(id, restored.currentBackStackEntry?.toRoute<SpotDetailsRoute>()?.spotId)
            assertTrue(restored.popBackStack())
            assertEquals(id, restored.currentBackStackEntry?.toRoute<MapRoute>()?.spotId)
        }

    @Test
    fun restoredReviewFormAndBackKeepTheExactSpotId() =
        onMain {
            val nav = controller()
            val id = SpotId("osm", "way:151302848")
            nav.navigate(SpotDetailsRoute(id.source, id.value))
            nav.navigate(ReviewsRoute(id.source, id.value))
            nav.navigate(ReviewFormRoute(id.source, id.value))
            val restored = controller(savedState = nav.saveState())

            assertEquals(id, restored.currentBackStackEntry?.toRoute<ReviewFormRoute>()?.spotId)
            assertTrue(restored.popBackStack())
            assertEquals(id, restored.currentBackStackEntry?.toRoute<ReviewsRoute>()?.spotId)
            assertTrue(restored.popBackStack())
            assertEquals(id, restored.currentBackStackEntry?.toRoute<SpotDetailsRoute>()?.spotId)
        }

    private fun controller(savedState: Bundle? = null): NavHostController =
        NavHostController(InstrumentationRegistry.getInstrumentation().targetContext).apply {
            navigatorProvider.addNavigator(ComposeNavigator())
            restoreState(savedState)
            graph =
                createGraph(startDestination = "home") {
                    composable("home") {}
                    composable<MapRoute> {}
                    composable<SpotDetailsRoute> {}
                    composable<ReviewsRoute> {}
                    composable<ReviewFormRoute> {}
                }
        }

    private fun onMain(block: () -> Unit) {
        val check = FutureTask(block, Unit)
        InstrumentationRegistry.getInstrumentation().runOnMainSync(check)
        check.get()
    }
}
