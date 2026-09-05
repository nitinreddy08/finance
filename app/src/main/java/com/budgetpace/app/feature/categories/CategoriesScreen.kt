package com.budgetpace.app.feature.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.budgetpace.app.core.designsystem.components.CATEGORY_EMOJI_CHOICES
import com.budgetpace.app.core.designsystem.components.CategoryIcon
import com.budgetpace.app.core.designsystem.theme.bpColors
import com.budgetpace.app.core.model.BudgetCarryForward
import com.budgetpace.app.core.model.Category
import com.budgetpace.app.core.model.CategorySummary
import com.budgetpace.app.core.model.MonthStatus
import com.budgetpace.app.core.model.Transaction
import com.budgetpace.app.core.money.Money
import com.budgetpace.app.data.local.dao.BudgetMonthDao
import com.budgetpace.app.data.local.dao.CarryForwardDao
import com.budgetpace.app.data.local.dao.CategoryDao
import com.budgetpace.app.data.local.dao.TransactionDao
import com.budgetpace.app.data.local.db.BudgetDatabase
import com.budgetpace.app.data.local.mapper.toDomain
import com.budgetpace.app.data.local.mapper.toEntity
import com.budgetpace.app.domain.categorization.CategorizationPrompts
import com.budgetpace.app.domain.repository.BudgetRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class CategoriesViewModel @Inject constructor(
    private val budgetRepository: BudgetRepository,
    private val budgetMonthDao: BudgetMonthDao,
    private val categoryDao: CategoryDao,
    private val transactionDao: TransactionDao,
    private val carryForwardDao: CarryForwardDao,
    private val budgetDatabase: BudgetDatabase,
    private val prompts: CategorizationPrompts,
) : ViewModel() {

    val uiState: StateFlow<CategoriesUiState> = budgetRepository.observeActiveMonthSummary()
        .map { summary -> CategoriesUiState.Success(summary?.categories ?: emptyList()) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = CategoriesUiState.Loading
        )

    /** So the Categories tab (always the active month) can build a categories/{monthId}/{id}
     * route without a second round-trip through the DAO in the nav graph. */
    val activeMonthId: StateFlow<String?> = budgetRepository.observeActiveMonthSummary()
        .map { it?.month?.id?.toString() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Category Detail needs to read an ARCHIVED month too, and needs the month's own status to
     * know whether editing is allowed — [uiState] only ever reflects the active month, by
     * design, for the Categories list. */
    fun summaryForMonth(monthId: String) = budgetRepository.observeMonthSummary(monthId)

    /** Spec §23: categories are fully user-defined; this is the only place new ones are created. */
    fun addCategory(name: String, budgetMinor: Long, periodCount: Int, iconKey: String) {
        if (name.isBlank() || budgetMinor <= 0) return
        viewModelScope.launch {
            val month = budgetMonthDao.getActiveMonth() ?: return@launch
            val sortOrder = categoryDao.getByMonth(month.id).size
            val now = Instant.now()
            categoryDao.insert(
                Category(
                    id = UUID.randomUUID(),
                    monthId = UUID.fromString(month.id),
                    name = name.trim(),
                    monthlyBudgetMinor = budgetMinor,
                    periodCount = periodCount,
                    iconKey = iconKey,
                    sortOrder = sortOrder,
                    active = true,
                    createdAt = now,
                    updatedAt = now,
                ).toEntity()
            )
        }
    }

    fun updateCategory(categoryId: String, name: String, budgetMinor: Long, periodCount: Int, iconKey: String) {
        if (name.isBlank() || budgetMinor <= 0) return
        viewModelScope.launch {
            val existing = categoryDao.getById(categoryId) ?: return@launch
            val periodCountChanged = existing.periodCount != periodCount
            val updated = existing.copy(
                name = name.trim(),
                monthlyBudgetMinor = budgetMinor,
                periodCount = periodCount,
                iconKey = iconKey,
                updatedAt = Instant.now().toEpochMilli(),
            )
            if (periodCountChanged) {
                // Changing how many periods a category has renumbers them, so every
                // carry-forward it holds now points at a period that means something else —
                // both writes happen together or not at all.
                budgetDatabase.withTransaction {
                    categoryDao.update(updated)
                    carryForwardDao.deleteByCategory(categoryId)
                }
            } else {
                categoryDao.update(updated)
            }
        }
    }

    suspend fun transactionsForCategory(categoryId: String): List<Transaction> =
        transactionDao.getByCategory(categoryId).map { it.toDomain() }

    /** Spec: move unused budget from a completed/current period to any later period. */
    fun carryForward(categoryId: String, sourcePeriod: Int, targetPeriod: Int, amountMinor: Long) {
        if (amountMinor <= 0 || targetPeriod <= sourcePeriod) return
        viewModelScope.launch {
            val month = budgetMonthDao.getActiveMonth() ?: return@launch
            carryForwardDao.insert(
                BudgetCarryForward(
                    id = UUID.randomUUID(),
                    monthId = UUID.fromString(month.id),
                    categoryId = UUID.fromString(categoryId),
                    sourcePeriod = sourcePeriod,
                    targetPeriod = targetPeriod,
                    amountMinor = amountMinor,
                    createdAt = Instant.now(),
                ).toEntity()
            )
        }
    }

    /** Reactive transaction list for Category Detail — reflects edits/deletes without
     * renavigating. Takes the month explicitly (not always the active one) so an ARCHIVED
     * month's category detail reads that month's own expenses, not the active month's. */
    fun observeCategoryTransactions(monthId: String, categoryId: String): Flow<List<Transaction>> =
        transactionDao.observeByMonthAndCategory(monthId, categoryId)
            .map { list -> list.map { it.toDomain() } }

    /** Spec §45: never silently delete transactions — move them all, then deactivate the category. */
    fun moveAllAndDeactivate(categoryId: String, targetCategoryId: String) {
        viewModelScope.launch {
            val now = Instant.now().toEpochMilli()
            val affected = transactionDao.getByCategory(categoryId).map { it.id }
            transactionDao.moveAllFromCategory(categoryId, targetCategoryId, now)
            categoryDao.deactivate(categoryId, now)
            // A pending "what was this for?" prompt for any of these would still show the old
            // category's quick-actions after the move — take it down for each affected expense.
            affected.forEach { prompts.cancel(it) }
        }
    }

    /**
     * Moves only the selected transactions. The category is deactivated only once every one of
     * its transactions has actually been moved somewhere — it must never be left pointing at an
     * inactive category.
     */
    fun moveSelectedAndMaybeDeactivate(
        categoryId: String,
        targetCategoryId: String,
        transactionIds: List<String>,
        totalTransactionCount: Int,
    ) {
        viewModelScope.launch {
            val now = Instant.now().toEpochMilli()
            transactionIds.forEach { id ->
                transactionDao.updateCategory(id, targetCategoryId, now)
                prompts.cancel(id)
            }
            if (transactionIds.size >= totalTransactionCount) {
                categoryDao.deactivate(categoryId, now)
            }
        }
    }

    /** Only safe when the category genuinely has no transactions left to orphan — the delete
     * flow only ever reaches this branch after confirming that. */
    fun deactivateCategory(categoryId: String) {
        viewModelScope.launch {
            categoryDao.deactivate(categoryId, Instant.now().toEpochMilli())
        }
    }
}

sealed interface CategoriesUiState {
    object Loading : CategoriesUiState
    data class Success(val categories: List<CategorySummary>) : CategoriesUiState
    object Error : CategoriesUiState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesRoute(
    viewModel: CategoriesViewModel,
    onBack: () -> Unit,
    onCategoryClick: (String) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }

    val categories = (uiState as? CategoriesUiState.Success)?.categories ?: emptyList()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Categories", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
                    }
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add category", tint = MaterialTheme.colorScheme.onBackground)
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
        when (val state = uiState) {
            is CategoriesUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize().padding(innerPadding).background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.bpColors.statusGreen)
                }
            }
            is CategoriesUiState.Success -> {
                if (state.categories.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                        Text(
                            "Create your first spending category.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                } else {
                    CategoriesList(
                        categories = state.categories,
                        modifier = Modifier.padding(innerPadding),
                        // Spec §7: tapping a category opens Category Detail, not an action sheet —
                        // Edit/Delete now live inside that screen instead.
                        onCategoryClick = { onCategoryClick(it.category.id.toString()) }
                    )
                }
            }
            is CategoriesUiState.Error -> {
                Box(modifier = Modifier.fillMaxSize().padding(innerPadding).background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
                    Text("Couldn't load categories.", color = MaterialTheme.colorScheme.onBackground)
                }
            }
        }
    }

    if (showAddDialog) {
        CategoryFormDialog(
            title = "Add category",
            initialName = "",
            initialBudget = "",
            initialPeriodCount = 4,
            initialIconKey = CATEGORY_EMOJI_CHOICES.first(),
            existingNames = categories.map { it.category.name },
            onDismiss = { showAddDialog = false },
            onConfirm = { name, budgetMinor, periodCount, iconKey ->
                viewModel.addCategory(name, budgetMinor, periodCount, iconKey)
                showAddDialog = false
            }
        )
    }
}

