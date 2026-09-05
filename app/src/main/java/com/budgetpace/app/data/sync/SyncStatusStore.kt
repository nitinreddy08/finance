package com.budgetpace.app.data.sync

import android.content.Context
import androidx.core.content.edit
import com.budgetpace.app.core.security.PREFS_SYNC_STATUS
import com.budgetpace.app.core.security.appPrefs
import com.budgetpace.app.domain.sync.SyncProblem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** What the Google backup screen's "Sync details" row shows. */
data class SyncStatus(
    val lastSuccessAtMillis: Long? = null,
    val lastAttemptAtMillis: Long? = null,
    val problem: SyncProblem? = null,
)

/**
 * Persists only [SyncProblem.code] and [SyncProblem.detail] — never the copy itself — so a later
 * build showing different wording for the same code never has to migrate anything, and a build
 * that no longer recognises an old code degrades to [SyncProblem.Unknown] instead of crashing.
 */
@Singleton
class SyncStatusStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = appPrefs(context, PREFS_SYNC_STATUS)

    private val _status = MutableStateFlow(readFromPrefs())
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    private fun readFromPrefs(): SyncStatus {
        val lastSuccess = prefs.getLong(KEY_LAST_SUCCESS, -1L).takeIf { it >= 0 }
        val lastAttempt = prefs.getLong(KEY_LAST_ATTEMPT, -1L).takeIf { it >= 0 }
        val code = prefs.getString(KEY_PROBLEM_CODE, null)
        val detail = prefs.getString(KEY_PROBLEM_DETAIL, null)
        return SyncStatus(lastSuccess, lastAttempt, code?.let { SyncProblem.fromCode(it, detail) })
    }

    /** Called at the very start of a pass, before the token/workbook/plan steps run. */
    fun recordAttemptStarted() {
        val now = System.currentTimeMillis()
        prefs.edit { putLong(KEY_LAST_ATTEMPT, now) }
        _status.value = _status.value.copy(lastAttemptAtMillis = now)
    }

    fun recordSuccess() {
        val now = System.currentTimeMillis()
        prefs.edit {
            putLong(KEY_LAST_SUCCESS, now)
            putLong(KEY_LAST_ATTEMPT, now)
            remove(KEY_PROBLEM_CODE)
            remove(KEY_PROBLEM_DETAIL)
        }
        _status.value = _status.value.copy(lastSuccessAtMillis = now, lastAttemptAtMillis = now, problem = null)
    }

    /** A [SyncProblem.isSilent] problem (the owner cancelled a consent sheet) is never recorded. */
    fun recordFailure(problem: SyncProblem) {
        if (problem.isSilent) return
        val now = System.currentTimeMillis()
        prefs.edit {
            putLong(KEY_LAST_ATTEMPT, now)
            putString(KEY_PROBLEM_CODE, problem.code)
            val detail = problem.detail
            if (detail != null) putString(KEY_PROBLEM_DETAIL, detail) else remove(KEY_PROBLEM_DETAIL)
        }
        _status.value = _status.value.copy(lastAttemptAtMillis = now, problem = problem)
    }

    companion object {
        private const val KEY_LAST_SUCCESS = "last_success_at"
        private const val KEY_LAST_ATTEMPT = "last_attempt_at"
        private const val KEY_PROBLEM_CODE = "problem_code"
        private const val KEY_PROBLEM_DETAIL = "problem_detail"
    }
}
