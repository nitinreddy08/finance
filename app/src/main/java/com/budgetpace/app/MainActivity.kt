package com.budgetpace.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
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
            // Force dark theme as per the high-fidelity mockups
            BudgetPaceTheme(darkTheme = true) {
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                // Only show the bottom bar on the 4 main root screens
                val showBottomBar = currentRoute in listOf(
                    Screen.Dashboard.route,
                    Screen.Transactions.route,
                    Screen.Categories.route,
                    Screen.Settings.route
                )

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = MaterialTheme.colorScheme.background,
                    floatingActionButtonPosition = FabPosition.Center,
                    floatingActionButton = {
                        if (showBottomBar) {
                            FloatingActionButton(
                                onClick = { navController.navigate(Screen.AddTransaction.route) },
                                shape = CircleShape,
                                containerColor = Color(0xFF2A2D35), // Dark grey from mockup
                                contentColor = Color.White,
                                elevation = FloatingActionButtonDefaults.elevation(0.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Add Transaction")
                            }
                        }
                    },
                    bottomBar = {
                        if (showBottomBar) {
                            NavigationBar(
                                containerColor = MaterialTheme.colorScheme.surface,
                                tonalElevation = 0.dp, // Flat look as per mockup
                            ) {
                                NavigationBarItem(
                                    selected = currentRoute == Screen.Dashboard.route,
                                    onClick = { 
                                        navController.navigate(Screen.Dashboard.route) { 
                                            popUpTo(Screen.Dashboard.route) { inclusive = true }
                                            launchSingleTop = true 
                                        } 
                                    },
                                    icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                                    label = { Text("Home") },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = Color(0xFF4CAF50), // Green accent from mockup
                                        selectedTextColor = Color(0xFF4CAF50),
                                        unselectedIconColor = Color.Gray,
                                        unselectedTextColor = Color.Gray,
                                        indicatorColor = Color.Transparent
                                    )
                                )
                                NavigationBarItem(
                                    selected = currentRoute == Screen.Transactions.route,
                                    onClick = { navController.navigate(Screen.Transactions.route) { launchSingleTop = true } },
                                    icon = { Icon(Icons.Default.List, contentDescription = "Transactions") },
                                    label = { Text("Transactions") },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = Color.White,
                                        selectedTextColor = Color.White,
                                        unselectedIconColor = Color.Gray,
                                        unselectedTextColor = Color.Gray,
                                        indicatorColor = Color.Transparent
                                    )
                                )
                                
                                // Empty item to leave space for the FAB in the center
                                NavigationBarItem(
                                    selected = false,
                                    onClick = { },
                                    icon = { },
                                    enabled = false
                                )

                                NavigationBarItem(
                                    selected = currentRoute == Screen.Categories.route,
                                    onClick = { navController.navigate(Screen.Categories.route) { launchSingleTop = true } },
                                    icon = { Icon(Icons.Default.GridView, contentDescription = "Categories") },
                                    label = { Text("Categories") },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = Color.White,
                                        selectedTextColor = Color.White,
                                        unselectedIconColor = Color.Gray,
                                        unselectedTextColor = Color.Gray,
                                        indicatorColor = Color.Transparent
                                    )
                                )
                                NavigationBarItem(
                                    selected = currentRoute == Screen.Settings.route,
                                    onClick = { navController.navigate(Screen.Settings.route) { launchSingleTop = true } },
                                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                                    label = { Text("Settings") },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = Color.White,
                                        selectedTextColor = Color.White,
                                        unselectedIconColor = Color.Gray,
                                        unselectedTextColor = Color.Gray,
                                        indicatorColor = Color.Transparent
                                    )
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        BudgetPaceNavGraph(navController = navController)
                    }
                }
            }
        }
    }
}
