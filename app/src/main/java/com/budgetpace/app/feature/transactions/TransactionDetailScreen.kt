package com.budgetpace.app.feature.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.budgetpace.app.core.designsystem.components.CategoryChooserSheet
import com.budgetpace.app.core.designsystem.components.CategoryIcon
import com.budgetpace.app.core.designsystem.theme.bpColors
import com.budgetpace.app.core.model.Bank
import com.budgetpace.app.core.model.Category
import com.budgetpace.app.core.model.RecordDecision
import com.budgetpace.app.core.model.SyncState
import com.budgetpace.app.core.model.TransactionDirection
import com.budgetpace.app.core.model.TransactionWithCategory
import com.budgetpace.app.core.money.Money
import com.budgetpace.app.data.local.dao.CategoryDao
import com.budgetpace.app.data.local.mapper.toDomain
import com.budgetpace.app.domain.categorization.PromptContentFactory
import com.budgetpace.app.domain.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class TransactionDetailViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val categoryDao: CategoryDao,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    // The nav graph reads "id" from its own backStackEntry and calls setTransactionId(); reading
    // it here too means the ViewModel is correct on its own if that call is ever dropped.
    private val transactionId = MutableStateFlow(savedStateHandle.get<String>("id"))

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<TransactionDetailUiState> = transactionId
        .filterNotNull()
        .flatMapLatest { id -> transactionRepository.observeWithCategoryById(id) }
        .map { if (it != null) TransactionDetailUiState.Success(it) else TransactionDetailUiState.NotFound }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = TransactionDetailUiState.Loading
        )

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val availableCategories: StateFlow<List<Category>> = uiState
        .map { (it as? TransactionDetailUiState.Success)?.item?.transaction?.monthId?.toString() }
        .filterNotNull()
        .distinctUntilChanged()
        .flatMapLatest { monthId -> categoryDao.observeByMonth(monthId) }
        .map { list -> list.map { it.toDomain() } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun setTransactionId(id: String) {
        if (transactionId.value != id) transactionId.value = id
    }

    fun changeCategory(categoryId: String) {
        val current = (uiState.value as? TransactionDetailUiState.Success)?.item?.transaction ?: return
        viewModelScope.launch {
            transactionRepository.update(
                current.copy(
                    categoryId = UUID.fromString(categoryId),
                    syncState = SyncState.PENDING,
                    updatedAt = Instant.now(),
                )
            )
        }
    }

    fun dontRecord() {
        val current = (uiState.value as? TransactionDetailUiState.Success)?.item?.transaction ?: return
        viewModelScope.launch {
            transactionRepository.update(
                current.copy(
                    recordDecision = RecordDecision.IGNORED,
                    syncState = SyncState.PENDING,
                    updatedAt = Instant.now(),
                )
            )
        }
    }

    /** Undo of [dontRecord] — spec §21's "Not recorded · Record it". */
    fun record() {
        viewModelScope.launch {
            transactionId.value?.let { transactionRepository.recordIfIgnored(it) }
        }
    }

    fun deleteTransaction() {
        viewModelScope.launch {
            transactionId.value?.let { transactionRepository.delete(it) }
        }
    }
}

