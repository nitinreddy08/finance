package com.budgetpace.app.domain.sync

import com.budgetpace.app.core.model.SyncState
import com.budgetpace.app.data.local.dao.CategoryDao
import com.budgetpace.app.data.local.dao.TransactionDao
import com.budgetpace.app.data.local.mapper.toDomain
import com.budgetpace.app.data.local.mapper.toEntity
import com.budgetpace.app.data.google.sheets.GoogleSheetsApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class CloudSyncRepository @Inject constructor(
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val sheetsApi: GoogleSheetsApi
) {
    // Spreadsheet ID would be stored in EncryptedSharedPreferences after initial creation
    private var cachedSpreadsheetId: String? = null

    suspend fun ensureWorkbook(): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (cachedSpreadsheetId == null) {
                val newId = sheetsApi.createWorkbook("Budget Pace Data")
                sheetsApi.setupTabs(newId)
                cachedSpreadsheetId = newId
            }
            Result.success(cachedSpreadsheetId!!)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun syncPendingChanges(): Result<Unit> = withContext(Dispatchers.IO) {
        val sheetId = cachedSpreadsheetId ?: return@withContext Result.failure(IllegalStateException("No workbook configured"))
        
        try {
            // 1. Fetch pending transactions from Room
            val pendingEntities = transactionDao.getPending()
            if (pendingEntities.isEmpty()) return@withContext Result.success(Unit)
            
            val pendingDomain = pendingEntities.map { it.toDomain() }
            
            // 2. Sync to Google Sheets
            val success = sheetsApi.syncPendingTransactions(sheetId, pendingDomain)
            
            // 3. Mark as SYNCED in Room (Spec §52)
            if (success) {
                pendingEntities.forEach { entity ->
                    transactionDao.update(entity.copy(syncState = SyncState.SYNCED.name))
                }
            } else {
                return@withContext Result.failure(Exception("Failed to update Google Sheet"))
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
