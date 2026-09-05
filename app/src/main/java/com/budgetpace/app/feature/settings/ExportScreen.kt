package com.budgetpace.app.feature.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.budgetpace.app.core.model.BudgetMonth
import com.budgetpace.app.domain.export.ExpenseCsv
import java.time.format.TextStyle
import java.util.Locale

/** Spec §55: Settings → Data → Export — a month picker plus "Save CSV..." / "Share". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportRoute(
    viewModel: SettingsViewModel = hiltViewModel(),
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val exportState by viewModel.exportState.collectAsStateWithLifecycle()
    val months by viewModel.months.collectAsStateWithLifecycle()

    var selectedMonth by remember { mutableStateOf<BudgetMonth?>(null) }
    var monthMenuExpanded by remember { mutableStateOf(false) }
    LaunchedEffect(months) {
        if (selectedMonth == null && months.isNotEmpty()) selectedMonth = months.first()
    }

    val snackbarHostState = remember { SnackbarHostState() }

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        val month = selectedMonth
        if (uri != null && month != null) viewModel.exportCsvToUri(month.id.toString(), uri)
    }

    LaunchedEffect(exportState) {
        when (val state = exportState) {
            is ExportState.Success -> {
                snackbarHostState.showSnackbar("Export complete")
                viewModel.consumeExportState()
            }
            is ExportState.Error -> {
                // Spec §61: never expose raw technical errors to the user.
                snackbarHostState.showSnackbar(state.message)
                viewModel.consumeExportState()
            }
            else -> Unit
        }
    }

    val isExporting = exportState is ExportState.Exporting

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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier.padding(innerPadding).fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Text(
                "Month",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            Box {
                OutlinedButton(
                    onClick = { if (months.isNotEmpty()) monthMenuExpanded = true },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    enabled = months.isNotEmpty(),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(selectedMonth?.let(::monthLabel) ?: "No months yet")
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                }
                DropdownMenu(expanded = monthMenuExpanded, onDismissRequest = { monthMenuExpanded = false }) {
                    months.forEach { month ->
                        DropdownMenuItem(
                            text = { Text(monthLabel(month)) },
                            onClick = { selectedMonth = month; monthMenuExpanded = false },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    val month = selectedMonth ?: return@Button
                    saveLauncher.launch(ExpenseCsv.fileName(month.year, month.month))
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled = selectedMonth != null && !isExporting,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                Text(
                    if (isExporting) "Exporting…" else "Save CSV…",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = {
                    val month = selectedMonth ?: return@OutlinedButton
                    val fileName = ExpenseCsv.fileName(month.year, month.month)
                    viewModel.prepareCsvForSharing(month.id.toString(), fileName) { file ->
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/csv"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share CSV"))
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled = selectedMonth != null && !isExporting,
            ) {
                Text("Share")
            }
        }
    }
}

private fun monthLabel(month: BudgetMonth): String {
    val monthName = java.time.Month.of(month.month).getDisplayName(TextStyle.FULL, Locale.getDefault())
    return "$monthName ${month.year}"
}
