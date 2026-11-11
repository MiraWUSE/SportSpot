package com.mirawuse.sportspot.data.local.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SportSpotPreferencesDataSource
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) {
        val preferences: Flow<UserPreferences> =
            dataStore.data.map { stored ->
                val theme = stored[THEME_MODE]
                UserPreferences(
                    themeMode = ThemeMode.entries.firstOrNull { it.name == theme } ?: ThemeMode.SYSTEM,
                    searchRadiusMeters = stored[SEARCH_RADIUS_METERS]?.takeIf { it in validSearchRadii } ?: DEFAULT_SEARCH_RADIUS_METERS,
                )
            }

        suspend fun setThemeMode(themeMode: ThemeMode) {
            dataStore.edit { it[THEME_MODE] = themeMode.name }
        }

        suspend fun setSearchRadiusMeters(searchRadiusMeters: Int) {
            require(searchRadiusMeters in validSearchRadii) { "Search radius must be between 100 and 3000 meters" }
            dataStore.edit { it[SEARCH_RADIUS_METERS] = searchRadiusMeters }
        }

        companion object {
            private val THEME_MODE = stringPreferencesKey("theme_mode")
            private val SEARCH_RADIUS_METERS = intPreferencesKey("search_radius_meters")
        }
    }
