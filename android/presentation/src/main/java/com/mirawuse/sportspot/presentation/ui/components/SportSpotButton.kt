
package com.mirawuse.sportspot.presentation.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mirawuse.sportspot.presentation.ui.theme.SportSpotPillShape
import com.mirawuse.sportspot.presentation.ui.theme.SportSpotSpacing

enum class SportSpotButtonVariant {
    PRIMARY,
    OUTLINED,
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
                SportSpotButtonContent(text, isLoading)
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
                SportSpotButtonContent(text, isLoading)
            }
        }

        SportSpotButtonVariant.TEXT -> {
            TextButton(
                onClick = onClick,
                modifier = buttonModifier,
                enabled = buttonEnabled,
                shape = SportSpotPillShape,
                contentPadding = padding,
            ) {
                SportSpotButtonContent(text, isLoading)
            }
        }
    }
}

@Composable
private fun SportSpotButtonContent(
    text: String,
    isLoading: Boolean,
) {
    if (isLoading) {
        CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            color = LocalContentColor.current,
            strokeWidth = 2.dp,
        )

        Spacer(
            modifier = Modifier.width(SportSpotSpacing.sm),
        )
    }

    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
    )
}
