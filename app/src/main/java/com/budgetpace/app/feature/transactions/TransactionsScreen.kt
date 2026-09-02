package com.budgetpace.app.feature.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.budgetpace.app.core.designsystem.theme.bpColors
import com.budgetpace.app.core.model.Transaction
import com.budgetpace.app.core.model.TransactionDirection
import com.budgetpace.app.core.money.Money
import com.budgetpace.app.domain.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class TransactionsViewModel @Inject constructor(
    transactionRepository: TransactionRepository
) : ViewModel() {

    // Passing empty string for phase 1 testing, would use actual monthId in production
    val uiState: StateFlow<TransactionsUiState> = transactionRepository.observeByMonth("")
        .map { TransactionsUiState.Success(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = TransactionsUiState.Loading
        )
}

sealed interface TransactionsUiState {
    object Loading : TransactionsUiState
    data class Success(val transactions: List<Transaction>) : TransactionsUiState
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
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Transactions") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.bpColors.background,
                    titleContentColor = MaterialTheme.bpColors.textPrimary
                )
            )
        },
        containerColor = MaterialTheme.bpColors.background
    ) { innerPadding ->
        when (val state = uiState) {
            is TransactionsUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                    CircularProgressIndicator(modifier = Modifier.align(androidx.compose.ui.Alignment.Center))
                }
            }
            is TransactionsUiState.Success -> {
                TransactionsList(
                    transactions = state.transactions,
                    modifier = Modifier.padding(innerPadding),
                    onTransactionClick = onTransactionClick
                )
            }
            is TransactionsUiState.Error -> {}
        }
    }
}

@Composable
fun TransactionsList(
    transactions: List<Transaction>,
    modifier: Modifier = Modifier,
    onTransactionClick: (String) -> Unit
) {
    val grouped = transactions.groupBy { it.transactionDate }
    
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        grouped.forEach { (date, dailyTxns) ->
            item {
                DateHeader(date)
            }
            items(dailyTxns) { txn ->
                TransactionRow(
                    transaction = txn,
                    onClick = { onTransactionClick(txn.id.toString()) }
                )
                Divider(color = MaterialTheme.bpColors.border.copy(alpha = 0.5f), modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
    }
}

@Composable
private fun DateHeader(date: LocalDate) {
    val formatter = DateTimeFormatter.ofPattern("dd MMM yyyy")
    val displayDate = if (date == LocalDate.now()) "TODAY" else date.format(formatter).uppercase()
    
    Text(
        text = displayDate,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.bpColors.textSecondary,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.bpColors.background)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

@Composable
private fun TransactionRow(
    transaction: Transaction,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = Money.formatRupees(transaction.amountMinor),
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = if (transaction.direction == TransactionDirection.CREDIT) 
                    MaterialTheme.bpColors.statusGreen else MaterialTheme.bpColors.textPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Category placeholder", // Would map categoryId to name in real app
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.bpColors.textPrimary
            )
        }
        
        Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
            val sourceText = if (transaction.bank.name != "UNKNOWN") {
                "${transaction.bank.name} ${transaction.accountSuffix ?: ""}"
            } else {
                "Manual"
            }
            
            Text(
                text = sourceText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.bpColors.textSecondary
            )
        }
    }
}
