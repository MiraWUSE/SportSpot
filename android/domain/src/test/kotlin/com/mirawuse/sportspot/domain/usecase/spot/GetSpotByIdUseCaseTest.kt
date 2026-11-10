package com.mirawuse.sportspot.domain.usecase.spot

import com.mirawuse.sportspot.domain.model.location.GeoPoint
import com.mirawuse.sportspot.domain.model.spot.SportType
import com.mirawuse.sportspot.domain.model.spot.Spot
import com.mirawuse.sportspot.domain.model.spot.SpotId
import com.mirawuse.sportspot.domain.repository.SpotRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GetSpotByIdUseCaseTest {
    private val spot =
        Spot(
            id = SpotId("osm", "12345"),
            name = "Футбольное поле",
            sportType = SportType.FOOTBALL,
            location = GeoPoint(56.3269, 44.0059),
        )

    @Test
    fun `возвращает площадку по существующему идентификатору`() =
        runTest {
            val repository = FakeSpotRepository(spot)
            val useCase = GetSpotByIdUseCase(repository)

            val result = useCase(spot.id)

            assertEquals(spot, result)
            assertEquals(spot.id, repository.requestedId)
        }

    @Test
    fun `возвращает null если площадка отсутствует`() =
        runTest {
            val repository = FakeSpotRepository(null)
            val useCase = GetSpotByIdUseCase(repository)

            val result = useCase(SpotId("osm", "99999"))

            assertNull(result)
        }

    @Test
    fun `возвращает null при другом идентификаторе`() =
        runTest {
            val repository = FakeSpotRepository(spot)
            val useCase = GetSpotByIdUseCase(repository)

            val result = useCase(SpotId("osm", "99999"))

            assertNull(result)
        }

    private class FakeSpotRepository(
        private val spot: Spot?,
    ) : SpotRepository {
        var requestedId: SpotId? = null

        override suspend fun getSpots(): List<Spot> = emptyList()

        override suspend fun getSpotById(id: SpotId): Spot? {
            requestedId = id
            return spot?.takeIf { it.id == id }
        }

        override suspend fun searchNearby(
            center: GeoPoint,
            radiusMeters: Int,
            sportType: SportType?,
        ): List<Spot> = emptyList()
    }
}
