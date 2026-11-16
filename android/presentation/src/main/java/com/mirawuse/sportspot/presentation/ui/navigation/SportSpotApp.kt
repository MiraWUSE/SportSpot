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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.mirawuse.sportspot.presentation.R
import com.mirawuse.sportspot.presentation.ui.home.HomeRoute
import com.mirawuse.sportspot.presentation.ui.theme.SportSpotSpacing

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
    val colors = MaterialTheme.colorScheme

    Scaffold(
        bottomBar = {
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
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Tab.HOME.route,
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        ) {
            composable(Tab.HOME.route) { HomeRoute() }
            composable(Tab.MAP.route) { ComingSoonScreen(Tab.MAP) }
            composable(Tab.FAVORITES.route) { ComingSoonScreen(Tab.FAVORITES) }
            composable(Tab.PROFILE.route) { ComingSoonScreen(Tab.PROFILE) }
        }
    }
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
