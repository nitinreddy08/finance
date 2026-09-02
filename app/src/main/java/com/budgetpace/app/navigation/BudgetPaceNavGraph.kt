package com.budgetpace.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.hilt.navigation.compose.hiltViewModel
import com.budgetpace.app.feature.dashboard.DashboardRoute
import com.budgetpace.app.feature.dashboard.DashboardViewModel
import com.budgetpace.app.feature.onboarding.OnboardingRoute
import com.budgetpace.app.feature.transactions.TransactionsRoute
import com.budgetpace.app.feature.transactions.TransactionsViewModel
import com.budgetpace.app.feature.transactions.TransactionDetailRoute
import com.budgetpace.app.feature.transactions.TransactionDetailViewModel
import com.budgetpace.app.feature.transactions.AddTransactionRoute
import com.budgetpace.app.feature.transactions.AddTransactionViewModel
import com.budgetpace.app.feature.categories.CategoriesRoute
import com.budgetpace.app.feature.categories.CategoriesViewModel
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
    object Settings : Screen("settings")
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
            )
        }
        
        composable(Screen.Transactions.route) {
            val viewModel = hiltViewModel<TransactionsViewModel>()
            TransactionsRoute(
                viewModel = viewModel,
                onBack = { navController.navigateUp() },
                onTransactionClick = { id -> 
                    navController.navigate(Screen.TransactionDetail.createRoute(id)) 
                }
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
            CategoriesRoute(
                viewModel = viewModel,
                onBack = { navController.navigateUp() },
            )
        }
        
        composable(Screen.Settings.route) {
            SettingsRoute(
                onBack = { navController.navigateUp() }
            )
        }
    }
}
