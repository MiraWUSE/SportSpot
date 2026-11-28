package com.mirawuse.sportspot.presentation.ui.components

import android.graphics.Bitmap
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.mirawuse.sportspot.domain.model.photo.PhotoTarget
import com.mirawuse.sportspot.domain.model.spot.Spot
import com.mirawuse.sportspot.domain.model.spot.SpotPhoto
import com.mirawuse.sportspot.presentation.R
import com.mirawuse.sportspot.presentation.ui.photos.SpotCoverLoader
import com.mirawuse.sportspot.presentation.ui.photos.SpotCoverSize
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

@Composable
internal fun SpotPhotoImage(
    spot: Spot,
    @DrawableRes illustration: Int,
    modifier: Modifier = Modifier,
    coverLoader: SpotCoverLoader? = null,
    coverSize: SpotCoverSize = SpotCoverSize.THUMBNAIL,
    attribution: @Composable (SpotPhoto) -> Unit = {},
) {
    val target = remember(spot.id) { runCatching { PhotoTarget.spot(spot.id) }.getOrNull() }
    val approved =
        if (coverLoader != null && target != null) {
            key(target, coverLoader, coverSize, coverLoader.revision(target)) {
                val bitmap by produceState<Bitmap?>(null) { value = coverLoader.cover(target, coverSize) }
                bitmap
            }
        } else {
            null
        }
    if (approved != null) {
        Image(
            bitmap = approved.asImageBitmap(),
            contentDescription = stringResource(R.string.spot_user_photo_description, spot.name),
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
    } else {
        WikimediaSpotPhoto(spot, illustration, modifier, attribution)
    }
}

@Composable
private fun WikimediaSpotPhoto(
    spot: Spot,
    @DrawableRes illustration: Int,
    modifier: Modifier,
    attribution: @Composable (SpotPhoto) -> Unit,
) {
    val photo = spot.photo
    val placeholder = painterResource(illustration)
    if (photo == null) {
        Image(placeholder, contentDescription = null, modifier = modifier)
        return
    }

    key(photo) {
        val context = LocalContext.current
        val imageLoader =
            remember(context) {
                SingletonImageLoader.setSafe { appContext ->
                    ImageLoader
                        .Builder(appContext)
                        .components {
                            add(
                                OkHttpNetworkFetcherFactory(callFactory = {
                                    OkHttpClient
                                        .Builder()
                                        .followRedirects(false)
                                        .callTimeout(10, TimeUnit.SECONDS)
                                        .addInterceptor { chain ->
                                            chain.proceed(
                                                chain
                                                    .request()
                                                    .newBuilder()
                                                    .header(
                                                        "User-Agent",
                                                        "SportSpot/1.0 (+https://github.com/MiraWUSE/SportSpot)",
                                                    ).build(),
                                            )
                                        }.build()
                                }),
                            )
                        }.build()
                }
                SingletonImageLoader.get(context)
            }
        var loaded by remember { mutableStateOf(false) }
        Column {
            Box(modifier) {
                if (!loaded) Image(placeholder, contentDescription = null, modifier = Modifier.matchParentSize())
                AsyncImage(
                    model = photo.url,
                    imageLoader = imageLoader,
                    contentDescription = if (loaded) stringResource(R.string.spot_photo_description, spot.name) else null,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop,
                    onState = { loaded = it is AsyncImagePainter.State.Success },
                )
            }
            if (loaded) attribution(photo)
        }
    }
}
