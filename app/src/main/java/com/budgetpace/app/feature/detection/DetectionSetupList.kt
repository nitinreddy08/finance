package com.budgetpace.app.feature.detection

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.budgetpace.app.core.designsystem.components.SettingsRow
import com.budgetpace.app.core.designsystem.theme.bpColors
import com.budgetpace.app.domain.ingestion.DetectionStatus

private const val SETUP_PREFS = "detection_setup_ui"
private const val KEY_SMS_REQUESTED_ONCE = "sms_permission_requested_once"

/**
 * The five permission rows Detection health is built around (spec's Detection health section).
 * Each row shows the platform's own live state — read fresh on every resume via
 * [LifecycleResumeEffect], since every one of these is only ever changed in a system Settings
 * screen the owner navigates away to and back from.
 */
@Composable
fun DetectionSetupList(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val setupPrefs = remember { context.applicationContext.getSharedPreferences(SETUP_PREFS, Context.MODE_PRIVATE) }

    var status by remember { mutableStateOf(DetectionStatusChecker.currentStatus(context)) }
    fun refresh() {
        status = DetectionStatusChecker.currentStatus(context)
    }

    LifecycleResumeEffect(Unit) {
        refresh()
        onPauseOrDispose {}
    }

    val smsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refresh() }
    val notificationsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refresh() }

    Column(modifier = modifier) {
        SmsRow(
            status = status,
            setupPrefs = setupPrefs,
            onRequest = { smsPermissionLauncher.launch(android.Manifest.permission.RECEIVE_SMS) },
        )
        ExpenseAlertsRow(
            status = status,
            onRequest = {
                val permission = DetectionStatusChecker.postNotificationsPermission
                if (permission != null) {
                    notificationsPermissionLauncher.launch(permission)
                } else {
                    runCatching { context.startActivity(DetectionStatusChecker.appNotificationSettingsIntent(context)) }
                }
            },
        )
        NotificationAccessRow(status = status)
        BatteryRow(status = status)
        if (DetectionStatusChecker.hasKnownBackgroundQuirks()) {
            BackgroundActivityRow()
        }
    }
}

@Composable
private fun SmsRow(
    status: DetectionStatus,
    setupPrefs: SharedPreferences,
    onRequest: () -> Unit,
) {
    val context = LocalContext.current
    SettingsRow(
        icon = Icons.Outlined.Sms,
        title = "Read bank SMS",
        subtitle = if (!status.smsPermissionGranted) {
            "Android's dialog mentions sending SMS - Budget Pace never sends messages."
        } else {
            null
        },
        trailingText = if (status.smsPermissionGranted) "Granted" else "Not granted",
        trailingTextColor = if (status.smsPermissionGranted) MaterialTheme.bpColors.statusGreen else MaterialTheme.bpColors.statusOrange,
        onClick = if (status.smsPermissionGranted) null else {
            {
                val activity = context.findActivity()
                val requestedBefore = setupPrefs.getBoolean(KEY_SMS_REQUESTED_ONCE, false)
                val permanentlyDenied = activity != null && requestedBefore &&
                    !activity.shouldShowRequestPermissionRationale(android.Manifest.permission.RECEIVE_SMS)
                if (permanentlyDenied) {
                    runCatching { context.startActivity(DetectionStatusChecker.appInfoIntent(context)) }
                } else {
                    setupPrefs.edit().putBoolean(KEY_SMS_REQUESTED_ONCE, true).apply()
                    onRequest()
                }
            }
        },
    )
}

@Composable
private fun ExpenseAlertsRow(status: DetectionStatus, onRequest: () -> Unit) {
    SettingsRow(
        icon = Icons.Outlined.NotificationsActive,
        title = "Expense alerts",
        subtitle = "Lets a categorization prompt appear right after an expense is recorded.",
        trailingText = if (status.notificationsEnabled) "On" else "Off",
        trailingTextColor = if (status.notificationsEnabled) MaterialTheme.bpColors.statusGreen else MaterialTheme.bpColors.statusOrange,
        onClick = if (status.notificationsEnabled) null else onRequest,
    )
}

@Composable
private fun NotificationAccessRow(status: DetectionStatus) {
    val context = LocalContext.current
    SettingsRow(
        icon = Icons.Outlined.Notifications,
        title = "Notification access (backup)",
        subtitle = "A fallback capture path for a bank message the direct path misses.",
        trailingText = if (status.listenerEnabled) "On" else "Off",
        trailingTextColor = if (status.listenerEnabled) MaterialTheme.bpColors.statusGreen else MaterialTheme.bpColors.statusOrange,
        onClick = if (status.listenerEnabled) null else {
            {
                try {
                    context.startActivity(DetectionStatusChecker.notificationListenerSettingsIntent(context))
                } catch (e: ActivityNotFoundException) {
                    runCatching {
                        context.startActivity(DetectionStatusChecker.notificationListenerSettingsFallbackIntent())
                    }
                }
            }
        },
    )
}

@Composable
private fun BatteryRow(status: DetectionStatus) {
    val context = LocalContext.current
    SettingsRow(
        icon = Icons.Outlined.BatteryChargingFull,
        title = "Unrestricted battery",
        subtitle = "Stops Android from pausing bank SMS detection to save power.",
        trailingText = if (status.batteryUnrestricted) "On" else "Off",
        trailingTextColor = if (status.batteryUnrestricted) MaterialTheme.bpColors.statusGreen else MaterialTheme.bpColors.statusOrange,
        onClick = if (status.batteryUnrestricted) null else {
            { runCatching { context.startActivity(DetectionStatusChecker.batteryOptimizationIntent(context)) } }
        },
    )
}

/**
 * Never shows Granted/Not granted — there is no reliable API to read a OnePlus/Oppo/Realme
 * autostart grant, so this only offers navigation and never claims a state (spec requirement).
 */
@Composable
private fun BackgroundActivityRow() {
    val context = LocalContext.current
    SettingsRow(
        icon = Icons.Outlined.Settings,
        title = "Allow auto-launch",
        subtitle = "In Settings > Apps > Budget Pace > Battery usage choose Unrestricted, and turn " +
            "on both \"Allow background activity\" and \"Allow auto-launch\". Without auto-launch, " +
            "SMS received while Budget Pace is closed may be missed.",
        onClick = {
            val candidates = DetectionStatusChecker.autostartIntents(context)
            val opened = candidates.any { intent ->
                try {
                    context.startActivity(intent)
                    true
                } catch (e: ActivityNotFoundException) {
                    false
                }
            }
            if (!opened) {
                runCatching { context.startActivity(DetectionStatusChecker.appInfoIntent(context)) }
            }
        },
    )
}

private fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}
