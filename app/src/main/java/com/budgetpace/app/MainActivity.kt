package com.budgetpace.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.budgetpace.app.core.designsystem.theme.BudgetPaceTheme
import com.budgetpace.app.core.designsystem.theme.ThemeMode
import com.budgetpace.app.navigation.BudgetPaceNavGraph
import com.budgetpace.app.navigation.Screen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    companion object {
        /** Set when launched from the categorization notification (spec section 21). */
        const val EXTRA_TRANSACTION_ID = "transactionId"

        /** Set when launched from the "N expenses need a category" summary notification. */
        const val EXTRA_DESTINATION = "destination"
        const val DESTINATION_EXPENSES = "expenses"
    }

    // Held outside setContent so onNewIntent (tapping a notification while the app is already
    // running) can push a new value in without recreating the Activity.
    private val pendingTransactionId = mutableStateOf<String?>(null)
    private val pendingDestination = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Only on a genuinely new launch: re-reading the extra after a rotation would navigate
        // back to the expense the owner has already dealt with.
        if (savedInstanceState == null) consumeIntentExtras(intent)

        setContent {
            val appStartViewModel = hiltViewModel<AppStartViewModel>()
            val themeMode by appStartViewModel.themeMode.collectAsStateWithLifecycle()
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            // System bar icons follow the theme the owner picked in the app, not the system's:
            // choosing Dark on a phone set to Light otherwise leaves the clock and battery
            // unreadable against the dark background.
            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(
                        Color.Transparent.toArgb(),
                        Color.Transparent.toArgb(),
                    ) { darkTheme },
                    navigationBarStyle = SystemBarStyle.auto(
                        LIGHT_SCRIM.toArgb(),
                        DARK_SCRIM.toArgb(),
                    ) { darkTheme },
                )
                onDispose {}
            }

            // A month can roll over while the app sits in Recents, so this cannot only run at
            // process start: without it the 1st of the month still shows last month as active.
            LifecycleResumeEffect(Unit) {
                appStartViewModel.onForegrounded()
                onPauseOrDispose {}
            }

            BudgetPaceTheme(darkTheme = darkTheme) {
                val isOnboarded by appStartViewModel.isOnboarded.collectAsStateWithLifecycle()

                // NavHost reads startDestination only on its first composition, so it must not be
                // created until we know whether onboarding already ran — otherwise a returning
                // owner is sent back through onboarding on every launch.
                when (val onboardedNow = isOnboarded) {
                    null -> LoadingScreen()
                    else -> AppShell(
                        startDestination = if (onboardedNow) Screen.Dashboard.route else Screen.Onboarding.route,
                        pendingTransactionId = pendingTransactionId,
                        pendingDestination = pendingDestination,
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeIntentExtras(intent)
    }

    /** Reads each extra once and clears it, so it cannot be replayed on a configuration change. */
    private fun consumeIntentExtras(intent: Intent?) {
        intent?.getStringExtra(EXTRA_TRANSACTION_ID)?.let { id ->
            pendingTransactionId.value = id
            intent.removeExtra(EXTRA_TRANSACTION_ID)
        }
        intent?.getStringExtra(EXTRA_DESTINATION)?.let { destination ->
            pendingDestination.value = destination
            intent.removeExtra(EXTRA_DESTINATION)
        }
    }
}

private val LIGHT_SCRIM = Color(0x00FFFFFF)
private val DARK_SCRIM = Color(0x00000000)

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
    pendingDestination: MutableState<String?>,
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val pendingId by pendingTransactionId
    LaunchedEffect(pendingId) {
        pendingId?.let { id ->
            // Replace any expense screen already open rather than stacking a second one: tapping
            // the notification body and then its confirmation would otherwise leave two identical
            // screens on the back stack, each re-opening the category chooser.
            navController.navigate(Screen.TransactionDetail.createRoute(id)) {
                popUpTo(Screen.TransactionDetail.route) { inclusive = true }
                launchSingleTop = true
            }
            pendingTransactionId.value = null
        }
    }

    val destination by pendingDestination
    LaunchedEffect(destination) {
        if (destination == MainActivity.DESTINATION_EXPENSES) {
            navController.navigateToTab(Screen.Transactions.route)
            pendingDestination.value = null
        }
    }

    val showBottomBar = currentRoute in listOf(
        Screen.Dashboard.route,
        Screen.Transactions.route,
        Screen.Categories.route,
        Screen.Settings.route
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        // Spec section 21: bottom nav is icons only, no FAB — "+ Add expense" lives on the
        // Expenses screen itself.
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                ) {
                    NavIconItem(
                        selected = currentRoute == Screen.Dashboard.route,
                        icon = Icons.Default.Home,
                        contentDescription = "Home",
                        onClick = { navController.navigateToTab(Screen.Dashboard.route) },
                    )
                    NavIconItem(
                        selected = currentRoute == Screen.Transactions.route,
                        icon = Icons.Default.ReceiptLong,
                        contentDescription = "Expenses",
                        onClick = { navController.navigateToTab(Screen.Transactions.route) },
                    )
                    NavIconItem(
                        selected = currentRoute == Screen.Categories.route,
                        icon = Icons.Default.GridView,
                        contentDescription = "Categories",
                        onClick = { navController.navigateToTab(Screen.Categories.route) },
                    )
                    NavIconItem(
                        selected = currentRoute == Screen.Settings.route,
                        icon = Icons.Default.Settings,
                        contentDescription = "Settings",
                        onClick = { navController.navigateToTab(Screen.Settings.route) },
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                // padding alone does not tell the screens below that these insets are already
                // spoken for, so their own top bars would add the status bar height a second time.
                .consumeWindowInsets(innerPadding)
        ) {
            BudgetPaceNavGraph(navController = navController, startDestination = startDestination)
        }
    }
}

/**
 * Switching tabs must not grow the back stack: without this, hopping between tabs leaves one entry
 * per tap, so Back walks backwards through every tab visited instead of returning to Home.
 */
private fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** Spec section 21: icons only, a subtle dot marks the selected destination. */
@Composable
private fun RowScope.NavIconItem(
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(icon, contentDescription = contentDescription)
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .background(
                            if (selected) MaterialTheme.colorScheme.onBackground else Color.Transparent,
                            shape = CircleShape
                        )
                )
            }
        },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.onBackground,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            indicatorColor = Color.Transparent
        )
    )
}
