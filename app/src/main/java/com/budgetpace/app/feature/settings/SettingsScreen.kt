package com.budgetpace.app.feature.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.budgetpace.app.R
import com.budgetpace.app.core.designsystem.components.SettingsRow
import com.budgetpace.app.core.designsystem.theme.ThemeMode
import com.budgetpace.app.feature.detection.DetectionHealthViewModel
import com.budgetpace.app.feature.detection.DetectionStatusChecker
import com.budgetpace.app.domain.ingestion.DetectionHealth
import kotlinx.coroutines.launch
import java.time.Instant

private const val DEVELOPER_EMAIL = "nitinreddy.nv@gmail.com"

fun isNotificationListenerEnabled(context: android.content.Context): Boolean =
    androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsRoute(
    viewModel: SettingsViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onGoogleBackupClick: () -> Unit = {},
    onExportClick: () -> Unit = {},
    onDetectionHealthClick: () -> Unit = {},
) {
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        SettingsScreen(
            viewModel = viewModel,
            modifier = Modifier.padding(innerPadding),
            snackbarHostState = snackbarHostState,
            onGoogleBackupClick = onGoogleBackupClick,
            onExportClick = onExportClick,
            onDetectionHealthClick = onDetectionHealthClick,
        )
    }
}

/** Spec §13: DATA / BANK NOTIFICATIONS / APPEARANCE / ABOUT — nothing else. */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onGoogleBackupClick: () -> Unit = {},
    onExportClick: () -> Unit = {},
    onDetectionHealthClick: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val session by viewModel.session.collectAsStateWithLifecycle()
    val isSheetsAuthorized by viewModel.isSheetsAuthorized.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    var showThemePicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val detectionViewModel: DetectionHealthViewModel = hiltViewModel()
    val detectionSnapshot by detectionViewModel.snapshot.collectAsStateWithLifecycle()
    var detectionSummary by remember { mutableStateOf("") }
    LifecycleResumeEffect(Unit) {
        detectionSummary = DetectionHealth.summarize(detectionSnapshot, DetectionStatusChecker.currentStatus(context), Instant.now())
        onPauseOrDispose {}
    }
    LaunchedEffect(detectionSnapshot) {
        detectionSummary = DetectionHealth.summarize(detectionSnapshot, DetectionStatusChecker.currentStatus(context), Instant.now())
    }

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
                Text("This removes expenses and budgets stored on this phone.\n\nYour Google Sheet will not be deleted.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    viewModel.deleteLocalData()
                }) { Text("Delete local data", color = MaterialTheme.colorScheme.error) }
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
            SettingsRow(
                icon = Icons.Outlined.Backup,
                title = "Google backup",
                trailingText = if (isSheetsAuthorized) "Connected" else "Not connected",
                onClick = onGoogleBackupClick,
            )
            SettingsRow(
                icon = Icons.Outlined.FileDownload,
                title = "Export",
                trailingText = "CSV",
                onClick = onExportClick,
            )
            SettingsRow(
                icon = Icons.Outlined.DeleteForever,
                title = "Delete local data",
                destructive = true,
                onClick = { showDeleteConfirm = true },
            )
        }

        item {
            SettingsSectionHeader("BANK DETECTION")
            SettingsRow(
                icon = Icons.Outlined.AccountBalance,
                title = "Detection health",
                subtitle = detectionSummary.ifEmpty { null },
                onClick = onDetectionHealthClick,
            )
        }

        item {
            SettingsSectionHeader("APPEARANCE")
            SettingsRow(
                icon = Icons.Outlined.Palette,
                title = "Theme",
                trailingText = when (themeMode) {
                    ThemeMode.SYSTEM -> "System"
                    ThemeMode.LIGHT -> "Light"
                    ThemeMode.DARK -> "Dark"
                },
                onClick = { showThemePicker = true },
            )
        }

        item {
            SettingsSectionHeader("ABOUT")
            ContactDeveloperItem(
                onClick = {
                    try {
                        val intent = Intent(Intent.ACTION_SENDTO).apply {
                            data = Uri.parse("mailto:$DEVELOPER_EMAIL")
                        }
                        context.startActivity(intent)
                    } catch (e: ActivityNotFoundException) {
                        scope.launch {
                            snackbarHostState.showSnackbar("No email app found on this phone.")
                        }
                    }
                },
            )
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
                        modifier = Modifier.fillMaxWidth()
                            .selectable(selected = mode == current, onClick = { onSelect(mode) })
                            .padding(vertical = 10.dp),
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
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 8.dp)
    )
}

/** Same row layout as [SettingsRow], but with the developer's real photo instead of an icon. */
@Composable
fun ContactDeveloperItem(
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
            Image(
                painter = painterResource(R.drawable.img_developer_avatar),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(32.dp).clip(CircleShape)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "Nitin Reddy N V",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = DEVELOPER_EMAIL,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outline,
        modifier = Modifier.padding(horizontal = 24.dp)
    )
}
