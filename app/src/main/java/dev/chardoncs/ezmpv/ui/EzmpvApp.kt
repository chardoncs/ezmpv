package dev.chardoncs.ezmpv.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.chardoncs.ezmpv.EzmpvApplication
import dev.chardoncs.ezmpv.player.PlayerController
import dev.chardoncs.ezmpv.ui.screens.AudioScreen
import dev.chardoncs.ezmpv.ui.screens.MiniPlayerBar
import dev.chardoncs.ezmpv.ui.screens.NowPlayingScreen
import dev.chardoncs.ezmpv.ui.screens.PlaceholderScreen
import dev.chardoncs.ezmpv.ui.screens.VideoScreen

const val NOW_PLAYING_ROUTE = "now_playing"

@Composable
fun EzmpvApp() {
    val context = LocalContext.current
    val controller = remember {
        (context.applicationContext as EzmpvApplication).playerController
    }
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            TopLevelDestination.entries.forEach { destination ->
                val selected = currentRoute == destination.route
                item(
                    selected = selected,
                    onClick = { navController.navigateTo(destination) },
                    icon = {
                        Icon(
                            imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                            contentDescription = stringResource(destination.labelRes),
                        )
                    },
                    label = { Text(stringResource(destination.labelRes)) },
                )
            }
        },
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            EzmpvNavHost(
                navController = navController,
                controller = controller,
                modifier = Modifier.weight(1f),
            )
            if (currentRoute != NOW_PLAYING_ROUTE) {
                MiniPlayerBar(
                    controller = controller,
                    onClick = { navController.navigate(NOW_PLAYING_ROUTE) },
                )
            }
        }
    }
}

@Composable
private fun EzmpvNavHost(
    navController: NavHostController,
    controller: PlayerController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = TopLevelDestination.BROWSE.route,
        modifier = modifier,
    ) {
        TopLevelDestination.entries.forEach { destination ->
            composable(route = destination.route) {
                when (destination) {
                    TopLevelDestination.VIDEO -> VideoScreen(
                        controller = controller,
                        onOpenPlayer = { navController.navigate(NOW_PLAYING_ROUTE) },
                    )
                    TopLevelDestination.AUDIO -> AudioScreen(
                        controller = controller,
                        onOpenPlayer = { navController.navigate(NOW_PLAYING_ROUTE) },
                    )
                    else -> PlaceholderScreen(destination = destination)
                }
            }
        }
        composable(route = NOW_PLAYING_ROUTE) {
            NowPlayingScreen(
                controller = controller,
                onBack = { navController.popBackStack() },
            )
        }
    }
}

private fun NavController.navigateTo(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
