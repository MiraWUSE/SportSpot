package com.mirawuse.sportspot.data.mapper

import com.mirawuse.sportspot.domain.model.spot.SportType
import com.mirawuse.sportspot.domain.model.spot.SpotSport

internal fun String.toDomainSport(): SportType = toSpotSport().category

internal fun String.toSpotSports(): List<SpotSport> =
    splitToSequence(';')
        .map { it.trim().lowercase() }
        .filter { it.isNotEmpty() && it.length <= 64 && it != "unspecified" }
        .distinct()
        .take(8)
        .map { it.toSpotSport() }
        .distinct()
        .toList()

private fun String.toSpotSport(): SpotSport =
    when (this) {
        "soccer" -> SpotSport.FOOTBALL
        "basketball" -> SpotSport.BASKETBALL
        "volleyball" -> SpotSport.VOLLEYBALL
        "tennis" -> SpotSport.TENNIS
        "fitness" -> SpotSport.WORKOUT
        "table_tennis" -> SpotSport.TABLE_TENNIS
        "hockey" -> SpotSport.HOCKEY
        "ice_hockey" -> SpotSport.ICE_HOCKEY
        else -> SpotSport.OTHER
    }

fun SportType?.toApiSport(): String =
    when (this) {
        null -> "all"
        SportType.FOOTBALL -> "soccer"
        SportType.BASKETBALL -> "basketball"
        SportType.VOLLEYBALL -> "volleyball"
        SportType.TENNIS -> "tennis"
        SportType.WORKOUT -> "fitness"
        SportType.OTHER -> throw IllegalArgumentException("Unsupported sport filter")
    }
