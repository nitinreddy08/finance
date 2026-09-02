package com.budgetpace.app.feature.settings

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel

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
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF15161A),
                    titleContentColor = Color.White
                )
            )
        },
        containerColor = Color(0xFF15161A)
    ) { innerPadding ->
        SettingsScreen(viewModel = viewModel, modifier = Modifier.padding(innerPadding))
    }
}

@Composable
fun SettingsScreen(viewModel: SettingsViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 100.dp) // Space for bottom nav
    ) {
        // User Profile Section
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
                        .background(Color(0xFF2A2D35)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("N", style = MaterialTheme.typography.headlineMedium, color = Color.White)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text("Nilesh N.", style = MaterialTheme.typography.titleMedium, color = Color.White)
                    Text("nilesh@example.com", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                }
            }
        }
        
        item {
            SettingsSectionHeader("ACCOUNT")
            SettingsMockupItem(icon = "G", title = "Google Account", subtitle = "Connected")
            SettingsMockupItem(
                icon = "☁", 
                title = "Daily Backup to Google Sheets", 
                subtitle = "On", 
                onClick = { viewModel.toggleDailyBackup(context, true) }
            )
            SettingsMockupItem(icon = "📊", title = "Sheet", subtitle = "My Budget - Nilesh")
        }
        
        item {
            SettingsSectionHeader("PREFERENCES")
            SettingsMockupItem(icon = "⚙", title = "Currency", subtitle = "INR (₹)")
        }
    }
}

@Composable
fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall,
        color = Color.Gray,
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
                color = Color.White
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray)
        }
    }
    Divider(
        color = Color(0xFF2A2D35),
        modifier = Modifier.padding(horizontal = 24.dp)
    )
}
