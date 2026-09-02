package com.budgetpace.app.data.google.auth

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.budgetpace.app.core.security.securePrefs
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.api.services.sheets.v4.SheetsScopes
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

sealed class AuthorizationOutcome {
    object Authorized : AuthorizationOutcome()
    data class NeedsConsent(val pendingIntent: PendingIntent) : AuthorizationOutcome()
    data class Failed(val message: String) : AuthorizationOutcome()
}

/**
 * Spec §7: authorization ("can the app read/write the user's Sheet?") is a distinct step from
 * sign-in ("who is the user?"), using the Google Identity Services Authorization API — this is
 * the currently-documented way to request incremental OAuth scopes on Android outside of
 * Credential Manager, which only handles identity.
 *
 * Requests `drive.file` (spec §7's "prefer drive.file over full Drive access") together with the
 * Sheets scope, since drive.file alone is not always sufficient for Sheets API value reads/writes
 * on a spreadsheet this app created — both are needed for the create-then-edit workflow §51/§52
 * require.
 */
@Singleton
class GoogleAuthorizationManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private val REQUESTED_SCOPES = listOf(
            Scope("https://www.googleapis.com/auth/drive.file"),
            Scope(SheetsScopes.SPREADSHEETS),
        )
        private const val KEY_WAS_AUTHORIZED = "was_authorized"
    }

    // The access token itself is never persisted (it's short-lived) — only whether consent was
    // previously granted, so restoreIfNeeded() knows it's worth attempting a silent refresh
    // instead of leaving the UI stuck on "not connected" after every process restart even though
    // Google-side consent is still valid.
    private val prefs = securePrefs(context, "google_authorization")

    private val _isAuthorized = MutableStateFlow(false)
    val isAuthorized: StateFlow<Boolean> = _isAuthorized.asStateFlow()

    @Volatile
    private var cachedAccessToken: String? = null

    fun currentAccessToken(): String? = cachedAccessToken

    /** Call once at app startup. If consent was granted in a previous session, attempts a silent
     * refresh via [requestAuthorization] — per its own doc comment, that resolves with a fresh
     * token and no UI when consent already exists. Does nothing if never authorized before. */
    suspend fun restoreIfNeeded() {
        if (prefs.getBoolean(KEY_WAS_AUTHORIZED, false)) {
            requestAuthorization()
        }
    }

    /**
     * Call this to (re)authorize. If consent was already granted previously, this typically
     * resolves to [AuthorizationOutcome.Authorized] directly with a fresh token and no UI —
     * that also makes it the right call to refresh a token from a background worker. Only the
     * very first grant (or one that was revoked) surfaces [AuthorizationOutcome.NeedsConsent],
     * whose PendingIntent the caller must launch via
     * ActivityResultContracts.StartIntentSenderForResult and feed the result back into
     * [handleAuthorizationResult].
     */
    suspend fun requestAuthorization(): AuthorizationOutcome {
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(REQUESTED_SCOPES)
            .build()
        return try {
            val result = Identity.getAuthorizationClient(context).authorize(request).await()
            if (result.hasResolution()) {
                val pendingIntent = result.pendingIntent
                    ?: return AuthorizationOutcome.Failed("No consent screen available")
                AuthorizationOutcome.NeedsConsent(pendingIntent)
            } else {
                cachedAccessToken = result.accessToken
                _isAuthorized.value = true
                prefs.edit().putBoolean(KEY_WAS_AUTHORIZED, true).apply()
                AuthorizationOutcome.Authorized
            }
        } catch (e: Exception) {
            // Never shown raw to the user (spec §61) — but this is the only way to diagnose a
            // Google Cloud Console misconfiguration (wrong client type, SHA-1 not registered,
            // Sheets/Drive API not enabled, consent screen in Testing without this account added)
            // from a real device's logcat.
            Log.e("GoogleAuth", "requestAuthorization failed: ${e.javaClass.simpleName}: ${e.message}", e)
            AuthorizationOutcome.Failed(e.message ?: "Authorization failed")
        }
    }

    /** Feed the [Intent] from the launched consent PendingIntent's ActivityResult back in here. */
    fun handleAuthorizationResult(data: Intent?): AuthorizationOutcome {
        return try {
            val result = Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data)
            cachedAccessToken = result.accessToken
            _isAuthorized.value = true
            prefs.edit().putBoolean(KEY_WAS_AUTHORIZED, true).apply()
            AuthorizationOutcome.Authorized
        } catch (e: Exception) {
            Log.e("GoogleAuth", "handleAuthorizationResult failed: ${e.javaClass.simpleName}: ${e.message}", e)
            AuthorizationOutcome.Failed(e.message ?: "Authorization failed")
        }
    }

    fun clear() {
        cachedAccessToken = null
        _isAuthorized.value = false
        prefs.edit().putBoolean(KEY_WAS_AUTHORIZED, false).apply()
    }
}
