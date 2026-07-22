package dev.chardoncs.ezmpv.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.pointer.pointerInput
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
import dev.chardoncs.ezmpv.player.PlayerController
import dev.chardoncs.ezmpv.ui.screens.AudioScreen
import dev.chardoncs.ezmpv.ui.screens.MiniPlayerBar
import dev.chardoncs.ezmpv.ui.screens.NowPlayingScreen
import dev.chardoncs.ezmpv.ui.screens.PlaceholderScreen
import dev.chardoncs.ezmpv.ui.screens.VideoScreen
import kotlin.math.roundToInt

private const val PLAYER_ENTER_DURATION = 360
private const val PLAYER_EXIT_DURATION = 240
private const val PLAYER_FADE_IN_DELAY = 40
private const val PLAYER_FADE_IN_DURATION = 220
private const val PLAYER_FADE_OUT_DURATION = 160
private const val PLAYER_DISMISS_THRESHOLD_DP = 96

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
    val playerVisibilityState = remember { MutableTransitionState(false) }
    var playerOpen by rememberSaveable { mutableStateOf(false) }
    val isLandscape = LocalConfiguration.current.orientation ==
        android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val view = LocalView.current
    val hideSystemBars = playerOpen && isLandscape
    var swipeOffset by remember { mutableStateOf(0f) }
    val swipeThreshold = with(androidx.compose.ui.platform.LocalDensity.current) {
        PLAYER_DISMISS_THRESHOLD_DP.dp.toPx()
    }

    LaunchedEffect(playerOpen) {
        if (playerOpen) swipeOffset = 0f
    }

    SideEffect {
        playerVisibilityState.targetState = playerOpen
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

    val keepScreenOn = playerVisibilityState.targetState &&
        state.hasVideo &&
        !state.audioOnly &&
        state.isPlaying

    DisposableEffect(view, keepScreenOn) {
        view.keepScreenOn = keepScreenOn
        onDispose {
            view.keepScreenOn = false
        }
    }

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
                if (!playerVisibilityState.currentState && !playerVisibilityState.targetState) {
                    MiniPlayerBar(
                        controller = controller,
                        onClick = { playerOpen = true },
                    )
                }
            }
        }

        AnimatedVisibility(
            visibleState = playerVisibilityState,
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
            enter = slideInVertically(
                animationSpec = tween(PLAYER_ENTER_DURATION, easing = FastOutSlowInEasing),
                initialOffsetY = { it },
            ) + fadeIn(
                animationSpec = tween(
                    durationMillis = PLAYER_FADE_IN_DURATION,
                    delayMillis = PLAYER_FADE_IN_DELAY,
                    easing = FastOutSlowInEasing,
                ),
            ),
            exit = slideOutVertically(
                animationSpec = tween(PLAYER_EXIT_DURATION, easing = FastOutSlowInEasing),
                targetOffsetY = { it },
            ) + fadeOut(
                animationSpec = tween(
                    durationMillis = PLAYER_FADE_OUT_DURATION,
                    easing = FastOutSlowInEasing,
                ),
            ),
        ) {
            NowPlayingScreen(
                controller = controller,
                onBack = { playerOpen = false },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    BackHandler(enabled = playerOpen) {
        playerOpen = false
    }
}

@Composable
private fun EzmpvNavHost(
    navController: NavHostController,
    controller: PlayerController,
    onOpenPlayer: () -> Unit,
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
                        onOpenPlayer = onOpenPlayer,
                    )
                    TopLevelDestination.AUDIO -> AudioScreen(
                        controller = controller,
                        onOpenPlayer = onOpenPlayer,
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
