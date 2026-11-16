package com.smsforward.app.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Settings
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.smsforward.app.ui.screens.AutoStartGuideScreen
import com.smsforward.app.ui.screens.MessagesScreen
import com.smsforward.app.ui.screens.RulesScreen
import com.smsforward.app.ui.screens.SettingsScreen
import kotlinx.coroutines.launch

object Routes {
    const val MESSAGES = "messages"
    const val RULES = "rules"
    const val SETTINGS = "settings"
    const val AUTOSTART = "autostart"
}

private data class TabItem(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    TabItem(Routes.MESSAGES, "收件箱", Icons.Rounded.Inbox),
    TabItem(Routes.RULES, "转发规则", Icons.AutoMirrored.Rounded.Send),
    TabItem(Routes.SETTINGS, "设置", Icons.Rounded.Settings),
)

@Composable
fun AppRoot(viewModel: AppViewModel) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val notifier: (String) -> Unit = remember(snackbarHostState) {
        { message ->
            scope.launch {
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar(message)
            }
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { notifier(it) }
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute == null || currentRoute in tabs.map { it.route }

    CompositionLocalProvider(LocalNotifier provides notifier) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                if (showBottomBar) {
                    Column {
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 0.dp,
                        ) {
                            tabs.forEach { tab ->
                                NavigationBarItem(
                                    selected = currentRoute == tab.route,
                                    onClick = {
                                        if (currentRoute == tab.route) return@NavigationBarItem
                                        navController.navigate(tab.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = { Icon(tab.icon, contentDescription = null) },
                                    label = { Text(tab.label) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
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
                startDestination = Routes.MESSAGES,
                modifier = Modifier.padding(innerPadding),
                // 页面切换：淡入 + 轻微放大
                enterTransition = {
                    fadeIn(tween(240)) + scaleIn(tween(240), initialScale = 0.97f)
                },
                exitTransition = { fadeOut(tween(160)) },
                popEnterTransition = { fadeIn(tween(220)) },
                popExitTransition = {
                    fadeOut(tween(160)) + scaleOut(tween(160), targetScale = 0.97f)
                },
            ) {
                composable(Routes.MESSAGES) { MessagesScreen(viewModel) }
                composable(Routes.RULES) { RulesScreen(viewModel) }
                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        viewModel = viewModel,
                        onOpenAutoStartGuide = { navController.navigate(Routes.AUTOSTART) },
                    )
                }
                composable(Routes.AUTOSTART) {
                    AutoStartGuideScreen(
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() },
                    )
                }
            }
        }
    }
}
