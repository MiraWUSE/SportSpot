package com.mirawuse.sportspot.domain.model.spot

import java.net.URI

data class SpotPhoto(
    val url: String,
    val sourceUrl: String,
    val author: String,
    val license: String,
    val licenseUrl: String,
) {
    init {
        require(
            isHttpsResource(url, "thumb.wikimedia.org", "/wikipedia/commons/thumb/") ||
                isHttpsResource(url, "upload.wikimedia.org", "/wikipedia/commons/thumb/"),
        )
        require(isHttpsResource(sourceUrl, "commons.wikimedia.org", "/wiki/File:"))
        require(
            author.isNotBlank() &&
                author.length <= 300 &&
                author.none { it in "<>" || it.isISOControl() || Character.getType(it) == Character.FORMAT.toInt() },
        )
        require(!author.contains("://"))
        val path =
            when (license) {
                "CC BY 4.0" -> "by"
                "CC BY-SA 4.0" -> "by-sa"
                else -> throw IllegalArgumentException("Unsupported photo license")
            }
        require(licenseUrl == "https://creativecommons.org/licenses/$path/4.0/")
    }
}

private fun isHttpsResource(
    value: String,
    host: String,
    pathPrefix: String,
): Boolean {
    if (value.length > 2048 || value.any { it.isWhitespace() || it.isISOControl() }) return false
    val uri = runCatching { URI(value) }.getOrNull() ?: return false
    val path = uri.path ?: return false
    return uri.scheme == "https" &&
        uri.rawAuthority == host &&
        uri.rawQuery == null &&
        uri.rawFragment == null &&
        path.startsWith(pathPrefix) &&
        path.length > pathPrefix.length &&
        path.split('/').none { it == "." || it == ".." } &&
        '\\' !in path
}
