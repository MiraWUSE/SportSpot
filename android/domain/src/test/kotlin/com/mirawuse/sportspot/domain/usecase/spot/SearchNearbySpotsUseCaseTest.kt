
package com.mirawuse.sportspot.domain.usecase.spot

import com.mirawuse.sportspot.domain.model.location.GeoPoint
import com.mirawuse.sportspot.domain.model.spot.SportType
import com.mirawuse.sportspot.domain.model.spot.Spot
import com.mirawuse.sportspot.domain.model.spot.SpotId
import com.mirawuse.sportspot.domain.repository.SpotRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchNearbySpotsUseCaseTest {
    private val center = GeoPoint(56.3269, 44.0059)

    @Test
    fun `передает параметры поиска в репозиторий`() =
        runTest {
            val spot =
                Spot(
                    id = SpotId("osm", "123"),
                    name = "Футбольное поле",
                    sportType = SportType.FOOTBALL,
                    location = center,
                )

            val repository = FakeSpotRepository(listOf(spot))
            val useCase = SearchNearbySpotsUseCase(repository)

            val result = useCase(center, 1500, SportType.FOOTBALL)

            assertEquals(listOf(spot), result)
            assertEquals(
                SearchCall(center, 1500, SportType.FOOTBALL),
                repository.calls.single(),
            )
        }

    @Test
    fun `использует радиус по умолчанию`() =
        runTest {
            val repository = FakeSpotRepository()
            val useCase = SearchNearbySpotsUseCase(repository)

            useCase(center)

            assertEquals(
                SearchCall(center, 1000, null),
                repository.calls.single(),
            )
        }

    @Test
    fun `граничные значения радиуса допустимы`() =
        runTest {
            val repository = FakeSpotRepository()
            val useCase = SearchNearbySpotsUseCase(repository)

            useCase(center, 100)
            useCase(center, 3000)

            assertEquals(2, repository.calls.size)
            assertEquals(100, repository.calls[0].radiusMeters)
            assertEquals(3000, repository.calls[1].radiusMeters)
        }

    @Test
    fun `радиус меньше 100 вызывает ошибку`() =
        runTest {
            val repository = FakeSpotRepository()
            val useCase = SearchNearbySpotsUseCase(repository)

            val result =
                runCatching {
                    useCase(center, 99)
                }

            assertTrue(result.exceptionOrNull() is IllegalArgumentException)
            assertTrue(repository.calls.isEmpty())
        }

    @Test
    fun `радиус больше 3000 вызывает ошибку`() =
        runTest {
            val repository = FakeSpotRepository()
            val useCase = SearchNearbySpotsUseCase(repository)

            val result =
                runCatching {
                    useCase(center, 3001)
                }

            assertTrue(result.exceptionOrNull() is IllegalArgumentException)
            assertTrue(repository.calls.isEmpty())
        }

    @Test
    fun `возвращает пустой список если площадок нет`() =
        runTest {
            val repository = FakeSpotRepository()
            val useCase = SearchNearbySpotsUseCase(repository)

            val result = useCase(center)

            assertTrue(result.isEmpty())
        }

    private data class SearchCall(
        val center: GeoPoint,
        val radiusMeters: Int,
        val sportType: SportType?,
    )

    private class FakeSpotRepository(
        private val spots: List<Spot> = emptyList(),
    ) : SpotRepository {
        val calls = mutableListOf<SearchCall>()

        override suspend fun getSpots(): List<Spot> = emptyList()

        override suspend fun getSpotById(id: SpotId): Spot? = null

        override suspend fun searchNearby(
            center: GeoPoint,
            radiusMeters: Int,
            sportType: SportType?,
        ): List<Spot> {
            calls.add(SearchCall(center, radiusMeters, sportType))
            return spots
        }
    }
}
