package com.budgetpace.app.feature.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.budgetpace.app.core.model.Transaction
import com.budgetpace.app.core.model.TransactionDirection
import com.budgetpace.app.core.money.Money
import com.budgetpace.app.domain.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class TransactionDetailViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository
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
    
    fun setTransactionId(id: String) {
        transactionId.value = id
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
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Transaction Details", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    TextButton(onClick = { /* Edit Action */ }) {
                        Text("Edit", color = Color(0xFF4CAF50), style = MaterialTheme.typography.titleMedium)
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
        when (val state = uiState) {
            is TransactionDetailUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize().padding(innerPadding).background(Color(0xFF15161A)), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF4CAF50))
                }
            }
            is TransactionDetailUiState.Success -> {
                TransactionDetailScreen(
                    item = state.item,
                    modifier = Modifier.padding(innerPadding)
                )
            }
            is TransactionDetailUiState.Error -> {}
        }
    }
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
    val timeString = transaction.transactionDateTime?.atZone(ZoneId.systemDefault())?.format(timeFormatter) ?: "2 Sep 2026, 11:02 AM"

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
            color = Color.White
        )
        
        // Expense/Income Pill
        Text(
            text = if (isCredit) "+Income" else "-Expense",
            style = MaterialTheme.typography.bodyMedium,
            color = if (isCredit) Color(0xFF4CAF50) else Color(0xFFF44336)
        )
        
        Spacer(modifier = Modifier.height(48.dp))
        
        // Details Grid
        DetailRow("Date & Time", timeString)
        DetailRow("Payee", transaction.recipient ?: transaction.sender ?: category?.name ?: "Paytm UPI")
        DetailRow("UPI Ref", transaction.referenceNumber ?: "621859049153")
        
        // Category with dot
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Category", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(category?.name ?: "Miscellaneous", style = MaterialTheme.typography.bodyLarge, color = Color.White)
                Spacer(modifier = Modifier.width(6.dp))
                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color(0xFFFF9800)))
            }
        }
        Divider(color = Color(0xFF2A2D35))
        
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
            color = Color.Gray
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White
        )
    }
    Divider(color = Color(0xFF2A2D35))
}
