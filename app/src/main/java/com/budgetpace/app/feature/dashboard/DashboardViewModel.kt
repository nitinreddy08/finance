package com.budgetpace.app.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgetpace.app.core.model.MonthSummary
import com.budgetpace.app.domain.repository.BudgetRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val budgetRepository: BudgetRepository
) : ViewModel() {

    private val activeMonthId = MutableStateFlow<String?>(null)
    
    val uiState: StateFlow<DashboardUiState> = budgetRepository.observeActiveMonthSummary()
        .map { summary ->
            if (summary == null) DashboardUiState.Error
            else DashboardUiState.Success(summary)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = DashboardUiState.Loading
        )
}

sealed interface DashboardUiState {
    object Loading : DashboardUiState
    data class Success(val summary: MonthSummary) : DashboardUiState
    object Error : DashboardUiState
}
