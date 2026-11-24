package com.mirawuse.sportspot.presentation.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.mirawuse.sportspot.domain.model.spot.SportType
import com.mirawuse.sportspot.presentation.R
import com.mirawuse.sportspot.presentation.ui.components.labelRes
import com.mirawuse.sportspot.presentation.ui.theme.SportSpotSpacing

internal val SpotSearchCategories = listOf(null) + SportType.entries

@Composable
internal fun SpotSearchBar(
    filter: SearchFilterState,
    onQuery: (String) -> Unit,
    onSport: (SportType?) -> Unit,
) {
    val query = filter.query
    var menuOpen by remember { mutableStateOf(false) }
    val theme = MaterialTheme.colorScheme
    val searchLabel = stringResource(R.string.home_search)
    val textStyle = MaterialTheme.typography.bodySmall
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(if (theme.background.luminance() < 0.5f) theme.surfaceVariant else theme.surface)
                .padding(start = SportSpotSpacing.lg, end = SportSpotSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(SportSpotSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_home_search), contentDescription = null, tint = theme.onSurfaceVariant)
        BasicTextField(
            value = query,
            onValueChange = onQuery,
            modifier = Modifier.weight(1f).semantics { contentDescription = searchLabel }.padding(vertical = SportSpotSpacing.lg),
            textStyle = textStyle.copy(color = theme.onSurface),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            cursorBrush = SolidColor(theme.onSurface),
            decorationBox = { input ->
                Box {
                    if (query.isEmpty()) Text(searchLabel, color = theme.onSurfaceVariant, style = textStyle)
                    input()
                }
            },
        )
        Box {
            Box(
                modifier =
                    Modifier
                        .size(48.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .clickable(role = Role.Button) { menuOpen = true },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier.size(40.dp).background(theme.primary, MaterialTheme.shapes.medium),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(R.drawable.ic_home_filters),
                        contentDescription = stringResource(R.string.home_filters),
                        tint = theme.onPrimary,
                    )
                }
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                SpotSearchCategories.forEach { category ->
                    DropdownMenuItem(
                        text = { Text(stringResource(category?.labelRes() ?: R.string.home_filter_all)) },
                        leadingIcon = { RadioButton(selected = category == filter.sport, onClick = null) },
                        modifier = Modifier.semantics { selected = category == filter.sport },
                        onClick = {
                            onSport(category)
                            menuOpen = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
internal fun SpotFilterEmpty(
    message: String,
    onReset: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(SportSpotSpacing.xs)) {
        Text(message, style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = onReset) { Text(stringResource(R.string.spot_search_reset)) }
    }
}
