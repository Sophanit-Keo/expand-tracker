package com.expensetracker.ui

import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.expensetracker.R
import com.expensetracker.data.AppCurrency
import com.expensetracker.data.Category
import com.expensetracker.data.EntryType
import com.expensetracker.ui.analytics.AnalyticsScreen
import com.expensetracker.ui.analytics.AnalyticsTabs
import com.expensetracker.ui.edit.EditExpenseScreen
import com.expensetracker.ui.history.HistoryScreen
import com.expensetracker.ui.home.HomeScreen
import com.expensetracker.ui.importer.ImportScreen
import com.expensetracker.ui.settings.ExchangeRatesScreen
import com.expensetracker.ui.settings.SettingsScreen
import com.expensetracker.ui.theme.card
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

object Routes {
    const val HOME = "home"
    const val HISTORY = "history"
    const val ANALYTICS = "analytics"
    const val SETTINGS = "settings"
    const val EDIT = "edit?id={id}&type={type}&amount={amount}&category={category}&note={note}&currency={currency}&rate={rate}"
    const val IMPORT = "import"
    const val RATES = "rates"
    fun edit(id: Long? = null) = if (id == null) "edit" else "edit?id=$id"
    fun addIncome() = "edit?type=${EntryType.INCOME.name}"

    /** A new entry pre-filled with these values (duplicating an entry, logging a recurring bill). */
    fun prefilled(p: Prefill) = "edit?type=${p.category.type.name}&amount=${p.amount}&category=${p.category.name}&note=${Uri.encode(p.note)}" +
        (p.currency?.let { "&currency=${it.name}" } ?: "") + (p.rate?.let { "&rate=$it" } ?: "")
}

private enum class TopLevel(val route: String, @param:StringRes val label: Int, val selectedIcon: ImageVector, val icon: ImageVector) {
    HOME(Routes.HOME, R.string.nav_home, Icons.Rounded.Home, Icons.Outlined.Home),
    HISTORY(Routes.HISTORY, R.string.nav_history, Icons.AutoMirrored.Rounded.ReceiptLong, Icons.AutoMirrored.Outlined.ReceiptLong),
    ANALYTICS(Routes.ANALYTICS, R.string.nav_analytics, Icons.Rounded.Insights, Icons.Outlined.Insights),
    SETTINGS(Routes.SETTINGS, R.string.nav_settings, Icons.Rounded.Settings, Icons.Outlined.Settings),
}

/** Values to start a new entry with. */
/** Values to start a new entry with. [amount] is in [currency] (the main currency when null), at [rate]. */
data class Prefill(
    val amount: Double,
    val category: Category,
    val note: String,
    val currency: AppCurrency? = null,
    val rate: Double? = null,
)

private const val FADE_IN = 220
private const val FADE_OUT = 90

@Composable
fun ExpenseTrackerApp(navController: NavHostController = rememberNavController()) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val isTopLevel = TopLevel.entries.any { it.route == currentRoute }
    val showFab = currentRoute == Routes.HOME || currentRoute == Routes.HISTORY
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current

    // Confirmations posted by screens that have already closed (e.g. "Saved · Undo" from the editor).
    LaunchedEffect(Unit) {
        AppMessages.events.collectLatest { message ->
            val result = snackbar.showSnackbar(
                message = message.text.resolve(context),
                actionLabel = message.actionLabel?.resolve(context),
                withDismissAction = message.actionLabel == null,
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) {
                haptics.performHapticFeedback(HapticFeedbackType.Reject)
                scope.launch { message.action?.invoke() }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
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
                            label = { Text(stringResource(item.label), maxLines = 1) },
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
                    text = { Text(stringResource(R.string.action_add_expense)) },
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
            // Material "fade through" between tabs: the old screen fades out quickly, the new one
            // fades and scales in slightly.
            enterTransition = { fadeIn(tween(FADE_IN, delayMillis = FADE_OUT)) + scaleIn(tween(FADE_IN, delayMillis = FADE_OUT), initialScale = 0.96f) },
            exitTransition = { fadeOut(tween(FADE_OUT)) },
            popEnterTransition = { fadeIn(tween(FADE_IN, delayMillis = FADE_OUT)) + scaleIn(tween(FADE_IN, delayMillis = FADE_OUT), initialScale = 0.96f) },
            popExitTransition = { fadeOut(tween(FADE_OUT)) },
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    onAddExpense = { navController.navigate(Routes.edit()) },
                    onOpenExpense = { navController.navigate(Routes.edit(it)) },
                    onSeeAll = { navController.navigateTopLevel(Routes.HISTORY) },
                    onOpenAnalytics = { navController.navigateTopLevel(Routes.ANALYTICS) },
                    onOpenSettings = { navController.navigateTopLevel(Routes.SETTINGS) },
                    onImport = { navController.navigate(Routes.IMPORT) },
                    onAddIncome = { navController.navigate(Routes.addIncome()) },
                    onOpenCashFlow = {
                        AnalyticsTabs.openCashFlow()
                        navController.navigateTopLevel(Routes.ANALYTICS)
                    },
                )
            }
            composable(Routes.HISTORY) {
                HistoryScreen(
                    onOpenExpense = { navController.navigate(Routes.edit(it)) },
                    onImport = { navController.navigate(Routes.IMPORT) },
                )
            }
            composable(Routes.ANALYTICS) {
                AnalyticsScreen(
                    onLogRecurring = { navController.navigate(Routes.prefilled(it)) },
                    onAddIncome = { navController.navigate(Routes.addIncome()) },
                    onImport = { navController.navigate(Routes.IMPORT) },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onImport = { navController.navigate(Routes.IMPORT) },
                    onOpenRates = { navController.navigate(Routes.RATES) },
                )
            }
            composable(
                route = Routes.EDIT,
                arguments = listOf(
                    navArgument("id") { type = NavType.LongType; defaultValue = -1L },
                    navArgument("type") { type = NavType.StringType; defaultValue = EntryType.EXPENSE.name },
                    navArgument("amount") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("category") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("note") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("currency") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("rate") { type = NavType.StringType; nullable = true; defaultValue = null },
                ),
                enterTransition = { slideInVertically { it / 3 } + fadeIn() },
                exitTransition = { fadeOut() },
                popExitTransition = { slideOutVertically { it / 3 } + fadeOut() },
            ) {
                EditExpenseScreen(
                    onDone = { navController.popBackStack() },
                    onDuplicate = { prefill ->
                        navController.navigate(Routes.prefilled(prefill)) {
                            popUpTo(Routes.EDIT) { inclusive = true }
                        }
                    },
                )
            }
            composable(Routes.RATES) {
                ExchangeRatesScreen(onBack = { navController.popBackStack() })
            }
            composable(
                route = Routes.IMPORT,
                enterTransition = { slideInVertically { it / 3 } + fadeIn() },
                exitTransition = { fadeOut() },
                popExitTransition = { slideOutVertically { it / 3 } + fadeOut() },
            ) {
                ImportScreen(
                    onClose = { navController.popBackStack() },
                    onViewHistory = {
                        navController.popBackStack()
                        navController.navigateTopLevel(Routes.HISTORY)
                    },
                    onViewCashFlow = { month ->
                        AnalyticsTabs.openCashFlow(month)
                        navController.popBackStack()
                        navController.navigateTopLevel(Routes.ANALYTICS)
                    },
                )
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
