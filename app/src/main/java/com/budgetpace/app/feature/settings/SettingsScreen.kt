package com.budgetpace.app.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.budgetpace.app.core.designsystem.theme.bpColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsRoute(
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.bpColors.background,
                    titleContentColor = MaterialTheme.bpColors.textPrimary
                )
            )
        },
        containerColor = MaterialTheme.bpColors.background
    ) { innerPadding ->
        SettingsScreen(modifier = Modifier.padding(innerPadding))
    }
}

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            SettingsSectionHeader("ACCOUNT")
            SettingsItem("Google account", "Not connected")
        }
        
        item {
            SettingsSectionHeader("TRANSACTION DETECTION")
            SettingsItem("Notification access", "Disabled")
            SettingsItem("Supported banks", "Kotak, SBI")
            SettingsSwitchItem("Detection enabled", true)
        }
        
        item {
            SettingsSectionHeader("BUDGET")
            SettingsItem("Categories", "Manage")
            SettingsItem("Monthly budget", "View overall")
            SettingsItem("Carry-forward", "View allocations")
        }
        
        item {
            SettingsSectionHeader("GOOGLE SHEETS")
            SettingsItem("Connected sheet", "None")
            SettingsItem("Last sync", "Never")
            SettingsItem("Sync now", "")
            SettingsSwitchItem("Automatic daily export", false)
        }
        
        item {
            SettingsSectionHeader("DATA")
            SettingsItem("Export CSV", "")
            SettingsItem("Delete local data", "", MaterialTheme.bpColors.statusRed)
        }
        
        item {
            SettingsSectionHeader("ABOUT")
            SettingsItem("Version", "1.0.0")
            SettingsItem("Privacy", "")
        }
    }
}

@Composable
fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.bpColors.textSecondary,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 32.dp, bottom = 8.dp)
    )
}

@Composable
fun SettingsItem(
    title: String,
    subtitle: String,
    titleColor: androidx.compose.ui.graphics.Color = MaterialTheme.bpColors.textPrimary
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            color = titleColor
        )
        if (subtitle.isNotEmpty()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.bpColors.textSecondary
            )
        }
    }
    Divider(
        color = MaterialTheme.bpColors.border.copy(alpha = 0.5f),
        modifier = Modifier.padding(horizontal = 24.dp)
    )
}

@Composable
fun SettingsSwitchItem(title: String, checked: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.bpColors.textPrimary
        )
        Switch(
            checked = checked,
            onCheckedChange = null // Read-only for Phase 2 UI placeholder
        )
    }
    Divider(
        color = MaterialTheme.bpColors.border.copy(alpha = 0.5f),
        modifier = Modifier.padding(horizontal = 24.dp)
    )
}
