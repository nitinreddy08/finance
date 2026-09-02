package com.budgetpace.app.feature.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.DateFormat
import java.util.Date

/** Spec §14: Settings → Data → Google backup — connection state, last backup, sync now, disconnect. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoogleBackupRoute(
    viewModel: SettingsViewModel = hiltViewModel(),
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val session by viewModel.session.collectAsStateWithLifecycle()
    val isSheetsAuthorized by viewModel.isSheetsAuthorized.collectAsStateWithLifecycle()
    val sheetsSyncState by viewModel.sheetsSyncState.collectAsStateWithLifecycle()
    val pendingSyncCount by viewModel.pendingSyncCount.collectAsStateWithLifecycle()
    val syncedCount by viewModel.syncedCount.collectAsStateWithLifecycle()

    val authorizationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        viewModel.handleAuthorizationResult(result.data)
    }

    LaunchedEffect(Unit) {
        viewModel.signInError.collect {
            // Spec §61: never expose the raw exception — the real cause is logged via Log.e in
            // AuthRepositoryImpl for diagnosis from logcat.
            Toast.makeText(context, "Couldn't sign in with Google. Please try again.", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(sheetsSyncState) {
        when (val state = sheetsSyncState) {
            is SheetsSyncState.Success -> {
                val message = if (state.syncedCount == 0) "Already up to date" else "Synced ${state.syncedCount} transaction(s)"
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                viewModel.consumeSheetsSyncState()
            }
            is SheetsSyncState.Error -> {
                // Spec §61: never expose raw technical errors to the user.
                Toast.makeText(context, "Couldn't sync with Google Sheets. Your local data is safe.", Toast.LENGTH_LONG).show()
                viewModel.consumeSheetsSyncState()
            }
            else -> Unit
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
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            SettingsSectionHeader("ACCOUNT")
            SettingsMockupItem(
                icon = "G",
                title = session?.displayName ?: "Sign in with Google",
                subtitle = session?.email ?: "Not signed in",
                onClick = { if (session == null) viewModel.signInWithGoogle(context) },
            )

            SettingsSectionHeader("GOOGLE SHEETS")
            if (!isSheetsAuthorized) {
                SettingsMockupItem(
                    icon = "☁",
                    title = "Connect Google Sheets",
                    subtitle = "Not connected",
                    onClick = {
                        viewModel.beginSheetsAuthorization { pendingIntent ->
                            authorizationLauncher.launch(
                                IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                            )
                        }
                    }
                )
            } else {
                SettingsMockupItem(icon = "☁", title = "Google Sheets", subtitle = "Connected")
                SettingsMockupItem(
                    icon = "🔁",
                    title = "Daily backup",
                    subtitle = "On",
                    onClick = { viewModel.toggleDailyBackup(context, true) }
                )
                val lastSync = remember(sheetsSyncState) { viewModel.lastSyncAtMillis() }
                SettingsMockupItem(
                    icon = "🕒",
                    title = "Last backup",
                    subtitle = lastSync?.let { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it)) } ?: "Never"
                )
                SettingsMockupItem(
                    icon = "⬆",
                    title = if (sheetsSyncState == SheetsSyncState.Syncing) "Syncing…" else "Sync now",
                    subtitle = if (pendingSyncCount > 0) "$pendingSyncCount change${if (pendingSyncCount == 1) "" else "s"} waiting" else "Up to date",
                    onClick = { if (sheetsSyncState != SheetsSyncState.Syncing) viewModel.syncNow() }
                )
                SettingsMockupItem(
                    icon = "📊",
                    title = "Backed up",
                    subtitle = "$syncedCount transaction${if (syncedCount == 1) "" else "s"}"
                )

                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    TextButton(onClick = { viewModel.disconnectGoogle() }) {
                        Text("Disconnect Google", color = Color(0xFFF44336))
                    }
                }
            }
        }
    }
}
