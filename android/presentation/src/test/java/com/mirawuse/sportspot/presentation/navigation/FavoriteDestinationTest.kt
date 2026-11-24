package com.mirawuse.sportspot.presentation.navigation

import com.mirawuse.sportspot.domain.model.auth.AuthState
import com.mirawuse.sportspot.domain.model.favorite.Favorite
import com.mirawuse.sportspot.domain.model.favorite.FavoriteList
import com.mirawuse.sportspot.domain.model.location.GeoPoint
import com.mirawuse.sportspot.domain.model.spot.SportType
import com.mirawuse.sportspot.domain.model.spot.Spot
import com.mirawuse.sportspot.domain.model.spot.SpotId
import com.mirawuse.sportspot.domain.model.user.UserProfile
import com.mirawuse.sportspot.domain.model.user.UserRole
import com.mirawuse.sportspot.presentation.ui.account.FavoritesUiState
import com.mirawuse.sportspot.presentation.ui.account.visibleFor
import com.mirawuse.sportspot.presentation.ui.home.HomeUiState
import com.mirawuse.sportspot.presentation.ui.navigation.MapRoute
import com.mirawuse.sportspot.presentation.ui.navigation.SpotDetailsRoute
import com.mirawuse.sportspot.presentation.ui.navigation.favoriteDestinationState
import com.mirawuse.sportspot.presentation.ui.navigation.withOpenedMapSpot
import org.junit.Assert.assertEquals
import org.junit.Test

class FavoriteDestinationTest {
    private val spot = Spot(SpotId("osm", "way:42"), "Площадка", SportType.FOOTBALL, GeoPoint(56.4, 44.1))
    private val saved = FavoritesUiState.Content("owner", FavoriteList(listOf(Favorite("owner", spot.id, "2026-11-24", spot))))

    @Test
    fun favoriteDetailsAndMapUseTheSamePrivateSnapshot() {
        val details = SpotDetailsRoute(spot.id.source, spot.id.value, favorite = true)
        val map = MapRoute(spot.id.source, spot.id.value, favorite = true)
        val account = auth("owner")
        val state = saved.visibleFor(account)
        assertEquals(HomeUiState.Content(listOf(spot)), favoriteDestinationState(state, details.spotId, "Ошибка"))
        assertEquals(HomeUiState.Content(listOf(spot)), favoriteDestinationState(state, map.spotId, "Ошибка"))
    }

    @Test
    fun privateSnapshotIsHiddenIfTheGuestOrAnotherAccountRestoresTheRoute() {
        assertEquals(HomeUiState.Empty, favoriteDestinationState(saved.visibleFor(AuthState.Guest), spot.id, "Ошибка"))
        assertEquals(HomeUiState.Loading, favoriteDestinationState(saved.visibleFor(auth("other")), spot.id, "Ошибка"))
    }

    @Test
    fun mapDetailsUseTheOpenedSpotWithoutReplacingHomeOrMatchingOnlyTheValue() {
        val explored = spot.copy(id = SpotId("community", spot.id.value), location = GeoPoint(56.5, 44.2))
        val home = HomeUiState.Content(listOf(spot))
        assertEquals(HomeUiState.Content(listOf(explored, spot)), home.withOpenedMapSpot(explored.id, explored))
        assertEquals(home, home.withOpenedMapSpot(spot.id, explored))
        assertEquals(HomeUiState.Content(listOf(spot)), home)
    }

    private fun auth(owner: String): AuthState = AuthState.Authenticated(UserProfile(owner, "Имя", "fixture@example.test", UserRole.USER))
}
