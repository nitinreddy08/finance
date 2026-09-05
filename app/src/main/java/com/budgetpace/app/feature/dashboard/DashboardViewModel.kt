package com.budgetpace.app.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgetpace.app.core.model.BudgetMonth
import com.budgetpace.app.core.model.MonthSummary
import com.budgetpace.app.data.local.dao.BudgetMonthDao
import com.budgetpace.app.data.local.dao.TransactionDao
import com.budgetpace.app.data.local.mapper.toDomain
import com.budgetpace.app.domain.repository.BudgetRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val budgetRepository: BudgetRepository,
    budgetMonthDao: BudgetMonthDao,
    private val transactionDao: TransactionDao,
) : ViewModel() {

    // null means "the active month" — selecting a past month is a deliberate, one-off choice,
    // not something that should persist across navigating away and back.
    private val selectedMonthId = MutableStateFlow<String?>(null)

    val availableMonths: StateFlow<List<BudgetMonth>> = budgetMonthDao.observeAll()
        .map { list -> list.map { it.toDomain() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<DashboardUiState> = selectedMonthId
        .flatMapLatest { monthId ->
            if (monthId == null) budgetRepository.observeActiveMonthSummary()
            else budgetRepository.observeMonthSummary(monthId)
        }
        .map { summary ->
            if (summary == null) DashboardUiState.NoMonth
            else DashboardUiState.Success(summary)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = DashboardUiState.Loading
        )

    /**
     * Drives Home's "N expenses need a category" row, for whichever month is actually on screen
     * right now — the active month by default, or the archived month the owner picked from the
     * month picker — never hard-coded to the active month alone.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val uncategorizedCount: StateFlow<Int> = uiState
        .flatMapLatest { state ->
            val monthId = (state as? DashboardUiState.Success)?.summary?.month?.id?.toString()
            if (monthId == null) flowOf(0) else transactionDao.observeUncategorizedCount(monthId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** Pass null to return to the active month. */
    fun selectMonth(monthId: String?) {
        selectedMonthId.value = monthId
    }
}

sealed interface DashboardUiState {
    object Loading : DashboardUiState
    data class Success(val summary: MonthSummary) : DashboardUiState
    /** No active BudgetMonth row exists yet — a brief window before onboarding finishes writing
     * one, not a genuine error. Spec: never show "Error" copy for this. */
    object NoMonth : DashboardUiState
}
