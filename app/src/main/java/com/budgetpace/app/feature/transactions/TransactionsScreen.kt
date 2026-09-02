package com.budgetpace.app.feature.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.budgetpace.app.core.designsystem.theme.bpColors
import com.budgetpace.app.core.model.Transaction
import com.budgetpace.app.core.model.TransactionDirection
import com.budgetpace.app.core.money.Money
import com.budgetpace.app.data.local.dao.BudgetMonthDao
import com.budgetpace.app.domain.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class TransactionsViewModel @Inject constructor(
    transactionRepository: TransactionRepository,
    budgetMonthDao: BudgetMonthDao,
) : ViewModel() {
    // Spec §7: Transactions is expense-only in V1 — there is no income feature at all.
    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<TransactionsUiState> = budgetMonthDao.observeActiveMonth()
        .filterNotNull()
        .flatMapLatest { month -> transactionRepository.observeWithCategoryByMonth(month.id) }
        .map { list -> TransactionsUiState.Success(list.filter { it.transaction.direction == TransactionDirection.DEBIT }) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = TransactionsUiState.Loading
        )
}

sealed interface TransactionsUiState {
    object Loading : TransactionsUiState
    data class Success(val transactions: List<com.budgetpace.app.core.model.TransactionWithCategory>) : TransactionsUiState
    object Error : TransactionsUiState
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

    when (val state = uiState) {
        is TransactionsUiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFF4CAF50))
            }
        }
        is TransactionsUiState.Success -> {
            TransactionsScreen(
                transactions = state.transactions,
                onBack = onBack,
                onTransactionClick = onTransactionClick,
                onAddExpense = onAddExpense,
            )
        }
        is TransactionsUiState.Error -> {
            Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
                Text("Error loading transactions", color = MaterialTheme.colorScheme.onBackground)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    transactions: List<com.budgetpace.app.core.model.TransactionWithCategory>,
    onBack: () -> Unit,
    onTransactionClick: (String) -> Unit,
    onAddExpense: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Transactions", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
                    }
                },
                actions = {
                    IconButton(onClick = { }) {
                        Icon(Icons.Default.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.onBackground)
                    }
                    // Spec §7: the primary action here is "+ Add expense" — V1 is expense-only.
                    IconButton(onClick = onAddExpense) {
                        Icon(Icons.Default.Add, contentDescription = "Add expense", tint = MaterialTheme.colorScheme.onBackground)
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
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            TransactionsList(
                transactions = transactions,
                onTransactionClick = onTransactionClick
            )
        }
    }
}

@Composable
fun TransactionsList(
    transactions: List<com.budgetpace.app.core.model.TransactionWithCategory>,
    onTransactionClick: (String) -> Unit
) {
    // Sort transactions by descending date, then by time if available
    val sorted = transactions.sortedWith(
        compareByDescending<com.budgetpace.app.core.model.TransactionWithCategory> { it.transaction.transactionDate }
            .thenByDescending { it.transaction.transactionDateTime }
    )
    val grouped = sorted.groupBy { it.transaction.transactionDate }
    
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 100.dp) // Space for bottom nav
    ) {
        grouped.forEach { (date, dailyTxns) ->
            item {
                DateHeader(date = date, dailyTxns = dailyTxns)
            }
            items(dailyTxns) { txn ->
                TransactionMockupRow(
                    item = txn,
                    onClick = { onTransactionClick(txn.transaction.id.toString()) }
                )
            }
        }
    }
}

@Composable
private fun DateHeader(date: LocalDate, dailyTxns: List<com.budgetpace.app.core.model.TransactionWithCategory>) {
    val formatter = DateTimeFormatter.ofPattern("d MMM")
    val today = LocalDate.now()
    val yesterday = today.minusDays(1)
    
    val displayDate = when (date) {
        today -> "Today, ${date.format(formatter)}"
        yesterday -> "Yesterday, ${date.format(formatter)}"
        else -> date.format(formatter)
    }
    
    // Calculate net or total amount (mockup shows sum of expenses if on expenses tab, here we'll just sum all for simplicity)
    val totalMinor = dailyTxns.filter { it.transaction.direction == TransactionDirection.DEBIT }.sumOf { it.transaction.amountMinor }
    val displayTotal = if (totalMinor > 0) Money.formatRupeesWhole(totalMinor) else ""
    
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
private fun TransactionMockupRow(
    item: com.budgetpace.app.core.model.TransactionWithCategory,
    onClick: () -> Unit
) {
    val transaction = item.transaction
    val category = item.category

    val isCredit = transaction.direction == TransactionDirection.CREDIT
    val amountText = Money.formatRupeesWhole(transaction.amountMinor)
    val displayAmount = if (isCredit) "+$amountText" else amountText
    val amountColor = if (isCredit) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onBackground

    val payee = transaction.recipient ?: transaction.sender ?: category?.name ?: "Uncategorized"
    val time = transaction.transactionDateTime?.atZone(ZoneId.systemDefault())?.format(DateTimeFormatter.ofPattern("hh:mm a")) ?: ""
    val categoryName = category?.name ?: "Uncategorized"
    
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
                name = categoryName,
            )

            Spacer(modifier = Modifier.width(12.dp))
            
            Column {
                Text(
                    text = payee,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onBackground
                )
                if (!transaction.referenceNumber.isNullOrEmpty()) {
                    Text(
                        text = "UPI Ref: ${transaction.referenceNumber}",
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = categoryName,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
