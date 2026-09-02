package com.budgetpace.app.data.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.budgetpace.app.domain.repository.TransactionRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.format.DateTimeFormatter

@HiltWorker
class GoogleSheetsSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val transactionRepository: TransactionRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Log.d("GoogleSheetsSyncWorker", "Starting sync to Google Sheets")
        
        return try {
            // 1. Fetch transactions
            // Passing empty string for phase testing
            val transactions = transactionRepository.observeByMonth("").first()
            
            if (transactions.isEmpty()) {
                Log.d("GoogleSheetsSyncWorker", "No transactions to sync")
                return Result.success()
            }

            // 2. Prepare data for Sheets API (mocking the actual HTTP call for now)
            val sheetData = transactions.map { txn ->
                listOf(
                    txn.transactionDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
                    txn.direction.name,
                    (txn.amountMinor / 100.0).toString(),
                    txn.recipient ?: txn.sender ?: "Unknown",
                    txn.bank.name
                )
            }
            
            // 3. TODO: Use Google API Client to write `sheetData` to a Sheet
            // val credential = GoogleCredential().setAccessToken(...)
            // val sheetsService = Sheets.Builder(NetHttpTransport(), GsonFactory(), credential).build()
            // sheetsService.spreadsheets().values().append(...)
            
            Log.d("GoogleSheetsSyncWorker", "Successfully synced ${transactions.size} transactions to Sheets (Mock)")
            
            Result.success()
        } catch (e: Exception) {
            Log.e("GoogleSheetsSyncWorker", "Sync failed", e)
            Result.retry()
        }
    }
}
