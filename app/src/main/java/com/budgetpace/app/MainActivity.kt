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
import androidx.compose.foundation.isSystemInDarkTheme
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
import com.budgetpace.app.core.designsystem.theme.ThemeMode
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
            val appStartViewModel = hiltViewModel<AppStartViewModel>()
            val themeMode by appStartViewModel.themeMode.collectAsStateWithLifecycle()
            // Spec §13: Settings → Appearance → Theme (System/Light/Dark); both palettes are
            // defined per spec §38.
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            BudgetPaceTheme(darkTheme = darkTheme) {
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
        // Spec §21/§2: bottom nav is icons only, no FAB — "+ Add expense" lives on the
        // Transactions screen itself instead of a global button.
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
                        onClick = {
                            navController.navigate(Screen.Dashboard.route) {
                                popUpTo(Screen.Dashboard.route) { inclusive = true }
                                launchSingleTop = true
                            }
                        },
                    )
                    NavIconItem(
                        selected = currentRoute == Screen.Transactions.route,
                        icon = Icons.Default.SwapHoriz,
                        contentDescription = "Expenses",
                        onClick = { navController.navigate(Screen.Transactions.route) { launchSingleTop = true } },
                    )
                    NavIconItem(
                        selected = currentRoute == Screen.Categories.route,
                        icon = Icons.Default.GridView,
                        contentDescription = "Categories",
                        onClick = { navController.navigate(Screen.Categories.route) { launchSingleTop = true } },
                    )
                    NavIconItem(
                        selected = currentRoute == Screen.Settings.route,
                        icon = Icons.Default.Settings,
                        contentDescription = "Settings",
                        onClick = { navController.navigate(Screen.Settings.route) { launchSingleTop = true } },
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

/** Spec §21: icons only, no text labels, a subtle dot marks the selected destination. */
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
