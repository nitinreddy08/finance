package com.budgetpace.app.data.google.sheets

import android.content.Context
import androidx.core.content.edit
import com.budgetpace.app.core.model.CategorySummary
import com.budgetpace.app.core.model.MonthSummary
import com.budgetpace.app.core.model.Transaction
import com.budgetpace.app.core.security.PREFS_GOOGLE_SHEETS
import com.budgetpace.app.core.security.appPrefs
import com.budgetpace.app.data.local.dao.CategoryDao
import com.budgetpace.app.domain.export.ExpenseCsv
import com.budgetpace.app.domain.sync.SyncPlan
import com.google.api.client.http.HttpRequestInitializer
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.model.AddSheetRequest
import com.google.api.services.sheets.v4.model.BatchClearValuesRequest
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest
import com.google.api.services.sheets.v4.model.BatchUpdateValuesRequest
import com.google.api.services.sheets.v4.model.GridProperties
import com.google.api.services.sheets.v4.model.Request
import com.google.api.services.sheets.v4.model.Sheet
import com.google.api.services.sheets.v4.model.SheetProperties
import com.google.api.services.sheets.v4.model.Spreadsheet
import com.google.api.services.sheets.v4.model.SpreadsheetProperties
import com.google.api.services.sheets.v4.model.ValueRange
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Spec §51/§52's workbook: a thin, orchestration-free wrapper over the Sheets HTTP API.
 *
 * This class only knows how to talk to Google once told exactly what to send — which token to
 * use, whether a workbook needs creating, what a sync pass's [SyncPlan] contains. Deciding those
 * things (token refresh, consent, retry, error classification, the single-flight guarantee) is
 * [com.budgetpace.app.data.sync.SheetsSyncCoordinator]'s job, not this one's, so that orchestration
 * can be tested without a real Sheets connection.
 *
 * Every multi-row write goes through exactly one HTTP call — `values().batchUpdate` for in-place
 * edits, one `append` for new rows, one `values().batchClear` for removals — because Google's
 * Sheets quota is 60 write requests per user per minute; a request per row made every sync flaky
 * once the owner had more than a handful of pending expenses.
 */
