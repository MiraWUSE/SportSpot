package com.mirawuse.sportspot.data.mapper

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.mirawuse.sportspot.data.remote.dto.SpotDto
import com.mirawuse.sportspot.domain.model.spot.SportType
import com.mirawuse.sportspot.domain.model.spot.Spot
import com.mirawuse.sportspot.domain.model.spot.SpotId
import com.mirawuse.sportspot.domain.model.spot.SpotSport

internal fun Spot.toFavoriteSnapshot(): JsonObject {
    val snapshot =
        JsonObject().apply {
            addProperty("version", 1)
            addProperty("id", id.toCanonicalId())
            addProperty("name", name.trim().take(250))
            val sport =
                sports?.joinToString(";") { it.snapshotCode() }?.ifEmpty { "unspecified" }
                    ?: if (sportType == SportType.OTHER) "other" else sportType.toApiSport()
            addProperty("sport_type", sport)
            addProperty("latitude", location.latitude)
            addProperty("longitude", location.longitude)
            address?.let { addProperty("address", it.take(502)) }
            surface?.let { addProperty("surface", it.take(250)) }
            lighting?.let { addProperty("lighting", it) }
            equipment?.let { addProperty("equipment", it.take(250)) }
            photo?.takeIf { id.source == "osm" }?.let {
                add(
                    "photo",
                    JsonObject().apply {
                        addProperty("url", it.url)
                        addProperty("source_url", it.sourceUrl)
                        addProperty("author", it.author)
                        addProperty("license", it.license)
                        addProperty("license_url", it.licenseUrl)
                    },
                )
            }
        }
    require(snapshot.toString().encodeToByteArray().size <= 12_288)
    return snapshot
}

internal fun JsonElement?.toFavoriteSpot(
    id: SpotId,
    gson: Gson,
): Spot? {
    val snapshot = this?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
    if (snapshot["version"]?.toString() != "1" || snapshot.toString().encodeToByteArray().size > 12_288) return null
    return try {
        val dto = gson.fromJson(snapshot, SpotDto::class.java)
        if (dto.id != id.toCanonicalId()) return null
        val osm = id.source == "osm"
        dto
            .copy(
                source = id.source,
                osmType = id.value.substringBefore(':').takeIf { osm },
                osmId =
                    id.value
                        .substringAfter(':')
                        .toLongOrNull()
                        .takeIf { osm },
            ).toDomain()
    } catch (_: RuntimeException) {
        null
    }
}

private fun SpotSport.snapshotCode(): String =
    when (this) {
        SpotSport.TABLE_TENNIS -> "table_tennis"
        SpotSport.HOCKEY -> "hockey"
        SpotSport.ICE_HOCKEY -> "ice_hockey"
        SpotSport.OTHER -> "other"
        else -> category.toApiSport()
    }
