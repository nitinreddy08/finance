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
import kotlinx.coroutines.flow.MutableStateFlow
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

    private val _onboardingComplete = MutableStateFlow(false)
    val onboardingComplete = _onboardingComplete.asStateFlow()

    fun signInWithGoogle(context: Context) {
        viewModelScope.launch {
            try {
                val result = authRepository.signInWithGoogle(context)
                _signInState.value = result.isSuccess
            } catch (e: Exception) {
                // Google Sign-In not configured yet, skip gracefully
                _signInState.value = false
            }
        }
    }

    fun completeOnboarding(spendLimitMinor: Long, categories: List<Pair<String, Long>>) {
        viewModelScope.launch {
            try {
                val monthId = UUID.randomUUID()
                val now = LocalDate.now()

                // Create active month
                val budgetMonth = BudgetMonth(
                    id = monthId,
                    year = now.year,
                    month = now.monthValue,
                    status = MonthStatus.ACTIVE,
                    createdAt = Instant.now(),
                    archivedAt = null
                )
                budgetMonthDao.insert(budgetMonth.toEntity())

                // Insert each category with its individual budget
                categories.forEachIndexed { index, (name, budgetMinor) ->
                    val category = Category(
                        id = UUID.randomUUID(),
                        monthId = monthId,
                        name = name,
                        monthlyBudgetMinor = budgetMinor,
                        weeklyPacingEnabled = true,
                        iconKey = "default",
                        sortOrder = index,
                        active = true,
                        createdAt = Instant.now(),
                        updatedAt = Instant.now()
                    )
                    categoryDao.insert(category.toEntity())
                }

                _onboardingComplete.value = true
            } catch (e: Exception) {
                // Log error but don't crash
                e.printStackTrace()
            }
        }
    }
}
