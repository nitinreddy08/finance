package com.budgetpace.app.data.google.sheets

import android.content.Context
import com.budgetpace.app.core.model.Transaction
import com.budgetpace.app.data.google.auth.GoogleAuthorizationManager
import com.budgetpace.app.data.local.dao.CategoryDao
import com.budgetpace.app.data.local.dao.DeletedTransactionDao
import com.budgetpace.app.domain.repository.BudgetRepository
import com.budgetpace.app.domain.repository.TransactionRepository
import com.google.api.client.http.HttpRequestInitializer
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.model.ClearValuesRequest
import com.google.api.services.sheets.v4.model.Sheet
import com.google.api.services.sheets.v4.model.SheetProperties
import com.google.api.services.sheets.v4.model.Spreadsheet
import com.google.api.services.sheets.v4.model.SpreadsheetProperties
import com.google.api.services.sheets.v4.model.ValueRange
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Spec §51/§52: creates the user's own workbook on first export and keeps its Transactions tab
 * in sync with PENDING local rows — append when the transaction's UUID isn't in the sheet yet,
 * otherwise update that row in place. This is a one-way, local-authoritative export (spec §60):
 * the app never reads changes back out of the Sheet.
 */
@Singleton
class GoogleSheetsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authorizationManager: GoogleAuthorizationManager,
    private val transactionRepository: TransactionRepository,
    private val categoryDao: CategoryDao,
    private val deletedTransactionDao: DeletedTransactionDao,
    private val budgetRepository: BudgetRepository,
) {
    private val prefs = context.getSharedPreferences("google_sheets_sync", Context.MODE_PRIVATE)

    private var spreadsheetId: String?
        get() = prefs.getString(KEY_SPREADSHEET_ID, null)
        set(value) = prefs.edit().putString(KEY_SPREADSHEET_ID, value).apply()

    /** Epoch millis of the last successful sync, for the Settings "Last backup" line (spec §54). */
    fun lastSyncAtMillis(): Long? = prefs.getLong(KEY_LAST_SYNC_AT, -1L).takeIf { it >= 0 }

    private fun sheetsClient(accessToken: String): Sheets {
        val requestInitializer = HttpRequestInitializer { request ->
            request.headers.authorization = "Bearer $accessToken"
        }
        return Sheets.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance(), requestInitializer)
            .setApplicationName("Budget Pace")
            .build()
    }

    /** Creates the workbook (spec §51's 4 tabs, each with its header row) if one isn't cached yet. */
    suspend fun ensureWorkbook(): Result<String> = withContext(Dispatchers.IO) {
        spreadsheetId?.let { return@withContext Result.success(it) }
        val token = authorizationManager.currentAccessToken()
            ?: return@withContext Result.failure(IllegalStateException("Google Sheets is not authorized yet"))

        try {
            val sheets = sheetsClient(token)
            val spreadsheet = Spreadsheet()
                .setProperties(SpreadsheetProperties().setTitle("Budget Pace"))
                .setSheets(
                    listOf("Dashboard", "Transactions", "Analytics", "Categories").map { tabTitle ->
                        Sheet().setProperties(SheetProperties().setTitle(tabTitle))
                    }
                )
            val created = sheets.spreadsheets().create(spreadsheet).execute()
            val id = created.spreadsheetId
                ?: return@withContext Result.failure(IllegalStateException("Sheets API returned no spreadsheet id"))

            sheets.spreadsheets().values()
                .update(id, "Transactions!A1", ValueRange().setValues(listOf(TRANSACTIONS_HEADER)))
                .setValueInputOption("RAW")
                .execute()
            sheets.spreadsheets().values()
                .update(id, "Categories!A1", ValueRange().setValues(listOf(CATEGORIES_HEADER)))
                .setValueInputOption("RAW")
                .execute()
            sheets.spreadsheets().values()
                .update(id, "Dashboard!A1", ValueRange().setValues(listOf(DASHBOARD_HEADER)))
                .setValueInputOption("RAW")
                .execute()
            sheets.spreadsheets().values()
                .update(id, "Analytics!A1", ValueRange().setValues(listOf(ANALYTICS_HEADER)))
                .setValueInputOption("RAW")
                .execute()

            spreadsheetId = id
            Result.success(id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Spec §52: upload every PENDING transaction (appending or updating by stable UUID), clear
     * out any deleted transaction's row, and refresh the Categories/Dashboard/Analytics tabs from
     * the current month's already-computed summary. Returns the count of transactions synced
     * (uploaded or updated) — deletions and the summary tabs aren't counted, since they're not
     * "changes waiting" in the same PENDING-count sense the caller shows the user.
     */
    suspend fun syncPendingTransactions(): Result<Int> = withContext(Dispatchers.IO) {
        val token = authorizationManager.currentAccessToken()
            ?: return@withContext Result.failure(IllegalStateException("Google Sheets is not authorized yet"))
        val id = spreadsheetId
            ?: return@withContext Result.failure(IllegalStateException("Workbook has not been created yet"))

        try {
            val sheets = sheetsClient(token)

            // Column A holds the transaction UUID; read it once — shared by both the
            // append-vs-update decision below and deletion processing.
            val existingRowByTxnId: MutableMap<String, Int> = sheets.spreadsheets().values()
                .get(id, "Transactions!A2:A")
                .execute()
                .getValues()
                ?.mapIndexedNotNull { index, row -> row.firstOrNull()?.toString()?.let { it to (index + 2) } }
                ?.toMap()
                ?.toMutableMap()
                ?: mutableMapOf()

            processPendingDeletions(sheets, id, existingRowByTxnId)

            val pending = transactionRepository.getPending()
            var syncedCount = 0
            if (pending.isNotEmpty()) {
                val toAppend = mutableListOf<Pair<Transaction, List<Any>>>()

                for (txn in pending) {
                    val row = rowFor(txn)
                    val existingRow = existingRowByTxnId[txn.id.toString()]
                    if (existingRow != null) {
                        sheets.spreadsheets().values()
                            .update(id, "Transactions!A$existingRow", ValueRange().setValues(listOf(row)))
                            .setValueInputOption("RAW")
                            .execute()
                        // Only mark SYNCED once the write actually succeeded.
                        transactionRepository.markSynced(txn.id.toString())
                        syncedCount++
                    } else {
                        toAppend += txn to row
                    }
                }

                if (toAppend.isNotEmpty()) {
                    sheets.spreadsheets().values()
                        .append(id, "Transactions!A:A", ValueRange().setValues(toAppend.map { it.second }))
                        .setValueInputOption("RAW")
                        .execute()
                    toAppend.forEach { (txn, _) -> transactionRepository.markSynced(txn.id.toString()) }
                    syncedCount += toAppend.size
                }
            }

            syncSummaryTabs(sheets, id)

            prefs.edit().putLong(KEY_LAST_SYNC_AT, System.currentTimeMillis()).apply()
            Result.success(syncedCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * A local hard-delete (Transaction Detail's Delete button) leaves a tombstone since the row
     * itself is gone and can no longer be looked up by UUID — this clears the matching row in the
     * Sheet (rather than reshuffling every row below it, which risks corrupting concurrent writes)
     * and then removes the tombstone once handled.
     */
    private suspend fun processPendingDeletions(sheets: Sheets, spreadsheetId: String, existingRowByTxnId: MutableMap<String, Int>) {
        val tombstones = deletedTransactionDao.getAll()
        for (tombstone in tombstones) {
            val row = existingRowByTxnId[tombstone.transactionId]
            if (row != null) {
                sheets.spreadsheets().values()
                    .clear(spreadsheetId, "Transactions!A$row:M$row", ClearValuesRequest())
                    .execute()
                existingRowByTxnId.remove(tombstone.transactionId)
            }
            // No matching row (never synced before it was deleted) is just as done as clearing one.
            deletedTransactionDao.deleteById(tombstone.transactionId)
        }
    }

    /** Refreshes Categories/Dashboard/Analytics from the current month's already-computed summary. */
    private suspend fun syncSummaryTabs(sheets: Sheets, spreadsheetId: String) {
        val summary = budgetRepository.observeActiveMonthSummary().first() ?: return

        sheets.spreadsheets().values().clear(spreadsheetId, "Categories!A2:Z1000", ClearValuesRequest()).execute()
        val categoryRows = summary.categories.map { cs ->
            val timeFormatter = DateTimeFormatter.ISO_INSTANT
            listOf(
                cs.category.id.toString(),
                cs.category.name,
                paiseToDecimalString(cs.category.monthlyBudgetMinor),
                cs.category.weeklyPacingEnabled.toString(),
                cs.category.active.toString(),
                timeFormatter.format(cs.category.createdAt),
                timeFormatter.format(cs.category.updatedAt),
            )
        }
        if (categoryRows.isNotEmpty()) {
            sheets.spreadsheets().values()
                .update(spreadsheetId, "Categories!A2", ValueRange().setValues(categoryRows))
                .setValueInputOption("RAW")
                .execute()
        }

        val pct = if (summary.totalBudgetMinor > 0) (summary.totalSpentMinor * 100 / summary.totalBudgetMinor) else 0
        sheets.spreadsheets().values().clear(spreadsheetId, "Dashboard!A2:Z1000", ClearValuesRequest()).execute()
        sheets.spreadsheets().values()
            .update(
                spreadsheetId, "Dashboard!A2",
                ValueRange().setValues(listOf(listOf(
                    "${summary.month.year}-${summary.month.month.toString().padStart(2, '0')}",
                    paiseToDecimalString(summary.totalBudgetMinor),
                    paiseToDecimalString(summary.totalSpentMinor),
                    paiseToDecimalString(summary.safeToSpendMinor),
                    "$pct%",
                )))
            )
            .setValueInputOption("RAW")
            .execute()

        sheets.spreadsheets().values().clear(spreadsheetId, "Analytics!A2:Z1000", ClearValuesRequest()).execute()
        val analyticsRows = summary.categories.map { cs ->
            val catPct = if (cs.category.monthlyBudgetMinor > 0) (cs.totalSpentMinor * 100 / cs.category.monthlyBudgetMinor) else 0
            listOf(
                cs.category.name,
                paiseToDecimalString(cs.category.monthlyBudgetMinor),
                paiseToDecimalString(cs.totalSpentMinor),
                "$catPct%",
                cs.overallStatus.name,
            )
        }
        if (analyticsRows.isNotEmpty()) {
            sheets.spreadsheets().values()
                .update(spreadsheetId, "Analytics!A2", ValueRange().setValues(analyticsRows))
                .setValueInputOption("RAW")
                .execute()
        }
    }

    private suspend fun rowFor(txn: Transaction): List<Any> {
        val categoryName = txn.categoryId?.let { categoryDao.getById(it.toString())?.name } ?: ""
        val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())
        return listOf(
            txn.id.toString(),
            txn.transactionDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
            txn.transactionDateTime?.let { timeFormatter.format(it) } ?: "",
            paiseToDecimalString(txn.amountMinor),
            txn.direction.name,
            categoryName,
            txn.bank.name,
            txn.accountSuffix ?: "",
            txn.recipient ?: txn.sender ?: "",
            txn.referenceNumber ?: "",
            txn.sourcePackage ?: "Manual",
            timeFormatter.format(txn.createdAt),
            timeFormatter.format(txn.updatedAt),
        )
    }

    private fun paiseToDecimalString(paise: Long): String =
        BigDecimal(paise).movePointLeft(2).toPlainString()

    companion object {
        private const val KEY_SPREADSHEET_ID = "spreadsheet_id"
        private const val KEY_LAST_SYNC_AT = "last_sync_at"
        private val TRANSACTIONS_HEADER = listOf(
            "Transaction ID", "Date", "Time", "Amount", "Direction", "Category",
            "Bank", "Account", "Recipient", "Reference", "Source", "Created At", "Updated At"
        )
        private val CATEGORIES_HEADER = listOf(
            "Category ID", "Name", "Monthly Budget", "Weekly Pacing", "Active", "Created At", "Updated At"
        )
        private val DASHBOARD_HEADER = listOf(
            "Month", "Total Budget", "Total Spent", "Safe To Spend", "% Used"
        )
        private val ANALYTICS_HEADER = listOf(
            "Category", "Budget", "Spent", "% Used", "Status"
        )
    }
}
