package com.budgetpace.app.feature.settings

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgetpace.app.core.designsystem.theme.ThemeMode
import com.budgetpace.app.core.designsystem.theme.ThemePreference
import com.budgetpace.app.core.model.BudgetMonth
import com.budgetpace.app.data.google.auth.GoogleAuthorizationManager
import com.budgetpace.app.data.google.auth.TokenResult
import com.budgetpace.app.data.google.sheets.GoogleSheetsRepository
import com.budgetpace.app.data.local.dao.BudgetMonthDao
import com.budgetpace.app.data.local.dao.TransactionDao
import com.budgetpace.app.data.local.db.BudgetDatabase
import com.budgetpace.app.data.local.mapper.toDomain
import com.budgetpace.app.data.sync.SheetsSyncCoordinator
import com.budgetpace.app.data.sync.SyncRunResult
import com.budgetpace.app.data.sync.SyncScheduler
import com.budgetpace.app.data.sync.SyncStatus
import com.budgetpace.app.data.sync.SyncStatusStore
import com.budgetpace.app.data.sync.toFailureFacts
import com.budgetpace.app.domain.auth.AuthRepository
import com.budgetpace.app.domain.auth.UserSession
import com.budgetpace.app.domain.export.CsvExportService
import com.budgetpace.app.domain.sync.classifySyncFailure
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

