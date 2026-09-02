package com.budgetpace.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.budgetpace.app.core.designsystem.theme.BudgetPaceTheme
import com.budgetpace.app.navigation.BudgetPaceNavGraph
import com.budgetpace.app.navigation.Screen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    companion object {
        /** Set when launched from the categorization notification (spec §21). */
        const val EXTRA_TRANSACTION_ID = "transactionId"
    }

    // Held outside setContent so onNewIntent (tapping the notification while the app is
    // already running) can push a new value in without recreating the Activity.
    private val pendingTransactionId = mutableStateOf<String?>(null)

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op either way */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingTransactionId.value = intent?.getStringExtra(EXTRA_TRANSACTION_ID)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            // Follow the system light/dark setting (BudgetPaceTheme defaults to
            // isSystemInDarkTheme()); both palettes are defined per spec §38.
            BudgetPaceTheme {
                val appStartViewModel = hiltViewModel<AppStartViewModel>()
                val isOnboarded by appStartViewModel.isOnboarded.collectAsStateWithLifecycle()

                // NavHost's startDestination is only read on its first composition, so we must
                // not create it until we actually know whether onboarding already ran —
                // otherwise a returning user would be sent back through onboarding every launch.
                when (val onboardedNow = isOnboarded) {
                    null -> LoadingScreen()
                    else -> AppShell(
                        startDestination = if (onboardedNow) Screen.Dashboard.route else Screen.Onboarding.route,
                        pendingTransactionId = pendingTransactionId,
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(EXTRA_TRANSACTION_ID)?.let { pendingTransactionId.value = it }
    }
}

@Composable
private fun LoadingScreen() {
    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun AppShell(
    startDestination: String,
    pendingTransactionId: MutableState<String?>,
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val pendingId by pendingTransactionId
    LaunchedEffect(pendingId) {
        pendingId?.let { id ->
            navController.navigate(Screen.TransactionDetail.createRoute(id))
            pendingTransactionId.value = null
        }
    }

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
                    // Inverse-surface role: the FAB should read as a solid, high-contrast chip
                    // against the background in both themes, not a subtle border tint.
                    containerColor = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
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
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            indicatorColor = Color.Transparent
                        )
                    )
                    NavigationBarItem(
                        selected = currentRoute == Screen.Transactions.route,
                        onClick = { navController.navigate(Screen.Transactions.route) { launchSingleTop = true } },
                        icon = { Icon(Icons.Default.List, contentDescription = "Transactions") },
                        label = { Text("Transactions") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onBackground,
                            selectedTextColor = MaterialTheme.colorScheme.onBackground,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
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
                            selectedIconColor = MaterialTheme.colorScheme.onBackground,
                            selectedTextColor = MaterialTheme.colorScheme.onBackground,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            indicatorColor = Color.Transparent
                        )
                    )
                    NavigationBarItem(
                        selected = currentRoute == Screen.Settings.route,
                        onClick = { navController.navigate(Screen.Settings.route) { launchSingleTop = true } },
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                        label = { Text("Settings") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onBackground,
                            selectedTextColor = MaterialTheme.colorScheme.onBackground,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            indicatorColor = Color.Transparent
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            BudgetPaceNavGraph(navController = navController, startDestination = startDestination)
        }
    }
}
