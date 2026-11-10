package com.mirawuse.sportspot.data.mapper

import com.mirawuse.sportspot.data.remote.dto.SpotDto
import com.mirawuse.sportspot.data.remote.dto.SpotSearchResponseDto
import com.mirawuse.sportspot.domain.model.location.GeoPoint
import com.mirawuse.sportspot.domain.model.spot.Spot

fun SpotDto.toDomain(): Spot {
    val spotId = id?.let(::parseCanonicalSpotId) ?: invalidResponse()
    if (source != spotId.source) invalidResponse()
    if (source == "osm") {
        if (osmType != spotId.value.substringBefore(':') ||
            osmId != spotId.value.substringAfter(':').toLong()
        ) {
            invalidResponse()
        }
    } else if (osmType != null || osmId != null) {
        invalidResponse()
    }

    val spotName = name?.takeIf { it.isNotBlank() } ?: invalidResponse()
    val sport = sportType?.takeIf { it.isNotBlank() } ?: invalidResponse()
    val lat = latitude ?: invalidResponse()
    val lon = longitude ?: invalidResponse()
    if (!lat.isFinite() ||
        lat !in -90.0..90.0 ||
        !lon.isFinite() ||
        lon !in -180.0..180.0
    ) {
        invalidResponse()
    }

    return Spot(
        id = spotId,
        name = spotName,
        sportType = sport.toDomainSport(),
        location = GeoPoint(lat, lon),
        surface = surface,
        lighting = lighting,
    )
}

fun SpotSearchResponseDto.toDomain(): List<Spot> {
    val items = spots ?: invalidResponse()
    if (count != items.size ||
        items.size > 100 ||
        source != "Overpass API" ||
        attribution.isNullOrBlank() ||
        cache !in setOf("hit", "miss")
    ) {
        invalidResponse()
    }
    return items.map { item ->
        if (item == null || item.source != "osm") invalidResponse()
        item.toDomain()
    }
}

private fun invalidResponse(): Nothing = throw SpotMappingException()
