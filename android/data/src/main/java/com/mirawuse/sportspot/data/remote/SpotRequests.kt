package com.mirawuse.sportspot.data.remote

import com.mirawuse.sportspot.data.mapper.toApiSport
import com.mirawuse.sportspot.data.mapper.toCanonicalId
import com.mirawuse.sportspot.data.remote.error.NetworkError
import com.mirawuse.sportspot.data.remote.error.NetworkException
import com.mirawuse.sportspot.domain.model.location.GeoPoint
import com.mirawuse.sportspot.domain.model.spot.SportType
import com.mirawuse.sportspot.domain.model.spot.SpotId
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.math.BigDecimal

internal data class SpotSearchRequest(
    val latitude: String,
    val longitude: String,
    val radiusMeters: Int,
    val sport: String,
) {
    // The cache key uses the same values sent in the HTTP query.
    val cacheKey: String
        get() = "$latitude:$longitude:$radiusMeters:$sport"
}

internal suspend fun validatedCanonicalId(id: SpotId): String = validateRequest { id.toCanonicalId() }

internal suspend fun validatedSearchRequest(
    center: GeoPoint,
    radiusMeters: Int,
    sportType: SportType?,
): SpotSearchRequest =
    validateRequest {
        require(radiusMeters in 100..3000) { "Search radius must be between 100 and 3000 meters" }
        SpotSearchRequest(
            latitude = formatCoordinate(center.latitude),
            longitude = formatCoordinate(center.longitude),
            radiusMeters = radiusMeters,
            sport = sportType.toApiSport(),
        )
    }

private fun formatCoordinate(value: Double): String {
    val decimal = BigDecimal.valueOf(value).stripTrailingZeros()
    require(decimal.scale() <= 6) { "Search coordinates support at most six decimal places" }
    return decimal.toPlainString()
}

private suspend fun <T> validateRequest(block: () -> T): T {
    currentCoroutineContext().ensureActive()
    return try {
        block()
    } catch (failure: IllegalArgumentException) {
        currentCoroutineContext().ensureActive()
        throw NetworkException(NetworkError.InvalidRequest, failure)
    }
}
