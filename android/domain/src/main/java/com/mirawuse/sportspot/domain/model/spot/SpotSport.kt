package com.mirawuse.sportspot.domain.model.spot

/**
 * A sport reported for a spot. Several sports may belong to the same existing filter category.
 */
enum class SpotSport(
    val category: SportType = SportType.OTHER,
) {
    FOOTBALL(SportType.FOOTBALL),
    BASKETBALL(SportType.BASKETBALL),
    VOLLEYBALL(SportType.VOLLEYBALL),
    TENNIS(SportType.TENNIS),
    WORKOUT(SportType.WORKOUT),
    TABLE_TENNIS,
    HOCKEY,
    ICE_HOCKEY,
    OTHER,
}
