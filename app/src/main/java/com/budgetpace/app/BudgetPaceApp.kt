package com.budgetpace.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkManager
import com.budgetpace.app.notification.presenter.CategorizationNotificationManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class BudgetPaceApp : Application(), Configuration.Provider {

    // Without this, WorkManager tries to instantiate @HiltWorker workers (e.g.
    // GoogleSheetsSyncWorker) via its default no-arg reflection path and crashes at runtime,
    // since a Hilt-generated worker has no such constructor.
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        // WorkManager's normal auto-init (a ContentProvider) runs before this onCreate — before
        // Hilt has injected workerFactory above — and is disabled in the manifest for exactly
        // that reason. Initialize it manually here instead, now that workerFactory is set.
        WorkManager.initialize(this, workManagerConfiguration)
        createCategorizationChannel()
    }

    private fun createCategorizationChannel() {
        val channel = NotificationChannel(
            CategorizationNotificationManager.CHANNEL_ID,
            "Transaction categorization",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Prompts to categorize a detected bank transaction (spec §21)."
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }
}
