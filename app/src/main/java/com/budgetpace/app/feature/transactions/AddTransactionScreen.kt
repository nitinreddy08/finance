package com.budgetpace.app.feature.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
            monthId = UUID.randomUUID(),
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
    var selectedTab by remember { mutableStateOf("Expense") }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add Transaction", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    TextButton(onClick = {
                        viewModel.addTransaction(amount, null, LocalDate.now(), note)
                        onBack()
                    }) {
                        Text("Save", color = Color(0xFF4CAF50), style = MaterialTheme.typography.titleMedium)
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Tabs
            Row(modifier = Modifier.fillMaxWidth()) {
                val expenseSelected = selectedTab == "Expense"
                Column(
                    modifier = Modifier.weight(1f).clickable { selectedTab = "Expense" }.padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Expense", color = if (expenseSelected) Color.White else Color.Gray, fontWeight = if (expenseSelected) FontWeight.Bold else FontWeight.Normal)
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(modifier = Modifier.fillMaxWidth().height(2.dp).background(if (expenseSelected) Color(0xFFF44336) else Color.Transparent))
                }
                val incomeSelected = selectedTab == "Income"
                Column(
                    modifier = Modifier.weight(1f).clickable { selectedTab = "Income" }.padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Income", color = if (incomeSelected) Color.White else Color.Gray, fontWeight = if (incomeSelected) FontWeight.Bold else FontWeight.Normal)
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(modifier = Modifier.fillMaxWidth().height(2.dp).background(if (incomeSelected) Color(0xFF4CAF50) else Color.Transparent))
                }
            }
            
            Divider(color = Color(0xFF2A2D35))
            
            Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                
                // Amount
                Column {
                    Text("Amount", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    TextField(
                        value = amount,
                        onValueChange = { amount = it },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.headlineSmall.copy(color = Color.White),
                        prefix = { Text("₹ ", style = MaterialTheme.typography.headlineSmall, color = Color.Gray) },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color(0xFF4CAF50),
                            unfocusedIndicatorColor = Color(0xFF2A2D35),
                        ),
                        placeholder = { Text("0", style = MaterialTheme.typography.headlineSmall, color = Color.DarkGray) }
                    )
                }
                
                // Date & Time
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Date & Time", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("2 Sep 2026, 11:30 AM", style = MaterialTheme.typography.bodyLarge, color = Color.White)
                    }
                    Icon(Icons.Default.CalendarToday, contentDescription = "Select Date", tint = Color.Gray)
                }
                Divider(color = Color(0xFF2A2D35))
                
                // Category
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Category", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Select category", style = MaterialTheme.typography.bodyLarge, color = Color.Gray)
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = "Select Category", tint = Color.Gray)
                }
                Divider(color = Color(0xFF2A2D35))
                
                // Payment Method
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Payment Method", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Cash", style = MaterialTheme.typography.bodyLarge, color = Color.White)
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = "Select Method", tint = Color.Gray)
                }
                Divider(color = Color(0xFF2A2D35))
                
                // Note
                Column {
                    Text("Note (optional)", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    TextField(
                        value = note,
                        onValueChange = { note = it },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color(0xFF4CAF50),
                            unfocusedIndicatorColor = Color(0xFF2A2D35),
                        ),
                        placeholder = { Text("Add a note", style = MaterialTheme.typography.bodyLarge, color = Color.Gray) }
                    )
                }
            }
        }
    }
}
