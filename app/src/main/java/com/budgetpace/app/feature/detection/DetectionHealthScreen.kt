package com.budgetpace.app.feature.detection

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.budgetpace.app.domain.ingestion.DetectionHealth
import com.budgetpace.app.domain.ingestion.DetectionSnapshot
import com.budgetpace.app.ingestion.DetectionDiagnostics
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class DetectionHealthViewModel @Inject constructor(
    diagnostics: DetectionDiagnostics,
) : ViewModel() {
    val snapshot: StateFlow<DetectionSnapshot> = diagnostics.snapshot
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetectionHealthRoute(
    viewModel: DetectionHealthViewModel = hiltViewModel(),
    onBack: () -> Unit,
) {
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Detection health", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
    ) { innerPadding ->
        DetectionHealthScreen(snapshot = snapshot, modifier = Modifier.padding(innerPadding))
    }
}

/**
 * Spec's Detection health screen: the one-line status (also shown, shorter, on the Settings row)
 * followed by the five permission rows every bank-detection path depends on.
 */
@Composable
fun DetectionHealthScreen(snapshot: DetectionSnapshot, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var status by remember { mutableStateOf(DetectionStatusChecker.currentStatus(context)) }
    LifecycleResumeEffect(Unit) {
        status = DetectionStatusChecker.currentStatus(context)
        onPauseOrDispose {}
    }

    val summary = remember(snapshot, status) {
        DetectionHealth.summarize(snapshot, status, Instant.now())
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(16.dp),
        ) {
            Text(
                "STATUS",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp,
            )
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(6.dp))
            Text(
                summary,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        DetectionSetupList()
    }
}
