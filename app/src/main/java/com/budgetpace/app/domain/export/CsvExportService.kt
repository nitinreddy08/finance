package com.budgetpace.app.domain.export

import android.content.Context
import com.budgetpace.app.core.model.Transaction
import com.budgetpace.app.data.local.dao.CategoryDao
import com.budgetpace.app.data.local.dao.TransactionDao
import com.budgetpace.app.data.local.mapper.toDomain
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * Spec §55: CSV export works independently of Google authorization — everything here reads only
 * from the local database, never Google Sheets.
 */
class CsvExportService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
) {
    /**
     * Writes [monthId]'s recorded expenses as CSV directly into [out]. Does not close [out] — the
     * caller owns whatever stream it opened (a SAF document, a cache file) and closes it once this
     * returns.
     */
    suspend fun export(monthId: String, out: OutputStream): Unit = withContext(Dispatchers.IO) {
        val rows = transactionDao.getRecordedByMonth(monthId).map { it.toDomain().toCsvRow() }
        val writer = OutputStreamWriter(out, StandardCharsets.UTF_8)
        ExpenseCsv.write(rows, writer)
        writer.flush()
    }

    /**
     * Writes the same export into a fresh file under `cacheDir/exports/` — the path
     * `res/xml/file_paths.xml` already grants the FileProvider — so the caller can hand it to
     * another app via `ACTION_SEND` without needing storage permission.
     */
    suspend fun exportToCacheFile(monthId: String, fileName: String): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, fileName)
        FileOutputStream(file).use { out -> export(monthId, out) }
        file
    }

    private suspend fun Transaction.toCsvRow(): CsvExpenseRow {
        val categoryName = categoryId?.let { categoryDao.getById(it.toString())?.name } ?: ""
        val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())
        return CsvExpenseRow(
            expenseId = id.toString(),
            date = transactionDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
            time = transactionDateTime?.let { timeFormatter.format(it) } ?: "",
            amountMinor = amountMinor,
            direction = direction.name,
            category = categoryName,
            bank = bank.name,
            account = accountSuffix ?: "",
            recipient = recipient ?: sender ?: "",
            reference = referenceNumber ?: "",
            source = sourcePackage ?: "Manual",
            createdAt = DateTimeFormatter.ISO_INSTANT.format(createdAt),
            updatedAt = DateTimeFormatter.ISO_INSTANT.format(updatedAt),
        )
    }
}
