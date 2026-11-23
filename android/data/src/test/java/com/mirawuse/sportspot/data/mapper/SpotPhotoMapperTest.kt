package com.mirawuse.sportspot.data.mapper

import com.google.gson.Gson
import com.mirawuse.sportspot.data.remote.dto.SpotSearchResponseDto
import com.mirawuse.sportspot.domain.model.spot.Spot
import com.mirawuse.sportspot.domain.model.spot.SpotPhoto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpotPhotoMapperTest {
    private val validPhoto =
        SpotPhoto(
            url = "https://thumb.wikimedia.org/wikipedia/commons/thumb/a/a1/Test.jpg/500px-Test.jpg",
            sourceUrl = "https://commons.wikimedia.org/wiki/File:Test.jpg",
            author = "Test Author",
            license = "CC BY-SA 4.0",
            licenseUrl = "https://creativecommons.org/licenses/by-sa/4.0/",
        )
    private val photoJson =
        """
        {
          "url":"${validPhoto.url}", "source_url":"${validPhoto.sourceUrl}",
          "author":"${validPhoto.author}", "license":"${validPhoto.license}", "license_url":"${validPhoto.licenseUrl}"
        }
        """.trimIndent()

    @Test
    fun `new JSON maps all verified photo fields and old items stay valid`() {
        val spots = decode(photoJson)
        assertEquals(2, spots.size)
        assertEquals(validPhoto, spots.first().photo)
        assertNull(spots[1].photo)
    }

    @Test
    fun `null invalid JSON types or unsafe metadata affect only the optional photo`() {
        val invalid =
            listOf(
                "null",
                "[]",
                "{}",
                photoJson.replace("\"Test Author\"", "43"),
                photoJson.replace("Test Author", "<b>Author</b>"),
                photoJson.replace("thumb.wikimedia.org", "thumb.wikimedia.org.example.com"),
                photoJson.replace("https://commons.wikimedia.org", "http://commons.wikimedia.org"),
                photoJson.replace("CC BY-SA 4.0", "CC BY-NC 4.0"),
                photoJson.replace("creativecommons.org", "example.test"),
            )
        val expected = decode(null)
        for (value in invalid) assertEquals(expected, decode(value))
    }

    private fun decode(photo: String?): List<Spot> {
        val field = photo?.let { ",\"photo\":$it" }.orEmpty()
        val json = """
            {
              "spots":[
                {"id":"osm:node:10","source":"osm","osm_type":"node","osm_id":10,
                 "name":"Court","sport_type":"basketball","latitude":56,"longitude":44$field},
                {"id":"osm:node:11","source":"osm","osm_type":"node","osm_id":11,
                 "name":"Second court","sport_type":"soccer","latitude":56,"longitude":44}
              ],
              "count":2,"source":"Overpass API","attribution":"© OpenStreetMap contributors","cache":"miss"
            }
        """
        return Gson().fromJson(json, SpotSearchResponseDto::class.java).toDomain()
    }
}
