package com.budgetpace.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.hilt.navigation.compose.hiltViewModel
import com.budgetpace.app.feature.dashboard.DashboardRoute
import com.budgetpace.app.feature.dashboard.DashboardViewModel
import com.budgetpace.app.feature.detection.DetectionHealthRoute
import com.budgetpace.app.feature.onboarding.OnboardingRoute
import com.budgetpace.app.feature.transactions.PendingExpensesFilter
import com.budgetpace.app.feature.transactions.TransactionsRoute
import com.budgetpace.app.feature.transactions.TransactionsViewModel
import com.budgetpace.app.feature.transactions.TransactionDetailRoute
import com.budgetpace.app.feature.transactions.TransactionDetailViewModel
import com.budgetpace.app.feature.transactions.AddTransactionRoute
import com.budgetpace.app.feature.transactions.AddTransactionViewModel
import com.budgetpace.app.feature.categories.CategoriesRoute
import com.budgetpace.app.feature.categories.CategoriesViewModel
import com.budgetpace.app.feature.categories.CategoryDetailRoute
import com.budgetpace.app.feature.settings.ExportRoute
import com.budgetpace.app.feature.settings.GoogleBackupRoute
import com.budgetpace.app.feature.settings.SettingsRoute

sealed class Screen(val route: String) {
    object Onboarding : Screen("onboarding")
    object Dashboard : Screen("dashboard")
    object Transactions : Screen("transactions")
    object TransactionDetail : Screen("transactions/{id}") {
        fun createRoute(id: String) = "transactions/$id"
    }
    object AddTransaction : Screen("transactions/add")
    object Categories : Screen("categories")
    // Carries the month too (not just the category id) so a tile tapped from an ARCHIVED
    // month's Home view resolves against that month's own summary, not the active month's.
    object CategoryDetail : Screen("categories/{monthId}/{id}") {
        fun createRoute(monthId: String, id: String) = "categories/$monthId/$id"
    }
    object Settings : Screen("settings")
    object GoogleBackup : Screen("settings/google-backup")
    object Export : Screen("settings/export")
    object DetectionHealth : Screen("settings/detection-health")
}

@Composable
fun BudgetPaceNavGraph(
    navController: NavHostController = rememberNavController(),
    startDestination: String = Screen.Onboarding.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Onboarding.route) {
            OnboardingRoute(onComplete = {
                navController.navigate(Screen.Dashboard.route) {
                    popUpTo(0)
                }
            })
        }

        composable(Screen.Dashboard.route) {
            val viewModel = hiltViewModel<DashboardViewModel>()
            DashboardRoute(
                viewModel = viewModel,
                onCategoryClick = { monthId, categoryId ->
                    navController.navigate(Screen.CategoryDetail.createRoute(monthId, categoryId))
                },
                onOpenDetectionHealth = { navController.navigate(Screen.DetectionHealth.route) },
                onOpenUncategorized = {
                    PendingExpensesFilter.requestUncategorized = true
                    navController.navigate(Screen.Transactions.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
            )
        }

        composable(Screen.Transactions.route) {
            val viewModel = hiltViewModel<TransactionsViewModel>()
            TransactionsRoute(
                viewModel = viewModel,
                onBack = { navController.navigateUp() },
                onTransactionClick = { id ->
                    navController.navigate(Screen.TransactionDetail.createRoute(id))
                },
                onAddExpense = { navController.navigate(Screen.AddTransaction.route) },
            )
        }

        composable(Screen.AddTransaction.route) {
            val viewModel = hiltViewModel<AddTransactionViewModel>()
            AddTransactionRoute(
                viewModel = viewModel,
                onBack = { navController.navigateUp() }
            )
        }

        composable(Screen.TransactionDetail.route) { backStackEntry ->
            val id = backStackEntry.arguments?.getString("id") ?: return@composable
            val viewModel = hiltViewModel<TransactionDetailViewModel>()
            viewModel.setTransactionId(id)

            TransactionDetailRoute(
                viewModel = viewModel,
                onBack = { navController.navigateUp() }
            )
        }

        composable(Screen.Categories.route) {
            val viewModel = hiltViewModel<CategoriesViewModel>()
            // The Categories tab only ever lists the active month's categories.
            val activeMonthId by viewModel.activeMonthId.collectAsStateWithLifecycle()
            CategoriesRoute(
                viewModel = viewModel,
                onBack = { navController.navigateUp() },
                onCategoryClick = { id ->
                    activeMonthId?.let { monthId ->
                        navController.navigate(Screen.CategoryDetail.createRoute(monthId, id))
                    }
                },
            )
        }

        composable(Screen.CategoryDetail.route) { backStackEntry ->
            val monthIdArg = backStackEntry.arguments?.getString("monthId") ?: return@composable
            val id = backStackEntry.arguments?.getString("id") ?: return@composable
            val viewModel = hiltViewModel<CategoriesViewModel>()
            CategoryDetailRoute(
                viewModel = viewModel,
                monthId = monthIdArg,
                categoryId = id,
                onBack = { navController.navigateUp() },
            )
        }

        composable(Screen.Settings.route) {
            SettingsRoute(
                onBack = { navController.navigateUp() },
                onGoogleBackupClick = { navController.navigate(Screen.GoogleBackup.route) },
                onExportClick = { navController.navigate(Screen.Export.route) },
                onDetectionHealthClick = { navController.navigate(Screen.DetectionHealth.route) },
            )
        }

        composable(Screen.GoogleBackup.route) {
            GoogleBackupRoute(onBack = { navController.navigateUp() })
        }

        composable(Screen.Export.route) {
            ExportRoute(onBack = { navController.navigateUp() })
        }

        composable(Screen.DetectionHealth.route) {
            DetectionHealthRoute(onBack = { navController.navigateUp() })
        }
    }
}
