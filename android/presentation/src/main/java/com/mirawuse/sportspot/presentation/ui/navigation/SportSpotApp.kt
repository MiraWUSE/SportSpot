package com.mirawuse.sportspot.presentation.ui.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
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
import com.mirawuse.sportspot.domain.model.auth.AuthState
import com.mirawuse.sportspot.domain.model.spot.SpotId
import com.mirawuse.sportspot.presentation.R
import com.mirawuse.sportspot.presentation.ui.account.AuthScreen
import com.mirawuse.sportspot.presentation.ui.account.AuthViewModel
import com.mirawuse.sportspot.presentation.ui.account.FavoritesScreen
import com.mirawuse.sportspot.presentation.ui.account.FavoritesUiState
import com.mirawuse.sportspot.presentation.ui.account.FavoritesViewModel
import com.mirawuse.sportspot.presentation.ui.account.ProfileScreen
import com.mirawuse.sportspot.presentation.ui.account.labelRes
import com.mirawuse.sportspot.presentation.ui.account.visibleFor
import com.mirawuse.sportspot.presentation.ui.components.SportSpotButton
import com.mirawuse.sportspot.presentation.ui.components.SportSpotButtonVariant
import com.mirawuse.sportspot.presentation.ui.details.SpotDetailsScreen
import com.mirawuse.sportspot.presentation.ui.home.HomeScreen
import com.mirawuse.sportspot.presentation.ui.home.HomeUiState
import com.mirawuse.sportspot.presentation.ui.home.HomeViewModel
import com.mirawuse.sportspot.presentation.ui.map.MapScreen
import com.mirawuse.sportspot.presentation.ui.proposal.ProposalScreen
import com.mirawuse.sportspot.presentation.ui.reviews.ReviewFormScreen
import com.mirawuse.sportspot.presentation.ui.reviews.ReviewsScreen
import kotlinx.serialization.Serializable

private const val APP_GRAPH = "sportspot"
private const val HOME_ROUTE = "home"
private const val PROPOSAL_ROUTE = "proposal"
private const val AUTH_ROUTE = "auth"

@Serializable
internal data class SpotDetailsRoute(
    val source: String,
    val value: String,
    val favorite: Boolean = false,
) {
    val spotId: SpotId
        get() = SpotId(source, value)
}

