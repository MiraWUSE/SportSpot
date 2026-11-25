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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LifecycleResumeEffect
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
import com.mirawuse.sportspot.domain.model.photo.PhotoTarget
import com.mirawuse.sportspot.domain.model.photo.PhotoTargetType
import com.mirawuse.sportspot.domain.model.proposal.ProposalStatus
import com.mirawuse.sportspot.domain.model.spot.Spot
import com.mirawuse.sportspot.domain.model.spot.SpotId
import com.mirawuse.sportspot.domain.policy.AuthorizationPolicy
import com.mirawuse.sportspot.domain.result.DomainResult
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
import com.mirawuse.sportspot.presentation.ui.components.sportLabel
import com.mirawuse.sportspot.presentation.ui.details.SpotDetailsScreen
import com.mirawuse.sportspot.presentation.ui.forms.FormScreen
import com.mirawuse.sportspot.presentation.ui.home.HomeScreen
import com.mirawuse.sportspot.presentation.ui.home.HomeUiState
import com.mirawuse.sportspot.presentation.ui.home.HomeViewModel
import com.mirawuse.sportspot.presentation.ui.map.MapAnchor
import com.mirawuse.sportspot.presentation.ui.map.MapScreen
import com.mirawuse.sportspot.presentation.ui.map.MapViewModel
import com.mirawuse.sportspot.presentation.ui.moderation.ModerationScreen
import com.mirawuse.sportspot.presentation.ui.moderation.ModerationViewModel
import com.mirawuse.sportspot.presentation.ui.photos.PhotoActionButton
import com.mirawuse.sportspot.presentation.ui.photos.PhotoEditor
import com.mirawuse.sportspot.presentation.ui.photos.PhotoGallery
import com.mirawuse.sportspot.presentation.ui.photos.PhotoHistory
import com.mirawuse.sportspot.presentation.ui.photos.PhotoModeration
import com.mirawuse.sportspot.presentation.ui.photos.PhotosViewModel
import com.mirawuse.sportspot.presentation.ui.photos.ProposalPhotoEvidence
import com.mirawuse.sportspot.presentation.ui.photos.SpotUserPhotos
import com.mirawuse.sportspot.presentation.ui.proposal.MyProposalsScreen
import com.mirawuse.sportspot.presentation.ui.proposal.ProposalScreen
import com.mirawuse.sportspot.presentation.ui.proposal.ProposalsViewModel
import com.mirawuse.sportspot.presentation.ui.reviews.ReviewFormScreen
import com.mirawuse.sportspot.presentation.ui.reviews.ReviewScore
import com.mirawuse.sportspot.presentation.ui.reviews.ReviewsScreen
import com.mirawuse.sportspot.presentation.ui.reviews.ReviewsState
import com.mirawuse.sportspot.presentation.ui.reviews.ReviewsViewModel
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

