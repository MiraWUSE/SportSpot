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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.mirawuse.sportspot.domain.model.spot.SportType
import com.mirawuse.sportspot.presentation.R
import com.mirawuse.sportspot.presentation.ui.theme.SportSpotSpacing

internal val SpotSearchCategories = listOf(null) + SportType.entries

@Composable
internal fun SpotSearchBar(
    filter: SearchFilterState,
    onQuery: (String) -> Unit,
    onApply: (SearchFilterState) -> Unit,
    onReset: () -> Unit,
    showRadius: Boolean = false,
) {
    val query = filter.query
    var panelOpen by rememberSaveable { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val theme = MaterialTheme.colorScheme
    val searchLabel = stringResource(R.string.home_search)
    val textStyle = MaterialTheme.typography.bodySmall
    val active =
        listOf(
            filter.sport != null,
            filter.sort != SpotSort.DEFAULT,
            filter.lighting != LightingFilter.ALL,
            filter.surface != SurfaceFilter.ALL,
        ).count { it }
    val countLabel = stringResource(R.string.search_filters_count, active)
    val radiusLabel =
        if (showRadius) {
            stringResource(
                R.string.search_radius_summary,
                stringResource(radiusLabelRes(filter.radiusMeters)),
            )
        } else {
            null
        }
    val distanceLabel =
        if (filter.sort == SpotSort.NEAREST) {
            stringResource(if (showRadius) R.string.search_distance_origin else R.string.map_distance_origin)
        } else {
            null
        }

    Column(verticalArrangement = Arrangement.spacedBy(SportSpotSpacing.xs)) {
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
            Box(
                modifier =
                    Modifier
                        .size(48.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .clickable(role = Role.Button) {
                            focus.clearFocus()
                            keyboard?.hide()
                            panelOpen = true
                        }.semantics { if (active > 0) stateDescription = countLabel },
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
        }
        val summary = listOfNotNull(radiusLabel, countLabel.takeIf { active > 0 }, distanceLabel)
        if (summary.isNotEmpty()) {
            Text(summary.joinToString(" · "), color = theme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
        }
    }
    if (panelOpen) {
        SpotFilterPanel(
            filter = filter,
            showRadius = showRadius,
            onApply = {
                onApply(it)
                panelOpen = false
            },
            onReset = {
                onReset()
                panelOpen = false
            },
            onDismiss = { panelOpen = false },
        )
    }
}

@Composable
internal fun SpotFilterEmpty(
    message: String,
    onReset: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(SportSpotSpacing.xs)) {
        Text(message, style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = onReset, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) {
            Text(stringResource(R.string.spot_search_reset))
        }
    }
}
