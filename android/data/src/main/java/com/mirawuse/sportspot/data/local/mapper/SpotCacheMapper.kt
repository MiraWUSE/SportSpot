package com.mirawuse.sportspot.data.local.mapper

import com.mirawuse.sportspot.data.local.entity.SpotColumns
import com.mirawuse.sportspot.data.mapper.parseCanonicalSpotId
import com.mirawuse.sportspot.data.mapper.toCanonicalId
import com.mirawuse.sportspot.domain.model.location.GeoPoint
import com.mirawuse.sportspot.domain.model.spot.SportType
import com.mirawuse.sportspot.domain.model.spot.Spot

internal fun Spot.toCacheColumns(): SpotColumns =
    SpotColumns(
        canonicalId = id.toCanonicalId(),
        name = name,
        sportType = sportType.name,
        latitude = location.latitude,
        longitude = location.longitude,
        surface = surface,
        lighting = lighting,
    )

internal fun SpotColumns.toDomain(): Spot =
    Spot(
        id = requireNotNull(parseCanonicalSpotId(canonicalId)) { "Invalid cached spot identifier" },
        name = name,
        sportType = SportType.valueOf(sportType),
        location = GeoPoint(latitude, longitude),
        surface = surface,
        lighting = lighting,
    )
