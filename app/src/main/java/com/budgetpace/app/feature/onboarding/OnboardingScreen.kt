package com.budgetpace.app.feature.onboarding

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.budgetpace.app.core.designsystem.components.CATEGORY_EMOJI_CHOICES
import com.budgetpace.app.core.designsystem.components.CategoryIcon
import com.budgetpace.app.core.designsystem.theme.bpColors
import com.budgetpace.app.core.money.Money
import com.budgetpace.app.feature.categories.CategoryFormDialog

private const val STEP_WELCOME = 0
private const val STEP_GOOGLE = 1
private const val STEP_BUDGET_SETUP = 2
private const val STEP_COUNT = 3

/**
 * Spec §1/§2: onboarding is exactly 3 screens — Welcome, Google, Budget Setup. Notification
 * access is no longer requested here; it moved to Settings, reachable any time after setup.
 */
@Composable
fun OnboardingRoute(
    viewModel: OnboardingViewModel = hiltViewModel(),
    onComplete: () -> Unit
) {
    val step by viewModel.step.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()

    BackHandler(enabled = step > STEP_WELCOME) {
        viewModel.goToStep(step - 1)
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            StepIndicator(currentStep = step, totalSteps = STEP_COUNT)

            when (step) {
                STEP_WELCOME -> WelcomeStep(onGetStarted = { viewModel.goToStep(STEP_GOOGLE) })
                STEP_GOOGLE -> GoogleStep(
                    viewModel = viewModel,
                    onContinue = { viewModel.goToStep(STEP_BUDGET_SETUP) },
                )
                STEP_BUDGET_SETUP -> BudgetSetupStep(
                    categories = categories,
                    onAddCategory = viewModel::addCategory,
                    onRemoveCategory = viewModel::removeCategory,
                    onFinish = { viewModel.completeOnboarding(onDone = onComplete) },
                )
            }
        }
    }
}

@Composable
private fun StepIndicator(currentStep: Int, totalSteps: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        repeat(totalSteps) { index ->
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .size(if (index == currentStep) 10.dp else 8.dp)
                    .clip(CircleShape)
                    .background(
                        if (index <= currentStep) MaterialTheme.bpColors.accent
                        else MaterialTheme.colorScheme.surfaceContainerHigh
                    )
            )
        }
    }
}

@Composable
private fun WelcomeStep(onGetStarted: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "BUDGET PACE",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            onClick = onGetStarted,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.bpColors.accent, contentColor = MaterialTheme.bpColors.onAccent),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("Get Started", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
        }
    }
}

@Composable
private fun GoogleStep(
    viewModel: OnboardingViewModel,
    onContinue: () -> Unit,
) {
    val context = LocalContext.current
    val signInState by viewModel.signInState.collectAsStateWithLifecycle()
    LaunchedEffect(signInState) {
        if (signInState == true) onContinue()
    }
    LaunchedEffect(Unit) {
        viewModel.signInError.collect {
            // Spec §61: never expose the raw exception to the user — the real cause is logged
            // via Log.e in AuthRepositoryImpl for diagnosis from logcat.
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
            // Google's Sign-in button branding is fixed by Google, not by this app's theme — but
            // it still has to invert between a light and a dark surface, so the two variants
            // come from Color.kt tokens (light/dark) rather than one hardcoded literal.
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.bpColors.googleButtonBackground,
                contentColor = MaterialTheme.bpColors.googleButtonText,
            ),
            border = BorderStroke(1.dp, MaterialTheme.bpColors.googleButtonBorder),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("Continue with Google", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
        }
        Spacer(modifier = Modifier.height(14.dp))
        TextButton(onClick = onContinue) {
            Text("Skip for now", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun BudgetSetupStep(
    categories: List<CategoryEntry>,
    onAddCategory: (CategoryEntry) -> Unit,
    onRemoveCategory: (String) -> Unit,
    onFinish: () -> Unit,
) {
    var showAddDialog by remember { mutableStateOf(false) }
    val totalMinor = categories.sumOf { it.budgetMinor }

    if (showAddDialog) {
        CategoryFormDialog(
            title = "Add category",
            initialName = "",
            initialBudget = "",
            initialPeriodCount = 4,
            initialIconKey = CATEGORY_EMOJI_CHOICES.first(),
            existingNames = categories.map { it.name },
            onDismiss = { showAddDialog = false },
            onConfirm = { name, budgetMinor, periodCount, iconKey ->
                val trimmed = name.trim()
                if (trimmed.isNotBlank() && budgetMinor > 0 && categories.none { it.name.equals(trimmed, ignoreCase = true) }) {
                    onAddCategory(CategoryEntry(trimmed, budgetMinor, periodCount, iconKey))
                }
                showAddDialog = false
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Set your categories",
                style = MaterialTheme.typography.headlineMedium,
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
                color = if (totalMinor > 0) MaterialTheme.bpColors.accent.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Total monthly budget", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        Money.formatRupeesWhole(totalMinor),
                        color = if (totalMinor > 0) MaterialTheme.bpColors.accent else MaterialTheme.colorScheme.onSurfaceVariant,
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
                        .background(MaterialTheme.colorScheme.surfaceContainer)
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
                        Money.formatRupeesWhole(entry.budgetMinor),
                        color = MaterialTheme.bpColors.accent,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    IconButton(onClick = { onRemoveCategory(entry.name) }) {
                        Icon(Icons.Default.Close, contentDescription = "Remove", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(8.dp)) }
        }

        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
            OutlinedButton(
                onClick = { showAddDialog = true },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.bpColors.accent, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add category", color = MaterialTheme.bpColors.accent, style = MaterialTheme.typography.titleSmall)
            }
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = onFinish,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.bpColors.accent, contentColor = MaterialTheme.bpColors.onAccent),
                shape = RoundedCornerShape(14.dp),
                enabled = categories.isNotEmpty()
            ) {
                Text(
                    if (categories.isEmpty()) "Add at least one category"
                    else "Finish Setup  →  Total ${Money.formatRupeesWhole(totalMinor)}",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
            }
        }
    }
}