/** How many periods a category's budget can be spread across — 1 means the whole amount is
 * available up front ("start of month"); anything higher divides it that many ways, equally. */
private val PERIOD_COUNT_OPTIONS = listOf(1, 2, 3, 4)

private const val MAX_BUDGET_DIGITS = 9 // up to ₹99,99,99,999 — comfortably above any real budget

@Composable
fun CategoryFormDialog(
    title: String,
    initialName: String,
    initialBudget: String,
    initialPeriodCount: Int,
    initialIconKey: String,
    onDismiss: () -> Unit,
    onConfirm: (name: String, budgetMinor: Long, periodCount: Int, iconKey: String) -> Unit,
    existingNames: List<String> = emptyList(),
) {
    var name by remember { mutableStateOf(initialName) }
    var budget by remember { mutableStateOf(initialBudget) }
    var periodCount by remember { mutableStateOf(initialPeriodCount) }
    var iconKey by remember { mutableStateOf(initialIconKey) }
    // Dismissing the dialog on confirm happens on the next recomposition, not instantly — an
    // impatient double-tap on Save could land both clicks first and insert the category twice.
    var hasConfirmed by remember { mutableStateOf(false) }

    val trimmedName = name.trim()
    val budgetMinor = Money.rupeesToPaise(budget.ifBlank { "0" })
    val isDuplicateName = trimmedName.isNotEmpty() &&
        existingNames.any { it.equals(trimmedName, ignoreCase = true) && !it.equals(initialName, ignoreCase = true) }
    val isEmoji = isLikelyEmoji(iconKey)

    val nameError = when {
        trimmedName.isEmpty() -> "Give this category a name."
        isDuplicateName -> "You already have a category with this name."
        else -> null
    }
    val budgetError = if (budgetMinor <= 0) "Enter a budget above ₹0." else null
    val iconError = if (!isEmoji) "Pick an emoji, not text." else null
    val canSave = nameError == null && budgetError == null && iconError == null

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onBackground,
        title = { Text(title) },
        text = {
            // Scrollable: up to 4 pacing options plus the emoji/name/budget fields can exceed a
            // small screen's height, and AlertDialog doesn't scroll its content by default.
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Emoji picker — spec §4: the chosen icon appears beside the category everywhere.
                // The curated row is a quick pick; the field below lets any emoji from the
                // device's own keyboard be used instead, since 20 curated choices don't scale.
                androidx.compose.foundation.lazy.LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(CATEGORY_EMOJI_CHOICES) { emoji ->
                        val selected = emoji == iconKey
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(if (selected) MaterialTheme.bpColors.accent.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant)
                                .selectable(selected = selected, onClick = { iconKey = emoji }),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(emoji, style = MaterialTheme.typography.titleLarge)
                        }
                    }
                }
                OutlinedTextField(
                    value = iconKey,
                    // Emoji can be multiple UTF-16 code units (skin tones, ZWJ sequences) — cap
                    // generously rather than truncating a single typed character in half.
                    onValueChange = { iconKey = it.take(8) },
                    label = { Text("Or type any emoji") },
                    singleLine = true,
                    isError = iconError != null,
                    supportingText = iconError?.let { { Text(it) } },
                    textStyle = MaterialTheme.typography.headlineSmall,
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Category name") },
                    singleLine = true,
                    isError = nameError != null,
                    supportingText = nameError?.let { { Text(it) } },
                )
                OutlinedTextField(
                    value = budget,
                    onValueChange = { input -> budget = input.filter { c -> c.isDigit() }.take(MAX_BUDGET_DIGITS) },
                    label = { Text("Monthly budget") },
                    prefix = { Text("₹ ") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    isError = budgetError != null,
                    supportingText = budgetError?.let { { Text(it) } },
                )

                Text(
                    "HOW SHOULD THIS BUDGET BE PACED?",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                PacingOption(
                    selected = periodCount == 1,
                    title = "Spend at start of month",
                    subtitle = "${Money.formatRupeesWhole(budgetMinor)} available in Period 1",
                    onClick = { periodCount = 1 },
                )
                PERIOD_COUNT_OPTIONS.filter { it > 1 }.forEach { n ->
                    val split = remember(budgetMinor, n) {
                        com.budgetpace.app.core.time.PeriodCalculator.splitBudget(budgetMinor, n)
                    }
                    PacingOption(
                        selected = periodCount == n,
                        title = "Spread across $n periods",
                        subtitle = split.joinToString(" · ") { Money.formatRupeesWhole(it) },
                        onClick = { periodCount = n },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave && !hasConfirmed,
                onClick = {
                    hasConfirmed = true
                    onConfirm(trimmedName, budgetMinor, periodCount, iconKey)
                }
            ) {
                Text("Save", color = if (canSave) MaterialTheme.bpColors.accent else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    )
}

/** A crude but effective emoji check: real emoji are Unicode Symbol/Other codepoints, never
 * Unicode letters — this rejects plain alphabetic text ("AB") typed into the free emoji field
 * without needing a full emoji-property table. */
private fun isLikelyEmoji(s: String): Boolean = s.isNotBlank() && s.none { it.isLetter() }

@Composable
private fun PacingOption(
    selected: Boolean,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) MaterialTheme.bpColors.accent.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceContainerHigh)
            .selectable(selected = selected, onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(modifier = Modifier.width(4.dp))
        Column {
            Text(title, color = MaterialTheme.colorScheme.onBackground)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private enum class DeleteMode { CONFIRM, NEEDS_ANOTHER_CATEGORY, MOVE_ALL, SELECT }

/** Spec §45: delete confirmation -> move-all or per-transaction selection, never a silent delete. */
@Composable
fun DeleteCategoryFlow(
    category: Category,
    otherCategories: List<Category>,
    viewModel: CategoriesViewModel,
    onDone: () -> Unit,
    onCancel: () -> Unit,
) {
    var transactions by remember { mutableStateOf<List<Transaction>?>(null) }
    var mode by remember { mutableStateOf(DeleteMode.CONFIRM) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(category.id) {
        transactions = viewModel.transactionsForCategory(category.id.toString())
    }

    val txns = transactions
    if (txns == null) return // still loading — no UI flash

    when (mode) {
        DeleteMode.CONFIRM -> AlertDialog(
            onDismissRequest = onCancel,
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onBackground,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            title = { Text("Delete \"${category.name}\"?") },
            text = {
                Text(
                    if (txns.isEmpty()) "No expenses use this category."
                    else "${txns.size} expense${if (txns.size == 1) "" else "s"} use this category."
                )
            },
            confirmButton = {
                if (txns.isEmpty()) {
                    TextButton(onClick = { viewModel.deactivateCategory(category.id.toString()); onDone() }) {
                        Text("Delete", color = MaterialTheme.bpColors.danger)
                    }
                } else if (otherCategories.isNotEmpty()) {
                    TextButton(onClick = { mode = DeleteMode.MOVE_ALL }) {
                        Text("Move all to another category", color = MaterialTheme.bpColors.accent)
                    }
                } else {
                    TextButton(onClick = { mode = DeleteMode.NEEDS_ANOTHER_CATEGORY }) {
                        Text("Continue", color = MaterialTheme.bpColors.accent)
                    }
                }
            },
            dismissButton = {
                Column {
                    if (txns.isNotEmpty() && otherCategories.isNotEmpty()) {
                        TextButton(onClick = { mode = DeleteMode.SELECT }) {
                            Text("Select expenses", color = MaterialTheme.colorScheme.onBackground)
                        }
                    }
                    TextButton(onClick = onCancel) { Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        )

        DeleteMode.NEEDS_ANOTHER_CATEGORY -> AlertDialog(
            onDismissRequest = onCancel,
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onBackground,
            title = { Text("Create another category first") },
            text = {
                Text(
                    "This category's expenses have to go somewhere. Add another category, then come back to delete \"${category.name}\".",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                TextButton(onClick = onCancel) { Text("OK", color = MaterialTheme.bpColors.accent) }
            }
        )

        DeleteMode.MOVE_ALL -> {
            var targetCategoryId by remember { mutableStateOf(otherCategories.firstOrNull()?.id?.toString()) }
            AlertDialog(
                onDismissRequest = onCancel,
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onBackground,
                title = { Text("Move all expenses to") },
                text = {
                    Column(
                        modifier = Modifier
                            .heightIn(max = 320.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        otherCategories.forEach { c ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(selected = targetCategoryId == c.id.toString(), onClick = { targetCategoryId = c.id.toString() })
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = targetCategoryId == c.id.toString(), onClick = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                CategoryIcon(iconKey = c.iconKey, name = c.name, size = 32.dp)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(c.name, color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = targetCategoryId != null,
                        onClick = {
                            targetCategoryId?.let { viewModel.moveAllAndDeactivate(category.id.toString(), it) }
                            onDone()
                        }
                    ) { Text("Move & delete", color = MaterialTheme.bpColors.accent) }
                },
                dismissButton = {
                    TextButton(onClick = onCancel) { Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            )
        }

        DeleteMode.SELECT -> {
            var targetCategoryId by remember { mutableStateOf(otherCategories.firstOrNull()?.id?.toString()) }
            AlertDialog(
                onDismissRequest = onCancel,
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onBackground,
                title = { Text("Move expenses") },
                text = {
                    Column(
                        modifier = Modifier
                            .heightIn(max = 360.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                selected = if (selected.size == txns.size) emptySet() else txns.map { it.id.toString() }.toSet()
                            },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = selected.size == txns.size && txns.isNotEmpty(), onCheckedChange = null)
                            Text("Select all", color = MaterialTheme.colorScheme.onBackground)
                        }
                        txns.forEach { txn ->
                            val id = txn.id.toString()
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    selected = if (id in selected) selected - id else selected + id
                                },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(checked = id in selected, onCheckedChange = null)
                                Text(
                                    "${Money.formatRupeesWhole(txn.amountMinor)}  ${txn.transactionDate}",
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            }
                        }
                        if (otherCategories.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Move selected to:", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                            otherCategories.forEach { c ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().clickable { targetCategoryId = c.id.toString() },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(selected = targetCategoryId == c.id.toString(), onClick = null)
                                    Text(c.name, color = MaterialTheme.colorScheme.onBackground)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = selected.isNotEmpty() && targetCategoryId != null,
                        onClick = {
                            viewModel.moveSelectedAndMaybeDeactivate(
                                category.id.toString(), targetCategoryId!!, selected.toList(), txns.size
                            )
                            onDone()
                        }
                    ) { Text("Move", color = MaterialTheme.bpColors.accent) }
                },
                dismissButton = {
                    TextButton(onClick = onCancel) { Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            )
        }
    }
}

@Composable
fun CategoriesList(
    categories: List<CategorySummary>,
    modifier: Modifier = Modifier,
    onCategoryClick: (CategorySummary) -> Unit
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 100.dp) // Space for bottom nav
    ) {
        item { CategoriesColumnHeader() }
        items(categories, key = { it.category.id.toString() }) { category ->
            CategoryMockupRow(
                summary = category,
                onClick = { onCategoryClick(category) }
            )
        }
    }
}

/** Labels the three trailing values every [CategoryMockupRow] shows — otherwise a bare
 * "₹9,000  ₹9,000  100%" gives no clue which number is the limit and which is spent. */
@Composable
private fun CategoriesColumnHeader() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Spacer(modifier = Modifier.weight(1f))
        Row {
            Text(
                text = "Limit",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "Spent",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(40.dp)
            )
        }
    }
}

@Composable
fun CategoryMockupRow(
    summary: CategorySummary,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            CategoryIcon(iconKey = summary.category.iconKey, name = summary.category.name)
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = summary.category.name,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = if (summary.category.periodCount <= 1) "Start of month" else "${summary.category.periodCount} periods",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = Money.formatRupeesWhole(summary.category.monthlyBudgetMinor),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = Money.formatRupeesWhole(summary.totalSpentMinor),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.width(16.dp))
            val pct = if (summary.category.monthlyBudgetMinor > 0)
                (summary.totalSpentMinor.toFloat() / summary.category.monthlyBudgetMinor * 100).toInt()
            else 0
            Text(
                text = "$pct%",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(40.dp)
            )
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
}
