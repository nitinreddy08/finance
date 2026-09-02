package com.budgetpace.app.feature.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.budgetpace.app.data.local.dao.BudgetMonthDao
import com.budgetpace.app.data.local.db.BudgetDatabase
import com.budgetpace.app.data.sync.GoogleSheetsSyncWorker
import com.budgetpace.app.domain.auth.AuthRepository
import com.budgetpace.app.domain.auth.UserSession
import com.budgetpace.app.domain.export.CsvExportService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val csvExportService: CsvExportService,
    private val budgetMonthDao: BudgetMonthDao,
    private val budgetDatabase: BudgetDatabase,
) : ViewModel() {

    val session: StateFlow<UserSession?> = authRepository.currentSession

    private val _exportState = MutableStateFlow<ExportState>(ExportState.Idle)
    val exportState: StateFlow<ExportState> = _exportState.asStateFlow()

    fun toggleDailyBackup(context: Context, enabled: Boolean) {
        val workManager = WorkManager.getInstance(context)
        val workName = "DailyGoogleSheetsBackup"

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
