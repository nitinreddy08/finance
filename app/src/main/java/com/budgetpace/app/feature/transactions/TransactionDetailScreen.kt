package com.budgetpace.app.feature.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.budgetpace.app.core.model.Category
import com.budgetpace.app.core.model.SyncState
import com.budgetpace.app.core.model.Transaction
import com.budgetpace.app.core.model.TransactionDirection
import com.budgetpace.app.core.money.Money
import com.budgetpace.app.data.local.dao.CategoryDao
import com.budgetpace.app.data.local.mapper.toDomain
import com.budgetpace.app.domain.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class TransactionDetailViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val categoryDao: CategoryDao,
) : ViewModel() {

    private val transactionId = MutableStateFlow<String?>(null)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<TransactionDetailUiState> = transactionId
        .filterNotNull()
        .flatMapLatest { id -> transactionRepository.observeWithCategoryById(id) }
        .map { if (it != null) TransactionDetailUiState.Success(it) else TransactionDetailUiState.Error }
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
        transactionId.value = id
    }

    fun changeCategory(categoryId: String) {
        val current = (uiState.value as? TransactionDetailUiState.Success)?.item?.transaction ?: return
        viewModelScope.launch {
            transactionRepository.update(
                current.copy(
                    categoryId = java.util.UUID.fromString(categoryId),
                    syncState = SyncState.PENDING,
                    updatedAt = Instant.now(),
                )
            )
        }
    }

    fun deleteTransaction() {
        viewModelScope.launch {
            transactionId.value?.let {
                transactionRepository.delete(it)
            }
        }
    }
}

sealed interface TransactionDetailUiState {
    object Loading : TransactionDetailUiState
    data class Success(val item: com.budgetpace.app.core.model.TransactionWithCategory) : TransactionDetailUiState
    object Error : TransactionDetailUiState
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Transaction Details", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
                    }
                },
                actions = {
                    // Spec §43: [ Change category ] [ Delete ]
                    TextButton(onClick = { showCategoryPicker = true }) {
                        Text("Change category", color = Color(0xFF4CAF50), style = MaterialTheme.typography.labelLarge)
                    }
                    IconButton(onClick = {
                        viewModel.deleteTransaction()
                        onBack()
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFF44336))
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
                    CircularProgressIndicator(color = Color(0xFF4CAF50))
                }
            }
            is TransactionDetailUiState.Success -> {
                TransactionDetailScreen(
                    item = state.item,
                    modifier = Modifier.padding(innerPadding)
                )
                if (showCategoryPicker) {
                    CategoryPickerDialog(
                        categories = categories,
                        onDismiss = { showCategoryPicker = false },
                        onSelect = { categoryId ->
                            viewModel.changeCategory(categoryId)
                            showCategoryPicker = false
                        }
                    )
                }
            }
            is TransactionDetailUiState.Error -> {}
        }
    }
}

@Composable
fun CategoryPickerDialog(
    categories: List<com.budgetpace.app.core.model.Category>,
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
                            com.budgetpace.app.core.designsystem.components.CategoryIcon(
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
fun TransactionDetailScreen(
    item: com.budgetpace.app.core.model.TransactionWithCategory,
    modifier: Modifier = Modifier
) {
    val transaction = item.transaction
    val category = item.category
    
    val isCredit = transaction.direction == TransactionDirection.CREDIT
    val iconColor = if (isCredit) Color(0xFF2E7D32).copy(alpha = 0.2f) else Color(0xFFF29C38).copy(alpha = 0.2f)
    val iconTint = if (isCredit) Color(0xFF4CAF50) else Color(0xFFF29C38)
    val iconVector = if (isCredit) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward
    
    val timeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy, hh:mm a")
    val dateOnlyFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")
    // Spec §17: the bank only sometimes supplies a time; fall back to the date-only value
    // rather than inventing a time that was never reported.
    val timeString = transaction.transactionDateTime?.atZone(ZoneId.systemDefault())?.format(timeFormatter)
        ?: transaction.transactionDate.format(dateOnlyFormatter)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        
        // Icon
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(iconColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(iconVector, contentDescription = null, tint = iconTint, modifier = Modifier.size(32.dp))
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Amount
        Text(
            text = Money.formatRupeesWhole(transaction.amountMinor),
            style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
        )
        
        // Expense/Income Pill
        Text(
            text = if (isCredit) "+Income" else "-Expense",
            style = MaterialTheme.typography.bodyMedium,
            color = if (isCredit) Color(0xFF4CAF50) else Color(0xFFF44336)
        )
        
        Spacer(modifier = Modifier.height(48.dp))
        
        // Details Grid — never fabricate a value the transaction doesn't actually have.
        DetailRow("Date & Time", timeString)
        DetailRow("Payee", transaction.recipient ?: transaction.sender ?: "—")
        DetailRow("Bank", "${transaction.bank.name}${transaction.accountSuffix?.let { " •••$it" } ?: ""}")
        DetailRow("UPI Ref", transaction.referenceNumber ?: "—")

        // Category with dot
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Category", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(category?.name ?: "Uncategorized", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
                Spacer(modifier = Modifier.width(6.dp))
                val dotColor = if (category != null) Color(0xFF4CAF50) else Color(0xFF6B7280)
                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(dotColor))
            }
        }
        Divider(color = MaterialTheme.colorScheme.outline)
        
        DetailRow("Payment Method", "UPI")
        DetailRow("Note", "-")
    }
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
