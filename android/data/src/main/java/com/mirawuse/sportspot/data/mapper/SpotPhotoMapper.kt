package com.mirawuse.sportspot.data.mapper

import com.google.gson.JsonElement
import com.mirawuse.sportspot.domain.model.spot.SpotPhoto

internal fun JsonElement?.toSpotPhoto(): SpotPhoto? {
    val fields = this?.takeIf { it.isJsonObject }?.asJsonObject ?: return null

    fun text(name: String): String? = fields.get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString

    return verifiedSpotPhoto(text("url"), text("source_url"), text("author"), text("license"), text("license_url"))
}

internal fun verifiedSpotPhoto(
    url: String?,
    sourceUrl: String?,
    author: String?,
    license: String?,
    licenseUrl: String?,
): SpotPhoto? {
    if (url == null || sourceUrl == null || author == null || license == null || licenseUrl == null) return null
    return try {
        SpotPhoto(url, sourceUrl, author, license, licenseUrl)
    } catch (_: IllegalArgumentException) {
        null
    }
}
