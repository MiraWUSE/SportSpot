
package com.mirawuse.sportspot.presentation.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mirawuse.sportspot.presentation.ui.theme.SportSpotSpacing
import com.mirawuse.sportspot.presentation.ui.theme.SportSpotTheme

@Preview(
    name = "Light Theme",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_NO,
)
@Preview(
    name = "Dark Theme",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun SportSpotButtonsPreview() {
    SportSpotTheme {
        Surface(
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(SportSpotSpacing.md),
            ) {
                SportSpotButton(
                    text = "Основная кнопка",
                    onClick = {},
                )

                SportSpotButton(
                    text = "Контурная кнопка",
                    onClick = {},
                    variant = SportSpotButtonVariant.OUTLINED,
                )

                SportSpotButton(
                    text = "Текстовая кнопка",
                    onClick = {},
                    variant = SportSpotButtonVariant.TEXT,
                )

                SportSpotButton(
                    text = "Недоступная кнопка",
                    onClick = {},
                    enabled = false,
                )

                SportSpotButton(
                    text = "Загрузка",
                    onClick = {},
                    isLoading = true,
                )
            }
        }
    }
}
