package com.mirawuse.sportspot.domain.model.photo

import com.mirawuse.sportspot.domain.model.spot.SpotId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PhotoTargetTest {
    @Test
    fun canonicalTargetsAndLimits() {
        assertEquals("osm:relation:42", PhotoTarget.spot(SpotId("osm", "relation:42")).id)
        assertEquals(5, PhotoTargetType.SPOT.limit)
        val id = "11111111-1111-4111-8111-111111111111"
        assertEquals("community:$id", PhotoTarget.spot(SpotId("community", id)).id)
        assertEquals(3, PhotoTarget.review(id).type.limit)
        assertEquals(3, PhotoTarget.proposal(id).type.limit)
        assertThrows(IllegalArgumentException::class.java) { PhotoTarget.spot(SpotId("osm", "way:9223372036854775808")) }
        assertThrows(IllegalArgumentException::class.java) { PhotoTarget.review("https://outside.test/") }
        assertThrows(IllegalArgumentException::class.java) { PhotoAttachment(id, ByteArray(MAX_USER_PHOTO_BYTES + 1)) }
    }
}
