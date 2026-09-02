package com.budgetpace.app.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgetpace.app.core.model.BudgetMonth
import com.budgetpace.app.core.model.MonthSummary
import com.budgetpace.app.data.local.dao.BudgetMonthDao
import com.budgetpace.app.data.local.mapper.toDomain
import com.budgetpace.app.domain.repository.BudgetRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val budgetRepository: BudgetRepository,
    budgetMonthDao: BudgetMonthDao,
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
            if (summary == null) DashboardUiState.Error
            else DashboardUiState.Success(summary)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = DashboardUiState.Loading
        )

    /** Pass null to return to the active month. */
    fun selectMonth(monthId: String?) {
        selectedMonthId.value = monthId
    }
}

sealed interface DashboardUiState {
    object Loading : DashboardUiState
    data class Success(val summary: MonthSummary) : DashboardUiState
    object Error : DashboardUiState
}
