package com.expensetracker.ui

import com.expensetracker.ui.theme.card
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.expensetracker.ui.analytics.AnalyticsScreen
import com.expensetracker.ui.edit.EditExpenseScreen
import com.expensetracker.ui.history.HistoryScreen
import com.expensetracker.ui.home.HomeScreen
import com.expensetracker.ui.settings.SettingsScreen

object Routes {
    const val HOME = "home"
    const val HISTORY = "history"
    const val ANALYTICS = "analytics"
    const val SETTINGS = "settings"
    const val EDIT = "edit?id={id}"
    fun edit(id: Long? = null) = if (id == null) "edit" else "edit?id=$id"
}

private enum class TopLevel(val route: String, val label: String, val selectedIcon: ImageVector, val icon: ImageVector) {
    HOME(Routes.HOME, "Home", Icons.Rounded.Home, Icons.Outlined.Home),
    HISTORY(Routes.HISTORY, "History", Icons.AutoMirrored.Rounded.ReceiptLong, Icons.AutoMirrored.Outlined.ReceiptLong),
    ANALYTICS(Routes.ANALYTICS, "Analytics", Icons.Rounded.Insights, Icons.Outlined.Insights),
    SETTINGS(Routes.SETTINGS, "Settings", Icons.Rounded.Settings, Icons.Outlined.Settings),
}

@Composable
fun ExpenseTrackerApp(navController: NavHostController = rememberNavController()) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val isTopLevel = TopLevel.entries.any { it.route == currentRoute }
    val showFab = currentRoute == Routes.HOME || currentRoute == Routes.HISTORY

    Scaffold(
        // Each screen draws its own top bar and handles the status-bar inset itself.
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            AnimatedVisibility(
                visible = isTopLevel,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.card) {
                    TopLevel.entries.forEach { item ->
                        val selected = currentRoute == item.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navController.navigateTopLevel(item.route) },
                            icon = { Icon(if (selected) item.selectedIcon else item.icon, contentDescription = null) },
                            label = { Text(item.label) },
                            modifier = Modifier.testTag("nav_${item.route}"),
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            AnimatedVisibility(visible = showFab, enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
                ExtendedFloatingActionButton(
                    onClick = { navController.navigate(Routes.edit()) },
                    icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                    text = { Text("Add expense") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("add_expense"),
                )
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding),
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() },
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    onAddExpense = { navController.navigate(Routes.edit()) },
                    onOpenExpense = { navController.navigate(Routes.edit(it)) },
                    onSeeAll = { navController.navigateTopLevel(Routes.HISTORY) },
                    onOpenAnalytics = { navController.navigateTopLevel(Routes.ANALYTICS) },
                    onOpenSettings = { navController.navigateTopLevel(Routes.SETTINGS) },
                )
            }
            composable(Routes.HISTORY) {
                HistoryScreen(onOpenExpense = { navController.navigate(Routes.edit(it)) })
            }
            composable(Routes.ANALYTICS) {
                AnalyticsScreen()
            }
            composable(Routes.SETTINGS) {
                SettingsScreen()
            }
            composable(
                route = Routes.EDIT,
                arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = -1L }),
                enterTransition = { slideInVertically { it / 3 } + fadeIn() },
                exitTransition = { fadeOut() },
                popExitTransition = { slideOutVertically { it / 3 } + fadeOut() },
            ) {
                EditExpenseScreen(onDone = { navController.popBackStack() })
            }
        }
    }
}

private fun NavHostController.navigateTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
