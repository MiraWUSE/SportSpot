package com.mirawuse.sportspot.presentation.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.mirawuse.sportspot.domain.model.spot.SpotId
import com.mirawuse.sportspot.presentation.R
import com.mirawuse.sportspot.presentation.ui.details.SpotDetailsScreen
import com.mirawuse.sportspot.presentation.ui.home.HomeScreen
import com.mirawuse.sportspot.presentation.ui.home.HomeViewModel
import com.mirawuse.sportspot.presentation.ui.theme.SportSpotSpacing
import kotlinx.serialization.Serializable

private const val APP_GRAPH = "sportspot"

@Serializable
internal data class SpotDetailsRoute(
    val source: String,
    val value: String,
) {
    val spotId: SpotId
        get() = SpotId(source, value)
}

private enum class Tab(
    val route: String,
    val label: Int,
    val icon: Int,
) {
    HOME("home", R.string.tab_home, R.drawable.ic_tab_home),
    MAP("map", R.string.tab_map, R.drawable.ic_tab_map),
    FAVORITES("favorites", R.string.tab_favorites, R.drawable.ic_tab_favorites),
    PROFILE("profile", R.string.tab_profile, R.drawable.ic_tab_profile),
}

@Composable
fun SportSpotApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val selectedRoute = backStackEntry?.destination?.route ?: Tab.HOME.route
    val isDetails = backStackEntry?.destination?.hasRoute<SpotDetailsRoute>() == true
    val colors = MaterialTheme.colorScheme

    Scaffold(
        bottomBar = {
            if (!isDetails) {
                Column {
                    HorizontalDivider(color = colors.outline.copy(alpha = 0.3f))
                    NavigationBar(containerColor = colors.surface) {
                        for (tab in Tab.entries) {
                            val selected = selectedRoute == tab.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    if (!selected) {
                                        navController.navigate(tab.route) {
                                            popUpTo(Tab.HOME.route) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                },
                                icon = { Icon(painter = painterResource(tab.icon), contentDescription = null) },
                                label = { Text(text = stringResource(tab.label), style = MaterialTheme.typography.labelSmall) },
                                colors =
                                    NavigationBarItemDefaults.colors(
                                        selectedIconColor = colors.onPrimary,
                                        selectedTextColor = colors.onSurface,
                                        indicatorColor = colors.primary,
                                        unselectedIconColor = colors.onSurfaceVariant,
                                        unselectedTextColor = colors.onSurfaceVariant,
                                    ),
                            )
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Tab.HOME.route,
            route = APP_GRAPH,
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        ) {
            composable(Tab.HOME.route) { entry ->
                val viewModel = searchViewModel(navController, entry)
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                HomeScreen(
                    state = state,
                    onRetry = viewModel::retry,
                    onOpenSpot = { navController.navigate(SpotDetailsRoute(it.source, it.value)) },
                )
            }
            composable(Tab.MAP.route) { ComingSoonScreen(Tab.MAP) }
            composable(Tab.FAVORITES.route) { ComingSoonScreen(Tab.FAVORITES) }
            composable(Tab.PROFILE.route) { ComingSoonScreen(Tab.PROFILE) }
            composable<SpotDetailsRoute> { entry ->
                val viewModel = searchViewModel(navController, entry)
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                SpotDetailsScreen(
                    spotId = entry.toRoute<SpotDetailsRoute>().spotId,
                    state = state,
                    onRetry = viewModel::retry,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}

@Composable
private fun searchViewModel(
    navController: NavHostController,
    entry: NavBackStackEntry,
): HomeViewModel {
    val graphEntry = remember(entry) { navController.getBackStackEntry(APP_GRAPH) }
    return hiltViewModel(graphEntry)
}

@Composable
private fun ComingSoonScreen(tab: Tab) {
    Column(
        modifier = Modifier.fillMaxSize().padding(SportSpotSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(SportSpotSpacing.sm, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(tab.label),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.section_coming_soon),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
    }
}
