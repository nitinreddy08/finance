package com.budgetpace.app.feature.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.budgetpace.app.core.designsystem.components.CategoryIcon
import com.budgetpace.app.core.designsystem.statusColor
import com.budgetpace.app.core.designsystem.theme.bpColors
import com.budgetpace.app.core.model.MonthStatus
import com.budgetpace.app.core.model.CategorySummary
import com.budgetpace.app.core.model.MonthSummary
import com.budgetpace.app.core.model.PeriodStatus
import com.budgetpace.app.core.model.PeriodSummary
import com.budgetpace.app.core.model.Transaction
import com.budgetpace.app.core.money.Money
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Spec §7/§10: tapping a category anywhere (Home's Category Pace, or the Categories list) lands
 * here first — Edit/Delete are reached from within this screen rather than an action sheet.
 *
 * Routed as categories/{monthId}/{id} (rather than just the category id) so a tile tapped from an
 * ARCHIVED month's Home view resolves against that month's own summary, not the active month's —
 * Edit, Delete and Carry-forward are all disabled once the month itself is archived.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryDetailRoute(
    viewModel: CategoriesViewModel,
    monthId: String,
    categoryId: String,
    onBack: () -> Unit,
) {
    val monthSummary: MonthSummary? by remember(monthId) { viewModel.summaryForMonth(monthId) }
        .collectAsStateWithLifecycle(initialValue = null)
    val categories = monthSummary?.categories
    val isArchived = monthSummary?.month?.status == MonthStatus.ARCHIVED

    val summary = categories?.firstOrNull { it.category.id.toString() == categoryId }

    val transactions by remember(monthId, categoryId) { viewModel.observeCategoryTransactions(monthId, categoryId) }
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val otherCategories = categories
        ?.map { it.category }
        ?.filter { it.id.toString() != categoryId }
        ?: emptyList()

    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteFlow by remember { mutableStateOf(false) }

    // Once the category is gone (deleted, or not found), leave the screen — there's nothing left
    // to show. Loading (categories == null) is a separate, transient state and must not trigger
    // this.
    val notFound = categories != null && summary == null
    LaunchedEffect(notFound) {
        if (notFound) onBack()
    }
    if (notFound) return

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(summary?.category?.name ?: "", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
                    }
                },
                actions = {
                    if (summary != null && !isArchived) {
                        TextButton(onClick = { showEditDialog = true }) {
                            Text("Edit", color = MaterialTheme.bpColors.accent, style = MaterialTheme.typography.labelLarge)
                        }
                        IconButton(onClick = { showDeleteFlow = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete category", tint = MaterialTheme.bpColors.danger)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        summary?.let {
            CategoryDetailBody(
                summary = it,
                transactions = transactions,
                editable = !isArchived,
                modifier = Modifier.padding(innerPadding),
                onCarryForward = { sourcePeriod, targetPeriod, amountMinor ->
                    viewModel.carryForward(categoryId, sourcePeriod, targetPeriod, amountMinor)
                },
            )
        }
    }

    if (showEditDialog && summary != null) {
        CategoryFormDialog(
            title = "Edit category",
            initialName = summary.category.name,
            initialBudget = (summary.category.monthlyBudgetMinor / 100).toString(),
            initialPeriodCount = summary.category.periodCount,
            initialIconKey = summary.category.iconKey.ifBlank { com.budgetpace.app.core.designsystem.components.CATEGORY_EMOJI_CHOICES.first() },
            existingNames = otherCategories.map { it.name },
            onDismiss = { showEditDialog = false },
            onConfirm = { name, budgetMinor, periodCount, iconKey ->
                viewModel.updateCategory(categoryId, name, budgetMinor, periodCount, iconKey)
                showEditDialog = false
            }
        )
    }

    if (showDeleteFlow && summary != null) {
        DeleteCategoryFlow(
            category = summary.category,
            otherCategories = otherCategories,
            viewModel = viewModel,
            onDone = { showDeleteFlow = false; onBack() },
            onCancel = { showDeleteFlow = false },
        )
    }
}

@Composable
private fun CategoryDetailBody(
    summary: CategorySummary,
    transactions: List<Transaction>,
    editable: Boolean,
    modifier: Modifier = Modifier,
    onCarryForward: (sourcePeriod: Int, targetPeriod: Int, amountMinor: Long) -> Unit = { _, _, _ -> },
) {
    var showCarryForwardDialog by remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        if (!editable) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(
                    "Past month — editing and carry-forward are turned off.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            CategoryIcon(iconKey = summary.category.iconKey, name = summary.category.name, size = 56.dp)
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = summary.category.name,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = if (summary.category.periodCount <= 1) "Start of month" else "${summary.category.periodCount} periods",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        val pct = if (summary.category.monthlyBudgetMinor > 0)
            (summary.totalSpentMinor.toFloat() / summary.category.monthlyBudgetMinor * 100).toInt().coerceAtLeast(0)
        else 0
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            LabeledAmount("Spent", Money.formatRupeesWhole(summary.totalSpentMinor))
            LabeledAmount("Budget", Money.formatRupeesWhole(summary.category.monthlyBudgetMinor))
            LabeledAmount("Used", "$pct%")
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (summary.category.periodCount > 1) {
            Text(
                text = "PERIODS",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                summary.periods.forEach { period ->
                    PeriodBar(period = period, modifier = Modifier.weight(1f))
                }
            }

            // Only offer this when there's actually unused budget in a period that has already
            // started, and a later period to move it into — and never on a past month.
            val canCarryForward = editable && summary.periods.any { source ->
                source.periodStatus != PeriodStatus.UPCOMING &&
                    source.remainingMinor > 0 &&
                    source.periodIndex < summary.periods.last().periodIndex
            }
            if (canCarryForward) {
                Spacer(modifier = Modifier.height(12.dp))
                TextButton(onClick = { showCarryForwardDialog = true }) {
                    Text("Carry forward unused budget", color = MaterialTheme.bpColors.accent)
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        if (showCarryForwardDialog) {
            CarryForwardDialog(
                periods = summary.periods,
                onDismiss = { showCarryForwardDialog = false },
                onConfirm = { sourcePeriod, targetPeriod, amountMinor ->
                    onCarryForward(sourcePeriod, targetPeriod, amountMinor)
                    showCarryForwardDialog = false
                }
            )
        }

        Text(
            text = "EXPENSES",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (transactions.isEmpty()) {
            Text(
                text = "No expenses in this category yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp)
            )
        } else {
            transactions.forEach { transaction ->
                CategoryTransactionRow(transaction)
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun LabeledAmount(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onBackground)
    }
}

/** Bars only — spec's minimal-wording rule keeps "Current/Upcoming/Completed" out of the UI. */
@Composable
private fun PeriodBar(period: PeriodSummary, modifier: Modifier = Modifier) {
    val color = statusColor(period.paceStatus)
    val filled = if (period.periodStatus == PeriodStatus.UPCOMING || period.effectiveBudgetMinor <= 0) 0f
    else (period.spentMinor.toFloat() / period.effectiveBudgetMinor).coerceIn(0f, 1f)
    val statusDescription = when (period.periodStatus) {
        PeriodStatus.UPCOMING -> "upcoming"
        PeriodStatus.CURRENT -> "current"
        PeriodStatus.COMPLETED -> "completed"
    }

    Column(
        modifier = modifier.semantics {
            contentDescription = "Period ${period.periodIndex + 1}, $statusDescription, " +
                "${Money.formatRupeesWhole(period.spentMinor)} of ${Money.formatRupeesWhole(period.effectiveBudgetMinor)}"
        },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(filled)
                    .clip(RoundedCornerShape(4.dp))
                    .background(color)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = if (period.periodStatus == PeriodStatus.UPCOMING) "—" else Money.formatRupeesWhole(period.spentMinor),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CategoryTransactionRow(transaction: Transaction) {
    val payee = transaction.recipient ?: transaction.sender ?: "Uncategorized"
    val time = transaction.transactionDateTime?.atZone(ZoneId.systemDefault())
        ?.format(DateTimeFormatter.ofPattern("d MMM, hh:mm a")) ?: transaction.transactionDate.toString()

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(payee, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
            Text(time, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            Money.formatRupeesWhole(transaction.amountMinor),
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

/**
 * Lets the user move unused budget from a period that has already started into any later
 * period — the source doesn't have to be the immediate previous one, and the target doesn't
 * have to be the immediate next one; BudgetEngine already applies carry-forwards generically by
 * period index.
 */
@Composable
private fun CarryForwardDialog(
    periods: List<PeriodSummary>,
    onDismiss: () -> Unit,
    onConfirm: (sourcePeriod: Int, targetPeriod: Int, amountMinor: Long) -> Unit,
) {
    val sourceOptions = periods.filter { it.periodStatus != PeriodStatus.UPCOMING && it.remainingMinor > 0 }
    var sourcePeriod by remember { mutableStateOf(sourceOptions.first().periodIndex) }
    val targetOptions = periods.filter { it.periodIndex > sourcePeriod }
    var targetPeriod by remember(sourcePeriod) { mutableStateOf(targetOptions.firstOrNull()?.periodIndex) }
    val maxAmountMinor = periods.first { it.periodIndex == sourcePeriod }.remainingMinor
    var amountText by remember(sourcePeriod) { mutableStateOf((maxAmountMinor / 100).toString()) }
    val amountMinor = Money.rupeesToPaise(amountText.ifBlank { "0" }).coerceIn(0, maxAmountMinor)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onBackground,
        title = { Text("Carry forward unused budget") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("From", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                sourceOptions.forEach { period ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { sourcePeriod = period.periodIndex },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = sourcePeriod == period.periodIndex, onClick = null)
                        Text(
                            "Period ${period.periodIndex + 1} — ${Money.formatRupeesWhole(period.remainingMinor)} unused",
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { c -> c.isDigit() } },
                    label = { Text("Amount") },
                    prefix = { Text("₹ ") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )

                Text("To", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (targetOptions.isEmpty()) {
                    Text("No later period available.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                targetOptions.forEach { period ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { targetPeriod = period.periodIndex },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = targetPeriod == period.periodIndex, onClick = null)
                        Text("Period ${period.periodIndex + 1}", color = MaterialTheme.colorScheme.onBackground)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = amountMinor > 0 && targetPeriod != null,
                onClick = { targetPeriod?.let { onConfirm(sourcePeriod, it, amountMinor) } }
            ) {
                Text("Carry forward", color = MaterialTheme.bpColors.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    )
}
