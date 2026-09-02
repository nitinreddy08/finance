package com.budgetpace.app.feature.transactions

import androidx.compose.foundation.layout.*
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
import com.budgetpace.app.core.money.Money
import com.budgetpace.app.domain.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class TransactionDetailViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository
) : ViewModel() {

    private val transactionId = MutableStateFlow<String?>(null)
    
    // In a real app we'd query by ID. For now returning dummy state.
    val uiState: StateFlow<TransactionDetailUiState> = MutableStateFlow(TransactionDetailUiState.Loading)
    
    fun setTransactionId(id: String) {
        transactionId.value = id
        // Load transaction logic
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
    data class Success(val transaction: Transaction) : TransactionDetailUiState
    object Error : TransactionDetailUiState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailRoute(
    viewModel: TransactionDetailViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Transaction Details") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.bpColors.background,
                    titleContentColor = MaterialTheme.bpColors.textPrimary
                ),
                navigationIcon = {
                    // Back button icon
                }
            )
        },
        containerColor = MaterialTheme.bpColors.background
    ) { innerPadding ->
        when (val state = uiState) {
            is TransactionDetailUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                    CircularProgressIndicator(modifier = Modifier.align(androidx.compose.ui.Alignment.Center))
                }
            }
            is TransactionDetailUiState.Success -> {
                TransactionDetailScreen(
                    transaction = state.transaction,
                    modifier = Modifier.padding(innerPadding),
                    onDelete = {
                        viewModel.deleteTransaction()
                        onBack()
                    }
                )
            }
            is TransactionDetailUiState.Error -> {}
        }
    }
}

@Composable
fun TransactionDetailScreen(
    transaction: Transaction,
    modifier: Modifier = Modifier,
    onDelete: () -> Unit
) {
    val colors = MaterialTheme.bpColors
    val typography = MaterialTheme.typography
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text(
            text = Money.formatRupees(transaction.amountMinor),
            style = typography.displayMedium,
            color = colors.textPrimary
        )
        
        Text(
            text = "Category Placeholder",
            style = typography.headlineMedium,
            color = colors.textPrimary
        )
        
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val dateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy")
            Text(
                text = transaction.transactionDate.format(dateFormatter),
                style = typography.bodyLarge,
                color = colors.textSecondary
            )
            // Optional time
        }
        
        if (transaction.bank.name != "UNKNOWN") {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = transaction.bank.name,
                    style = typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color = colors.textPrimary
                )
                Text(
                    text = "Account •••${transaction.accountSuffix ?: ""}",
                    style = typography.bodyLarge,
                    color = colors.textSecondary
                )
            }
        }
        
        if (transaction.recipient != null) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Recipient",
                    style = typography.labelMedium,
                    color = colors.textSecondary
                )
                Text(
                    text = transaction.recipient,
                    style = typography.bodyLarge,
                    color = colors.textPrimary
                )
            }
        }
        
        if (transaction.referenceNumber != null) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Reference",
                    style = typography.labelMedium,
                    color = colors.textSecondary
                )
                Text(
                    text = transaction.referenceNumber,
                    style = typography.bodyLarge,
                    color = colors.textPrimary
                )
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        OutlinedButton(
            onClick = { /* Change category flow */ },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium
        ) {
            Text("Change category")
        }
        
        TextButton(
            onClick = onDelete,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.textButtonColors(contentColor = colors.statusRed)
        ) {
            Text("Delete")
        }
    }
}
