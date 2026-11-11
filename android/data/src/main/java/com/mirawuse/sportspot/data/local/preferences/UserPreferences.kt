package com.mirawuse.sportspot.data.local.preferences

internal const val DEFAULT_SEARCH_RADIUS_METERS = 1000
internal val validSearchRadii = 100..3000

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val searchRadiusMeters: Int = DEFAULT_SEARCH_RADIUS_METERS,
)
