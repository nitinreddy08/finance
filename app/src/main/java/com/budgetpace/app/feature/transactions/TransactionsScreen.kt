package com.budgetpace.app.feature.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.budgetpace.app.core.designsystem.theme.bpColors
import com.budgetpace.app.core.model.BudgetMonth
import com.budgetpace.app.core.model.MonthStatus
import com.budgetpace.app.core.model.RecordDecision
import com.budgetpace.app.core.model.TransactionDirection
import com.budgetpace.app.core.model.TransactionWithCategory
import com.budgetpace.app.core.money.Money
import com.budgetpace.app.data.local.dao.BudgetMonthDao
import com.budgetpace.app.data.local.dao.TransactionDao
import com.budgetpace.app.data.local.mapper.toDomain
import com.budgetpace.app.domain.categorization.PromptContentFactory
import com.budgetpace.app.domain.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * The sole hand-off from Home's "N expenses need a category" row into this tab's initial filter.
 * Deliberately not a nav argument — the "transactions" tab route must stay the same literal
 * string MainActivity already switches tabs on — and not app state, since it is consumed once and
 * forgotten. See [TransactionsViewModel]'s init block.
 */
object PendingExpensesFilter {
    var requestUncategorized: Boolean = false
}

enum class ExpenseFilter { ALL, UNCATEGORIZED }

/** One calendar day's transactions, pre-grouped and pre-sorted so the screen only renders. */
data class DaySection(
    val date: LocalDate,
    val items: List<TransactionWithCategory>,
    val totalDebitMinor: Long,
)