sealed interface TransactionDetailUiState {
    object Loading : TransactionDetailUiState
    data class Success(val item: TransactionWithCategory) : TransactionDetailUiState
    object NotFound : TransactionDetailUiState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailRoute(
    viewModel: TransactionDetailViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val categories by viewModel.availableCategories.collectAsStateWithLifecycle()
    var showCategoryPicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    // Guards the auto-open below so it fires once per expense, not once per recomposition.
    var autoOpenedFor by rememberSaveable { mutableStateOf<String?>(null) }

    val successState = uiState as? TransactionDetailUiState.Success
    val transaction = successState?.item?.transaction

    LaunchedEffect(transaction?.id, transaction?.categoryId, transaction?.recordDecision) {
        val current = transaction ?: return@LaunchedEffect
        val id = current.id.toString()
        if (autoOpenedFor == id) return@LaunchedEffect
        if (current.direction == TransactionDirection.DEBIT &&
            current.categoryId == null &&
            current.recordDecision == RecordDecision.RECORDED
        ) {
            autoOpenedFor = id
            showCategoryPicker = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Expense Details", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
                    }
                },
                actions = {
                    if (successState != null) {
                        // Spec §43: [ Change category ] [ Delete ]
                        TextButton(onClick = { showCategoryPicker = true }) {
                            Text("Change category", color = MaterialTheme.bpColors.statusGreen, style = MaterialTheme.typography.labelLarge)
                        }
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.bpColors.statusRed)
                        }
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
            is TransactionDetailUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize().padding(innerPadding).background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.bpColors.statusGreen)
                }
            }
            is TransactionDetailUiState.Success -> {
                TransactionDetailScreen(
                    item = state.item,
                    onNeedsCategoryClick = { showCategoryPicker = true },
                    onRecordClick = { viewModel.record() },
                    modifier = Modifier.padding(innerPadding)
                )
            }
            is TransactionDetailUiState.NotFound -> {
                Box(modifier = Modifier.fillMaxSize().padding(innerPadding).background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
                    Text(
                        "This expense is no longer available.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (showCategoryPicker && successState != null) {
        CategoryChooserSheet(
            categories = categories,
            onSelectCategory = { category -> viewModel.changeCategory(category.id.toString()) },
            onDontRecord = { viewModel.dontRecord() },
            onDismiss = { showCategoryPicker = false },
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onBackground,
            title = { Text("Delete this expense?") },
            text = {
                Text(
                    "This can't be undone.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    viewModel.deleteTransaction()
                    onBack()
                }) {
                    Text("Delete", color = MaterialTheme.bpColors.statusRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }
}

@Composable
fun TransactionDetailScreen(
    item: TransactionWithCategory,
    onNeedsCategoryClick: () -> Unit,
    onRecordClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val transaction = item.transaction
    val category = item.category

    val timeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy, hh:mm a")
    val dateOnlyFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")
    // Spec §17: the bank only sometimes supplies a time; fall back to the date-only value
    // rather than inventing a time that was never reported.
    val timeString = transaction.transactionDateTime?.atZone(ZoneId.systemDefault())?.format(timeFormatter)
        ?: transaction.transactionDate.format(dateOnlyFormatter)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        // Spec §43: the category's own icon, not a generic debit/credit arrow.
        CategoryIcon(
            iconKey = category?.iconKey ?: "default",
            name = category?.name ?: "?",
            size = 64.dp,
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = Money.formatRupeesWhole(transaction.amountMinor),
            style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(48.dp))

        // Details Grid — never fabricate a value the transaction doesn't actually have.
        DetailRow("Date & Time", timeString)
        DetailRow("Payee", transaction.recipient ?: transaction.sender ?: "—")
        // A manual entry (Bank.UNKNOWN, no source app) has no bank to show.
        if (transaction.bank != Bank.UNKNOWN) {
            val bankText = PromptContentFactory.shortBankName(transaction.bank) +
                (transaction.accountSuffix?.let { " •••$it" } ?: "")
            DetailRow("Bank", bankText)
        }
        DetailRow("UPI Ref", transaction.referenceNumber ?: "—")

        if (category == null) {
            NeedsCategoryCard(onClick = onNeedsCategoryClick)
        } else {
            CategoryRow(category)
        }

        if (transaction.recordDecision == RecordDecision.IGNORED) {
            NotRecordedRow(onRecordClick = onRecordClick)
        }
    }
}

@Composable
private fun NeedsCategoryCard(onClick: () -> Unit) {
    val orange = MaterialTheme.bpColors.statusOrange
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .border(width = 1.dp, color = orange, shape = RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(
            text = "Needs a category",
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            color = orange,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "Tap to choose one",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CategoryRow(category: Category) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Category", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(category.name, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
    }
    Divider(color = MaterialTheme.colorScheme.outline)
}

@Composable
private fun NotRecordedRow(onRecordClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Not recorded", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = "Record it",
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.bpColors.statusGreen,
            modifier = Modifier.clickable(onClick = onRecordClick),
        )
    }
    Divider(color = MaterialTheme.colorScheme.outline)
}

/**
 * The category chooser used by Add expense and the Categories delete/reassign flow (both owned
 * by other tracks — see [com.budgetpace.app.core.designsystem.components.CategoryChooserSheet]
 * for the newer bottom-sheet version this screen uses instead). Kept here, unchanged, only
 * because [com.budgetpace.app.feature.transactions.AddTransactionScreen] and
 * [com.budgetpace.app.feature.categories.CategoriesScreen] both import it from this file.
 */
@Composable
fun CategoryPickerDialog(
    categories: List<Category>,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onBackground,
        title = { Text("Change category") },
        text = {
            if (categories.isEmpty()) {
                Text("No categories yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Column {
                    categories.forEach { category ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(category.id.toString()) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CategoryIcon(
                                iconKey = category.iconKey,
                                name = category.name,
                                size = 32.dp,
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = category.name,
                                color = MaterialTheme.colorScheme.onBackground,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    )
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
    Divider(color = MaterialTheme.colorScheme.outline)
}
