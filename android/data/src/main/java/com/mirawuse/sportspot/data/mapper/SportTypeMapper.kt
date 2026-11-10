package com.mirawuse.sportspot.data.mapper

import com.mirawuse.sportspot.domain.model.spot.SportType

internal fun String.toDomainSport(): SportType =
    when (this) {
        "soccer" -> SportType.FOOTBALL
        "basketball" -> SportType.BASKETBALL
        "volleyball" -> SportType.VOLLEYBALL
        "tennis" -> SportType.TENNIS
        "fitness" -> SportType.WORKOUT
        else -> SportType.OTHER
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
