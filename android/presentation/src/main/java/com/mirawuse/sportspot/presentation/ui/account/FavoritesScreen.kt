package com.mirawuse.sportspot.presentation.ui.account

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.mirawuse.sportspot.presentation.R
import com.mirawuse.sportspot.presentation.ui.components.SportSpotButton
import com.mirawuse.sportspot.presentation.ui.forms.FormCard
import com.mirawuse.sportspot.presentation.ui.forms.FormScreen

@Composable
internal fun FavoritesScreen(onOpenAuth: () -> Unit) {
    FormScreen(stringResource(R.string.tab_favorites)) {
        Text(stringResource(R.string.favorites_saved), style = MaterialTheme.typography.headlineSmall)
        FormCard(stringResource(R.string.favorites_guest_header)) {
            Text(stringResource(R.string.favorites_guest_message), style = MaterialTheme.typography.bodyMedium)
        }
        SportSpotButton(
            text = stringResource(R.string.auth_open),
            onClick = onOpenAuth,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
