package com.budgetpace.app.feature.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.budgetpace.app.core.model.Category
import com.budgetpace.app.core.model.CategorySummary
import com.budgetpace.app.core.model.Transaction
import com.budgetpace.app.core.money.Money
import com.budgetpace.app.data.local.dao.BudgetMonthDao
import com.budgetpace.app.data.local.dao.CategoryDao
import com.budgetpace.app.data.local.dao.TransactionDao
import com.budgetpace.app.data.local.mapper.toDomain
import com.budgetpace.app.data.local.mapper.toEntity
import com.budgetpace.app.domain.repository.BudgetRepository
import com.budgetpace.app.feature.transactions.CategoryPickerDialog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
) : ViewModel() {

    val uiState: StateFlow<CategoriesUiState> = budgetRepository.observeActiveMonthSummary()
        .map { summary -> CategoriesUiState.Success(summary?.categories ?: emptyList()) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = CategoriesUiState.Loading
        )

    /** Spec §23: categories are fully user-defined; this is the only place new ones are created. */
    fun addCategory(name: String, budgetMinor: Long, weeklyPacingEnabled: Boolean) {
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
                    weeklyPacingEnabled = weeklyPacingEnabled,
                    iconKey = "default",
                    sortOrder = sortOrder,
                    active = true,
                    createdAt = now,
                    updatedAt = now,
                ).toEntity()
            )
        }
    }

    fun updateCategory(categoryId: String, name: String, budgetMinor: Long, weeklyPacingEnabled: Boolean) {
        if (name.isBlank() || budgetMinor <= 0) return
        viewModelScope.launch {
            val existing = categoryDao.getById(categoryId) ?: return@launch
            categoryDao.update(
                existing.copy(
                    name = name.trim(),
                    monthlyBudgetMinor = budgetMinor,
                    weeklyPacingEnabled = weeklyPacingEnabled,
                    updatedAt = Instant.now().toEpochMilli(),
                )
            )
        }
    }

    suspend fun transactionsForCategory(categoryId: String): List<Transaction> =
        transactionDao.getByCategory(categoryId).map { it.toDomain() }

    /** Spec §45: never silently delete transactions — move them all, then deactivate the category. */
    fun moveAllAndDeactivate(categoryId: String, targetCategoryId: String) {
        viewModelScope.launch {
            val now = Instant.now().toEpochMilli()
            transactionDao.moveAllFromCategory(categoryId, targetCategoryId, now)
            categoryDao.deactivate(categoryId, now)
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
            transactionIds.forEach { transactionDao.updateCategory(it, targetCategoryId, now) }
            if (transactionIds.size >= totalTransactionCount) {
                categoryDao.deactivate(categoryId, now)
            }
        }
    }

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
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }
    var manageCategory by remember { mutableStateOf<CategorySummary?>(null) }
    var editCategory by remember { mutableStateOf<CategorySummary?>(null) }
    var deleteCategory by remember { mutableStateOf<CategorySummary?>(null) }

    val categories = (uiState as? CategoriesUiState.Success)?.categories ?: emptyList()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Categories", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
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
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        when (val state = uiState) {
            is CategoriesUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize().padding(innerPadding).background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF4CAF50))
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
                        onCategoryClick = { manageCategory = it }
                    )
                }
            }
            is CategoriesUiState.Error -> {
                Box(modifier = Modifier.fillMaxSize().padding(innerPadding).background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
                    Text("Error loading categories", color = MaterialTheme.colorScheme.onBackground)
                }
            }
        }
    }

    if (showAddDialog) {
        CategoryFormDialog(
            title = "Add category",
            initialName = "",
            initialBudget = "",
            initialWeeklyPacing = true,
            onDismiss = { showAddDialog = false },
            onConfirm = { name, budgetMinor, weeklyPacing ->
                viewModel.addCategory(name, budgetMinor, weeklyPacing)
                showAddDialog = false
            }
        )
    }

    editCategory?.let { target ->
        CategoryFormDialog(
            title = "Edit category",
            initialName = target.category.name,
            initialBudget = (target.category.monthlyBudgetMinor / 100).toString(),
            initialWeeklyPacing = target.category.weeklyPacingEnabled,
            onDismiss = { editCategory = null },
            onConfirm = { name, budgetMinor, weeklyPacing ->
                viewModel.updateCategory(target.category.id.toString(), name, budgetMinor, weeklyPacing)
                editCategory = null
            }
        )
    }

    manageCategory?.let { target ->
        AlertDialog(
            onDismissRequest = { manageCategory = null },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onBackground,
            title = { Text(target.category.name) },
            text = { Text("What would you like to do?", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = {
                TextButton(onClick = { editCategory = target; manageCategory = null }) {
                    Text("Edit", color = Color(0xFF4CAF50))
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { deleteCategory = target; manageCategory = null }) {
                        Text("Delete", color = Color(0xFFF44336))
                    }
                    TextButton(onClick = { manageCategory = null }) {
                        Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        )
    }

    deleteCategory?.let { target ->
        DeleteCategoryFlow(
            category = target.category,
            otherCategories = categories.map { it.category }.filter { it.id != target.category.id },
            viewModel = viewModel,
            onDone = { deleteCategory = null },
            onCancel = { deleteCategory = null },
        )
    }
}

@Composable
private fun CategoryFormDialog(
    title: String,
    initialName: String,
    initialBudget: String,
    initialWeeklyPacing: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (name: String, budgetMinor: Long, weeklyPacing: Boolean) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var budget by remember { mutableStateOf(initialBudget) }
    var weeklyPacing by remember { mutableStateOf(initialWeeklyPacing) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onBackground,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Category name") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = budget,
                    onValueChange = { budget = it.filter { c -> c.isDigit() } },
                    label = { Text("Monthly budget") },
                    prefix = { Text("₹ ") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Weekly pacing", color = MaterialTheme.colorScheme.onBackground)
                        // Spec §24: e.g. Rent normally has this off — it still counts toward
                        // the overall monthly pace, it just skips its own 4-tile breakdown.
                        Text(
                            "Off for fixed monthly costs like rent",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = weeklyPacing, onCheckedChange = { weeklyPacing = it })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val budgetMinor = Money.rupeesToPaise(budget.ifBlank { "0" })
                onConfirm(name, budgetMinor, weeklyPacing)
            }) {
                Text("Save", color = Color(0xFF4CAF50))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    )
}

/** Spec §45: delete confirmation -> move-all or per-transaction selection, never a silent delete. */
@Composable
private fun DeleteCategoryFlow(
    category: Category,
    otherCategories: List<Category>,
    viewModel: CategoriesViewModel,
    onDone: () -> Unit,
    onCancel: () -> Unit,
) {
    var transactions by remember { mutableStateOf<List<Transaction>?>(null) }
    var mode by remember { mutableStateOf("confirm") } // confirm -> moveAll | select
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(category.id) {
        transactions = viewModel.transactionsForCategory(category.id.toString())
    }

    val txns = transactions
    if (txns == null) return // still loading — no UI flash

    when (mode) {
        "confirm" -> AlertDialog(
            onDismissRequest = onCancel,
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onBackground,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            title = { Text("Delete \"${category.name}\"?") },
            text = {
                Text(
                    if (txns.isEmpty()) "No transactions use this category."
                    else "${txns.size} transaction${if (txns.size == 1) "" else "s"} use this category."
                )
            },
            confirmButton = {
                if (txns.isEmpty()) {
                    TextButton(onClick = { viewModel.deactivateCategory(category.id.toString()); onDone() }) {
                        Text("Delete", color = Color(0xFFF44336))
                    }
                } else if (otherCategories.isNotEmpty()) {
                    TextButton(onClick = { mode = "moveAll" }) {
                        Text("Move all to another category", color = Color(0xFF4CAF50))
                    }
                }
            },
            dismissButton = {
                Column {
                    if (txns.isNotEmpty()) {
                        TextButton(onClick = { mode = "select" }) {
                            Text("Select transactions", color = MaterialTheme.colorScheme.onBackground)
                        }
                    }
                    TextButton(onClick = onCancel) { Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        )

        "moveAll" -> CategoryPickerDialog(
            categories = otherCategories,
            onDismiss = onCancel,
            onSelect = { targetId ->
                viewModel.moveAllAndDeactivate(category.id.toString(), targetId)
                onDone()
            }
        )

        "select" -> {
            var targetCategoryId by remember { mutableStateOf(otherCategories.firstOrNull()?.id?.toString()) }
            AlertDialog(
                onDismissRequest = onCancel,
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onBackground,
                title = { Text("Move transactions") },
                text = {
                    Column {
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
                    ) { Text("Move", color = Color(0xFF4CAF50)) }
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
        contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp) // Space for bottom nav
    ) {
        items(categories) { category ->
            CategoryMockupRow(
                summary = category,
                onClick = { onCategoryClick(category) }
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
            // Spec §33: no literal emoji/color-square icons in production.
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    summary.category.name.firstOrNull()?.uppercase() ?: "?",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = summary.category.name,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onBackground
            )
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
    Divider(color = MaterialTheme.colorScheme.outline)
}
