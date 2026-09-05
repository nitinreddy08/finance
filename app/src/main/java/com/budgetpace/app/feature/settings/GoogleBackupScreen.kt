package com.budgetpace.app.feature.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.budgetpace.app.core.designsystem.components.SettingsRow
import com.budgetpace.app.core.designsystem.theme.bpColors
import com.budgetpace.app.data.sync.SyncStatus
import com.budgetpace.app.domain.sync.SyncAction
import com.budgetpace.app.domain.sync.SyncProblem
import java.text.DateFormat
import java.util.Date

/** Spec §14: Settings → Data → Google backup — connection state, sync status, disconnect. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoogleBackupRoute(
    viewModel: SettingsViewModel = hiltViewModel(),
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val session by viewModel.session.collectAsStateWithLifecycle()
    val hasConsent by viewModel.isSheetsAuthorized.collectAsStateWithLifecycle()
    val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()
    val dailyBackupEnabled by viewModel.dailyBackupEnabled.collectAsStateWithLifecycle()
    val manualSyncRunning by viewModel.manualSyncRunning.collectAsStateWithLifecycle()
    val pendingSyncCount by viewModel.pendingSyncCount.collectAsStateWithLifecycle()
    val syncedCount by viewModel.syncedCount.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var showDisconnectConfirm by remember { mutableStateOf(false) }
    var showForgetSheetConfirm by remember { mutableStateOf(false) }
    var showStartNewSheetConfirm by remember { mutableStateOf(false) }

    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        viewModel.onConsentResult(result.resultCode, result.data)
    }

    LaunchedEffect(Unit) {
        viewModel.consentRequests.collect { pendingIntent ->
            consentLauncher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
        }
    }
    LaunchedEffect(Unit) {
        // Spec §61: never expose the raw exception — the real cause is logged via Log.e in
        // AuthRepositoryImpl for diagnosis from logcat.
        viewModel.signInError.collect {
            snackbarHostState.showSnackbar("Couldn't sign in with Google. Please try again.")
        }
    }
    LaunchedEffect(Unit) {
        viewModel.snackbarMessages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Google backup", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            SettingsSectionHeader("ACCOUNT")
            SettingsRow(
                icon = Icons.Outlined.AccountCircle,
                title = session?.displayName ?: "Sign in with Google",
                subtitle = session?.email ?: "Not signed in",
                onClick = if (session == null) ({ viewModel.signInWithGoogle(context) }) else null,
            )

            SettingsSectionHeader("GOOGLE SHEETS")
            when {
                session == null -> SettingsRow(
                    icon = Icons.Outlined.Backup,
                    title = "Sign in with Google first",
                    subtitle = "Required to connect Sheets",
                    onClick = { viewModel.signInWithGoogle(context) },
                )
                !hasConsent -> SettingsRow(
                    icon = Icons.Outlined.Backup,
                    title = "Connect Google Sheets",
                    subtitle = "Not connected",
                    onClick = { viewModel.syncNow() },
                )
                else -> {
                    val needsReconnect = syncStatus.problem?.code == SyncProblem.CODE_NEEDS_RECONNECT
                    SettingsRow(
                        icon = Icons.Outlined.Backup,
                        title = "Google Sheets",
                        subtitle = if (needsReconnect) "Needs reconnect" else "Connected",
                    )

                    DailyBackupRow(enabled = dailyBackupEnabled, onToggle = viewModel::setDailyBackupEnabled)

                    val spreadsheetUrl = viewModel.currentSpreadsheetUrl()
                    if (spreadsheetUrl != null) {
                        SettingsRow(
                            icon = Icons.Outlined.Link,
                            title = "Open backup sheet",
                            subtitle = "docs.google.com",
                            onClick = {
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(spreadsheetUrl)))
                                }
                            },
                        )
                    }

                    SyncNowRow(
                        running = manualSyncRunning,
                        pendingCount = pendingSyncCount,
                        onClick = viewModel::syncNow,
                    )

                    SyncDetailsSection(
                        status = syncStatus,
                        onTryAgain = viewModel::syncNow,
                        onReconnect = viewModel::syncNow,
                        onStartNewSheet = { showStartNewSheetConfirm = true },
                        onOpenAccountSettings = {
                            runCatching { context.startActivity(Intent(Settings.ACTION_SYNC_SETTINGS)) }
                        },
                    )

                    SettingsRow(
                        icon = Icons.Outlined.BarChart,
                        title = "Backed up",
                        subtitle = "$syncedCount expense${if (syncedCount == 1) "" else "s"}",
                    )

                    TextButton(
                        onClick = { showForgetSheetConfirm = true },
                        modifier = Modifier.padding(start = 16.dp, top = 4.dp),
                    ) {
                        Text("Forget backup sheet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            if (session != null) {
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    TextButton(onClick = { showDisconnectConfirm = true }) {
                        Text("Disconnect Google", color = MaterialTheme.bpColors.danger)
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showDisconnectConfirm) {
        AlertDialog(
            onDismissRequest = { showDisconnectConfirm = false },
            title = { Text("Disconnect Google?") },
            text = { Text("You'll be signed out and daily backup will stop. Your local expenses and budgets are unaffected.") },
            confirmButton = {
                TextButton(onClick = { showDisconnectConfirm = false; viewModel.disconnectGoogle() }) {
                    Text("Disconnect", color = MaterialTheme.bpColors.danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisconnectConfirm = false }) { Text("Cancel") }
            }
        )
    }

    if (showForgetSheetConfirm) {
        AlertDialog(
            onDismissRequest = { showForgetSheetConfirm = false },
            title = { Text("Forget backup sheet?") },
            text = { Text("Budget Pace will stop updating that sheet. The sheet itself is not deleted.") },
            confirmButton = {
                TextButton(onClick = { showForgetSheetConfirm = false; viewModel.forgetBackupSheet() }) {
                    Text("Forget", color = MaterialTheme.bpColors.danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { showForgetSheetConfirm = false }) { Text("Cancel") }
            }
        )
    }

    if (showStartNewSheetConfirm) {
        AlertDialog(
            onDismissRequest = { showStartNewSheetConfirm = false },
            title = { Text("Start a new sheet?") },
            text = { Text("Budget Pace will create a fresh Google Sheet and upload your whole expense history into it. Nothing on this phone is lost.") },
            confirmButton = {
                TextButton(onClick = { showStartNewSheetConfirm = false; viewModel.startNewSheet() }) {
                    Text("Start new sheet")
                }
            },
            dismissButton = {
                TextButton(onClick = { showStartNewSheetConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun DailyBackupRow(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                Text("🔁", fontSize = 16.sp)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text("Daily backup", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
        }
        Switch(checked = enabled, onCheckedChange = onToggle)
    }
    Divider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(horizontal = 24.dp))
}

@Composable
private fun SyncNowRow(running: Boolean, pendingCount: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (running) Modifier else Modifier.clickable(onClick = onClick))
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                Text("⬆", fontSize = 16.sp)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                if (running) "Syncing…" else "Sync now",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        if (running) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        } else {
            Text(
                if (pendingCount > 0) "$pendingCount change${if (pendingCount == 1) "" else "s"} waiting" else "Up to date",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    Divider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(horizontal = 24.dp))
}

@Composable
private fun SyncDetailsSection(
    status: SyncStatus,
    onTryAgain: () -> Unit,
    onReconnect: () -> Unit,
    onStartNewSheet: () -> Unit,
    onOpenAccountSettings: () -> Unit,
) {
    val problem = status.problem
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) {
        Text(
            "SYNC DETAILS",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Last success: ${status.lastSuccessAtMillis?.let(::formatDateTime) ?: "Never"}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            "Last attempt: ${status.lastAttemptAtMillis?.let(::formatDateTime) ?: "Never"}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        if (problem != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                problem.title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.bpColors.statusOrange
            )
            Text(problem.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Code: ${problem.code}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            if (problem.action != SyncAction.NONE) {
                val actionLabel = when (problem.action) {
                    SyncAction.RECONNECT -> "Reconnect"
                    SyncAction.START_NEW_SHEET -> "Start a new sheet"
                    SyncAction.OPEN_ACCOUNT_SETTINGS -> "Open account settings"
                    else -> "Try again"
                }
                val onActionClick: () -> Unit = when (problem.action) {
                    SyncAction.RECONNECT -> onReconnect
                    SyncAction.START_NEW_SHEET -> onStartNewSheet
                    SyncAction.OPEN_ACCOUNT_SETTINGS -> onOpenAccountSettings
                    else -> onTryAgain
                }
                TextButton(onClick = onActionClick, modifier = Modifier.padding(top = 4.dp)) { Text(actionLabel) }
            }
        }
    }
    Divider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(horizontal = 24.dp))
}

private fun formatDateTime(millis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(millis))
