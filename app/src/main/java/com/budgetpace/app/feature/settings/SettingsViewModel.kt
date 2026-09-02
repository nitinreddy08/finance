package com.budgetpace.app.feature.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.budgetpace.app.core.designsystem.theme.ThemeMode
import com.budgetpace.app.core.designsystem.theme.ThemePreference
import com.budgetpace.app.data.google.auth.AuthorizationOutcome
import com.budgetpace.app.data.google.auth.GoogleAuthorizationManager
import com.budgetpace.app.data.google.sheets.GoogleSheetsRepository
import com.budgetpace.app.data.local.dao.BudgetMonthDao
import com.budgetpace.app.data.local.dao.TransactionDao
import com.budgetpace.app.data.local.db.BudgetDatabase
import com.budgetpace.app.data.sync.GoogleSheetsSyncWorker
import com.budgetpace.app.domain.auth.AuthRepository
import com.budgetpace.app.domain.auth.UserSession
import com.budgetpace.app.domain.export.CsvExportService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject

sealed interface ExportState {
    object Idle : ExportState
    object Exporting : ExportState
    data class Success(val uri: Uri) : ExportState
    data class Error(val message: String) : ExportState
}

sealed interface SheetsSyncState {
    object Idle : SheetsSyncState
    object Syncing : SheetsSyncState
    data class Success(val syncedCount: Int) : SheetsSyncState
    data class Error(val message: String) : SheetsSyncState
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val csvExportService: CsvExportService,
    private val budgetMonthDao: BudgetMonthDao,
    private val transactionDao: TransactionDao,
    private val budgetDatabase: BudgetDatabase,
    private val authorizationManager: GoogleAuthorizationManager,
    private val sheetsRepository: GoogleSheetsRepository,
    private val themePreference: ThemePreference,
) : ViewModel() {

    val session: StateFlow<UserSession?> = authRepository.currentSession
    val isSheetsAuthorized: StateFlow<Boolean> = authorizationManager.isAuthorized

    private val _exportState = MutableStateFlow<ExportState>(ExportState.Idle)
    val exportState: StateFlow<ExportState> = _exportState.asStateFlow()

    private val _sheetsSyncState = MutableStateFlow<SheetsSyncState>(SheetsSyncState.Idle)
    val sheetsSyncState: StateFlow<SheetsSyncState> = _sheetsSyncState.asStateFlow()

    val pendingSyncCount: StateFlow<Int> = transactionDao.observePendingCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val syncedCount: StateFlow<Int> = transactionDao.observeSyncedCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val themeMode: StateFlow<ThemeMode> = themePreference.mode

    fun setThemeMode(mode: ThemeMode) = themePreference.setMode(mode)

    fun lastSyncAtMillis(): Long? = sheetsRepository.lastSyncAtMillis()

    /** Spec §14: "Disconnect Google" signs out and revokes the Sheets/Drive authorization. */
    fun disconnectGoogle() {
        viewModelScope.launch {
            authRepository.signOut()
            authorizationManager.clear()
        }
    }

    /**
     * Kicks off Drive/Sheets authorization (spec §7 — a step separate from sign-in).
     * If consent UI is needed, [onNeedsConsent] receives the IntentSenderRequest to launch;
     * the caller must feed the ActivityResult's data Intent back into [handleAuthorizationResult].
     */
    fun beginSheetsAuthorization(onNeedsConsent: (android.app.PendingIntent) -> Unit) {
        viewModelScope.launch {
            when (val outcome = authorizationManager.requestAuthorization()) {
                is AuthorizationOutcome.NeedsConsent -> onNeedsConsent(outcome.pendingIntent)
                is AuthorizationOutcome.Authorized -> Unit // isSheetsAuthorized flow updates itself
                is AuthorizationOutcome.Failed -> _sheetsSyncState.value = SheetsSyncState.Error(outcome.message)
            }
        }
    }

    fun handleAuthorizationResult(data: Intent?) {
        when (val outcome = authorizationManager.handleAuthorizationResult(data)) {
            is AuthorizationOutcome.Authorized -> Unit
            is AuthorizationOutcome.Failed -> _sheetsSyncState.value = SheetsSyncState.Error(outcome.message)
            is AuthorizationOutcome.NeedsConsent -> Unit // shouldn't happen from a result callback
        }
    }

    /** Spec §55: manual "Sync now" — separate from the daily WorkManager job. */
    fun syncNow() {
        viewModelScope.launch {
            _sheetsSyncState.value = SheetsSyncState.Syncing
            val workbook = sheetsRepository.ensureWorkbook()
            if (workbook.isFailure) {
                _sheetsSyncState.value = SheetsSyncState.Error(
                    workbook.exceptionOrNull()?.message ?: "Couldn't create the workbook."
                )
                return@launch
            }
            val result = sheetsRepository.syncPendingTransactions()
            _sheetsSyncState.value = result.fold(
                onSuccess = { SheetsSyncState.Success(it) },
                onFailure = { SheetsSyncState.Error(it.message ?: "Sync failed.") }
            )
        }
    }

    fun consumeSheetsSyncState() {
        _sheetsSyncState.value = SheetsSyncState.Idle
    }

    fun toggleDailyBackup(context: Context, enabled: Boolean) {
        val workManager = WorkManager.getInstance(context)
        val workName = GoogleSheetsSyncWorker.WORK_NAME

        if (enabled) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val syncRequest = PeriodicWorkRequestBuilder<GoogleSheetsSyncWorker>(24, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()

            workManager.enqueueUniquePeriodicWork(
                workName,
                ExistingPeriodicWorkPolicy.UPDATE,
                syncRequest
            )
        } else {
            workManager.cancelUniqueWork(workName)
        }
    }

    /** Spec §55: CSV export must work independently of Google authorization. */
    fun exportCurrentMonthCsv() {
        viewModelScope.launch {
            _exportState.value = ExportState.Exporting
            val activeMonth = budgetMonthDao.getActiveMonth()
            if (activeMonth == null) {
                _exportState.value = ExportState.Error("No active month to export yet.")
                return@launch
            }
            val result = csvExportService.exportMonthToCsv(activeMonth.id)
            _exportState.value = result.fold(
                onSuccess = { ExportState.Success(it) },
                onFailure = { ExportState.Error(it.message ?: "Export failed.") }
            )
        }
    }

    fun consumeExportState() {
        _exportState.value = ExportState.Idle
    }

    /** Spec §73: removes local transactions/budgets only — never touches the user's Google Sheet. */
    fun deleteLocalData() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                budgetDatabase.clearAllTables()
            }
        }
    }
}
