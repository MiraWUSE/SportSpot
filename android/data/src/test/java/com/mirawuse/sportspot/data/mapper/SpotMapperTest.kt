package com.mirawuse.sportspot.data.mapper

import com.google.gson.JsonParseException
import com.mirawuse.sportspot.data.remote.NetworkClient
import com.mirawuse.sportspot.data.remote.dto.SpotDto
import com.mirawuse.sportspot.data.remote.dto.SpotSearchResponseDto
import com.mirawuse.sportspot.domain.model.location.GeoPoint
import com.mirawuse.sportspot.domain.model.spot.SportType
import com.mirawuse.sportspot.domain.model.spot.Spot
import com.mirawuse.sportspot.domain.model.spot.SpotId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class SpotMapperTest {
    private val gson = NetworkClient.createGson()

    @Test
    fun `OpenAPI search fixture maps snake case JSON into the complete Domain model`() {
        val response = searchFixture()
        val dto = response.spots!!.single()!!

        assertEquals("basketball", dto.sportType)
        assertEquals("node", dto.osmType)
        assertEquals(12345L, dto.osmId)
        assertEquals(
            listOf(
                Spot(
                    id = SpotId("osm", "node:12345"),
                    name = "Спортивная площадка",
                    sportType = SportType.BASKETBALL,
                    location = GeoPoint(56.3269, 44.0059),
                    surface = "asphalt",
                    lighting = null,
                ),
            ),
            response.toDomain(),
        )
    }

    @Test
    fun `node way and relation preserve their identity and round trip`() {
        listOf("node" to 12345L, "way" to 456L, "relation" to 789L).forEach { (type, id) ->
            val dto = validSpot().copy(id = "osm:$type:$id", osmType = type, osmId = id)

            val mapped = dto.toDomain()

            assertEquals(SpotId("osm", "$type:$id"), mapped.id)
            assertEquals(dto.id, mapped.id.toCanonicalId())
        }
    }

    @Test
    fun `unknown extra JSON fields are ignored`() {
        val json = fixture("search-spots.json").replace("\"count\": 1", "\"future_field\": true, \"count\": 1")

        assertEquals(searchFixture().toDomain(), gson.fromJson(json, SpotSearchResponseDto::class.java).toDomain())
    }

    @Test
    fun `unknown and compound sports map to OTHER without changing identity`() {
        listOf("unspecified", "baseball", "soccer;basketball").forEach { sport ->
            val mapped = validSpot().copy(sportType = sport).toDomain()

            assertEquals(SportType.OTHER, mapped.sportType)
            assertEquals(SpotId("osm", "node:12345"), mapped.id)
        }
    }

    @Test
    fun `all supported sports map in both directions`() {
        val sports =
            mapOf(
                "soccer" to SportType.FOOTBALL,
                "basketball" to SportType.BASKETBALL,
                "volleyball" to SportType.VOLLEYBALL,
                "tennis" to SportType.TENNIS,
                "fitness" to SportType.WORKOUT,
            )

        sports.forEach { (raw, domain) ->
            assertEquals(domain, validSpot().copy(sportType = raw).toDomain().sportType)
            assertEquals(raw, domain.toApiSport())
        }
        assertEquals("all", null.toApiSport())
    }

    @Test
    fun `unsupported outgoing sport does not silently become all`() {
        assertThrows(IllegalArgumentException::class.java) { SportType.OTHER.toApiSport() }
    }

    @Test
    fun `nullable surface and lighting remain null while false remains false`() {
        val unknown = validSpot().copy(surface = null, lighting = null).toDomain()
        val unlit = validSpot().copy(lighting = false).toDomain()
        val lit = validSpot().copy(lighting = true).toDomain()

        assertNull(unknown.surface)
        assertNull(unknown.lighting)
        assertEquals(false, unlit.lighting)
        assertEquals(true, lit.lighting)
    }

    @Test
    fun `coordinate boundaries are valid`() {
        assertEquals(GeoPoint(-90.0, -180.0), validSpot().copy(latitude = -90.0, longitude = -180.0).toDomain().location)
        assertEquals(GeoPoint(90.0, 180.0), validSpot().copy(latitude = 90.0, longitude = 180.0).toDomain().location)
    }

    @Test
    fun `missing null out of range and nonfinite coordinates fail mapping`() {
        listOf(
            validSpot().copy(latitude = null),
            validSpot().copy(longitude = null),
            validSpot().copy(latitude = 90.000001),
            validSpot().copy(longitude = -180.000001),
            validSpot().copy(latitude = Double.NaN),
            validSpot().copy(longitude = Double.POSITIVE_INFINITY),
        ).forEach(::assertInvalid)
    }

    @Test
    fun `absent JSON coordinates do not become zero`() {
        val json = """{"id":"osm:node:12345","source":"osm","osm_type":"node","osm_id":12345,"name":"Spot","sport_type":"soccer"}"""
        val dto = gson.fromJson(json, SpotDto::class.java)

        assertNull(dto.latitude)
        assertNull(dto.longitude)
        assertInvalid(dto)
    }

    @Test
    fun `missing or blank required fields fail mapping`() {
        listOf(
            SpotDto(),
            validSpot().copy(id = null),
            validSpot().copy(source = null),
            validSpot().copy(name = null),
            validSpot().copy(name = "  "),
            validSpot().copy(sportType = null),
            validSpot().copy(sportType = ""),
        ).forEach(::assertInvalid)
    }

    @Test
    fun `invalid and noncanonical identifiers fail incoming and outgoing mapping`() {
        listOf(
            "osm:12345",
            "osm:area:12345",
            "osm:node:00123",
            "osm:node:123.5",
            "osm:node:9223372036854775808",
            "osm:node:12345:extra",
            "unknown:12345",
            "community:not-a-uuid",
            "community:1-1-1-1-1",
        ).forEach { raw ->
            assertInvalid(validSpot().copy(id = raw))
            val id = SpotId(raw.substringBefore(':'), raw.substringAfter(':'))
            assertThrows(IllegalArgumentException::class.java) { id.toCanonicalId() }
        }
    }

    @Test
    fun `identifier attributes must agree with canonical ID`() {
        listOf(
            validSpot().copy(source = "community"),
            validSpot().copy(osmType = "way"),
            validSpot().copy(osmType = null),
            validSpot().copy(osmId = 54321),
            validSpot().copy(osmId = null),
            validSpot().copy(id = "community:33333333-3333-4333-8333-333333333333", source = "community"),
        ).forEach(::assertInvalid)
    }

    @Test
    fun `missing null inconsistent or oversized search lists fail mapping`() {
        val response = searchFixture()
        listOf(
            response.copy(spots = null),
            response.copy(spots = listOf(null)),
            response.copy(count = null),
            response.copy(count = 0),
            response.copy(count = -1),
            response.copy(spots = List(101) { validSpot() }, count = 101),
        ).forEach { invalid ->
            assertThrows(SpotMappingException::class.java) { invalid.toDomain() }
        }
    }

    @Test
    fun `missing or invalid search metadata fails mapping`() {
        val response = searchFixture()
        listOf(
            response.copy(source = null),
            response.copy(source = "unknown"),
            response.copy(attribution = null),
            response.copy(attribution = ""),
            response.copy(cache = null),
            response.copy(cache = "unknown"),
        ).forEach { invalid ->
            assertThrows(SpotMappingException::class.java) { invalid.toDomain() }
        }
        assertEquals(response.toDomain(), response.copy(cache = "hit").toDomain())
    }

    @Test
    fun `search does not accept a community row as an OSM result`() {
        val community =
            validSpot().copy(
                id = "community:33333333-3333-4333-8333-333333333333",
                source = "community",
                osmType = null,
                osmId = null,
            )

        assertThrows(SpotMappingException::class.java) { searchFixture().copy(spots = listOf(community)).toDomain() }
    }

    @Test
    fun `malformed JSON is rejected by the configured strict converter`() {
        assertThrows(JsonParseException::class.java) { gson.fromJson("{spots:[]}", SpotSearchResponseDto::class.java) }
        assertThrows(JsonParseException::class.java) { gson.fromJson("{", SpotDto::class.java) }
    }

    @Test
    fun `lighting accepts JSON booleans and null but never coerces strings into false`() {
        listOf("\"true\"", "\"false\"", "\"unknown\"", "1").forEach { value ->
            val json = fixture("search-spots.json").replace("\"lighting\": null", "\"lighting\": $value")
            assertThrows(JsonParseException::class.java) { gson.fromJson(json, SpotSearchResponseDto::class.java) }
        }
    }

    @Test
    fun `mapping failure message does not expose server field contents`() {
        val error =
            assertThrows(SpotMappingException::class.java) {
                validSpot().copy(id = "sensitive-response-value").toDomain()
            }

        assertEquals("Invalid spot response", error.message)
        assertNull(error.cause)
    }

    private fun validSpot(): SpotDto = searchFixture().spots!!.single()!!

    private fun searchFixture(): SpotSearchResponseDto = gson.fromJson(fixture("search-spots.json"), SpotSearchResponseDto::class.java)

    private fun assertInvalid(dto: SpotDto) {
        assertThrows(SpotMappingException::class.java) { dto.toDomain() }
    }

    // Contract fixtures, not live responses: search is verbatim OpenAPI; REST adds full SQL/schema fields.
    private fun fixture(name: String): String = javaClass.getResource("/fixtures/$name")!!.readText()
}
