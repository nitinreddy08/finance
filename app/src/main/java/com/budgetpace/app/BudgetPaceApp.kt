package com.budgetpace.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.budgetpace.app.notification.presenter.CategorizationNotificationManager
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class BudgetPaceApp : Application() {

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
