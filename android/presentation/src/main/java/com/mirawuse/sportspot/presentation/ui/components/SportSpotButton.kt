
package com.mirawuse.sportspot.presentation.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.mirawuse.sportspot.presentation.ui.theme.SportSpotPillShape
import com.mirawuse.sportspot.presentation.ui.theme.SportSpotSpacing

enum class SportSpotButtonVariant {
    PRIMARY,
    OUTLINED,
    SECONDARY,
    DANGER,
    TEXT,
}

@Composable
fun SportSpotButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: SportSpotButtonVariant = SportSpotButtonVariant.PRIMARY,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    icon: Int? = null,
) {
    val buttonModifier = modifier.heightIn(min = 48.dp)
    val buttonEnabled = enabled && !isLoading

    val padding =
        PaddingValues(
            horizontal = SportSpotSpacing.xl,
            vertical = SportSpotSpacing.md,
        )

    when (variant) {
        SportSpotButtonVariant.PRIMARY -> {
            Button(
                onClick = onClick,
                modifier = buttonModifier,
                enabled = buttonEnabled,
                shape = SportSpotPillShape,
                contentPadding = padding,
            ) {
                SportSpotButtonContent(text, isLoading, icon)
            }
        }

        SportSpotButtonVariant.OUTLINED -> {
            OutlinedButton(
                onClick = onClick,
                modifier = buttonModifier,
                enabled = buttonEnabled,
                shape = SportSpotPillShape,
                contentPadding = padding,
            ) {
                SportSpotButtonContent(text, isLoading, icon)
            }
        }

        SportSpotButtonVariant.SECONDARY, SportSpotButtonVariant.DANGER -> {
            val colors = MaterialTheme.colorScheme
            val danger = variant == SportSpotButtonVariant.DANGER
            val borderColor = if (danger) colors.error else colors.outline
            OutlinedButton(
                onClick = onClick,
                modifier = buttonModifier,
                enabled = buttonEnabled,
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(1.dp, borderColor.copy(alpha = if (buttonEnabled) 1f else 0.38f)),
                colors =
                    ButtonDefaults.outlinedButtonColors(
                        containerColor = colors.surfaceVariant,
                        contentColor = if (danger) colors.error else colors.onSurface,
                        disabledContainerColor = colors.surfaceVariant,
                    ),
                contentPadding = PaddingValues(horizontal = SportSpotSpacing.md, vertical = SportSpotSpacing.sm),
            ) {
                SportSpotButtonContent(text, isLoading, icon)
            }
        }

        SportSpotButtonVariant.TEXT -> {
            TextButton(
                onClick = onClick,
                modifier = buttonModifier,
                enabled = buttonEnabled,
                shape = SportSpotPillShape,
                contentPadding = padding,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
            ) {
                SportSpotButtonContent(text, isLoading, icon)
            }
        }
    }
}

@Composable
private fun SportSpotButtonContent(
    text: String,
    isLoading: Boolean,
    icon: Int?,
) {
    if (isLoading) {
        CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            color = LocalContentColor.current,
            strokeWidth = 2.dp,
        )
    } else if (icon != null) {
        Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(18.dp))
    }
    if (isLoading || icon != null) Spacer(Modifier.width(SportSpotSpacing.sm))

    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
    )
}
