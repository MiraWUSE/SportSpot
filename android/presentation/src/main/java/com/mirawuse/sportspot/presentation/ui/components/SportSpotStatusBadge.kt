package com.mirawuse.sportspot.presentation.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.mirawuse.sportspot.presentation.ui.theme.SportSpotDarkColors
import com.mirawuse.sportspot.presentation.ui.theme.SportSpotLightColors
import com.mirawuse.sportspot.presentation.ui.theme.SportSpotPillShape
import com.mirawuse.sportspot.presentation.ui.theme.SportSpotSpacing

internal enum class StatusTone { NEUTRAL, PENDING, APPROVED, REJECTED }

@Composable
internal fun SportSpotStatusBadge(
    text: String,
    tone: StatusTone,
) {
    val colors = MaterialTheme.colorScheme
    val dark = colors.surface.luminance() < 0.5f
    val tint =
        when (tone) {
            StatusTone.NEUTRAL -> colors.onSurfaceVariant
            StatusTone.PENDING -> if (dark) SportSpotDarkColors.warning else SportSpotLightColors.warning
            StatusTone.APPROVED -> if (dark) SportSpotDarkColors.success else SportSpotLightColors.success
            StatusTone.REJECTED -> colors.error
        }
    val fill = if (dark) tint.copy(alpha = 0.12f) else lerp(colors.surface, tint, 0.04f)
    Surface(shape = SportSpotPillShape, color = fill, contentColor = tint) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = SportSpotSpacing.md, vertical = SportSpotSpacing.xs),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}
