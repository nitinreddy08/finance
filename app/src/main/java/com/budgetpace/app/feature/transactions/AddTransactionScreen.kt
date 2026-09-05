package com.budgetpace.app.feature.transactions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.budgetpace.app.core.designsystem.components.CategoryChooserSheet
import com.budgetpace.app.core.designsystem.theme.bpColors
import com.budgetpace.app.core.model.*
import com.budgetpace.app.core.money.Money
import com.budgetpace.app.data.local.dao.BudgetMonthDao
import com.budgetpace.app.data.local.dao.CategoryDao
import com.budgetpace.app.data.local.mapper.toDomain
import com.budgetpace.app.domain.repository.TransactionRepository
import com.budgetpace.app.domain.usecase.EnsureActiveMonthUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject

/** Set right before popping back from a successful save; consumed once by [TransactionsRoute]. */
object AddedExpenseSignal {
    var pending: Boolean = false
}

@HiltViewModel
class AddTransactionViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val budgetMonthDao: BudgetMonthDao,
    private val categoryDao: CategoryDao,
    private val ensureActiveMonth: EnsureActiveMonthUseCase,
) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    val categories: StateFlow<List<Category>> = budgetMonthDao.observeActiveMonth()
        .filterNotNull()
        .flatMapLatest { month -> categoryDao.observeByMonth(month.id) }
        .map { list -> list.map { it.toDomain() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeMonth: StateFlow<BudgetMonth?> = budgetMonthDao.observeActiveMonth()
        .map { it?.toDomain() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun addTransaction(
        amountMinor: Long,
        categoryId: String?,
        date: LocalDate,
        note: String,
        onDone: () -> Unit,
    ) {
        if (amountMinor <= 0) return

        viewModelScope.launch {
            val activeMonth = ensureActiveMonth()
            val now = Instant.now()
            val today = LocalDate.now()

            val transaction = Transaction(
                id = UUID.randomUUID(),
                monthId = activeMonth.id,
                amountMinor = amountMinor,
                currency = "INR",
                direction = TransactionDirection.DEBIT,
                categoryId = categoryId?.let { UUID.fromString(it) },
                // A manual entry only truly has a time when it's dated today; a past date has no
                // real time to report, so it falls back to the date-only display everywhere else.
                transactionDateTime = if (date == today) now else null,
                transactionDate = date,
                notificationReceivedAt = now,
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
                createdAt = now,
                updatedAt = now,
            )

            transactionRepository.add(transaction)
            onDone()
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
    var selectedCategory by remember { mutableStateOf<Category?>(null) }
    var showCategoryPicker by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }

    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val activeMonth by viewModel.activeMonth.collectAsStateWithLifecycle()
    val dateFormatter = remember { DateTimeFormatter.ofPattern("d MMM yyyy") }
    val amountMinor = Money.rupeesToPaise(amount.ifBlank { "0" })

    if (showCategoryPicker) {
        CategoryChooserSheet(
            categories = categories,
            onSelectCategory = { category -> selectedCategory = category },
            onDismiss = { showCategoryPicker = false },
        )
    }

    if (showDatePicker) {
        ConstrainedDatePickerDialog(
            month = activeMonth,
            initialDate = selectedDate,
            onConfirm = { selectedDate = it; showDatePicker = false },
            onDismiss = { showDatePicker = false },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                // Spec §7: "+ Add expense", not "Add transaction" — V1 is expense-only, no income.
                title = { Text("Add expense", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
                    }
                },
                actions = {
                    TextButton(
                        enabled = amountMinor > 0,
                        onClick = {
                            viewModel.addTransaction(
                                amountMinor = amountMinor,
                                categoryId = selectedCategory?.id?.toString(),
                                date = selectedDate,
                                note = note,
                                onDone = {
                                    AddedExpenseSignal.pending = true
                                    onBack()
                                },
                            )
                        }
                    ) {
                        Text(
                            "Save",
                            color = if (amountMinor > 0) MaterialTheme.bpColors.accent else MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .imePadding()
        ) {
            Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {

                // Amount — digits and at most one decimal point only.
                Column {
                    Text("Amount", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextField(
                        value = amount,
                        onValueChange = { input -> amount = filterAmountInput(input) },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.headlineSmall.copy(color = MaterialTheme.colorScheme.onBackground),
                        prefix = { Text("₹ ", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                            unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                            focusedIndicatorColor = MaterialTheme.bpColors.accent,
                            unfocusedIndicatorColor = MaterialTheme.colorScheme.outline,
                        ),
                        placeholder = { Text("0", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    )
                }

                // Date — constrained to the active month (spec).
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDatePicker = true },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Date", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(selectedDate.format(dateFormatter), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
                    }
                    Icon(Icons.Default.CalendarToday, contentDescription = "Change date", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                // Category
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showCategoryPicker = true },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Category", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            selectedCategory?.name ?: "Select category",
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (selectedCategory != null) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = "Select category", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                // Payment Method — always Cash for manually-entered transactions (spec §11/§22)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Payment Method", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Cash", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                // Note
                Column {
                    Text("Note (optional)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextField(
                        value = note,
                        onValueChange = { note = it },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onBackground),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                            unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                            focusedIndicatorColor = MaterialTheme.bpColors.accent,
                            unfocusedIndicatorColor = MaterialTheme.colorScheme.outline,
                        ),
                        placeholder = { Text("Add a note", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    )
                }
            }
        }
    }
}

/** Keeps only digits and at most one decimal point, so the field can never hold "12.34.56". */
private fun filterAmountInput(input: String): String {
    val builder = StringBuilder()
    var dotSeen = false
    for (c in input) {
        when {
            c.isDigit() -> builder.append(c)
            c == '.' && !dotSeen -> { builder.append(c); dotSeen = true }
        }
    }
    return builder.toString()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConstrainedDatePickerDialog(
    month: BudgetMonth?,
    initialDate: LocalDate,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val yearMonth = month?.let { YearMonth.of(it.year, it.month) } ?: YearMonth.from(initialDate)
    val selectableDates = remember(yearMonth) {
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                val date = java.time.Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate()
                return YearMonth.from(date) == yearMonth
            }
            override fun isSelectableYear(year: Int): Boolean = year == yearMonth.year
        }
    }
    val initialMillis = remember(yearMonth, initialDate) {
        val clamped = if (YearMonth.from(initialDate) == yearMonth) initialDate else yearMonth.atDay(1)
        clamped.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    }
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initialMillis,
        yearRange = IntRange(yearMonth.year, yearMonth.year),
        selectableDates = selectableDates,
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val millis = state.selectedDateMillis
                    if (millis != null) {
                        val date = java.time.Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        onConfirm(date)
                    } else {
                        onDismiss()
                    }
                }
            ) { Text("OK", color = MaterialTheme.bpColors.accent) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    ) {
        DatePicker(state = state)
    }
}
