package com.budgetpace.app.feature.onboarding

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun OnboardingRoute(
    viewModel: OnboardingViewModel = hiltViewModel(),
    onComplete: () -> Unit
) {
    var step by remember { mutableStateOf(0) }
    var income by remember { mutableStateOf("") }
    val categories = listOf("Groceries", "Dining", "Fuel", "Bills", "Shopping", "Entertainment", "Health", "Travel")
    var selectedCategories by remember { mutableStateOf(setOf("Groceries", "Fuel", "Bills")) }
    val context = LocalContext.current

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF15161A)) {
        when (step) {
            0 -> {
                val signInState by viewModel.signInState.collectAsState()
                LaunchedEffect(signInState) { if (signInState == true) step = 1 }
                Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Spend at the speed you planned.", style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold), color = Color.White, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(24.dp))
                    Text("Automatically capture bank transactions, categorize them in one tap, and see whether you are spending too fast.", style = MaterialTheme.typography.bodyLarge, color = Color.Gray, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(64.dp))
                    Button(onClick = { viewModel.signInWithGoogle(context) }, modifier = Modifier.fillMaxWidth().height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black), shape = RoundedCornerShape(12.dp)) {
                        Text("Continue with Google", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    TextButton(onClick = { step = 1 }) { Text("Skip for now", color = Color.Gray) }
                }
            }
            1 -> {
                Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
                    Text("What is your monthly income?", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold), color = Color.White)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("We will use this to set up your baseline budget.", style = MaterialTheme.typography.bodyLarge, color = Color.Gray)
                    Spacer(modifier = Modifier.height(32.dp))
                    TextField(
                        value = income, onValueChange = { income = it },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.headlineMedium.copy(color = Color.White),
                        prefix = { Text("\u20B9 ", style = MaterialTheme.typography.headlineMedium, color = Color.Gray) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, focusedIndicatorColor = Color(0xFF4CAF50), unfocusedIndicatorColor = Color(0xFF2A2D35)),
                        placeholder = { Text("0", style = MaterialTheme.typography.headlineMedium, color = Color.DarkGray) }
                    )
                    Spacer(modifier = Modifier.height(64.dp))
                    Button(onClick = { step = 2 }, modifier = Modifier.fillMaxWidth().height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50), contentColor = Color.White), shape = RoundedCornerShape(12.dp), enabled = income.isNotBlank()) {
                        Text("Continue", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
            2 -> {
                Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
                    Spacer(modifier = Modifier.height(48.dp))
                    Text("Select your categories", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold), color = Color.White)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("What do you usually spend money on?", style = MaterialTheme.typography.bodyLarge, color = Color.Gray)
                    Spacer(modifier = Modifier.height(32.dp))
                    LazyVerticalGrid(columns = GridCells.Fixed(2), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
                        items(categories) { cat ->
                            val isSelected = selectedCategories.contains(cat)
                            Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (isSelected) Color(0xFF4CAF50).copy(alpha = 0.2f) else Color(0xFF1E1F24)).clickable { selectedCategories = if (isSelected) selectedCategories - cat else selectedCategories + cat }.padding(16.dp), contentAlignment = Alignment.Center) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (isSelected) { Icon(Icons.Default.Check, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(18.dp)); Spacer(modifier = Modifier.width(8.dp)) }
                                    Text(text = cat, color = if (isSelected) Color(0xFF4CAF50) else Color.White, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                                }
                            }
                        }
                    }
                    Button(onClick = { step = 3 }, modifier = Modifier.fillMaxWidth().height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50), contentColor = Color.White), shape = RoundedCornerShape(12.dp), enabled = selectedCategories.isNotEmpty()) {
                        Text("Continue", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
            3 -> {
                var showRestrictedDialog by remember { mutableStateOf(false) }
                if (showRestrictedDialog) {
                    AlertDialog(
                        onDismissRequest = { showRestrictedDialog = false },
                        title = { Text("Android 13+ Setup Required") },
                        text = { Text("Because this app was installed outside the Play Store, Android blocks sensitive permissions by default.\n\nTo fix this:\n1. Open phone Settings\n2. Go to Apps > BudgetPace\n3. Tap the 3-dot menu (top right)\n4. Tap Allow restricted settings\n\nThen come back and tap Grant Permission.") },
                        confirmButton = { TextButton(onClick = { showRestrictedDialog = false; context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }) { Text("Open Settings") } },
                        dismissButton = { TextButton(onClick = { showRestrictedDialog = false }) { Text("Cancel") } }
                    )
                }
                Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(modifier = Modifier.size(80.dp).clip(RoundedCornerShape(20.dp)).background(Color(0xFF1E1F24)), contentAlignment = Alignment.Center) {
                        Text("\uD83D\uDCE9", fontSize = 32.sp)
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Text("Read SMS Notifications", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), color = Color.White)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = "BudgetPace needs Notification Access to automatically parse SMS messages from Kotak and SBI so you never miss an expense.", style = MaterialTheme.typography.bodyLarge, color = Color.Gray, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(64.dp))
                    Button(onClick = { showRestrictedDialog = true }, modifier = Modifier.fillMaxWidth().height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black), shape = RoundedCornerShape(12.dp)) {
                        Text("Grant Permission", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    TextButton(onClick = {
                        val incomeMinor = (income.toLongOrNull() ?: 0L) * 100
                        viewModel.completeOnboarding(incomeMinor, selectedCategories.toList())
                        onComplete()
                    }) {
                        Text("I have done it, Finish Setup", color = Color(0xFF4CAF50))
                    }
                }
            }
        }
    }
}
