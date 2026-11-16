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
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.mirawuse.sportspot.domain.model.spot.SpotId
import com.mirawuse.sportspot.presentation.R
import com.mirawuse.sportspot.presentation.ui.account.AuthScreen
import com.mirawuse.sportspot.presentation.ui.account.ProfileScreen
import com.mirawuse.sportspot.presentation.ui.details.SpotDetailsScreen
import com.mirawuse.sportspot.presentation.ui.home.HomeScreen
import com.mirawuse.sportspot.presentation.ui.home.HomeUiState
import com.mirawuse.sportspot.presentation.ui.home.HomeViewModel
import com.mirawuse.sportspot.presentation.ui.map.MapScreen
import com.mirawuse.sportspot.presentation.ui.proposal.ProposalScreen
import com.mirawuse.sportspot.presentation.ui.reviews.ReviewFormScreen
import com.mirawuse.sportspot.presentation.ui.reviews.ReviewsScreen
import com.mirawuse.sportspot.presentation.ui.theme.SportSpotSpacing
import kotlinx.serialization.Serializable

private const val APP_GRAPH = "sportspot"
private const val HOME_ROUTE = "home"
private const val PROPOSAL_ROUTE = "proposal"
private const val AUTH_ROUTE = "auth"

@Serializable
internal data class SpotDetailsRoute(
    val source: String,
    val value: String,
) {
    val spotId: SpotId
        get() = SpotId(source, value)
}

@Serializable
internal data class MapRoute(
    val source: String? = null,
    val value: String? = null,
) {
    val spotId: SpotId?
        get() = if (source != null && value != null) SpotId(source, value) else null
}

@Serializable
internal data class ReviewsRoute(
    val source: String,
    val value: String,
) {
    val spotId: SpotId
        get() = SpotId(source, value)
}

@Serializable
internal data class ReviewFormRoute(
    val source: String,
    val value: String,
) {
    val spotId: SpotId
        get() = SpotId(source, value)
}

private enum class Tab(
    val route: String?,
    val label: Int,
    val icon: Int,
) {
    HOME(HOME_ROUTE, R.string.tab_home, R.drawable.ic_tab_home),
    MAP(null, R.string.tab_map, R.drawable.ic_tab_map),
    FAVORITES("favorites", R.string.tab_favorites, R.drawable.ic_tab_favorites),
    PROFILE("profile", R.string.tab_profile, R.drawable.ic_tab_profile),
}

@Composable
fun SportSpotApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val selectedTab =
        if (destination?.hasRoute<MapRoute>() == true) {
            Tab.MAP
        } else {
            Tab.entries.firstOrNull { it.route != null && it.route == destination?.route } ?: Tab.HOME
        }
    val showTabs = destination == null || destination.hasRoute<MapRoute>() || Tab.entries.any { it.route == destination.route }
    val colors = MaterialTheme.colorScheme

    Scaffold(
        bottomBar = {
            if (showTabs) {
                Column {
                    HorizontalDivider(color = colors.outline.copy(alpha = 0.3f))
                    NavigationBar(containerColor = colors.surface) {
                        for (tab in Tab.entries) {
                            val selected = selectedTab == tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    if (!selected) {
                                        navController.selectTab(tab)
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
            startDestination = HOME_ROUTE,
            route = APP_GRAPH,
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        ) {
            composable(HOME_ROUTE) { entry ->
                val viewModel = searchViewModel(navController, entry)
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                HomeScreen(
                    state = state,
                    onRetry = viewModel::retry,
                    onOpenSpot = { navController.navigate(SpotDetailsRoute(it.source, it.value)) },
                    onOpenMap = { navController.selectTab(Tab.MAP) },
                )
            }
            composable<MapRoute> { entry ->
                val viewModel = searchViewModel(navController, entry)
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                MapScreen(
                    state = state,
                    focus = entry.toRoute<MapRoute>().spotId,
                    onRetry = viewModel::retry,
                    onOpenSpot = { navController.navigate(SpotDetailsRoute(it.source, it.value)) },
                )
            }
            composable("favorites") { ComingSoonScreen(Tab.FAVORITES) }
            composable("profile") {
                ProfileScreen(
                    onBack = { navController.popBackStack() },
                    onOpenAuth = { navController.navigate(AUTH_ROUTE) },
                    onPropose = { navController.navigate(PROPOSAL_ROUTE) },
                )
            }
            composable(PROPOSAL_ROUTE) {
                ProposalScreen(
                    onBack = { navController.popBackStack() },
                    onOpenAuth = { navController.navigate(AUTH_ROUTE) },
                )
            }
            composable(AUTH_ROUTE) { AuthScreen(onBack = { navController.popBackStack() }) }
            composable<SpotDetailsRoute> { entry ->
                val viewModel = searchViewModel(navController, entry)
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                SpotDetailsScreen(
                    spotId = entry.toRoute<SpotDetailsRoute>().spotId,
                    state = state,
                    onRetry = viewModel::retry,
                    onBack = { navController.popBackStack() },
                    onOpenReviews = { navController.navigate(ReviewsRoute(it.source, it.value)) },
                    onShowMap = { id ->
                        navController.clearBackStack<MapRoute>()
                        navController.navigate(MapRoute(id.source, id.value)) {
                            popUpTo(HOME_ROUTE)
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable<ReviewsRoute> { entry ->
                val route = entry.toRoute<ReviewsRoute>()
                val viewModel = searchViewModel(navController, entry)
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                ReviewsScreen(
                    spotName = (state as? HomeUiState.Content)?.findSpot(route.spotId)?.name,
                    onBack = { navController.popBackStack() },
                    onWrite = { navController.navigate(ReviewFormRoute(route.source, route.value)) },
                )
            }
            composable<ReviewFormRoute> { entry ->
                val route = entry.toRoute<ReviewFormRoute>()
                val viewModel = searchViewModel(navController, entry)
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                ReviewFormScreen(
                    spotName = (state as? HomeUiState.Content)?.findSpot(route.spotId)?.name,
                    onBack = { navController.popBackStack() },
                    onOpenAuth = { navController.navigate(AUTH_ROUTE) },
                )
            }
        }
    }
}

private fun NavHostController.selectTab(tab: Tab) {
    val options: NavOptionsBuilder.() -> Unit = {
        popUpTo(HOME_ROUTE) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
    val route = tab.route
    if (route == null) {
        navigate(MapRoute(), options)
    } else {
        navigate(route, options)
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
