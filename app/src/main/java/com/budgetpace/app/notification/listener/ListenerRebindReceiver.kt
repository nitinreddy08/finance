package com.budgetpace.app.notification.listener

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.util.Log

/**
 * A notification listener is not always rebound automatically after an app update, and never
 * after a reboot until something asks for it. Declared in the manifest (not registered at
 * runtime) so BOOT_COMPLETED reaches it even though the app was never opened after the reboot.
 */
class ListenerRebindReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        try {
            NotificationListenerService.requestRebind(
                ComponentName(context, SmsNotificationListenerService::class.java)
            )
        } catch (error: Throwable) {
            Log.e("ListenerRebind", "requestRebind failed", error)
        }
    }
}