@Singleton
class GoogleSheetsRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val categoryDao: CategoryDao,
) {
    private val prefs = appPrefs(context, PREFS_GOOGLE_SHEETS)

    /** The cached workbook id, or null before the very first sync ever creates one. */
    var spreadsheetId: String?
        get() = prefs.getString(KEY_SPREADSHEET_ID, null)
        private set(value) = prefs.edit {
            if (value != null) putString(KEY_SPREADSHEET_ID, value) else remove(KEY_SPREADSHEET_ID)
        }

    /** The signed-in account [spreadsheetId] was created for — how an account switch is caught. */
    var spreadsheetOwnerEmail: String?
        get() = prefs.getString(KEY_OWNER_EMAIL, null)
        private set(value) = prefs.edit {
            if (value != null) putString(KEY_OWNER_EMAIL, value) else remove(KEY_OWNER_EMAIL)
        }

    /** "Forget backup sheet": stops referencing the old workbook without touching it on Google. */
    fun forgetWorkbook() {
        spreadsheetId = null
        spreadsheetOwnerEmail = null
    }

    private fun sheetsClient(accessToken: String): Sheets {
        val requestInitializer = HttpRequestInitializer { request ->
            request.headers.authorization = "Bearer $accessToken"
        }
        return Sheets.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance(), requestInitializer)
            .setApplicationName("Budget Pace")
            .build()
    }

    /** Creates a brand-new workbook (4 tabs, each with a frozen header row) and caches its id. */
    suspend fun createWorkbook(accessToken: String, ownerEmail: String?): String = withContext(Dispatchers.IO) {
        val sheets = sheetsClient(accessToken)
        val tabTitles = listOf(EXPENSES_TAB, "Categories", "Dashboard", "Analytics")
        val spreadsheet = Spreadsheet()
            .setProperties(SpreadsheetProperties().setTitle("Budget Pace"))
            .setSheets(tabTitles.map { title -> Sheet().setProperties(frozenHeaderProperties(title)) })

        val created = sheets.spreadsheets().create(spreadsheet).execute()
        val id = created.spreadsheetId
            ?: error("Sheets API returned no spreadsheet id")

        val headerWrites = listOf(
            ValueRange().setRange("$EXPENSES_TAB!A1").setValues(listOf(EXPENSES_HEADER)),
            ValueRange().setRange("Categories!A1").setValues(listOf(CATEGORIES_HEADER)),
            ValueRange().setRange("Dashboard!A1").setValues(listOf(DASHBOARD_HEADER)),
            ValueRange().setRange("Analytics!A1").setValues(listOf(ANALYTICS_HEADER)),
        )
        sheets.spreadsheets().values()
            .batchUpdate(id, BatchUpdateValuesRequest().setValueInputOption("RAW").setData(headerWrites))
            .execute()

        spreadsheetId = id
        spreadsheetOwnerEmail = ownerEmail
        id
    }

    /**
     * Confirms the cached workbook is still reachable and still has an Expenses tab, re-adding
     * the tab (with its header row) if it was renamed or deleted out from under the app — rather
     * than letting every later range call fail with "Unable to parse range" (HTTP 400).
     *
     * Throws on a workbook that is gone or no longer shared with this account (404/403); the
     * caller classifies that into [com.budgetpace.app.domain.sync.SyncProblem.SheetUnavailable]
     * and offers "Start a new sheet" rather than silently creating one.
     */
    suspend fun verifyWorkbook(accessToken: String, spreadsheetId: String) = withContext(Dispatchers.IO) {
        val sheets = sheetsClient(accessToken)
        val spreadsheet = sheets.spreadsheets().get(spreadsheetId)
            .setFields("spreadsheetId,sheets.properties(sheetId,title)")
            .execute()
        val hasExpensesTab = spreadsheet.sheets.orEmpty().any { it.properties?.title == EXPENSES_TAB }
        if (!hasExpensesTab) {
            sheets.spreadsheets().batchUpdate(
                spreadsheetId,
                BatchUpdateSpreadsheetRequest().setRequests(
                    listOf(Request().setAddSheet(AddSheetRequest().setProperties(frozenHeaderProperties(EXPENSES_TAB))))
                )
            ).execute()
            sheets.spreadsheets().values()
                .update(spreadsheetId, "$EXPENSES_TAB!A1", ValueRange().setValues(listOf(EXPENSES_HEADER)))
                .setValueInputOption("RAW")
                .execute()
        }
        Unit
    }

    private fun frozenHeaderProperties(title: String): SheetProperties =
        SheetProperties().setTitle(title).setGridProperties(GridProperties().setFrozenRowCount(1))

    /** Column A of the Expenses tab (the expense UUID) to the 1-based row it already occupies. */
    suspend fun fetchExpenseRowIndex(accessToken: String, spreadsheetId: String): Map<String, Int> =
        withContext(Dispatchers.IO) {
            sheetsClient(accessToken).spreadsheets().values()
                .get(spreadsheetId, "$EXPENSES_TAB!A2:A")
                .execute()
                .getValues()
                ?.mapIndexedNotNull { index, row ->
                    row.firstOrNull()?.toString()?.takeIf { it.isNotBlank() }?.let { it to (index + 2) }
                }
                ?.toMap()
                ?: emptyMap()
        }

    /** Applies a whole [SyncPlan] in at most three HTTP calls: one update, one append, one clear. */
    suspend fun applyPlan(accessToken: String, spreadsheetId: String, plan: SyncPlan) = withContext(Dispatchers.IO) {
        val sheets = sheetsClient(accessToken)

        if (plan.updates.isNotEmpty()) {
            val data = plan.updates.map { (row, txn) ->
                ValueRange().setRange("$EXPENSES_TAB!A$row").setValues(listOf(rowFor(txn)))
            }
            sheets.spreadsheets().values()
                .batchUpdate(spreadsheetId, BatchUpdateValuesRequest().setValueInputOption("RAW").setData(data))
                .execute()
        }

        if (plan.appends.isNotEmpty()) {
            val rows = plan.appends.map { rowFor(it) }
            sheets.spreadsheets().values()
                .append(spreadsheetId, "$EXPENSES_TAB!A:A", ValueRange().setValues(rows))
                .setValueInputOption("RAW")
                .execute()
        }

        if (plan.clearRows.isNotEmpty()) {
            val ranges = plan.clearRows.map { row -> "$EXPENSES_TAB!A$row:M$row" }
            sheets.spreadsheets().values()
                .batchClear(spreadsheetId, BatchClearValuesRequest().setRanges(ranges))
                .execute()
        }
        Unit
    }

    /**
     * Refreshes Dashboard/Categories/Analytics from the month's already-computed summary.
     * Dashboard and Categories are month/category-keyed upserts (one row per month, one row per
     * category, matched by the key already in column A) so a month rolling to ARCHIVED does not
     * erase its own history the next time a *different* month syncs — only Analytics (a purely
     * derived, always-current view of the active month's categories) is fully rewritten each pass.
     * Callers must treat a failure here as non-fatal: rows already synced by [applyPlan] must not
     * be undone by a summary-tab hiccup.
     */
    suspend fun syncSummaryTabs(accessToken: String, spreadsheetId: String, summary: MonthSummary) =
        withContext(Dispatchers.IO) {
            val sheets = sheetsClient(accessToken)
            upsertDashboardRow(sheets, spreadsheetId, summary)
            upsertCategoryRows(sheets, spreadsheetId, summary)
            refreshAnalyticsTab(sheets, spreadsheetId, summary)
            Unit
        }

    private fun monthKey(summary: MonthSummary): String =
        "${summary.month.year}-${summary.month.month.toString().padStart(2, '0')}"

    /** Column A of a tab to its 1-based row, read once. Shared by the Dashboard/Categories upserts. */
    private fun keyedRowIndex(sheets: Sheets, spreadsheetId: String, range: String): Map<String, Int> =
        sheets.spreadsheets().values()
            .get(spreadsheetId, range)
            .execute()
            .getValues()
            ?.mapIndexedNotNull { index, row ->
                row.firstOrNull()?.toString()?.takeIf { it.isNotBlank() }?.let { it to (index + 2) }
            }
            ?.toMap()
            ?: emptyMap()

    private fun upsertDashboardRow(sheets: Sheets, spreadsheetId: String, summary: MonthSummary) {
        val key = monthKey(summary)
        val existingRow = keyedRowIndex(sheets, spreadsheetId, "Dashboard!A2:A")[key]
        val pct = if (summary.totalBudgetMinor > 0) (summary.totalSpentMinor * 100 / summary.totalBudgetMinor) else 0
        val row = listOf(
            key,
            paiseToAmount(summary.totalBudgetMinor),
            paiseToAmount(summary.totalSpentMinor),
            paiseToAmount(summary.safeToSpendMinor),
            "$pct%",
        )
        if (existingRow != null) {
            sheets.spreadsheets().values()
                .update(spreadsheetId, "Dashboard!A$existingRow", ValueRange().setValues(listOf(row)))
                .setValueInputOption("RAW")
                .execute()
        } else {
            sheets.spreadsheets().values()
                .append(spreadsheetId, "Dashboard!A:A", ValueRange().setValues(listOf(row)))
                .setValueInputOption("RAW")
                .execute()
        }
    }

    private fun upsertCategoryRows(sheets: Sheets, spreadsheetId: String, summary: MonthSummary) {
        val existingRowById = keyedRowIndex(sheets, spreadsheetId, "Categories!A2:A")
        val updates = mutableListOf<ValueRange>()
        val appends = mutableListOf<List<Any>>()
        for (cs in summary.categories) {
            val row = categoryRow(cs)
            val existingRow = existingRowById[cs.category.id.toString()]
            if (existingRow != null) {
                updates += ValueRange().setRange("Categories!A$existingRow").setValues(listOf(row))
            } else {
                appends += row
            }
        }
        if (updates.isNotEmpty()) {
            sheets.spreadsheets().values()
                .batchUpdate(spreadsheetId, BatchUpdateValuesRequest().setValueInputOption("RAW").setData(updates))
                .execute()
        }
        if (appends.isNotEmpty()) {
            sheets.spreadsheets().values()
                .append(spreadsheetId, "Categories!A:A", ValueRange().setValues(appends))
                .setValueInputOption("RAW")
                .execute()
        }
    }

    private fun refreshAnalyticsTab(sheets: Sheets, spreadsheetId: String, summary: MonthSummary) {
        sheets.spreadsheets().values()
            .batchClear(spreadsheetId, BatchClearValuesRequest().setRanges(listOf("Analytics!A2:Z1000")))
            .execute()
        val rows = summary.categories.map(::analyticsRow)
        if (rows.isNotEmpty()) {
            sheets.spreadsheets().values()
                .update(spreadsheetId, "Analytics!A2", ValueRange().setValues(rows))
                .setValueInputOption("RAW")
                .execute()
        }
    }

    private fun categoryRow(cs: CategorySummary): List<Any> = listOf(
        cs.category.id.toString(),
        cs.category.name,
        paiseToAmount(cs.category.monthlyBudgetMinor),
        cs.category.periodCount.toDouble(),
        cs.category.active,
        ISO_INSTANT.format(cs.category.createdAt),
        ISO_INSTANT.format(cs.category.updatedAt),
    )

    private fun analyticsRow(cs: CategorySummary): List<Any> {
        val catPct = if (cs.category.monthlyBudgetMinor > 0) (cs.totalSpentMinor * 100 / cs.category.monthlyBudgetMinor) else 0
        return listOf(
            cs.category.name,
            paiseToAmount(cs.category.monthlyBudgetMinor),
            paiseToAmount(cs.totalSpentMinor),
            "$catPct%",
            cs.overallStatus.name,
        )
    }

    /** Column order matches [ExpenseCsv.EXPENSE_COLUMNS] exactly so the two exports cannot drift. */
    private suspend fun rowFor(txn: Transaction): List<Any> {
        val categoryName = txn.categoryId?.let { categoryDao.getById(it.toString())?.name } ?: ""
        val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())
        return listOf(
            txn.id.toString(),
            txn.transactionDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
            txn.transactionDateTime?.let { timeFormatter.format(it) } ?: "",
            paiseToAmount(txn.amountMinor),
            txn.direction.name,
            categoryName,
            txn.bank.name,
            txn.accountSuffix ?: "",
            txn.recipient ?: txn.sender ?: "",
            txn.referenceNumber ?: "",
            txn.sourcePackage ?: "Manual",
            ISO_INSTANT.format(txn.createdAt),
            ISO_INSTANT.format(txn.updatedAt),
        )
    }

    /** A plain [Double], not a formatted string: Sheets only sums a cell typed as a number. */
    private fun paiseToAmount(paise: Long): Double = BigDecimal(paise).movePointLeft(2).toDouble()

    companion object {
        private val ISO_INSTANT = DateTimeFormatter.ISO_INSTANT
        private const val KEY_SPREADSHEET_ID = "spreadsheet_id"
        private const val KEY_OWNER_EMAIL = "spreadsheet_owner_email"
        const val EXPENSES_TAB = "Expenses"

        val EXPENSES_HEADER: List<String> = ExpenseCsv.EXPENSE_COLUMNS
        private val CATEGORIES_HEADER = listOf(
            "Category ID", "Name", "Monthly Budget", "Period Count", "Active", "Created At", "Updated At"
        )
        private val DASHBOARD_HEADER = listOf(
            "Month", "Total Budget", "Total Spent", "Safe To Spend", "% Used"
        )
        private val ANALYTICS_HEADER = listOf(
            "Category", "Budget", "Spent", "% Used", "Status"
        )
    }
}
