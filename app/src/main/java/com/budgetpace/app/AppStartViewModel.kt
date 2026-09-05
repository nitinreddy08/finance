package com.budgetpace.app

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgetpace.app.core.designsystem.theme.ThemeMode
import com.budgetpace.app.core.designsystem.theme.ThemePreference
import com.budgetpace.app.data.local.dao.BudgetMonthDao
import com.budgetpace.app.domain.usecase.EnsureActiveMonthUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Whether onboarding has already been completed, so MainActivity can pick the right nav start
 * destination. `null` means "still loading" — deciding too early sent a returning owner back
 * through onboarding on every launch.
 */
@HiltViewModel
class AppStartViewModel @Inject constructor(
    private val budgetMonthDao: BudgetMonthDao,
    private val ensureActiveMonth: EnsureActiveMonthUseCase,
    themePreference: ThemePreference,
) : ViewModel() {

    val isOnboarded: StateFlow<Boolean?> = budgetMonthDao.observeAll()
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val themeMode: StateFlow<ThemeMode> = themePreference.mode

    /**
     * Spec section 57: pick up a calendar month rollover at the first suitable execution. Called on
     * every foreground rather than only at process start, because a phone with memory to spare
     * keeps the app alive for days — it would otherwise still show last month on the 1st.
     */
    fun onForegrounded() {
        viewModelScope.launch {
            try {
                // Only once onboarding has created the first month; creating one here would make
                // an unfinished onboarding look complete.
                if (budgetMonthDao.getActiveMonth() != null) {
                    ensureActiveMonth()
                }
            } catch (e: Exception) {
                // A month problem must never stop the app from opening.
                Log.e("AppStart", "Month rollover failed: ${e.javaClass.simpleName}", e)
            }
        }
    }
}
