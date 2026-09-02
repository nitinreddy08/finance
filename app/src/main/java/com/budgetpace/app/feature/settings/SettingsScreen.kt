package com.budgetpace.app.feature.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsRoute(
    viewModel: SettingsViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)) },
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
        SettingsScreen(viewModel = viewModel, modifier = Modifier.padding(innerPadding))
    }
}

@Composable
fun SettingsScreen(viewModel: SettingsViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val session by viewModel.session.collectAsStateWithLifecycle()
    val exportState by viewModel.exportState.collectAsStateWithLifecycle()
    val isSheetsAuthorized by viewModel.isSheetsAuthorized.collectAsStateWithLifecycle()
    val sheetsSyncState by viewModel.sheetsSyncState.collectAsStateWithLifecycle()
    val pendingSyncCount by viewModel.pendingSyncCount.collectAsStateWithLifecycle()
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val authorizationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        viewModel.handleAuthorizationResult(result.data)
    }

    LaunchedEffect(exportState) {
        when (val state = exportState) {
            is ExportState.Success -> {
                Toast.makeText(context, "Exported to ${state.uri.lastPathSegment}", Toast.LENGTH_LONG).show()
                viewModel.consumeExportState()
            }
            is ExportState.Error -> {
                // Spec §61: never expose raw technical errors to the user.
                Toast.makeText(context, "Couldn't export CSV. Your local data is safe.", Toast.LENGTH_LONG).show()
                viewModel.consumeExportState()
            }
            else -> Unit
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

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onBackground,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            title = { Text("Delete local data?") },
            text = {
                // Spec §73
                Text("This removes transactions and budgets stored on this phone.\n\nYour Google Sheet will not be deleted.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    viewModel.deleteLocalData()
                }) { Text("Delete local data", color = Color(0xFFF44336)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 100.dp) // Space for bottom nav
    ) {
        // User Profile Section — reflects the real signed-in session, never a fabricated identity.
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        // A solid avatar chip reads better as surfaceVariant than the thin
                        // outline tone (outline is meant for borders/dividers, not fills).
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        session?.displayName?.firstOrNull()?.uppercase() ?: "?",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(session?.displayName ?: "Not signed in", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
                    Text(session?.email ?: "Connect a Google account in onboarding", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        item {
            SettingsSectionHeader("ACCOUNT")
            SettingsMockupItem(icon = "G", title = "Google Account", subtitle = if (session != null) "Connected" else "Not connected")
        }

        item {
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
            }
        }

        item {
            SettingsSectionHeader("PREFERENCES")
            SettingsMockupItem(icon = "⚙", title = "Currency", subtitle = "INR (₹)")
        }

        item {
            SettingsSectionHeader("DATA")
            SettingsMockupItem(
                icon = "⬇",
                title = "Export CSV",
                subtitle = if (exportState == ExportState.Exporting) "Exporting…" else "Current month",
                onClick = { viewModel.exportCurrentMonthCsv() }
            )
            SettingsMockupItem(
                icon = "🗑",
                title = "Delete local data",
                subtitle = "",
                onClick = { showDeleteConfirm = true }
            )
        }
    }
}

@Composable
fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 8.dp)
    )
}

@Composable
fun SettingsMockupItem(
    icon: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(icon, fontSize = 16.sp) // Mock icons
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    Divider(
        color = MaterialTheme.colorScheme.outline,
        modifier = Modifier.padding(horizontal = 24.dp)
    )
}
