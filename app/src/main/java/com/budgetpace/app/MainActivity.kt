package com.budgetpace.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.budgetpace.app.core.designsystem.theme.BudgetPaceTheme
import com.budgetpace.app.navigation.BudgetPaceNavGraph
import com.budgetpace.app.navigation.Screen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BudgetPaceTheme {
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                // Show bottom bar only on top-level screens
                val showBottomBar = currentRoute in listOf(
                    Screen.Dashboard.route,
                    Screen.Transactions.route,
                    Screen.Categories.route,
                    Screen.Settings.route
                )

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        if (showBottomBar) {
                            NavigationBar(
                                containerColor = MaterialTheme.colorScheme.surface,
                            ) {
                                NavigationBarItem(
                                    selected = currentRoute == Screen.Dashboard.route,
                                    onClick = { 
                                        navController.navigate(Screen.Dashboard.route) { 
                                            popUpTo(Screen.Dashboard.route) { inclusive = true }
                                            launchSingleTop = true 
                                        } 
                                    },
                                    icon = { Text("📊") },
                                    label = { Text("Home") }
                                )
                                NavigationBarItem(
                                    selected = currentRoute == Screen.Transactions.route,
                                    onClick = { 
                                        navController.navigate(Screen.Transactions.route) { 
                                            launchSingleTop = true 
                                        } 
                                    },
                                    icon = { Text("💸") },
                                    label = { Text("Txns") }
                                )
                                NavigationBarItem(
                                    selected = currentRoute == Screen.Categories.route,
                                    onClick = { 
                                        navController.navigate(Screen.Categories.route) { 
                                            launchSingleTop = true 
                                        } 
                                    },
                                    icon = { Text("📂") },
                                    label = { Text("Categories") }
                                )
                                NavigationBarItem(
                                    selected = currentRoute == Screen.Settings.route,
                                    onClick = { 
                                        navController.navigate(Screen.Settings.route) { 
                                            launchSingleTop = true 
                                        } 
                                    },
                                    icon = { Text("⚙️") },
                                    label = { Text("Settings") }
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    androidx.compose.foundation.layout.Box(modifier = Modifier.padding(innerPadding)) {
                        BudgetPaceNavGraph(navController = navController)
                    }
                }
            }
        }
    }
}
