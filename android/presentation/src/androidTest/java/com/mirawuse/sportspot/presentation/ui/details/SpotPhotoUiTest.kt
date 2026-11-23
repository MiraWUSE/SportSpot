package com.mirawuse.sportspot.presentation.ui.details

import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.mirawuse.sportspot.domain.model.location.GeoPoint
import com.mirawuse.sportspot.domain.model.spot.SportType
import com.mirawuse.sportspot.domain.model.spot.Spot
import com.mirawuse.sportspot.domain.model.spot.SpotId
import com.mirawuse.sportspot.domain.model.spot.SpotPhoto
import com.mirawuse.sportspot.presentation.ui.home.HomeSpotCard
import com.mirawuse.sportspot.presentation.ui.home.HomeUiState
import com.mirawuse.sportspot.presentation.ui.theme.SportSpotTheme
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Path.Companion.toOkioPath
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicInteger

@OptIn(DelicateCoilApi::class)
class SpotPhotoUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val spot =
        Spot(
            id = SpotId("osm", "node:10"),
            name = "Тестовый корт",
            sportType = SportType.TENNIS,
            location = GeoPoint(56.3269, 44.0059),
            photo =
                SpotPhoto(
                    "https://thumb.wikimedia.org/wikipedia/commons/thumb/a/a1/Test.jpg/500px-Test.jpg",
                    "https://commons.wikimedia.org/wiki/File:Test.jpg",
                    "Test Author",
                    "CC BY 4.0",
                    "https://creativecommons.org/licenses/by/4.0/",
                ),
        )
    private val photoLabel = "Фото площадки «Тестовый корт»"
    private val credit = "Фото: Test Author · CC BY 4.0"
    private val cropNotice = "Кадрировано для отображения"
    private val source = "Источник фото на Wikimedia Commons"
    private val requests = AtomicInteger()
    private var loader: ImageLoader? = null

    @After
    fun restoreImageLoader() {
        loader?.shutdown()
        SingletonImageLoader.reset()
    }

    @Test
    fun loadedPhotoIsReusedOnDetailsAndAttributionLinksUseVerifiedUrls() {
        installFixture(200)
        val details = mutableStateOf(false)
        val darkTheme = mutableStateOf(false)
        val opened = mutableListOf<String>()
        compose.setContent {
            SportSpotTheme(darkTheme = darkTheme.value) {
                CompositionLocalProvider(
                    LocalUriHandler provides
                        object : UriHandler {
                            override fun openUri(uri: String) {
                                opened.add(uri)
                            }
                        },
                ) {
                    if (details.value) {
                        SpotDetailsScreen(spot.id, "Нижний Новгород", HomeUiState.Content(listOf(spot)), {}, {}, {}, {})
                    } else {
                        HomeSpotCard(spot, { details.value = true }, {})
                    }
                }
            }
        }
        waitForPhoto()
        compose.onNodeWithText(spot.name).performClick()
        waitForPhoto()
        compose.onNodeWithText(cropNotice).performScrollTo().assertIsDisplayed()
        compose
            .onNodeWithText(credit)
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        compose
            .onNodeWithText(source)
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        compose.runOnIdle {
            assertEquals(listOf(spot.photo?.licenseUrl, spot.photo?.sourceUrl), opened)
            assertEquals(1, requests.get())
            darkTheme.value = true
        }
        compose.onNodeWithText(cropNotice).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun absentOrUnavailablePhotoLeavesDetailsAndHidesAttribution() {
        installFixture(503)
        val current = mutableStateOf(spot.copy(photo = null))
        compose.setContent {
            SportSpotTheme(darkTheme = true) {
                SpotDetailsScreen(spot.id, "Нижний Новгород", HomeUiState.Content(listOf(current.value)), {}, {}, {}, {})
            }
        }
        compose.onNodeWithText(spot.name).assertIsDisplayed()
        compose.onNodeWithText(credit).assertDoesNotExist()
        compose.onNodeWithText(cropNotice).assertDoesNotExist()
        assertEquals(0, requests.get())
        compose.runOnIdle { current.value = spot }
        compose.waitUntil(10_000) { requests.get() == 1 }
        compose.onNodeWithText(spot.name).assertIsDisplayed()
        compose.onNodeWithContentDescription(photoLabel).assertDoesNotExist()
        compose.onNodeWithText(source).assertDoesNotExist()
        compose.onNodeWithText(cropNotice).assertDoesNotExist()
        compose.onNodeWithText("Показать на карте").performScrollTo().assertIsDisplayed()
    }

    private fun waitForPhoto() {
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription(photoLabel).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription(photoLabel).assertIsDisplayed()
    }

    private fun installFixture(status: Int) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val image =
            ByteArrayOutputStream().use { stream ->
                Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply {
                    eraseColor(android.graphics.Color.BLUE)
                    compress(Bitmap.CompressFormat.PNG, 100, stream)
                    recycle()
                }
                stream.toByteArray()
            }
        val client =
            OkHttpClient
                .Builder()
                .addInterceptor { chain ->
                    requests.incrementAndGet()
                    Response
                        .Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(status)
                        .message("Test fixture")
                        .body(image.toResponseBody("image/png".toMediaType()))
                        .build()
                }.build()
        loader =
            ImageLoader
                .Builder(context)
                .memoryCache(null)
                .diskCache {
                    coil3.disk.DiskCache
                        .Builder()
                        .directory(context.cacheDir.resolve("photo-test-$status").toOkioPath())
                        .build()
                }.components { add(OkHttpNetworkFetcherFactory(client)) }
                .build()
                .also(SingletonImageLoader::setUnsafe)
        loader?.diskCache?.clear()
    }
}
