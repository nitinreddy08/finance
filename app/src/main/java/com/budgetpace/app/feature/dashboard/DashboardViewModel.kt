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
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val budgetRepository: BudgetRepository
) : ViewModel() {

    // For Phase 1, we observe a hardcoded "ACTIVE" month or first month
    // In actual implementation, we would query the active month ID
    
    // Using dummy ID for the sample data seeded in BudgetDatabase
    private val activeMonthId = MutableStateFlow<String?>(null)
    
    val uiState: StateFlow<DashboardUiState> = budgetRepository.observeActiveMonthSummary()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = DashboardUiState.Loading
        ) as StateFlow<DashboardUiState>
}

sealed interface DashboardUiState {
    object Loading : DashboardUiState
    data class Success(val summary: MonthSummary) : DashboardUiState
    object Error : DashboardUiState
}
