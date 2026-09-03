package com.budgetpace.app.feature.onboarding

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgetpace.app.core.model.BudgetMonth
import com.budgetpace.app.core.model.Category
import com.budgetpace.app.core.model.MonthStatus
import com.budgetpace.app.data.local.dao.BudgetMonthDao
import com.budgetpace.app.data.local.dao.CategoryDao
import com.budgetpace.app.data.local.mapper.toEntity
import com.budgetpace.app.domain.auth.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val budgetMonthDao: BudgetMonthDao,
    private val categoryDao: CategoryDao,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _signInState = MutableStateFlow<Boolean?>(null)
    val signInState = _signInState.asStateFlow()

    // A signal, not a state — MutableStateFlow drops a repeated identical value (e.g. two
    // failed attempts in a row would both be `false` and only the first would emit), which is
    // exactly why the sign-in button used to look like it was doing nothing on every retry.
    private val _signInError = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val signInError = _signInError.asSharedFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving = _isSaving.asStateFlow()

    fun signInWithGoogle(context: Context) {
        viewModelScope.launch {
            val result = authRepository.signInWithGoogle(context)
            _signInState.value = result.isSuccess
            if (result.isFailure) _signInError.tryEmit(Unit)
        }
    }

    fun completeOnboarding(
        categories: List<CategoryEntry>,
        onDone: () -> Unit
    ) {
        if (_isSaving.value) return
        _isSaving.value = true
        viewModelScope.launch {
            try {
                val monthId = UUID.randomUUID()
                val now = LocalDate.now()

                val budgetMonth = BudgetMonth(
                    id = monthId,
                    year = now.year,
                    month = now.monthValue,
                    status = MonthStatus.ACTIVE,
                    createdAt = Instant.now(),
                    archivedAt = null
                )
                budgetMonthDao.insert(budgetMonth.toEntity())

                categories.forEachIndexed { index, entry ->
                    val category = Category(
                        id = UUID.randomUUID(),
                        monthId = monthId,
                        name = entry.name,
                        monthlyBudgetMinor = entry.budgetMinor,
                        periodCount = entry.periodCount,
                        iconKey = entry.iconKey,
                        sortOrder = index,
                        active = true,
                        createdAt = Instant.now(),
                        updatedAt = Instant.now()
                    )
                    categoryDao.insert(category.toEntity())
                }

                onDone()
            } catch (e: Exception) {
                e.printStackTrace()
                _isSaving.value = false
            }
        }
    }
}
