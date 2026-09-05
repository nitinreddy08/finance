package com.budgetpace.app.feature.onboarding

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgetpace.app.core.model.Category
import com.budgetpace.app.data.local.dao.CategoryDao
import com.budgetpace.app.data.local.mapper.toEntity
import com.budgetpace.app.domain.auth.AuthRepository
import com.budgetpace.app.domain.usecase.EnsureActiveMonthUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

/** Spec §23/§25: total spend limit is the sum of each category's own monthly budget. */
data class CategoryEntry(
    val name: String,
    val budgetMinor: Long,
    val periodCount: Int,
    val iconKey: String,
)

/**
 * Onboarding step and the categories picked so far, hoisted here (rather than living in
 * `remember { }` inside the composable) so a process death mid-onboarding — realistic here since
 * step 1 hands off to the Credential Manager's own sign-in UI — restores exactly where the owner
 * left off instead of silently resetting to Welcome.
 *
 * [categories] is encoded as a single String (not stored as a raw List in the SavedStateHandle)
 * deliberately: a String is one of the handful of types SavedStateHandle is unconditionally safe
 * to hold, sidestepping any question of whether a Kotlin List of a plain data class survives a
 * Bundle round-trip on every OEM.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val categoryDao: CategoryDao,
    private val authRepository: AuthRepository,
    private val ensureActiveMonth: EnsureActiveMonthUseCase,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val step: StateFlow<Int> = savedStateHandle.getStateFlow(KEY_STEP, 0)

    private val _categories = MutableStateFlow(decodeList(savedStateHandle.get<String>(KEY_CATEGORIES) ?: ""))
    val categories: StateFlow<List<CategoryEntry>> = _categories.asStateFlow()

    private val _signInState = MutableStateFlow<Boolean?>(null)
    val signInState = _signInState.asStateFlow()

    // A signal, not a state — MutableStateFlow drops a repeated identical value (e.g. two
    // failed attempts in a row would both be `false` and only the first would emit), which is
    // exactly why the sign-in button used to look like it was doing nothing on every retry.
    private val _signInError = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val signInError = _signInError.asSharedFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving = _isSaving.asStateFlow()

    fun goToStep(newStep: Int) {
        savedStateHandle[KEY_STEP] = newStep
    }

    fun addCategory(entry: CategoryEntry) {
        _categories.value = _categories.value + entry
        persistCategories()
    }

    fun removeCategory(name: String) {
        _categories.value = _categories.value.filter { it.name != name }
        persistCategories()
    }

    private fun persistCategories() {
        savedStateHandle[KEY_CATEGORIES] = encodeList(_categories.value)
    }

    fun signInWithGoogle(context: Context) {
        viewModelScope.launch {
            val result = authRepository.signInWithGoogle(context)
            _signInState.value = result.isSuccess
            if (result.isFailure) {
                _signInError.tryEmit(Unit)
            } else {
                // Spec: request Sheets authorization right after sign-in, not later in Settings.
                //
                // TODO(google-track): GoogleAuthorizationManager does not yet expose a
                // requestAuthorization(...)/AuthorizationOutcome API on this branch (only
                // getFreshAccessToken/onConsentResult exist here as of this write) — wire this up
                // once that lands, guarding the consent PendingIntent the same way
                // SettingsViewModel.beginSheetsAuthorization does. Skipping it here degrades
                // gracefully: the owner can still connect Sheets from Settings afterward.
            }
        }
    }

    fun completeOnboarding(onDone: () -> Unit) {
        if (_isSaving.value) return
        _isSaving.value = true
        viewModelScope.launch {
            try {
                // The one sanctioned way to create/find the active month — never a raw INSERT,
                // so this can never race with a background rollover or ingestion creating the
                // same row first.
                val activeMonth = ensureActiveMonth()
                val now = Instant.now()

                _categories.value.forEachIndexed { index, entry ->
                    val category = Category(
                        id = UUID.randomUUID(),
                        monthId = activeMonth.id,
                        name = entry.name,
                        monthlyBudgetMinor = entry.budgetMinor,
                        periodCount = entry.periodCount,
                        iconKey = entry.iconKey,
                        sortOrder = index,
                        active = true,
                        createdAt = now,
                        updatedAt = now,
                    )
                    categoryDao.insert(category.toEntity())
                }

                onDone()
            } catch (e: Exception) {
                _isSaving.value = false
            }
        }
    }

    private companion object {
        const val KEY_STEP = "onboarding_step"
        const val KEY_CATEGORIES = "onboarding_categories"
        // Built from character codes rather than typed as literal escapes, so there is no
        // ambiguity about what byte actually ends up between the quotes — these are ASCII
        // control characters (SOH / STX), never typeable in a category name or emoji, so they
        // can never collide with real field content.
        val FIELD_SEP: String = 1.toChar().toString()
        val ENTRY_SEP: String = 2.toChar().toString()

        fun CategoryEntry.encode(): String =
            listOf(name, budgetMinor.toString(), periodCount.toString(), iconKey).joinToString(FIELD_SEP)

        fun decodeEntry(raw: String): CategoryEntry? {
            val parts = raw.split(FIELD_SEP)
            if (parts.size != 4) return null
            val budgetMinor = parts[1].toLongOrNull() ?: return null
            val periodCount = parts[2].toIntOrNull() ?: return null
            return CategoryEntry(name = parts[0], budgetMinor = budgetMinor, periodCount = periodCount, iconKey = parts[3])
        }

        fun encodeList(list: List<CategoryEntry>): String = list.joinToString(ENTRY_SEP) { it.encode() }

        fun decodeList(raw: String): List<CategoryEntry> =
            if (raw.isBlank()) emptyList() else raw.split(ENTRY_SEP).mapNotNull { decodeEntry(it) }
    }
}
