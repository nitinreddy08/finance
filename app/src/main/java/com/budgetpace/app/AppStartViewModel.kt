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

        // AuthRepositoryImpl already restores email/displayName synchronously from encrypted
        // prefs at construction time, which is all the UI needs to show "signed in" correctly —
        // so authorization can tie itself to that account right away. We deliberately do NOT call
        // authRepository.refreshSessionSilently() here: even with Credential Manager's "silent"
        // options set, getCredential() still briefly renders its bottom-sheet chrome ("Signing you
        // in…") on screen before auto-dismissing, which showed up as a visible flash on every
        // single cold launch. There's no real need for a fresh ID token just to display identity.
        viewModelScope.launch { authorizationManager.restoreIfNeeded(authRepository.currentSession.value?.email) }
    }
}
