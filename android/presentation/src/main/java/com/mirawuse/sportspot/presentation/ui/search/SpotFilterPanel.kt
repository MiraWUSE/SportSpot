package com.mirawuse.sportspot.presentation.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mirawuse.sportspot.presentation.R
import com.mirawuse.sportspot.presentation.ui.components.SportSpotButton
import com.mirawuse.sportspot.presentation.ui.components.labelRes
import com.mirawuse.sportspot.presentation.ui.theme.SportSpotSpacing

@Composable
internal fun SpotFilterPanel(
    filter: SearchFilterState,
    showRadius: Boolean,
    onApply: (SearchFilterState) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    var sort by rememberSaveable { mutableStateOf(filter.sort) }
    var sport by rememberSaveable { mutableStateOf(filter.sport) }
    var lighting by rememberSaveable { mutableStateOf(filter.lighting) }
    var surface by rememberSaveable { mutableStateOf(filter.surface) }
    var radius by rememberSaveable { mutableIntStateOf(filter.radiusMeters) }
    val actionColors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.home_filters), Modifier.semantics { heading() }) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(SportSpotSpacing.md)) {
                FilterOptions(R.string.search_sort_title, SpotSort.entries, sort, { sort = it }, SpotSort::labelRes)
                FilterOptions(R.string.search_sport_title, SpotSearchCategories, sport, { sport = it }) {
                    it?.labelRes() ?: R.string.home_filter_all
                }
                FilterOptions(R.string.search_lighting_title, LightingFilter.entries, lighting, { lighting = it }, LightingFilter::labelRes)
                FilterOptions(R.string.search_surface_title, SurfaceFilter.entries, surface, { surface = it }, SurfaceFilter::labelRes)
                if (showRadius) {
                    FilterOptions(R.string.search_radius_title, searchRadiusOptions, radius, { radius = it }, ::radiusLabelRes)
                }
            }
        },
        confirmButton = {
            SportSpotButton(stringResource(R.string.search_filters_apply), {
                onApply(
                    filter.copy(
                        sort = sort,
                        sport = sport,
                        lighting = lighting,
                        surface = surface,
                        radiusMeters = if (showRadius) radius else filter.radiusMeters,
                    ),
                )
            })
        },
        dismissButton = {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(SportSpotSpacing.xs)) {
                TextButton(
                    onClick = onReset,
                    modifier = Modifier.heightIn(min = 48.dp),
                    colors = actionColors,
                ) { Text(stringResource(R.string.search_filters_clear)) }
                TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp), colors = actionColors) {
                    Text(stringResource(R.string.home_close))
                }
            }
        },
    )
}

@Composable
private fun <T> FilterOptions(
    title: Int,
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    labelRes: (T) -> Int,
) {
    Text(stringResource(title), Modifier.semantics { heading() }, style = MaterialTheme.typography.titleSmall)
    FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(SportSpotSpacing.sm)) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(stringResource(labelRes(option))) },
            )
        }
    }
}

private fun SpotSort.labelRes(): Int =
    when (this) {
        SpotSort.DEFAULT -> R.string.search_sort_default
        SpotSort.NEAREST -> R.string.search_sort_nearest
        SpotSort.NAME_ASC -> R.string.search_sort_name
    }

private fun LightingFilter.labelRes(): Int =
    when (this) {
        LightingFilter.ALL -> R.string.home_filter_all
        LightingFilter.LIT -> R.string.search_lighting_lit
        LightingFilter.UNLIT -> R.string.search_lighting_unlit
        LightingFilter.UNKNOWN -> R.string.search_option_unknown
    }

private fun SurfaceFilter.labelRes(): Int =
    when (this) {
        SurfaceFilter.ALL -> R.string.home_filter_all
        SurfaceFilter.RUBBER -> R.string.search_surface_rubber
        SurfaceFilter.HARD -> R.string.search_surface_hard
        SurfaceFilter.GRASS -> R.string.search_surface_grass
        SurfaceFilter.SAND -> R.string.search_surface_sand
        SurfaceFilter.OTHER -> R.string.search_surface_other
        SurfaceFilter.UNKNOWN -> R.string.search_option_unknown
    }

internal fun radiusLabelRes(radius: Int): Int =
    when (radius) {
        500 -> R.string.search_radius_500
        2000 -> R.string.search_radius_2000
        3000 -> R.string.search_radius_3000
        else -> R.string.search_radius_1000
    }
