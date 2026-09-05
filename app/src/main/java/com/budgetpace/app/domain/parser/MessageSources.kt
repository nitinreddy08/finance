package com.budgetpace.app.domain.parser

/**
 * Identifies where a candidate bank message came from.
 *
 * Direct SMS receiving is the primary capture path (Android 15 redacts notification text for every
 * third-party listener), so [NotificationInput.packageName] is either the SMS pseudo-package
 * `"sms:<DLT header>"` or the real package of the messaging app whose notification the fallback
 * listener read.
 */
object MessageSources {

    const val GOOGLE_MESSAGES: String = "com.google.android.apps.messaging"

    const val SMS_PREFIX: String = "sms:"

    /** Used when the platform hands over an SMS with no originating address. */
    const val UNKNOWN_SENDER: String = "unknown"

    /** Builds the pseudo-package for a message received over the SMS broadcast. */
    fun sms(originatingAddress: String?): String {
        val trimmed = originatingAddress?.trim().orEmpty()
        return SMS_PREFIX + trimmed.ifEmpty { UNKNOWN_SENDER }
    }

    fun isSms(packageName: String?): Boolean =
        packageName != null && packageName.startsWith(SMS_PREFIX)

    /** True for any source a bank parser is willing to look at. */
    fun isSupported(packageName: String?): Boolean =
        packageName == GOOGLE_MESSAGES || isSms(packageName)
}
