package com.budgetpace.app.feature.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.ShoppingCart
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
    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<TransactionsUiState> = budgetMonthDao.observeActiveMonth()
        .filterNotNull()
        .flatMapLatest { month -> transactionRepository.observeWithCategoryByMonth(month.id) }
        .map { TransactionsUiState.Success(it) }
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
    onTransactionClick: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    when (val state = uiState) {
        is TransactionsUiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize().background(Color(0xFF15161A)), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFF4CAF50))
            }
        }
        is TransactionsUiState.Success -> {
            TransactionsScreen(
                transactions = state.transactions,
                onBack = onBack,
                onTransactionClick = onTransactionClick
            )
        }
        is TransactionsUiState.Error -> {
            Box(modifier = Modifier.fillMaxSize().background(Color(0xFF15161A)), contentAlignment = Alignment.Center) {
                Text("Error loading transactions", color = Color.White)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    transactions: List<com.budgetpace.app.core.model.TransactionWithCategory>,
    onBack: () -> Unit,
    onTransactionClick: (String) -> Unit
) {
    var selectedTab by remember { mutableStateOf("Expenses") }
    
    val filteredTransactions = remember(transactions, selectedTab) {
        when (selectedTab) {
            "Expenses" -> transactions.filter { it.transaction.direction == TransactionDirection.DEBIT }
            "Income" -> transactions.filter { it.transaction.direction == TransactionDirection.CREDIT }
            else -> transactions
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text("Transactions", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { }) {
                        Icon(Icons.Default.FilterList, contentDescription = "Filter", tint = Color.White)
                    }
                    IconButton(onClick = { }) {
                        Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF15161A),
                    titleContentColor = Color.White
                )
            )
        },
        containerColor = Color(0xFF15161A)
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            
            // Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .background(Color(0xFF1E1F24), RoundedCornerShape(24.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val tabs = listOf("All", "Expenses", "Income")
                tabs.forEach { tab ->
                    val isSelected = selectedTab == tab
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isSelected) Color.White else Color.Transparent)
                            .clickable { selectedTab = tab }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tab,
                            color = if (isSelected) Color.Black else Color.Gray,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal)
                        )
                    }
                }
            }
            
            // List
            TransactionsList(
                transactions = filteredTransactions,
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
            .background(Color(0xFF15161A))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = displayDate,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = Color.Gray
        )
        if (displayTotal.isNotEmpty()) {
            Text(
                text = displayTotal,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = Color.Gray
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
    val amountColor = if (isCredit) Color(0xFF4CAF50) else Color.White
    
    // Fallback UI logic based on direction for mockup purposes
    val iconColor = if (isCredit) Color(0xFF2E7D32).copy(alpha = 0.2f) else Color(0xFFD32F2F).copy(alpha = 0.2f)
    val iconTint = if (isCredit) Color(0xFF4CAF50) else Color(0xFFF44336)
    val iconVector = if (isCredit) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward
    
    val payee = transaction.recipient ?: transaction.sender ?: category?.name ?: "Miscellaneous"
    val time = transaction.transactionDateTime?.atZone(ZoneId.systemDefault())?.format(DateTimeFormatter.ofPattern("hh:mm a")) ?: ""
    val categoryName = category?.name ?: "Miscellaneous"
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            // Icon
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(iconColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(iconVector, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column {
                Text(
                    text = payee,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color = Color.White
                )
                if (!transaction.referenceNumber.isNullOrEmpty()) {
                    Text(
                        text = "UPI Ref: ${transaction.referenceNumber}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
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
                color = Color.Gray
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color(0xFFFF9800))) // Mocking category dot
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = categoryName,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = Color.Gray
                )
            }
        }
    }
}
