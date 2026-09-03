package com.budgetpace.app.feature.onboarding

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.budgetpace.app.core.designsystem.components.CATEGORY_EMOJI_CHOICES
import com.budgetpace.app.core.designsystem.components.CategoryIcon
import com.budgetpace.app.feature.categories.CategoryFormDialog

/** Spec §23/§25: total spend limit is the sum of each category's own monthly budget. */
data class CategoryEntry(
    val name: String,
    val budgetMinor: Long,
    val periodCount: Int,
    val iconKey: String,
)

/**
 * Spec §1/§2: onboarding is exactly 3 screens — Welcome, Google, Budget Setup. Notification
 * access is no longer requested here; it moved to Settings, reachable any time after setup.
 */
@Composable
fun OnboardingRoute(
    viewModel: OnboardingViewModel = hiltViewModel(),
    onComplete: () -> Unit
) {
    var step by remember { mutableStateOf(0) }
    var categories by remember { mutableStateOf(listOf<CategoryEntry>()) }
    val context = LocalContext.current

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (step) {

            // ── STEP 0: Welcome ──────────────────────────────────────────────
            0 -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 28.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "BUDGET PACE",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 3.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Know what you can spend.",
                        style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold, lineHeight = 44.sp),
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(72.dp))
                    Button(
                        onClick = { step = 1 },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50), contentColor = Color.White),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Get Started", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                    }
                }
            }

            // ── STEP 1: Google ───────────────────────────────────────────────
            1 -> {
                val signInState by viewModel.signInState.collectAsState()
                LaunchedEffect(signInState) {
                    if (signInState == true) step = 2
                }
                LaunchedEffect(Unit) {
                    viewModel.signInError.collect {
                        // Spec §61: never expose the raw exception to the user — the real cause
                        // is logged via Log.e in AuthRepositoryImpl for diagnosis from logcat.
                        Toast.makeText(context, "Couldn't sign in with Google. You can try again or skip for now.", Toast.LENGTH_LONG).show()
                    }
                }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 28.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Keep your data backed up",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Connect Google to back up your budget and expenses to your personal Google Sheet.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(48.dp))
                    Button(
                        onClick = { viewModel.signInWithGoogle(context) },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        // Google's Sign-in button branding is a fixed white pill with black
                        // text/logo regardless of the host app's theme — not theme-adaptive.
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Continue with Google", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    TextButton(onClick = { step = 2 }) {
                        Text("Skip for now", color = Color(0xFF6B7280))
                    }
                }
            }

            // ── STEP 2: Budget Setup ─────────────────────────────────────────
            2 -> {
                var showAddDialog by remember { mutableStateOf(false) }
                val totalMinor = categories.sumOf { it.budgetMinor }

                if (showAddDialog) {
                    CategoryFormDialog(
                        title = "Add category",
                        initialName = "",
                        initialBudget = "",
                        initialPeriodCount = 4,
                        initialIconKey = CATEGORY_EMOJI_CHOICES.first(),
                        onDismiss = { showAddDialog = false },
                        onConfirm = { name, budgetMinor, periodCount, iconKey ->
                            val trimmed = name.trim()
                            if (trimmed.isNotBlank() && budgetMinor > 0 && categories.none { it.name.equals(trimmed, ignoreCase = true) }) {
                                categories = categories + CategoryEntry(trimmed, budgetMinor, periodCount, iconKey)
                            }
                            showAddDialog = false
                        }
                    )
                }

                Column(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                        Spacer(modifier = Modifier.height(56.dp))
                        Text(
                            "Set your categories",
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Add each spending category and its monthly budget. Your total spend limit is the sum.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Surface(
                            color = if (totalMinor > 0) Color(0xFF1B3A1B) else MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Total monthly budget", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "₹${"%,d".format(totalMinor / 100)}",
                                    color = if (totalMinor > 0) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    LazyColumn(
                        modifier = Modifier.weight(1f).padding(horizontal = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(categories, key = { it.name }) { entry ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    CategoryIcon(iconKey = entry.iconKey, name = entry.name, size = 36.dp)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(entry.name, color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
                                        Text(
                                            if (entry.periodCount <= 1) "Start of month" else "${entry.periodCount} periods",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                                Text(
                                    "₹${"%,d".format(entry.budgetMinor / 100)}",
                                    color = Color(0xFF4CAF50),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                IconButton(onClick = {
                                    categories = categories.filter { it.name != entry.name }
                                }) {
                                    Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color(0xFF6B7280), modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                        item { Spacer(modifier = Modifier.height(8.dp)) }
                    }

                    Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
                        OutlinedButton(
                            onClick = { showAddDialog = true },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Add category", color = Color(0xFF4CAF50), style = MaterialTheme.typography.titleSmall)
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = {
                                viewModel.completeOnboarding(
                                    categories = categories,
                                    onDone = { onComplete() }
                                )
                            },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50), contentColor = Color.White),
                            shape = RoundedCornerShape(14.dp),
                            enabled = categories.isNotEmpty()
                        ) {
                            Text(
                                if (categories.isEmpty()) "Add at least one category"
                                else "Finish Setup  →  Total ₹${"%,d".format(totalMinor / 100)}",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                        }
                    }
                }
            }
        }
    }
}
