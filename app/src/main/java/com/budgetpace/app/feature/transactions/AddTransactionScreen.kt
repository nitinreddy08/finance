package com.budgetpace.app.feature.transactions

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgetpace.app.core.designsystem.theme.bpColors
import com.budgetpace.app.core.model.*
import com.budgetpace.app.core.money.Money
import com.budgetpace.app.domain.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class AddTransactionViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository
) : ViewModel() {

    fun addTransaction(amountDecimal: String, categoryId: String?, date: LocalDate, note: String) {
        val amountMinor = Money.rupeesToPaise(amountDecimal)
        if (amountMinor <= 0) return
        
        val transaction = Transaction(
            id = UUID.randomUUID(),
            monthId = UUID.randomUUID(), // Would normally pull from active month
            amountMinor = amountMinor,
            currency = "INR",
            direction = TransactionDirection.DEBIT,
            categoryId = categoryId?.let { UUID.fromString(it) },
            transactionDateTime = null,
            transactionDate = date,
            notificationReceivedAt = Instant.now(),
            bank = Bank.UNKNOWN,
            accountSuffix = null,
            recipient = note.takeIf { it.isNotBlank() },
            sender = null,
            referenceNumber = null,
            sourcePackage = null,
            sourceSender = null,
            sourceMessageHash = null,
            duplicateKey = null,
            recordDecision = RecordDecision.RECORDED,
            syncState = SyncState.PENDING,
            parserVersion = null,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        
        viewModelScope.launch {
            transactionRepository.add(transaction)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionRoute(
    viewModel: AddTransactionViewModel,
    onBack: () -> Unit
) {
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add Transaction") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.bpColors.background,
                    titleContentColor = MaterialTheme.bpColors.textPrimary
                )
            )
        },
        containerColor = MaterialTheme.bpColors.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            
            Text(
                text = "Amount",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.bpColors.textSecondary
            )
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it },
                modifier = Modifier.fillMaxWidth(),
                prefix = { Text("₹ ") },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = MaterialTheme.shapes.small
            )
            
            Text(
                text = "Category",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.bpColors.textSecondary
            )
            // Simplified Category Dropdown placeholder
            OutlinedButton(
                onClick = { /* Open category selector */ },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small
            ) {
                Text("Select")
            }
            
            Text(
                text = "Date",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.bpColors.textSecondary
            )
            OutlinedButton(
                onClick = { /* Open date selector */ },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small
            ) {
                Text("Today")
            }
            
            Text(
                text = "Note (optional)",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.bpColors.textSecondary
            )
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small
            )
            
            Spacer(modifier = Modifier.weight(1f))
            
            Button(
                onClick = {
                    viewModel.addTransaction(amount, null, LocalDate.now(), note)
                    onBack()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            ) {
                Text("Add transaction")
            }
        }
    }
}