@HiltViewModel
class TransactionsViewModel @Inject constructor(
    transactionRepository: TransactionRepository,
    budgetMonthDao: BudgetMonthDao,
    transactionDao: TransactionDao,
) : ViewModel() {

    // null selected month means "the active month".
    private val selectedMonthId = MutableStateFlow<String?>(null)
    private val searchQuery = MutableStateFlow("")
    private val filter = MutableStateFlow(
        if (PendingExpensesFilter.requestUncategorized) ExpenseFilter.UNCATEGORIZED else ExpenseFilter.ALL
    )
    private val showHidden = MutableStateFlow(false)

    init {
        // Consumed once: a later visit to this tab (bottom-nav tap) must not keep reapplying it.
        PendingExpensesFilter.requestUncategorized = false
    }

    val availableMonths: StateFlow<List<BudgetMonth>> = budgetMonthDao.observeAll()
        .map { list -> list.map { it.toDomain() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val selectedMonth: StateFlow<String?> = selectedMonthId
    val searchText: StateFlow<String> = searchQuery
    val currentFilter: StateFlow<ExpenseFilter> = filter
    val showHiddenEnabled: StateFlow<Boolean> = showHidden

    fun selectMonth(monthId: String?) { selectedMonthId.value = monthId }
    fun setSearchText(text: String) { searchQuery.value = text }
    fun setFilter(f: ExpenseFilter) { filter.value = f }
    fun setShowHidden(enabled: Boolean) { showHidden.value = enabled }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val rawTransactions = selectedMonthId
        .flatMapLatest { monthId ->
            val idFlow = if (monthId != null) {
                kotlinx.coroutines.flow.flowOf(monthId)
            } else {
                budgetMonthDao.observeActiveMonth().filterNotNull().map { it.id }
            }
            idFlow.flatMapLatest { id -> transactionRepository.observeWithCategoryByMonth(id) }
        }

    // Ignored (hidden) rows aren't in observeWithCategoryByMonth at all (it's RECORDED-only), so
    // "Show hidden" needs the wider query — kept as a separate flow rather than always paying for
    // it, since IGNORED rows are the uncommon case.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val rawAllIncludingHidden = selectedMonthId
        .flatMapLatest { monthId ->
            val idFlow = if (monthId != null) {
                kotlinx.coroutines.flow.flowOf(monthId)
            } else {
                budgetMonthDao.observeActiveMonth().filterNotNull().map { it.id }
            }
            idFlow.flatMapLatest { id ->
                transactionDao.observeAllWithCategoryByMonth(id).map { list ->
                    list.map { TransactionWithCategory(it.transaction.toDomain(), it.category?.toDomain()) }
                }
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<TransactionsUiState> = combine(
        showHidden.flatMapLatest { hidden -> if (hidden) rawAllIncludingHidden else rawTransactions },
        searchQuery,
        filter,
    ) { list, query, activeFilter ->
        val debitsOnly = list.filter { it.transaction.direction == TransactionDirection.DEBIT }
        val filtered = when (activeFilter) {
            ExpenseFilter.ALL -> debitsOnly
            ExpenseFilter.UNCATEGORIZED -> debitsOnly.filter {
                it.transaction.categoryId == null && it.transaction.recordDecision == RecordDecision.RECORDED
            }
        }
        val searched = if (query.isBlank()) {
            filtered
        } else {
            val needle = query.trim().lowercase()
            filtered.filter { item ->
                val payee = (item.transaction.recipient ?: item.transaction.sender ?: "").lowercase()
                val categoryName = (item.category?.name ?: "").lowercase()
                val amountText = Money.formatRupeesWhole(item.transaction.amountMinor).lowercase()
                payee.contains(needle) || categoryName.contains(needle) || amountText.contains(needle)
            }
        }
        val sorted = searched.sortedWith(
            compareByDescending<TransactionWithCategory> { it.transaction.transactionDate }
                .thenByDescending { it.transaction.transactionDateTime }
                .thenByDescending { it.transaction.createdAt }
        )
        val sections = sorted
            .groupBy { it.transaction.transactionDate }
            .toSortedMap(compareByDescending { it })
            .map { (date, items) ->
                DaySection(
                    date = date,
                    items = items,
                    totalDebitMinor = items.sumOf { it.transaction.amountMinor },
                )
            }
        TransactionsUiState.Success(sections)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TransactionsUiState.Loading
    )
}

sealed interface TransactionsUiState {
    object Loading : TransactionsUiState
    data class Success(val sections: List<DaySection>) : TransactionsUiState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsRoute(
    viewModel: TransactionsViewModel,
    onBack: () -> Unit,
    onTransactionClick: (String) -> Unit,
    onAddExpense: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val availableMonths by viewModel.availableMonths.collectAsStateWithLifecycle()
    val selectedMonth by viewModel.selectedMonth.collectAsStateWithLifecycle()
    val searchText by viewModel.searchText.collectAsStateWithLifecycle()
    val filter by viewModel.currentFilter.collectAsStateWithLifecycle()
    val showHidden by viewModel.showHiddenEnabled.collectAsStateWithLifecycle()
    var showSearch by remember { mutableStateOf(false) }
    var showMonthPicker by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        if (AddedExpenseSignal.pending) {
            AddedExpenseSignal.pending = false
            snackbarHostState.showSnackbar("Expense added")
        }
    }

    if (showMonthPicker) {
        MonthSelectorDialog(
            months = availableMonths,
            onSelect = { monthId -> viewModel.selectMonth(monthId); showMonthPicker = false },
            onDismiss = { showMonthPicker = false },
        )
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        if (showSearch) {
                            OutlinedTextField(
                                value = searchText,
                                onValueChange = viewModel::setSearchText,
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("Search payee, category, amount") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            )
                        } else {
                            Text("Expenses", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = if (showSearch) { { showSearch = false; viewModel.setSearchText("") } } else onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
                        }
                    },
                    actions = {
                        if (!showSearch) {
                            IconButton(onClick = { showSearch = true }) {
                                Icon(Icons.Default.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.onBackground)
                            }
                            // Spec §7: the primary action here is "+ Add expense" — V1 is expense-only.
                            IconButton(onClick = onAddExpense) {
                                Icon(Icons.Default.Add, contentDescription = "Add expense", tint = MaterialTheme.colorScheme.onBackground)
                            }
                        } else if (searchText.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setSearchText("") }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear search", tint = MaterialTheme.colorScheme.onBackground)
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )
                FilterBar(
                    months = availableMonths,
                    selectedMonthId = selectedMonth,
                    onOpenMonthPicker = { showMonthPicker = true },
                    filter = filter,
                    onFilterChange = viewModel::setFilter,
                    showHidden = showHidden,
                    onShowHiddenChange = viewModel::setShowHidden,
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        when (val state = uiState) {
            is TransactionsUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize().padding(innerPadding).background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.bpColors.statusGreen)
                }
            }
            is TransactionsUiState.Success -> {
                if (state.sections.isEmpty()) {
                    EmptyExpensesState(
                        modifier = Modifier.padding(innerPadding),
                        hasFilter = filter != ExpenseFilter.ALL || searchText.isNotBlank(),
                    )
                } else {
                    TransactionsList(
                        sections = state.sections,
                        modifier = Modifier.padding(innerPadding),
                        onTransactionClick = onTransactionClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterBar(
    months: List<BudgetMonth>,
    selectedMonthId: String?,
    onOpenMonthPicker: () -> Unit,
    filter: ExpenseFilter,
    onFilterChange: (ExpenseFilter) -> Unit,
    showHidden: Boolean,
    onShowHiddenChange: (Boolean) -> Unit,
) {
    val selectedMonth = months.firstOrNull { it.id.toString() == selectedMonthId }
    val monthLabel = if (selectedMonth != null) {
        java.time.Month.of(selectedMonth.month).getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault()) + " ${selectedMonth.year}"
    } else {
        "This month"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AssistChip(
            onClick = onOpenMonthPicker,
            label = { Text(monthLabel) },
            trailingIcon = { Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(18.dp)) },
        )
        FilterChip(
            selected = filter == ExpenseFilter.UNCATEGORIZED,
            onClick = { onFilterChange(if (filter == ExpenseFilter.UNCATEGORIZED) ExpenseFilter.ALL else ExpenseFilter.UNCATEGORIZED) },
            label = { Text("Uncategorized") },
        )
        FilterChip(
            selected = showHidden,
            onClick = { onShowHiddenChange(!showHidden) },
            label = { Text("Show hidden") },
        )
    }
}

@Composable
private fun MonthSelectorDialog(
    months: List<BudgetMonth>,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onBackground,
        title = { Text("Select month") },
        text = {
            if (months.isEmpty()) {
                Text("No months yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Column(
                    modifier = Modifier
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    months.forEach { m ->
                        val label = java.time.Month.of(m.month).getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault()) + " ${m.year}"
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(if (m.status == MonthStatus.ACTIVE) null else m.id.toString()) }
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(label, color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.bodyLarge)
                            if (m.status == MonthStatus.ACTIVE) {
                                Text("Current", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    )
}

@Composable
private fun EmptyExpensesState(modifier: Modifier = Modifier, hasFilter: Boolean) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 32.dp)) {
            Text(
                text = if (hasFilter) "No expenses match" else "No expenses yet",
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (hasFilter) "Try a different search or filter." else "Your bank expenses will appear here automatically.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Composable
fun TransactionsList(
    sections: List<DaySection>,
    onTransactionClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        sections.forEach { section ->
            item(key = "header_${section.date}") {
                DateHeader(section = section)
            }
            items(section.items, key = { it.transaction.id.toString() }) { item ->
                TransactionRow(
                    item = item,
                    onClick = { onTransactionClick(item.transaction.id.toString()) }
                )
            }
        }
    }
}

@Composable
private fun DateHeader(section: DaySection) {
    val formatter = DateTimeFormatter.ofPattern("d MMM")
    val today = LocalDate.now()
    val yesterday = today.minusDays(1)

    val displayDate = when (section.date) {
        today -> "Today, ${section.date.format(formatter)}"
        yesterday -> "Yesterday, ${section.date.format(formatter)}"
        else -> section.date.format(formatter)
    }
    val displayTotal = if (section.totalDebitMinor > 0) Money.formatRupeesWhole(section.totalDebitMinor) else ""

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = displayDate,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (displayTotal.isNotEmpty()) {
            Text(
                text = displayTotal,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TransactionRow(
    item: TransactionWithCategory,
    onClick: () -> Unit
) {
    val transaction = item.transaction
    val category = item.category

    val isCredit = transaction.direction == TransactionDirection.CREDIT
    val amountText = Money.formatRupeesWhole(transaction.amountMinor)
    val displayAmount = if (isCredit) "+$amountText" else amountText
    val amountColor = if (isCredit) MaterialTheme.bpColors.statusGreen else MaterialTheme.colorScheme.onBackground

    val payee = transaction.recipient ?: transaction.sender ?: category?.name ?: "Uncategorized"
    val time = transaction.transactionDateTime?.atZone(ZoneId.systemDefault())?.format(DateTimeFormatter.ofPattern("hh:mm a")) ?: ""
    // Spec: show bank + masked account suffix ("Kotak •••7970"), not the UPI reference number.
    val bankLine = PromptContentFactory.bankLine(transaction.bank, transaction.accountSuffix)
    val isUncategorized = transaction.categoryId == null && transaction.recordDecision == RecordDecision.RECORDED
    val isHidden = transaction.recordDecision == RecordDecision.IGNORED

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            // Spec §7: represent the category by its emoji rather than a generic direction icon.
            com.budgetpace.app.core.designsystem.components.CategoryIcon(
                iconKey = category?.iconKey ?: "default",
                name = category?.name ?: "?",
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = payee,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color = if (isHidden) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onBackground
                )
                if (bankLine.isNotEmpty()) {
                    Text(
                        text = bankLine,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = displayAmount,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = amountColor
            )
            Text(
                text = time,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(2.dp))
            when {
                isUncategorized -> Text(
                    text = "Choose category",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.bpColors.statusOrange,
                )
                isHidden -> Text(
                    text = "Hidden",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                category != null -> Text(
                    text = category.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
