package dev.chardoncs.ezmpv.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.chardoncs.ezmpv.EzmpvApplication
import dev.chardoncs.ezmpv.ui.screens.BrowseScreen
import dev.chardoncs.ezmpv.ui.screens.FileBrowserScreen
import dev.chardoncs.ezmpv.ui.screens.LibraryScreen
import dev.chardoncs.ezmpv.ui.screens.MiniPlayerBar
import dev.chardoncs.ezmpv.ui.screens.NowPlayingScreen
import dev.chardoncs.ezmpv.ui.screens.PlaceholderScreen
import android.net.Uri
import kotlin.math.roundToInt

private const val PLAYER_ENTER_DURATION = 360
private const val PLAYER_EXIT_DURATION = 240
private const val PLAYER_DISMISS_THRESHOLD_DP = 96

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun EzmpvApp() {
    val context = LocalContext.current
    val controller = remember {
        (context.applicationContext as EzmpvApplication).playerController
    }
    val state by controller.state.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    var playerOpen by rememberSaveable { mutableStateOf(false) }
    val isLandscape = LocalConfiguration.current.orientation ==
        android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val view = LocalView.current
    val hasTrack = state.playlist.isNotEmpty() && state.currentIndex >= 0
    val hideSystemBars = playerOpen && isLandscape
    var swipeOffset by remember { mutableStateOf(0f) }
    val swipeThreshold = with(LocalDensity.current) {
        PLAYER_DISMISS_THRESHOLD_DP.dp.toPx()
    }

    LaunchedEffect(playerOpen) {
        swipeOffset = 0f
    }

    DisposableEffect(view, hideSystemBars) {
        val insetsController = WindowCompat.getInsetsController(
            (view.context as Activity).window,
            view,
        )
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (hideSystemBars) {
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            insetsController.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            insetsController.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    val keepScreenOn = playerOpen &&
        state.hasVideo &&
        !state.audioOnly &&
        state.isPlaying

    DisposableEffect(view, keepScreenOn) {
        view.keepScreenOn = keepScreenOn
        onDispose {
            view.keepScreenOn = false
        }
    }

    SharedTransitionLayout(Modifier.fillMaxSize()) {
        val sharedScope = this
        Box(modifier = Modifier.fillMaxSize()) {
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
                        onOpenPlayer = { playerOpen = true },
                        modifier = Modifier.weight(1f),
                    )
                    if (hasTrack) {
                        AnimatedVisibility(
                            visible = !playerOpen,
                            enter = fadeIn(tween(200)),
                            exit = fadeOut(tween(150)),
                        ) {
                            Modifier.MiniPlayerBar(
                                controller = controller,
                                onClick = { playerOpen = true },
                                sharedTransitionScope = sharedScope,
                                animatedVisibilityScope = this,
                            )
                        }
                    }
                }
            }

            if (hasTrack) {
                AnimatedVisibility(
                    visible = playerOpen,
                    modifier = Modifier
                        .fillMaxSize()
                        .offset { IntOffset(0, swipeOffset.roundToInt()) }
                        .graphicsLayer {
                            alpha = 1f - (swipeOffset / size.height).coerceIn(0f, 0.35f)
                        }
                        .pointerInput(playerOpen) {
                            detectVerticalDragGestures(
                                onVerticalDrag = { change, dragAmount ->
                                    if (dragAmount > 0f || swipeOffset > 0f) {
                                        change.consume()
                                        swipeOffset = (swipeOffset + dragAmount).coerceAtLeast(0f)
                                    }
                                },
                                onDragEnd = {
                                    if (swipeOffset >= swipeThreshold) {
                                        playerOpen = false
                                    } else {
                                        swipeOffset = 0f
                                    }
                                },
                                onDragCancel = {
                                    swipeOffset = 0f
                                },
                            )
                        },
                    enter = fadeIn(
                        animationSpec = tween(
                            durationMillis = PLAYER_ENTER_DURATION,
                            easing = FastOutSlowInEasing,
                        ),
                    ),
                    exit = fadeOut(
                        animationSpec = tween(
                            durationMillis = PLAYER_EXIT_DURATION,
                            easing = FastOutSlowInEasing,
                        ),
                    ),
                ) {
                    NowPlayingScreen(
                        controller = controller,
                        onBack = { playerOpen = false },
                        sharedTransitionScope = sharedScope,
                        animatedVisibilityScope = this,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }

    BackHandler(enabled = playerOpen) {
        playerOpen = false
    }
}

@Composable
private fun EzmpvNavHost(
    navController: NavHostController,
    controller: dev.chardoncs.ezmpv.player.PlayerController,
    onOpenPlayer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = TopLevelDestination.BROWSE.route,
        modifier = modifier,
    ) {
        composable(route = "file_browser/{treeUri}/{title}") { entry ->
            val treeUri = Uri.parse(entry.arguments?.getString("treeUri"))
            val title = entry.arguments?.getString("title") ?: "Folder"
            FileBrowserScreen(
                rootTreeUri = treeUri,
                rootTitle = title,
                onOpenPlayer = onOpenPlayer,
                onExit = { navController.popBackStack() },
            )
        }
        TopLevelDestination.entries.forEach { destination ->
            composable(route = destination.route) {
                when (destination) {
                    TopLevelDestination.LIBRARY -> LibraryScreen(
                        controller = controller,
                        onOpenPlayer = onOpenPlayer,
                    )
                    TopLevelDestination.BROWSE -> BrowseScreen(
                        onOpenBrowser = { treeUri, title ->
                            navController.navigate("file_browser/${Uri.encode(treeUri.toString())}/${Uri.encode(title)}")
                        },
                    )
                    else -> PlaceholderScreen(destination = destination)
                }
            }
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