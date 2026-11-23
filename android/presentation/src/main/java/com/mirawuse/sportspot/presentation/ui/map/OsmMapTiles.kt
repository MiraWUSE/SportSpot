package com.mirawuse.sportspot.presentation.ui.map

import android.content.Context
import okhttp3.Cache
import okhttp3.OkHttpClient
import org.maplibre.android.MapLibre
import org.maplibre.android.module.http.HttpRequestUtil
import java.io.File
import java.util.concurrent.TimeUnit

internal object OsmMapTiles {
    const val USER_AGENT = "SportSpot/1.0 (+https://github.com/MiraWUSE/SportSpot)"
    const val COPYRIGHT_URL = "https://www.openstreetmap.org/copyright"
    const val PREFETCH_ZOOM_DELTA = 0

    // Local style: no remote demo style, fonts, sprites or additional tile providers.
    val styleJson =
        """
        {
          "version": 8,
          "name": "SportSpot OpenStreetMap",
          "sources": {
            "osm": {
              "type": "raster",
              "tiles": ["https://tile.openstreetmap.org/{z}/{x}/{y}.png"],
              "tileSize": 256,
              "minzoom": 0,
              "maxzoom": 19,
              "attribution": "© OpenStreetMap contributors"
            }
          },
          "layers": [{"id": "osm", "type": "raster", "source": "osm"}]
        }
        """.trimIndent()

    private var initialized = false

    @Synchronized
    fun initialize(context: Context) {
        if (initialized) return
        MapLibre.getInstance(context.applicationContext)
        HttpRequestUtil.setOkHttpClient(createClient(File(context.cacheDir, "osm-http")))
        HttpRequestUtil.setLogEnabled(false)
        HttpRequestUtil.setPrintRequestUrlOnFailure(false)
        initialized = true
    }

    fun createClient(cacheDirectory: File): OkHttpClient =
        OkHttpClient
            .Builder()
            .cache(Cache(cacheDirectory, 64L * 1024 * 1024))
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                // This client is used only by MapLibre, not the SportSpot API client.
                chain.proceed(
                    chain
                        .request()
                        .newBuilder()
                        .header("User-Agent", USER_AGENT)
                        .build(),
                )
            }.build()
}
