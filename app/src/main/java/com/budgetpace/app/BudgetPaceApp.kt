package com.budgetpace.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
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
