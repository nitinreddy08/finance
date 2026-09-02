package com.budgetpace.app.feature.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.budgetpace.app.data.sync.GoogleSheetsSyncWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor() : ViewModel() {

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
}
