package com.budgetpace.app.feature.settings

import android.content.Intent
import android.provider.Settings
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.budgetpace.app.core.designsystem.theme.ThemeMode

fun isNotificationListenerEnabled(context: android.content.Context): Boolean =
    NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsRoute(
    viewModel: SettingsViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onGoogleBackupClick: () -> Unit = {},
    onExportClick: () -> Unit = {},
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
        SettingsScreen(
            viewModel = viewModel,
            modifier = Modifier.padding(innerPadding),
            onGoogleBackupClick = onGoogleBackupClick,
            onExportClick = onExportClick,
        )
    }
}

/** Spec §13: DATA / BANK NOTIFICATIONS / APPEARANCE / ABOUT — nothing else. */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier,
    onGoogleBackupClick: () -> Unit = {},
    onExportClick: () -> Unit = {},
) {
    val context = LocalContext.current
    val session by viewModel.session.collectAsStateWithLifecycle()
    val isSheetsAuthorized by viewModel.isSheetsAuthorized.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    var showThemePicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var notificationAccessEnabled by remember { mutableStateOf(isNotificationListenerEnabled(context)) }

    if (showThemePicker) {
        ThemePickerDialog(
            current = themeMode,
            onSelect = { viewModel.setThemeMode(it); showThemePicker = false },
            onDismiss = { showThemePicker = false }
        )
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
        // Profile header — reflects the real signed-in session, never a fabricated identity.
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
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
                    Text(session?.email ?: "Connect a Google account below", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        item {
            SettingsSectionHeader("DATA")
            SettingsMockupItem(
                icon = "☁",
                title = "Google backup",
                subtitle = if (isSheetsAuthorized) "Connected" else "Not connected",
                onClick = onGoogleBackupClick,
            )
            SettingsMockupItem(
                icon = "⬇",
                title = "Export",
                subtitle = "CSV",
                onClick = onExportClick,
            )
            SettingsMockupItem(
                icon = "🗑",
                title = "Delete local data",
                subtitle = "",
                onClick = { showDeleteConfirm = true },
            )
        }

        item {
            SettingsSectionHeader("BANK NOTIFICATIONS")
            SettingsMockupItem(icon = "🏦", title = "Kotak", subtitle = "Enabled")
            SettingsMockupItem(icon = "🏦", title = "SBI", subtitle = "Enabled")
            SettingsMockupItem(
                icon = "🔔",
                title = "Notification access",
                subtitle = if (notificationAccessEnabled) "Granted" else "Not granted",
                onClick = {
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    notificationAccessEnabled = isNotificationListenerEnabled(context)
                },
            )
        }

        item {
            SettingsSectionHeader("APPEARANCE")
            SettingsMockupItem(
                icon = "🎨",
                title = "Theme",
                subtitle = when (themeMode) {
                    ThemeMode.SYSTEM -> "System"
                    ThemeMode.LIGHT -> "Light"
                    ThemeMode.DARK -> "Dark"
                },
                onClick = { showThemePicker = true },
            )
        }

        item {
            SettingsSectionHeader("ABOUT")
            SettingsMockupItem(icon = "ℹ", title = "Privacy", subtitle = "")
            SettingsMockupItem(icon = "📦", title = "About Budget Pace", subtitle = "v1.0")
        }
    }
}

@Composable
private fun ThemePickerDialog(
    current: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onBackground,
        title = { Text("Theme") },
        text = {
            Column {
                listOf(ThemeMode.SYSTEM to "System", ThemeMode.LIGHT to "Light", ThemeMode.DARK to "Dark").forEach { (mode, label) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(mode) }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = mode == current, onClick = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(label, color = MaterialTheme.colorScheme.onBackground)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    )
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