sealed interface ExportState {
    object Idle : ExportState
    object Exporting : ExportState
    object Success : ExportState
    data class Error(val message: String) : ExportState
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authRepository: AuthRepository,
    private val csvExportService: CsvExportService,
    private val budgetMonthDao: BudgetMonthDao,
    private val transactionDao: TransactionDao,
    private val budgetDatabase: BudgetDatabase,
    private val authorizationManager: GoogleAuthorizationManager,
    private val sheetsRepository: GoogleSheetsRepository,
    private val sheetsSyncCoordinator: SheetsSyncCoordinator,
    private val syncScheduler: SyncScheduler,
    private val syncStatusStore: SyncStatusStore,
    private val themePreference: ThemePreference,
) : ViewModel() {

    val session: StateFlow<UserSession?> = authRepository.currentSession
    val isSigningIn: StateFlow<Boolean> = authRepository.isSigningIn

    // A signal, not a state: two failed sign-in attempts in a row must both reach a collector even
    // though nothing else changed in between — a plain StateFlow would collapse them into one value.
    private val _signInError = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val signInError = _signInError.asSharedFlow()

    /**
     * Durable "Sheets is connected", correct even before any network call happens. Named to match
     * the existing "Google backup: Connected/Not connected" subtitle on the main Settings screen
     * (`SettingsScreen.kt`, not owned by this track) — keep this name in step with that caller.
     */
    val isSheetsAuthorized: StateFlow<Boolean> = authorizationManager.hasConsent

    val syncStatus: StateFlow<SyncStatus> = syncStatusStore.status

    /** Bound to the real WorkManager job, not a remembered preference (spec: a real Switch). */
    val dailyBackupEnabled: StateFlow<Boolean> = syncScheduler.observeDailyBackupEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val manualSyncRunning: StateFlow<Boolean> = syncScheduler.observeManualSyncRunning()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val pendingSyncCount: StateFlow<Int> = transactionDao.observePendingCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** Ignored ("Don't record") expenses are never in the sheet, so they're never counted here. */
    val syncedCount: StateFlow<Int> = transactionDao.observeSyncedRecordedCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** The screen collects this and launches the consent PendingIntent it carries. */
    private val _consentRequests = Channel<PendingIntent>(Channel.CONFLATED)
    val consentRequests = _consentRequests.receiveAsFlow()

    /** One-shot messages the screen shows as a Snackbar (spec: never a Toast). */
    private val _snackbarMessages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val snackbarMessages = _snackbarMessages.asSharedFlow()

    private val _exportState = MutableStateFlow<ExportState>(ExportState.Idle)
    val exportState: StateFlow<ExportState> = _exportState.asStateFlow()

    /** Month picker: current (ACTIVE) + every archived month, newest first. */
    val months: StateFlow<List<BudgetMonth>> = budgetMonthDao.observeAll()
        .map { entities -> entities.map { it.toDomain() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val themeMode: StateFlow<ThemeMode> = themePreference.mode

    fun setThemeMode(mode: ThemeMode) = themePreference.setMode(mode)

    /**
     * Sign-in was previously only reachable from onboarding — if it failed or was skipped there,
     * there was no way back in without reinstalling. Settings needs its own entry point too.
     */
    fun signInWithGoogle(context: Context) {
        viewModelScope.launch {
            val result = authRepository.signInWithGoogle(context)
            if (result.isFailure) _signInError.tryEmit(Unit)
        }
    }

    /** The URL for "Open backup sheet"; null before any workbook has ever been created. */
    fun currentSpreadsheetUrl(): String? =
        sheetsRepository.spreadsheetId?.let { "https://docs.google.com/spreadsheets/d/$it/edit" }

    /**
     * Spec §55: manual "Sync now". Refreshes the token first (a token from an earlier authorization
     * can go stale by the time the owner taps this) and, when Google demands fresh consent, launches
     * the consent sheet instead of dead-ending — the bug the previous version of this screen had.
     * The sync itself always runs as a unique expedited WorkManager job (see [SyncScheduler]) so
     * leaving the screen can't cancel it mid-way.
     */
    fun syncNow() {
        viewModelScope.launch {
            when (val tokenResult = authorizationManager.getFreshAccessToken(session.value?.email)) {
                is TokenResult.Ok -> syncScheduler.enqueueManualSyncNow()
                is TokenResult.NeedsConsent -> _consentRequests.trySend(tokenResult.pendingIntent)
                TokenResult.Cancelled -> Unit // the owner backed out; not an error, nothing to show
                is TokenResult.Failed -> {
                    val problem = classifySyncFailure(
                        (tokenResult.cause ?: IllegalStateException("Token fetch failed")).toFailureFacts()
                    )
                    syncStatusStore.recordFailure(problem)
                    _snackbarMessages.tryEmit(problem.message)
                }
            }
        }
    }

    /** Feed the consent flow's ActivityResult back in here; retries the sync once it succeeds. */
    fun onConsentResult(resultCode: Int, data: Intent?) {
        viewModelScope.launch {
            when (val tokenResult = authorizationManager.onConsentResult(resultCode, data, session.value?.email)) {
                is TokenResult.Ok -> syncScheduler.enqueueManualSyncNow()
                is TokenResult.Failed -> {
                    val problem = classifySyncFailure(
                        (tokenResult.cause ?: IllegalStateException("Consent failed")).toFailureFacts()
                    )
                    syncStatusStore.recordFailure(problem)
                    _snackbarMessages.tryEmit(problem.message)
                }
                else -> Unit // cancelled, or (shouldn't happen from a result callback) NeedsConsent again
            }
        }
    }

    fun setDailyBackupEnabled(enabled: Boolean) {
        if (enabled) syncScheduler.ensureDailyScheduled() else syncScheduler.cancelDaily()
    }

    /** The confirmed "Start a new sheet" action — never triggered silently. */
    fun startNewSheet() {
        viewModelScope.launch {
            when (val outcome = sheetsSyncCoordinator.startNewSheet()) {
                is SyncRunResult.NeedsConsent -> _consentRequests.trySend(outcome.pendingIntent)
                is SyncRunResult.Success -> _snackbarMessages.tryEmit("Started a new backup sheet.")
                is SyncRunResult.Failed -> _snackbarMessages.tryEmit(outcome.problem.message)
                SyncRunResult.Cancelled -> Unit
            }
        }
    }

    /** "Disconnect Google": signs out and revokes Sheets access. Keeps the cached sheet id — see
     * [forgetBackupSheet] — so reconnecting the same account later doesn't fork a second workbook. */
    fun disconnectGoogle() {
        viewModelScope.launch {
            val email = session.value?.email
            authRepository.signOut()
            authorizationManager.revokeAndClear(email)
        }
    }

    /** A second, separately confirmed action: stop referencing the old workbook entirely. */
    fun forgetBackupSheet() {
        sheetsRepository.forgetWorkbook()
    }

    fun exportCsvToUri(monthId: String, uri: Uri) {
        viewModelScope.launch {
            _exportState.value = ExportState.Exporting
            try {
                withContext(Dispatchers.IO) {
                    val stream = context.contentResolver.openOutputStream(uri, "w")
                        ?: throw IllegalStateException("Couldn't open the picked file")
                    stream.use { csvExportService.export(monthId, it) }
                }
                _exportState.value = ExportState.Success
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // Spec §61: never expose raw technical errors to the user.
                _exportState.value = ExportState.Error("Couldn't export CSV. Your local data is safe.")
            }
        }
    }

    fun prepareCsvForSharing(monthId: String, fileName: String, onReady: (File) -> Unit) {
        viewModelScope.launch {
            try {
                onReady(csvExportService.exportToCacheFile(monthId, fileName))
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _exportState.value = ExportState.Error("Couldn't export CSV. Your local data is safe.")
            }
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
