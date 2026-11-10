package com.mirawuse.sportspot.domain.usecase.favorite

import com.mirawuse.sportspot.domain.model.favorite.Favorite
import com.mirawuse.sportspot.domain.model.spot.SpotId
import com.mirawuse.sportspot.domain.repository.FavoriteRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GetFavoritesUseCaseTest {
    private val favorite =
        Favorite(
            userId = "user-123",
            spotId = SpotId("osm", "12345"),
            addedAt = "2026-11-10T10:00:00Z",
        )

    @Test
    fun `возвращает избранные площадки`() =
        runTest {
            val repository = FakeFavoriteRepository(listOf(favorite))
            val useCase = GetFavoritesUseCase(repository)

            val result = useCase()

            assertEquals(listOf(favorite), result)
            assertEquals(1, repository.getFavoritesCalls)
        }

    @Test
    fun `возвращает пустой список если избранного нет`() =
        runTest {
            val repository = FakeFavoriteRepository(emptyList())
            val useCase = GetFavoritesUseCase(repository)

            val result = useCase()

            assertTrue(result.isEmpty())
            assertEquals(1, repository.getFavoritesCalls)
        }

    private class FakeFavoriteRepository(
        private val favorites: List<Favorite>,
    ) : FavoriteRepository {
        var getFavoritesCalls = 0

        override suspend fun getFavorites(): List<Favorite> {
            getFavoritesCalls++
            return favorites
        }

        override suspend fun addFavorite(spotId: SpotId): Favorite = error("Метод не используется в этом тесте")

        override suspend fun removeFavorite(spotId: SpotId) {
            error("Метод не используется в этом тесте")
        }

        override suspend fun isFavorite(spotId: SpotId): Boolean = error("Метод не используется в этом тесте")
    }
}
