package com.budgetpace.app.feature.detection

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.budgetpace.app.domain.ingestion.DetectionStatus
import com.budgetpace.app.notification.listener.SmsNotificationListenerService

/**
 * Live reads of the five things Detection health cares about, plus the Intents its rows launch.
 * Nothing here is cached — every value is re-read from the platform each time, which is what
 * makes [com.budgetpace.app.feature.detection.DetectionSetupList]'s LifecycleResumeEffect refresh
 * correctly the moment the owner comes back from a system Settings screen.
 */
object DetectionStatusChecker {

    fun currentStatus(context: Context): DetectionStatus = DetectionStatus(
        smsPermissionGranted = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.RECEIVE_SMS,
        ) == PackageManager.PERMISSION_GRANTED,
        notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled(),
        listenerEnabled = NotificationManagerCompat.getEnabledListenerPackages(context)
            .contains(context.packageName),
        batteryUnrestricted = ContextCompat.getSystemService(context, PowerManager::class.java)
            ?.isIgnoringBatteryOptimizations(context.packageName)
            ?: false,
    )

    /** Row 5 is offered on these OEMs regardless of the battery toggle — their own background
     * killers ignore the standard "ignore battery optimizations" grant. */
    fun hasKnownBackgroundQuirks(): Boolean =
        Build.MANUFACTURER.lowercase() in setOf("oneplus", "oppo", "realme")

    fun appInfoIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }

    /** Row 2 (API 33+ only) — the runtime POST_NOTIFICATIONS permission string. */
    val postNotificationsPermission: String?
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            android.Manifest.permission.POST_NOTIFICATIONS
        } else {
            null
        }

    /** Below API 33 there is no runtime permission for notifications at all — the only way to
     * change them is the app's own notification settings page. */
    fun appNotificationSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }

    /**
     * Row 3 — opens the per-app notification listener detail screen (API 30+), which is the only
     * one of the five system screens that lands directly on Budget Pace's own toggle rather than a
     * plain list the owner has to search. Falls back to the plain list on older APIs, and the
     * caller wraps the actual startActivity in try/catch in case a given OEM's Settings app
     * doesn't implement the detail variant despite advertising API 30+.
     */
    fun notificationListenerSettingsIntent(context: Context): Intent {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val component = ComponentName(context, SmsNotificationListenerService::class.java)
            return Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).apply {
                putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component.flattenToString())
            }
        }
        return Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
    }

    fun notificationListenerSettingsFallbackIntent(): Intent =
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)

    /** Row 4 — shows the system's own "allow unrestricted" confirmation directly. */
    fun batteryOptimizationIntent(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }

    /**
     * Row 5 — known OEM autostart-manager screens, filtered to ones this device can actually
     * resolve (package visibility per the manifest's <queries>). Never more than a best-effort
     * list: the caller still wraps startActivity in try/catch and falls back to App info when
     * none of these resolve, or resolution itself throws.
     */
    fun autostartIntents(context: Context): List<Intent> {
        val candidates = listOf(
            ComponentName(
                "com.oneplus.security",
                "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity",
            ),
            ComponentName(
                "com.coloros.safecenter",
                "com.coloros.safecenter.permission.startup.StartupAppListActivity",
            ),
            ComponentName(
                "com.coloros.safecenter",
                "com.coloros.safecenter.startupapp.StartupAppListActivity",
            ),
        )
        return candidates.mapNotNull { component ->
            val intent = Intent().apply {
                this.component = component
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val resolved = runCatching { intent.resolveActivity(context.packageManager) }.getOrNull()
            if (resolved != null) intent else null
        }
    }
}