@Serializable
internal data class MapRoute(
    val source: String? = null,
    val value: String? = null,
    val favorite: Boolean = false,
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
    val authViewModel: AuthViewModel = hiltViewModel()
    val favorites: FavoritesViewModel = hiltViewModel()
    val authState by authViewModel.authState.collectAsStateWithLifecycle()
    val favoriteState = favorites.state.visibleFor(authState)
    val favoriteList = (favoriteState as? FavoritesUiState.Content)?.list?.items.orEmpty()
    val savedIds = favoriteList.mapTo(mutableSetOf()) { it.spotId }
    val favoriteEnabled = !authViewModel.restoring && favoriteState !is FavoritesUiState.Loading && favorites.busySpot == null
    val favoriteError = stringResource(R.string.favorites_load_error)
    val snackbar = remember { SnackbarHostState() }
    val failureMessage = favorites.actionError?.takeIf { favoriteState.owner != null }?.let { stringResource(it.labelRes()) }
    LaunchedEffect(failureMessage, favoriteState.owner) {
        if (failureMessage != null) {
            snackbar.showSnackbar(failureMessage)
            favorites.clearActionError()
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { authViewModel.restore() }
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
        snackbarHost = { SnackbarHost(snackbar) },
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
                val location by viewModel.location.collectAsStateWithLifecycle()
                HomeScreen(
                    state = state,
                    location = location,
                    onLocate = viewModel::useDeviceLocation,
                    onPermissionDenied = viewModel::permissionDenied,
                    onCancelLocation = viewModel::cancelLocation,
                    onRetry = viewModel::retry,
                    onOpenSpot = { navController.navigate(SpotDetailsRoute(it.source, it.value)) },
                    onOpenMap = { navController.selectTab(Tab.MAP) },
                    signedIn = authState is AuthState.Authenticated,
                    savedSpotIds = savedIds,
                    favoriteEnabled = favoriteEnabled,
                    onFavorite = favorites::toggle,
                    onOpenAuth = { navController.navigate(AUTH_ROUTE) },
                )
            }
            composable<MapRoute> { entry ->
                val route = entry.toRoute<MapRoute>()
                val viewModel = searchViewModel(navController, entry)
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                val location by viewModel.location.collectAsStateWithLifecycle()
                val savedState =
                    if (route.favorite) favoriteDestinationState(favoriteState, route.spotId, favoriteError) else null
                val privateState = savedState?.takeUnless { it == HomeUiState.Empty }
                val savedSpot = (privateState as? HomeUiState.Content)?.spots?.singleOrNull()
                MapScreen(
                    state = privateState ?: state,
                    center = savedSpot?.location ?: location.center,
                    locationLabel = if (savedSpot == null) stringResource(location.labelRes) else savedSpot.address.orEmpty(),
                    focus = route.spotId.takeIf { !route.favorite || privateState != null },
                    onRetry = if (privateState == null) viewModel::retry else favorites::retry,
                    onOpenSpot = { navController.navigate(SpotDetailsRoute(it.source, it.value, favorite = privateState != null)) },
                )
            }
            composable("favorites") {
                FavoritesScreen(
                    state = favoriteState,
                    restoring = authViewModel.restoring,
                    busy = favorites.busySpot != null,
                    onOpenAuth = { navController.navigate(AUTH_ROUTE) },
                    onRetry = favorites::retry,
                    onOpenSpot = { navController.navigate(SpotDetailsRoute(it.source, it.value, favorite = true)) },
                    onOpenMap = { navController.navigate(MapRoute(it.source, it.value, favorite = true)) },
                    onRemove = favorites::removeSpot,
                )
            }
            composable("profile") {
                ProfileScreen(
                    state = authState,
                    viewModel = authViewModel,
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
            composable(AUTH_ROUTE) {
                AuthScreen(authState, authViewModel) {
                    if (!authViewModel.submitting || authState is AuthState.Authenticated) {
                        authViewModel.clearNotice()
                        navController.popBackStack()
                    }
                }
            }
            composable<SpotDetailsRoute> { entry ->
                val route = entry.toRoute<SpotDetailsRoute>()
                val viewModel = searchViewModel(navController, entry)
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                SpotDetailsScreen(
                    spotId = route.spotId,
                    state = if (route.favorite) favoriteDestinationState(favoriteState, route.spotId, favoriteError) else state,
                    onRetry = if (route.favorite) favorites::retry else viewModel::retry,
                    onBack = { navController.popBackStack() },
                    onOpenReviews = { navController.navigate(ReviewsRoute(it.source, it.value)) },
                    onShowMap = { id ->
                        if (route.favorite) {
                            navController.navigate(MapRoute(id.source, id.value, favorite = true)) { launchSingleTop = true }
                        } else {
                            navController.clearBackStack<MapRoute>()
                            navController.navigate(MapRoute(id.source, id.value)) {
                                popUpTo(HOME_ROUTE)
                                launchSingleTop = true
                            }
                        }
                    },
                    favoriteAction = { spot ->
                        SportSpotButton(
                            text = stringResource(if (spot.id in savedIds) R.string.favorites_remove else R.string.favorites_add),
                            onClick = {
                                if (authState is AuthState.Authenticated) favorites.toggle(spot) else navController.navigate(AUTH_ROUTE)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            variant = SportSpotButtonVariant.OUTLINED,
                            enabled = favoriteEnabled,
                        )
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

internal fun favoriteDestinationState(
    state: FavoritesUiState,
    spotId: SpotId?,
    errorMessage: String,
): HomeUiState =
    when (state) {
        FavoritesUiState.Guest -> HomeUiState.Empty
        is FavoritesUiState.Loading -> HomeUiState.Loading
        is FavoritesUiState.Error -> HomeUiState.Error(errorMessage)
        is FavoritesUiState.Content -> {
            val spot =
                state.list.items
                    .firstOrNull { it.spotId == spotId }
                    ?.spot
            if (spot == null) HomeUiState.Empty else HomeUiState.Content(listOf(spot))
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
