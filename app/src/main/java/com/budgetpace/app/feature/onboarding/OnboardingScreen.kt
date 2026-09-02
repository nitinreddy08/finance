package com.budgetpace.app.feature.onboarding

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

data class CategoryEntry(val name: String, val budgetMinor: Long)

@Composable
fun OnboardingRoute(
    viewModel: OnboardingViewModel = hiltViewModel(),
    onComplete: () -> Unit
) {
    var step by remember { mutableStateOf(0) }

    // Categories with individual budgets - spec section 23
    var categories by remember { mutableStateOf(listOf<CategoryEntry>()) }

    val context = LocalContext.current

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF15161A)) {
        when (step) {

            // ── STEP 0: Welcome ──────────────────────────────────────────────
            0 -> {
                val signInState by viewModel.signInState.collectAsState()
                LaunchedEffect(signInState) {
                    if (signInState == true) step = 1
                }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 28.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Spend at the speed\nyou planned.",
                        style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold, lineHeight = 44.sp),
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = "Automatically capture Kotak & SBI transactions, categorize in one tap, and see whether you are spending too fast.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(72.dp))
                    Button(
                        onClick = { viewModel.signInWithGoogle(context) },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Continue with Google", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    TextButton(onClick = { step = 1 }) {
                        Text("Skip for now", color = Color(0xFF6B7280))
                    }
                }
            }

            // ── STEP 1: Create Categories with individual budgets ─────────────
            // Per spec section 23 & 25: total = sum of category budgets
            1 -> {
                var newName by remember { mutableStateOf("") }
                var newBudget by remember { mutableStateOf("") }
                var showAddRow by remember { mutableStateOf(false) }
                val focusRequester = remember { FocusRequester() }

                // Total is SUM of all category budgets - per spec
                val totalMinor = categories.sumOf { it.budgetMinor }

                Column(modifier = Modifier.fillMaxSize()) {
                    // Header
                    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                        Spacer(modifier = Modifier.height(56.dp))
                        Text(
                            "Set your categories",
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Add each spending category and its monthly budget. Your total spend limit is the sum.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // Running total
                        Surface(
                            color = if (totalMinor > 0) Color(0xFF1B3A1B) else Color(0xFF1E1F24),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Total monthly budget", color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "\u20B9${"%,d".format(totalMinor / 100)}",
                                    color = if (totalMinor > 0) Color(0xFF4CAF50) else Color.Gray,
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // Category list
                    LazyColumn(
                        modifier = Modifier.weight(1f).padding(horizontal = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(categories, key = { it.name }) { entry ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF1E1F24))
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(entry.name, color = Color.White, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
                                    Text(
                                        "\u20B9${"%,d".format(entry.budgetMinor / 100)} / month",
                                        color = Color(0xFF4CAF50),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                IconButton(onClick = {
                                    categories = categories.filter { it.name != entry.name }
                                }) {
                                    Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color(0xFF6B7280), modifier = Modifier.size(18.dp))
                                }
                            }
                        }

                        // Add new row inline
                        if (showAddRow) {
                            item {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFF1E1F24))
                                        .padding(12.dp)
                                ) {
                                    TextField(
                                        value = newName,
                                        onValueChange = { newName = it },
                                        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                                        placeholder = { Text("Category name  e.g. Groceries", color = Color(0xFF6B7280)) },
                                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
                                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                                        colors = TextFieldDefaults.colors(
                                            focusedContainerColor = Color.Transparent,
                                            unfocusedContainerColor = Color.Transparent,
                                            focusedIndicatorColor = Color(0xFF4CAF50),
                                            unfocusedIndicatorColor = Color(0xFF2A2D35),
                                            cursorColor = Color(0xFF4CAF50)
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    TextField(
                                        value = newBudget,
                                        onValueChange = { newBudget = it.filter { c -> c.isDigit() } },
                                        modifier = Modifier.fillMaxWidth(),
                                        placeholder = { Text("Monthly budget (e.g. 3000)", color = Color(0xFF6B7280)) },
                                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
                                        prefix = { Text("\u20B9 ", color = Color(0xFF4CAF50), style = MaterialTheme.typography.bodyLarge) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                                        keyboardActions = KeyboardActions(onDone = {
                                            val n = newName.trim()
                                            val b = (newBudget.toLongOrNull() ?: 0L) * 100L
                                            if (n.isNotBlank() && b > 0 && categories.none { it.name.equals(n, ignoreCase = true) }) {
                                                categories = categories + CategoryEntry(n, b)
                                                newName = ""
                                                newBudget = ""
                                                showAddRow = false
                                            }
                                        }),
                                        colors = TextFieldDefaults.colors(
                                            focusedContainerColor = Color.Transparent,
                                            unfocusedContainerColor = Color.Transparent,
                                            focusedIndicatorColor = Color(0xFF4CAF50),
                                            unfocusedIndicatorColor = Color(0xFF2A2D35),
                                            cursorColor = Color(0xFF4CAF50)
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(
                                            onClick = { showAddRow = false; newName = ""; newBudget = "" },
                                            modifier = Modifier.weight(1f),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2A2D35)),
                                            shape = RoundedCornerShape(8.dp)
                                        ) { Text("Cancel", color = Color.Gray) }
                                        Button(
                                            onClick = {
                                                val n = newName.trim()
                                                val b = (newBudget.toLongOrNull() ?: 0L) * 100L
                                                if (n.isNotBlank() && b > 0 && categories.none { it.name.equals(n, ignoreCase = true) }) {
                                                    categories = categories + CategoryEntry(n, b)
                                                    newName = ""
                                                    newBudget = ""
                                                    showAddRow = false
                                                }
                                            },
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                                            shape = RoundedCornerShape(8.dp),
                                            enabled = newName.isNotBlank() && (newBudget.toLongOrNull() ?: 0L) > 0
                                        ) { Text("Add", color = Color.White) }
                                    }
                                }
                                LaunchedEffect(Unit) { focusRequester.requestFocus() }
                            }
                        }

                        item { Spacer(modifier = Modifier.height(8.dp)) }
                    }

                    // Bottom actions
                    Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
                        if (!showAddRow) {
                            OutlinedButton(
                                onClick = { showAddRow = true },
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2A2D35)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Add category", color = Color(0xFF4CAF50), style = MaterialTheme.typography.titleSmall)
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                        Button(
                            onClick = { step = 2 },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50), contentColor = Color.White),
                            shape = RoundedCornerShape(14.dp),
                            enabled = categories.isNotEmpty() && !showAddRow
                        ) {
                            Text(
                                if (categories.isEmpty()) "Add at least one category"
                                else "Continue  \u2192  Total \u20B9${"%,d".format(totalMinor / 100)}",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }

            // ── STEP 2: Notification Permission ───────────────────────────────
            2 -> {
                var showDialog by remember { mutableStateOf(false) }
                var userConfirmedPermission by remember { mutableStateOf(false) }

                if (showDialog) {
                    AlertDialog(
                        onDismissRequest = { showDialog = false },
                        containerColor = Color(0xFF1E1F24),
                        titleContentColor = Color.White,
                        textContentColor = Color.Gray,
                        title = { Text("One extra step for Android 13+", fontWeight = FontWeight.Bold) },
                        text = {
                            Text(
                                "Because this APK was installed outside the Play Store, Android blocks notification access by default.\n\n" +
                                "Steps:\n" +
                                "1. Settings \u2192 Apps \u2192 Budget Pace\n" +
                                "2. Tap \u22EE (3-dot menu) \u2192 Allow restricted settings\n" +
                                "3. Return here and tap Grant Permission again"
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                showDialog = false
                                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                            }) { Text("Open Settings", color = Color(0xFF4CAF50)) }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDialog = false }) { Text("Cancel", color = Color.Gray) }
                        }
                    )
                }

                Column(
                    modifier = Modifier.fillMaxSize().padding(28.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier.size(80.dp).clip(RoundedCornerShape(20.dp)).background(Color(0xFF1E1F24)),
                        contentAlignment = Alignment.Center
                    ) { Text("\uD83D\uDCF1", fontSize = 36.sp) }

                    Spacer(modifier = Modifier.height(28.dp))
                    Text("Enable notification access", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), color = Color.White, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Budget Pace uses Android notification access to detect Kotak and SBI bank transaction notifications shown by Google Messages. We never read personal messages.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )

                    if (userConfirmedPermission) {
                        Spacer(modifier = Modifier.height(20.dp))
                        Surface(color = Color(0xFF1B3A1B), shape = RoundedCornerShape(10.dp)) {
                            Text(
                                "\u2713  Notification access enabled",
                                color = Color(0xFF4CAF50),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(48.dp))

                    // Primary: open settings
                    Button(
                        onClick = { showDialog = true },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                        shape = RoundedCornerShape(14.dp)
                    ) { Text("Enable notification access", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)) }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Secondary: I have done it
                    OutlinedButton(
                        onClick = { userConfirmedPermission = true },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF4CAF50)),
                        shape = RoundedCornerShape(14.dp)
                    ) { Text("Notification access enabled \u2713", color = Color(0xFF4CAF50), style = MaterialTheme.typography.titleSmall) }

                    Spacer(modifier = Modifier.height(24.dp))
                    HorizontalDivider(color = Color(0xFF2A2D35))
                    Spacer(modifier = Modifier.height(24.dp))

                    // Finish
                    Button(
                        onClick = {
                            val totalMinor = categories.sumOf { it.budgetMinor }
                            viewModel.completeOnboarding(
                                spendLimitMinor = totalMinor,
                                categories = categories.map { it.name to it.budgetMinor },
                                onDone = { onComplete() }
                            )
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50), contentColor = Color.White),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Finish Setup  \u2192", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                    }
                }
            }
        }
    }
}
