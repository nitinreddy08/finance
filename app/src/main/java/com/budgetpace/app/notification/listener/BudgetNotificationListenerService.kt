package com.budgetpace.app.notification.listener

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.budgetpace.app.domain.parser.NotificationInput
import com.budgetpace.app.domain.parser.ParserCoordinator
import java.time.Instant

class BudgetNotificationListenerService : NotificationListenerService() {

    private val parserCoordinator = ParserCoordinator()
    
    // We only care about Google Messages per spec §12
    private val GOOGLE_MESSAGES_PACKAGE = "com.google.android.apps.messaging"

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d("BudgetPace", "NotificationListenerService Connected")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.d("BudgetPace", "NotificationListenerService Disconnected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        
        // Spec §12: V1 should process notifications from Google Messages
        if (sbn.packageName != GOOGLE_MESSAGES_PACKAGE) return
        
        val extras = sbn.notification.extras
        val title = extras.getString(android.app.Notification.EXTRA_TITLE) ?: ""
        var text = extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString() ?: ""
        
        // Check for big text layout which often contains the full SMS
        val bigText = extras.getCharSequence(android.app.Notification.EXTRA_BIG_TEXT)?.toString()
        if (!bigText.isNullOrBlank()) {
            text = bigText
        }
        
        if (text.isBlank()) return
        
        val input = NotificationInput(
            packageName = sbn.packageName,
            title = title,
            text = text,
            receivedAt = Instant.now()
        )
        
        // Pass to parser coordinator
        // In a real implementation this would be dispatched to a background coroutine/WorkManager 
        // to interact with the Room DB and post the categorization UI.
        val parsedTxn = parserCoordinator.parse(input)
        
        if (parsedTxn != null) {
            Log.d("BudgetPace", "Parsed transaction: $parsedTxn")
            // Here we would trigger the DuplicateDetector, save to Room, and fire CategorizationNotificationManager
        }
    }
}
