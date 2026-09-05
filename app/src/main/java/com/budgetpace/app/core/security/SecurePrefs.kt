package com.budgetpace.app.core.security

import android.content.Context
import android.content.SharedPreferences

/**
 * Small local preference files for state that is not secret: the signed-in email and display name,
 * whether Sheets consent was granted, the backup sheet's id and the last sync outcome.
 *
 * This used to be EncryptedSharedPreferences with a fall-back to plain SharedPreferences *under the
 * same file name*. That combination is worse than either half: once the Keystore entry behind the
 * master key is lost (a device restore, a lock-screen change, an OS upgrade) the fallback writes
 * plaintext into a file the next launch tries to decrypt, and every later read throws for the whole
 * life of the install. Nothing stored here is worth that risk — no access token or ID token is ever
 * persisted by this app; Play services holds those.
 *
 * The file names below are deliberately new: an existing install still has an encrypted file under
 * the old name that this code must never try to read.
 */
fun appPrefs(context: Context, fileName: String): SharedPreferences =
    context.getSharedPreferences(fileName, Context.MODE_PRIVATE)

/** Signed-in account identity (email, display name). */
const val PREFS_AUTH_SESSION: String = "auth_session_plain"

/** Whether the owner has granted Drive/Sheets consent, plus which account it was granted for. */
const val PREFS_GOOGLE_AUTHORIZATION: String = "google_authorization_plain"

/** Backup workbook id and the account that owns it. */
const val PREFS_GOOGLE_SHEETS: String = "google_sheets_sync"

/** Last sync attempt/success and the problem code behind a failure. */
const val PREFS_SYNC_STATUS: String = "sync_status"