private const val APP_GRAPH = "sportspot"
private const val HOME_ROUTE = "home"
private const val PROPOSAL_ROUTE = "proposal"
private const val MY_PROPOSALS_ROUTE = "my-proposals"
private const val MODERATION_ROUTE = "moderation"
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
    val mapViewModel: MapViewModel = hiltViewModel()
    val openedMapSpot by mapViewModel.openedSpot.collectAsStateWithLifecycle()
    val authState by authViewModel.authState.collectAsStateWithLifecycle()
    val reviews: ReviewsViewModel = hiltViewModel()
    val proposals: ProposalsViewModel = hiltViewModel()
    val accountId = (authState as? AuthState.Authenticated)?.profile?.id
    val moderatorId = accountId.takeIf { AuthorizationPolicy.moderatorError(authState) == null && !authViewModel.restoring }
    val favoriteState = favorites.state.visibleFor(authState)
    val favoriteList = (favoriteState as? FavoritesUiState.Content)?.list?.items.orEmpty()
    val savedIds = favoriteList.mapTo(mutableSetOf()) { it.spotId }
    val favoriteEnabled = !authViewModel.restoring && favoriteState !is FavoritesUiState.Loading && favorites.busySpot == null
    val favoriteError = stringResource(R.string.favorites_load_error)
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val routeScope = rememberCoroutineScope()
    val routeError = stringResource(R.string.spot_route_unavailable)
    val routeLauncher =
        remember(context, snackbar, routeScope, routeError) {
            WalkingRouteLauncher(
                startActivity = context::startActivity,
                onUnavailable = { routeScope.launch { snackbar.showSnackbar(routeError) } },
            )
        }
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
                val filter by viewModel.searchFilter.collectAsStateWithLifecycle()
                HomeScreen(
                    state = state,
                    location = location,
                    onLocate = viewModel::useDeviceLocation,
                    onPermissionDenied = viewModel::permissionDenied,
                    onCancelLocation = viewModel::cancelLocation,
                    onRetry = viewModel::retry,
                    onOpenSpot = { navController.navigate(SpotDetailsRoute(it.source, it.value)) },
                    onOpenMap = { navController.selectTab(Tab.MAP) },
                    filter = filter,
                    onSearchQuery = viewModel::setSearchQuery,
                    onSearchSport = viewModel::setSearchSport,
                    onResetSearch = viewModel::resetSearchFilter,
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
                val filter by viewModel.searchFilter.collectAsStateWithLifecycle()
                val anchor = MapAnchor(location.center, route.spotId)
                if (!route.favorite) {
                    LifecycleResumeEffect(anchor) {
                        mapViewModel.activate(anchor)
                        onPauseOrDispose { mapViewModel.deactivate(anchor) }
                    }
                }
                val explored by mapViewModel.state.collectAsStateWithLifecycle()
                val viewport = explored.takeIf { !route.favorite && it.anchor == anchor }
                val seed =
                    if (route.favorite) {
                        favoriteDestinationState(favoriteState, route.spotId, favoriteError)
                    } else {
                        state.withOpenedMapSpot(route.spotId, openedMapSpot)
                    }
                val savedSpot = if (route.favorite) (seed as? HomeUiState.Content)?.spots?.singleOrNull() else null
                val refreshMap = mapViewModel::retry
                MapScreen(
                    state = seed,
                    center = savedSpot?.location ?: location.center,
                    locationLabel = if (savedSpot == null) stringResource(location.labelRes) else savedSpot.address.orEmpty(),
                    focus = route.spotId,
                    onRetry = if (route.favorite) favorites::retry else refreshMap,
                    onOpenSpot = { id ->
                        if (!route.favorite) {
                            val spots = viewport?.spots ?: (seed as? HomeUiState.Content)?.spots.orEmpty()
                            mapViewModel.openSpot(spots.firstOrNull { it.id == id })
                        }
                        navController.navigate(SpotDetailsRoute(id.source, id.value, favorite = route.favorite))
                    },
                    onRefresh = refreshMap.takeUnless { route.favorite },
                    viewport = viewport,
                    onViewportMoveStarted = if (route.favorite) null else mapViewModel::onMoveStarted,
                    onViewportIdle = if (route.favorite) null else mapViewModel::onViewportIdle,
                    filter = filter.takeUnless { route.favorite },
                    onSearchQuery = viewModel::setSearchQuery,
                    onSearchSport = viewModel::setSearchSport,
                    onResetSearch = viewModel::resetSearchFilter,
                    showVisibleSpots = !route.favorite,
                    onBuildRoute = routeLauncher::open,
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
                    onPropose = {
                        proposals.clearSubmission()
                        navController.navigate(PROPOSAL_ROUTE)
                    },
                    onMyProposals = { navController.navigate(MY_PROPOSALS_ROUTE) },
                    onModeration = moderatorId?.let { { navController.navigate(MODERATION_ROUTE) } },
                )
            }
            composable(PROPOSAL_ROUTE) { entry ->
                val photos: PhotosViewModel = hiltViewModel(entry)
                val submission = proposals.submissionFor(accountId)
                val saved = (submission as? DomainResult.Success)?.value
                val ready = photos.readyFor(accountId)
                LaunchedEffect(accountId, saved?.id, ready) {
                    if (ready && accountId != null && saved != null) photos.afterParentSaved(accountId, PhotoTarget.proposal(saved.id))
                }
                ProposalScreen(
                    owner = accountId,
                    restoring = authViewModel.restoring,
                    busy = proposals.submitting || (accountId?.let { photos.draftFor(it).busy } == true),
                    submission = submission,
                    onBack = { navController.popBackStack() },
                    onOpenAuth = { navController.navigate(AUTH_ROUTE) },
                    onViewProposals = { navController.navigate(MY_PROPOSALS_ROUTE) },
                    onSubmit = { name, sport, point, address ->
                        accountId?.let { proposals.submit(it, name, sport, point, address) }
                    },
                    photoAttachments = { proposal ->
                        if (accountId != null) {
                            PhotoEditor(
                                accountId,
                                PhotoTargetType.PROPOSAL.limit,
                                photos,
                                target = proposal?.let { PhotoTarget.proposal(it.id) },
                                enabled = !proposals.submitting,
                            )
                        }
                    },
                )
            }
            composable(MY_PROPOSALS_ROUTE) { entry ->
                val photos: PhotosViewModel = hiltViewModel(entry)
                var editingPhotosFor by rememberSaveable(accountId) { mutableStateOf<String?>(null) }
                LaunchedEffect(accountId) { proposals.load(accountId) }
                LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { proposals.load(accountId) }
                MyProposalsScreen(
                    signedIn = accountId != null,
                    restoring = authViewModel.restoring,
                    state = proposals.listFor(accountId),
                    onBack = { navController.popBackStack() },
                    onOpenAuth = { navController.navigate(AUTH_ROUTE) },
                    onRetry = { proposals.load(accountId) },
                    photoHistory = { proposal ->
                        if (accountId != null) {
                            val target = PhotoTarget.proposal(proposal.id)
                            if (editingPhotosFor == proposal.id) {
                                val draft = photos.draftFor(accountId)
                                if (proposal.status != ProposalStatus.REJECTED) {
                                    PhotoEditor(accountId, target.type.limit, photos, target)
                                } else {
                                    PhotoHistory(accountId, target, photos)
                                }
                                val closeLabel = if (draft.attachments.isEmpty()) R.string.photos_done else R.string.content_cancel
                                PhotoActionButton(closeLabel, enabled = !draft.busy) {
                                    photos.clearDraft(accountId)
                                    editingPhotosFor = null
                                }
                            } else {
                                PhotoHistory(accountId, target, photos)
                                if (proposal.status != ProposalStatus.REJECTED) {
                                    PhotoActionButton(R.string.photos_add, enabled = editingPhotosFor == null) {
                                        editingPhotosFor = proposal.id
                                    }
                                }
                            }
                        }
                    },
                )
            }
            composable(MODERATION_ROUTE) { entry ->
                val moderation: ModerationViewModel = hiltViewModel(entry)
                val photos: PhotosViewModel = hiltViewModel(entry)
                val search = searchViewModel(navController, entry)
                LaunchedEffect(moderatorId) { moderation.load(moderatorId) }
                ModerationScreen(
                    auth = authState,
                    restoring = authViewModel.restoring,
                    pending = moderation.pendingFor(moderatorId),
                    busy = moderation.busy,
                    actionError = moderation.errorFor(moderatorId),
                    onBack = { navController.popBackStack() },
                    onRetry = { moderation.load(moderatorId) },
                    onApprove = { proposalId ->
                        moderatorId?.let { moderation.decide(it, proposalId, onPublished = search::refresh) }
                    },
                    onReject = { proposalId, reason ->
                        moderatorId?.let { moderation.decide(it, proposalId, reason) }
                    },
                    photoQueue = moderatorId?.let { owner -> { PhotoModeration(owner, photos) } },
                    proposalPhotos = { proposal -> moderatorId?.let { ProposalPhotoEvidence(it, proposal.id, photos) } },
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
                val photos: PhotosViewModel = hiltViewModel(entry)
                val viewModel = searchViewModel(navController, entry)
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                val resources = LocalResources.current
                val detailsState =
                    if (route.favorite) {
                        favoriteDestinationState(favoriteState, route.spotId, favoriteError)
                    } else {
                        state.withOpenedMapSpot(route.spotId, openedMapSpot)
                    }
                LaunchedEffect(route.spotId) { reviews.load(route.spotId) }
                SpotDetailsScreen(
                    spotId = route.spotId,
                    state = detailsState,
                    onRetry = if (route.favorite) favorites::retry else viewModel::retry,
                    onBack = { navController.popBackStack() },
                    onOpenReviews = { navController.navigate(ReviewsRoute(it.source, it.value)) },
                    onShowMap = { id ->
                        if (route.favorite) {
                            navController.navigate(MapRoute(id.source, id.value, favorite = true)) { launchSingleTop = true }
                        } else {
                            (detailsState as? HomeUiState.Content)?.findSpot(id)?.let { spot ->
                                viewModel.clearConflictingSearchFilter(spot) { it.sportLabel(resources::getString) }
                            }
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
                    reviewSummary = {
                        (reviews.stateFor(route.spotId) as? ReviewsState.Content)?.let { ReviewScore(it.items) }
                    },
                    userPhotos = { spot ->
                        runCatching { PhotoTarget.spot(spot.id) }.getOrNull()?.let { target ->
                            SpotUserPhotos(accountId, target, photos, onOpenAuth = { navController.navigate(AUTH_ROUTE) })
                        }
                    },
                    onBuildRoute = routeLauncher::open,
                )
            }
            composable<ReviewsRoute> { entry ->
                val route = entry.toRoute<ReviewsRoute>()
                val photos: PhotosViewModel = hiltViewModel(entry)
                val viewModel = searchViewModel(navController, entry)
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                LaunchedEffect(route.spotId) { reviews.load(route.spotId) }
                ReviewsScreen(
                    spotName =
                        (state as? HomeUiState.Content)?.findSpot(route.spotId)?.name
                            ?: openedMapSpot?.takeIf { it.id == route.spotId }?.name
                            ?: favoriteList.firstOrNull { it.spotId == route.spotId }?.spot?.name,
                    state = reviews.stateFor(route.spotId),
                    owner = accountId,
                    busy = reviews.busy,
                    error = reviews.errorFor(accountId),
                    onBack = { navController.popBackStack() },
                    onWrite = {
                        reviews.clearError()
                        navController.navigate(ReviewFormRoute(route.source, route.value))
                    },
                    onDelete = { review -> accountId?.let { reviews.delete(it, review) } },
                    onRetry = { reviews.load(route.spotId, refresh = true) },
                    reviewPhotos = { review -> PhotoGallery(PhotoTarget.review(review.id), photos, collapsible = true) },
                )
            }
            composable<ReviewFormRoute> { entry ->
                val route = entry.toRoute<ReviewFormRoute>()
                val photos: PhotosViewModel = hiltViewModel(entry)
                val savedTarget = photos.savedFor(accountId)
                if (savedTarget != null && accountId != null) {
                    val busy = photos.draftFor(accountId).busy
                    FormScreen(stringResource(R.string.photos_review_saved), onBack = {
                        if (!busy) navController.popBackStack()
                    }) {
                        PhotoEditor(accountId, PhotoTargetType.REVIEW.limit, photos, savedTarget)
                        SportSpotButton(stringResource(R.string.photos_done), { navController.popBackStack() }, enabled = !busy)
                    }
                    return@composable
                }
                val viewModel = searchViewModel(navController, entry)
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                LaunchedEffect(route.spotId) { reviews.load(route.spotId) }
                ReviewFormScreen(
                    spotName =
                        (state as? HomeUiState.Content)?.findSpot(route.spotId)?.name
                            ?: openedMapSpot?.takeIf { it.id == route.spotId }?.name
                            ?: favoriteList.firstOrNull { it.spotId == route.spotId }?.spot?.name,
                    state = reviews.stateFor(route.spotId),
                    owner = accountId,
                    restoring = authViewModel.restoring,
                    busy = reviews.busy || (accountId?.let { photos.draftFor(it).busy } == true),
                    error = reviews.errorFor(accountId),
                    onBack = { navController.popBackStack() },
                    onOpenAuth = { navController.navigate(AUTH_ROUTE) },
                    onRetry = { reviews.load(route.spotId, refresh = true) },
                    onSubmit = { existing, rating, text, title ->
                        if (accountId != null) {
                            reviews.save(route.spotId, accountId, existing, rating, text, title) { saved ->
                                if (photos.draftFor(accountId).attachments.isNotEmpty()) {
                                    photos.afterParentSaved(accountId, PhotoTarget.review(saved.id))
                                } else if (navController.currentBackStackEntry == entry) {
                                    navController.popBackStack()
                                }
                            }
                        }
                    },
                    photoAttachments = { review ->
                        if (accountId != null) {
                            PhotoEditor(
                                accountId,
                                PhotoTargetType.REVIEW.limit,
                                photos,
                                target = review?.let { PhotoTarget.review(it.id) },
                                allowUpload = false,
                                enabled = !reviews.busy,
                            )
                        }
                    },
                )
            }
        }
    }
}

internal fun HomeUiState.withOpenedMapSpot(
    spotId: SpotId?,
    opened: Spot?,
): HomeUiState {
    val content = this as? HomeUiState.Content
    if (opened == null || opened.id != spotId || content?.findSpot(opened.id) != null) return this
    return HomeUiState.Content(listOf(opened) + content?.spots.orEmpty())
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
