package com.mirawuse.sportspot.data.local.preferences

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SportSpotPreferencesDataSourceTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val file
        get() = folder.root.resolve("settings.preferences_pb")

    @Test
    fun `first read returns system theme and a radius of 1000 meters`() =
        runBlocking {
            withStore { store ->
                val source = SportSpotPreferencesDataSource(store)

                assertEquals(UserPreferences(ThemeMode.SYSTEM, 1000), source.preferences.first())
            }
        }

    @Test
    fun `settings flow emits updates and a new store reads them from disk`() =
        runBlocking {
            val expected = UserPreferences(ThemeMode.DARK, 2500)
            withStore { store ->
                val source = SportSpotPreferencesDataSource(store)
                coroutineScope {
                    val observed = async(start = CoroutineStart.UNDISPATCHED) { source.preferences.first { it == expected } }
                    source.setThemeMode(ThemeMode.DARK)
                    source.setSearchRadiusMeters(2500)
                    assertEquals(expected, observed.await())
                }
            }

            assertTrue(file.length() > 0)
            withStore { reopened ->
                assertEquals(expected, SportSpotPreferencesDataSource(reopened).preferences.first())
            }
        }

    @Test
    fun `radius limits are accepted and rejected values preserve the previous settings`() =
        runBlocking {
            withStore { store ->
                val source = SportSpotPreferencesDataSource(store)
                source.setThemeMode(ThemeMode.DARK)
                source.setSearchRadiusMeters(100)

                for (invalid in listOf(99, 3001)) {
                    val failure = runCatching { source.setSearchRadiusMeters(invalid) }.exceptionOrNull()
                    assertTrue(failure is IllegalArgumentException)
                    assertEquals(UserPreferences(ThemeMode.DARK, 100), source.preferences.first())
                }

                source.setSearchRadiusMeters(3000)
                assertEquals(UserPreferences(ThemeMode.DARK, 3000), source.preferences.first())
            }
        }

    @Test
    fun `unrecognized theme and out of range stored radius use defaults`() =
        runBlocking {
            withStore { store ->
                val source = SportSpotPreferencesDataSource(store)
                source.setThemeMode(ThemeMode.DARK)
                source.setSearchRadiusMeters(2500)
                store.edit { stored ->
                    stored[stringPreferencesKey("theme_mode")] = "UNKNOWN"
                    stored[intPreferencesKey("search_radius_meters")] = 3001
                }

                assertEquals(UserPreferences(ThemeMode.SYSTEM, 1000), source.preferences.first())
            }
        }

    @Test
    fun `concurrent updates preserve both fields`() =
        runBlocking {
            withStore { store ->
                val source = SportSpotPreferencesDataSource(store)
                coroutineScope {
                    launch { source.setThemeMode(ThemeMode.LIGHT) }
                    launch { source.setSearchRadiusMeters(1800) }
                }

                assertEquals(UserPreferences(ThemeMode.LIGHT, 1800), source.preferences.first())
            }
        }

    @Test
    fun `cancelled write propagates cancellation and leaves the previous theme on disk`() =
        runBlocking {
            withStore { store -> SportSpotPreferencesDataSource(store).setThemeMode(ThemeMode.LIGHT) }
            val initializationStarted = CompletableDeferred<Unit>()
            val finishInitialization = CompletableDeferred<Unit>()
            val pausedInitialization =
                object : DataMigration<Preferences> {
                    override suspend fun shouldMigrate(currentData: Preferences): Boolean {
                        initializationStarted.complete(Unit)
                        finishInitialization.await()
                        return false
                    }

                    override suspend fun migrate(currentData: Preferences): Preferences = currentData

                    override suspend fun cleanUp() = Unit
                }

            withStore(migrations = listOf(pausedInitialization)) { store ->
                val source = SportSpotPreferencesDataSource(store)
                val observedFailure = CompletableDeferred<Throwable?>()
                coroutineScope {
                    val write =
                        launch {
                            observedFailure.complete(runCatching { source.setThemeMode(ThemeMode.DARK) }.exceptionOrNull())
                        }
                    initializationStarted.await()
                    write.cancelAndJoin()
                    assertTrue(observedFailure.await() is CancellationException)
                    finishInitialization.complete(Unit)
                    source.setSearchRadiusMeters(1800)
                }
            }

            withStore { reopened ->
                assertEquals(UserPreferences(ThemeMode.LIGHT, 1800), SportSpotPreferencesDataSource(reopened).preferences.first())
            }
        }

    @Test
    fun `corrupted preferences fail the read without replacing the file`() =
        runBlocking {
            val corruptBytes = byteArrayOf(0x0A, 0x01)
            file.writeBytes(corruptBytes)
            withStore { store ->
                val source = SportSpotPreferencesDataSource(store)
                val failure = runCatching { source.preferences.first() }.exceptionOrNull()

                assertTrue(failure is CorruptionException)
            }
            assertArrayEquals(corruptBytes, file.readBytes())
        }

    private suspend fun withStore(
        migrations: List<DataMigration<Preferences>> = emptyList(),
        block: suspend (DataStore<Preferences>) -> Unit,
    ) {
        val scopeJob = SupervisorJob()
        val store =
            PreferenceDataStoreFactory.create(
                migrations = migrations,
                scope = CoroutineScope(Dispatchers.IO + scopeJob),
                produceFile = { file },
            )
        try {
            withTimeout(10_000) { block(store) }
        } finally {
            scopeJob.cancelAndJoin()
        }
    }
}
