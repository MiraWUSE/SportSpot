package com.mirawuse.sportspot.data.mapper

import com.mirawuse.sportspot.domain.model.spot.SpotId

private val osmIdPattern = Regex("osm:(node|way|relation):(-?(?:0|[1-9][0-9]*))")
private val communityIdPattern =
    Regex("community:[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")

internal fun parseCanonicalSpotId(value: String): SpotId? {
    val osm = osmIdPattern.matchEntire(value)
    if (osm != null) {
        val number = osm.groupValues[2].toLongOrNull() ?: return null
        if (number.toString() != osm.groupValues[2]) return null
        return SpotId("osm", value.substringAfter(':'))
    }
    if (communityIdPattern.matches(value)) {
        return SpotId("community", value.substringAfter(':'))
    }
    return null
}

/** Retrofit performs the query encoding; do not pre-encode this canonical ID. */
fun SpotId.toCanonicalId(): String {
    val canonical = "$source:$value"
    require(parseCanonicalSpotId(canonical) == this) { "Unsupported spot identifier" }
    return canonical
}
