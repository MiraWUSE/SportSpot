
package com.mirawuse.sportspot.presentation.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme =
    lightColorScheme(
        primary = SportSpotLightColors.primary,
        onPrimary = SportSpotLightColors.onPrimary,
        primaryContainer = SportSpotLightColors.primary,
        onPrimaryContainer = SportSpotLightColors.onPrimary,
        secondary = SportSpotLightColors.primary,
        onSecondary = SportSpotLightColors.onPrimary,
        background = SportSpotLightColors.background,
        onBackground = SportSpotLightColors.textPrimary,
        surface = SportSpotLightColors.surface,
        onSurface = SportSpotLightColors.textPrimary,
        surfaceVariant = SportSpotLightColors.surfaceVariant,
        onSurfaceVariant = SportSpotLightColors.textSecondary,
        surfaceContainer = SportSpotLightColors.surfaceVariant,
        outline = SportSpotLightColors.border,
        error = SportSpotLightColors.error,
        onError = SportSpotLightColors.surface,
    )

private val DarkColorScheme =
    darkColorScheme(
        primary = SportSpotDarkColors.primary,
        onPrimary = SportSpotDarkColors.onPrimary,
        primaryContainer = SportSpotDarkColors.primary,
        onPrimaryContainer = SportSpotDarkColors.onPrimary,
        secondary = SportSpotDarkColors.primary,
        onSecondary = SportSpotDarkColors.onPrimary,
        background = SportSpotDarkColors.background,
        onBackground = SportSpotDarkColors.textPrimary,
        surface = SportSpotDarkColors.surface,
        onSurface = SportSpotDarkColors.textPrimary,
        surfaceVariant = SportSpotDarkColors.surfaceVariant,
        onSurfaceVariant = SportSpotDarkColors.textSecondary,
        surfaceContainer = SportSpotDarkColors.surfaceVariant,
        outline = SportSpotDarkColors.border,
        error = SportSpotDarkColors.error,
        onError = SportSpotDarkColors.background,
    )

@Composable
fun SportSpotTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme =
        when {
            dynamicColor &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val context = LocalContext.current

                if (darkTheme) {
                    dynamicDarkColorScheme(context)
                } else {
                    dynamicLightColorScheme(context)
                }
            }

            darkTheme -> DarkColorScheme

            else -> LightColorScheme
        }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = SportSpotTypography,
        content = content,
    )
}
