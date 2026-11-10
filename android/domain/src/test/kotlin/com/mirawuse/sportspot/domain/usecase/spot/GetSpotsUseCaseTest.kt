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

class GetSpotsUseCaseTest {
    @Test
    fun `возвращает список площадок из репозитория`() =
        runTest {
            val spots =
                listOf(
                    Spot(
                        id = SpotId("osm", "123"),
                        name = "Футбольное поле",
                        sportType = SportType.FOOTBALL,
                        location = GeoPoint(56.3269, 44.0059),
                    ),
                )

            val repository = FakeSpotRepository(spots)
            val useCase = GetSpotsUseCase(repository)

            val result = useCase()

            assertEquals(spots, result)
            assertEquals(1, repository.getSpotsCalls)
        }

    @Test
    fun `возвращает пустой список если площадок нет`() =
        runTest {
            val repository = FakeSpotRepository(emptyList())
            val useCase = GetSpotsUseCase(repository)

            val result = useCase()

            assertTrue(result.isEmpty())
        }

    private class FakeSpotRepository(
        private val spots: List<Spot>,
    ) : SpotRepository {
        var getSpotsCalls = 0

        override suspend fun getSpots(): List<Spot> {
            getSpotsCalls++
            return spots
        }

        override suspend fun getSpotById(id: SpotId): Spot? = null

        override suspend fun searchNearby(
            center: GeoPoint,
            radiusMeters: Int,
            sportType: SportType?,
        ): List<Spot> = emptyList()
    }
}
