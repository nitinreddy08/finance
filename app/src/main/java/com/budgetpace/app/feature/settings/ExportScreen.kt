package com.budgetpace.app.feature.settings

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Spec §15: Settings → Data → Export. Only "Transactions" is actually wired to CSV export in V1;
 * Budget and Analytics are shown honestly as not yet available rather than pretending to work.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportRoute(
    viewModel: SettingsViewModel = hiltViewModel(),
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val exportState by viewModel.exportState.collectAsStateWithLifecycle()
    var includeTransactions by remember { mutableStateOf(true) }

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Export", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)) },
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
        Column(
            modifier = Modifier.padding(innerPadding).fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Text(
                "What to export",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(12.dp))

            ExportOptionRow(
                label = "Transactions",
                checked = includeTransactions,
                enabled = true,
                onCheckedChange = { includeTransactions = it },
            )
            ExportOptionRow(label = "Budget", checked = false, enabled = false, subtitle = "Not yet available")
            ExportOptionRow(label = "Analytics", checked = false, enabled = false, subtitle = "Not yet available")

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = { viewModel.exportCurrentMonthCsv() },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled = includeTransactions && exportState != ExportState.Exporting,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                Text(
                    if (exportState == ExportState.Exporting) "Exporting…" else "Export current month CSV",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
            }
        }
    }
}

@Composable
private fun ExportOptionRow(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    subtitle: String? = null,
    onCheckedChange: (Boolean) -> Unit = {},
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(label, color = if (enabled) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
