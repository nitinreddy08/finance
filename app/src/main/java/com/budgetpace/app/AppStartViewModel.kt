package com.budgetpace.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgetpace.app.core.designsystem.theme.ThemeMode
import com.budgetpace.app.core.designsystem.theme.ThemePreference
import com.budgetpace.app.data.google.auth.GoogleAuthorizationManager
import com.budgetpace.app.data.local.dao.BudgetMonthDao
import com.budgetpace.app.domain.auth.AuthRepository
import com.budgetpace.app.domain.usecase.EnsureActiveMonthUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Whether onboarding has already been completed, so MainActivity can pick the right nav
 * start destination. `null` means "still loading" — the previous implementation always
 * launched onboarding.route regardless of prior state, resetting a returning user to
 * onboarding on every single app open even though their data was already in Room.
 */
@HiltViewModel
class AppStartViewModel @Inject constructor(
    budgetMonthDao: BudgetMonthDao,
    private val ensureActiveMonth: EnsureActiveMonthUseCase,
    themePreference: ThemePreference,
    private val authRepository: AuthRepository,
    private val authorizationManager: GoogleAuthorizationManager,
) : ViewModel() {

    val isOnboarded: StateFlow<Boolean?> = budgetMonthDao.observeAll()
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val themeMode: StateFlow<ThemeMode> = themePreference.mode

    init {
        // Spec §57: pick up a calendar month rollover at "the first suitable app ...
        // execution" — there is no background job for it in V1, so app launch is that point.
        // Only relevant once onboarding has created the first month at all.
        viewModelScope.launch {
            if (budgetMonthDao.getActiveMonth() != null) {
                ensureActiveMonth()
            }
        }

        // A restored session/authorization only carries identity, not a live token — refresh
        // both silently so the UI doesn't wrongly show "not connected" after every restart even
        // though Google-side consent from a previous session is still valid. The restored
        // session's email (if any) is already available synchronously, so authorization can tie
        // itself to that same account without waiting on the (idToken-only) session refresh.
        viewModelScope.launch { authRepository.refreshSessionSilently() }
        viewModelScope.launch { authorizationManager.restoreIfNeeded(authRepository.currentSession.value?.email) }
    }
}
