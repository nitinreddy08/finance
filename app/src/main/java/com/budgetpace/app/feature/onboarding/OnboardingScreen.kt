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

// Default starter categories the user can remove or keep
private val DEFAULT_CATEGORIES = listOf(
    "Groceries", "Rent", "Fuel", "Dining Out",
    "Bills & Utilities", "Entertainment", "Health", "Shopping"
)

data class CategoryEntry(val name: String, val budgetMinor: Long)

@Composable
fun OnboardingRoute(
    viewModel: OnboardingViewModel = hiltViewModel(),
    onComplete: () -> Unit
) {
    var step by remember { mutableStateOf(0) }
    var spendLimit by remember { mutableStateOf("") }

    // Categories: user can add/remove freely
    var categories by remember {
        mutableStateOf(DEFAULT_CATEGORIES.map { CategoryEntry(it, 0L) })
    }

    val context = LocalContext.current
    val onboardingComplete by viewModel.onboardingComplete.collectAsState()

    LaunchedEffect(onboardingComplete) {
        if (onboardingComplete) onComplete()
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF15161A)) {
        when (step) {

            // ── STEP 0: Welcome ──────────────────────────────────────────────
            0 -> {
                val signInState by viewModel.signInState.collectAsState()
                LaunchedEffect(signInState) {
                    if (signInState == true) step = 1
                }
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Spend at the speed\nyou planned.",
                        style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Auto-capture Kotak & SBI transactions, pace your budget across 4 weekly periods, and never overspend again.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(64.dp))
                    Button(
                        onClick = { viewModel.signInWithGoogle(context) },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Continue with Google", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    TextButton(onClick = { step = 1 }) {
                        Text("Skip for now", color = Color.Gray)
                    }
                }
            }

            // ── STEP 1: Monthly Spend Limit ──────────────────────────────────
            1 -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        "What is your monthly\nspend limit?",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "This is the total amount you want to stay within this month. Not your salary — your goal.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(40.dp))
                    TextField(
                        value = spendLimit,
                        onValueChange = { spendLimit = it.filter { c -> c.isDigit() } },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.headlineLarge.copy(color = Color.White, fontWeight = FontWeight.Bold),
                        prefix = { Text("\u20B9 ", style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold), color = Color(0xFF4CAF50)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color(0xFF4CAF50),
                            unfocusedIndicatorColor = Color(0xFF2A2D35),
                            cursorColor = Color(0xFF4CAF50)
                        ),
                        placeholder = { Text("30,000", style = MaterialTheme.typography.headlineLarge, color = Color(0xFF3A3D45)) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (spendLimit.isNotBlank()) {
                        val amt = spendLimit.toLongOrNull() ?: 0L
                        Text(
                            "That is \u20B9${"%,d".format(amt / 4)} per week across 4 periods",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF4CAF50)
                        )
                    }
                    Spacer(modifier = Modifier.height(64.dp))
                    Button(
                        onClick = { step = 2 },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50), contentColor = Color.White),
                        shape = RoundedCornerShape(12.dp),
                        enabled = spendLimit.isNotBlank() && (spendLimit.toLongOrNull() ?: 0L) > 0
                    ) {
                        Text("Continue", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                    }
                }
            }

            // ── STEP 2: Categories (fully customisable) ───────────────────────
            2 -> {
                var newCategoryName by remember { mutableStateOf("") }
                val totalBudget = (spendLimit.toLongOrNull() ?: 0L) * 100L
                val perCategory = if (categories.isNotEmpty()) totalBudget / categories.size else 0L

                Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
                    Spacer(modifier = Modifier.height(48.dp))
                    Text(
                        "Your spending categories",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Remove ones you don't need. Add your own. Budget split equally.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(20.dp))

                    // Add custom category row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextField(
                            value = newCategoryName,
                            onValueChange = { newCategoryName = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Add category...", color = Color.Gray) },
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                val trimmed = newCategoryName.trim()
                                if (trimmed.isNotBlank() && categories.none { it.name.equals(trimmed, ignoreCase = true) }) {
                                    categories = categories + CategoryEntry(trimmed, perCategory)
                                    newCategoryName = ""
                                }
                            }),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFF1E1F24),
                                unfocusedContainerColor = Color(0xFF1E1F24),
                                focusedIndicatorColor = Color(0xFF4CAF50),
                                unfocusedIndicatorColor = Color.Transparent,
                                cursorColor = Color(0xFF4CAF50)
                            ),
                            shape = RoundedCornerShape(topStart = 10.dp, bottomStart = 10.dp)
                        )
                        IconButton(
                            onClick = {
                                val trimmed = newCategoryName.trim()
                                if (trimmed.isNotBlank() && categories.none { it.name.equals(trimmed, ignoreCase = true) }) {
                                    categories = categories + CategoryEntry(trimmed, perCategory)
                                    newCategoryName = ""
                                }
                            },
                            modifier = Modifier.background(Color(0xFF4CAF50), RoundedCornerShape(topEnd = 10.dp, bottomEnd = 10.dp)).size(56.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add", tint = Color.White)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Category list
                    LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                    if (perCategory > 0) {
                                        Text("\u20B9${"%,d".format(perCategory / 100)}/mo", color = Color(0xFF4CAF50), style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                                IconButton(onClick = { categories = categories.filter { it.name != entry.name } }) {
                                    Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color.Gray, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { step = 3 },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50), contentColor = Color.White),
                        shape = RoundedCornerShape(12.dp),
                        enabled = categories.isNotEmpty()
                    ) {
                        Text("Continue (${categories.size} categories)", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }

            // ── STEP 3: SMS Permission ────────────────────────────────────────
            3 -> {
                var showDialog by remember { mutableStateOf(false) }
                var permissionGranted by remember { mutableStateOf(false) }

                if (showDialog) {
                    AlertDialog(
                        onDismissRequest = { showDialog = false },
                        containerColor = Color(0xFF1E1F24),
                        titleContentColor = Color.White,
                        textContentColor = Color.Gray,
                        title = { Text("One extra step for Android 13+") },
                        text = {
                            Text(
                                "Because this APK was installed outside Play Store, Android restricts notification access.\n\n" +
                                "Steps:\n" +
                                "1. Settings \u2192 Apps \u2192 Budget Pace\n" +
                                "2. Tap \u22EE (3-dot menu) \u2192 Allow restricted settings\n" +
                                "3. Come back here and tap Grant Permission again"
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
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier.size(80.dp).clip(RoundedCornerShape(20.dp)).background(Color(0xFF1E1F24)),
                        contentAlignment = Alignment.Center
                    ) { Text("\uD83D\uDCF1", fontSize = 36.sp) }

                    Spacer(modifier = Modifier.height(24.dp))
                    Text("Auto-capture bank SMS", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), color = Color.White)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Grant Notification Access so BudgetPace can read Kotak and SBI SMS messages automatically. We never read personal messages.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )

                    if (permissionGranted) {
                        Spacer(modifier = Modifier.height(24.dp))
                        Surface(color = Color(0xFF1B5E20), shape = RoundedCornerShape(10.dp)) {
                            Text(
                                "\u2713 Notification access granted!",
                                color = Color(0xFF4CAF50),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(48.dp))

                    Button(
                        onClick = { showDialog = true },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Grant Permission", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)) }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = { permissionGranted = true },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF4CAF50)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF4CAF50)),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("I have allowed it \u2713", style = MaterialTheme.typography.titleMedium) }

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            val totalMinor = (spendLimit.toLongOrNull() ?: 0L) * 100L
                            val perCat = if (categories.isNotEmpty()) totalMinor / categories.size else 0L
                            viewModel.completeOnboarding(
                                spendLimitMinor = totalMinor,
                                categories = categories.map { it.name to perCat }
                            )
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50), contentColor = Color.White),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Finish Setup \u2192", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)) }
                }
            }
        }
    }
}
