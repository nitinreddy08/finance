package com.budgetpace.app.data.google.auth

import android.accounts.Account
import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.edit
import com.budgetpace.app.core.security.PREFS_GOOGLE_AUTHORIZATION
import com.budgetpace.app.core.security.appPrefs
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.RevokeAccessRequest
import com.google.android.gms.common.api.Scope
import com.google.api.services.sheets.v4.SheetsScopes
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/** What one attempt to get a usable Sheets access token produced. */
sealed interface TokenResult {

    data class Ok(val accessToken: String) : TokenResult

    /** The consent sheet must be shown; launch this with `StartIntentSenderForResult`. */
    data class NeedsConsent(val pendingIntent: PendingIntent) : TokenResult

    /** The owner backed out of the consent sheet. Never an error, never shown. */
    data object Cancelled : TokenResult

    data class Failed(val cause: Throwable?) : TokenResult
}

/**
 * Spec section 7: authorization ("may the app write the owner's sheet?") is a separate step from
 * sign-in ("who is the owner?"), using the Google Identity Services Authorization API.
 *
 * Two facts are kept deliberately apart, because conflating them is what made the backup screen lie
 * offline:
 *
 * - **Consent** is durable and is persisted. [hasConsent] is seeded synchronously from that flag, so
 *   a screen opened with no network still says "Connected" when it is.
 * - **The access token** is short-lived and is fetched immediately before each call. There is no
 *   cache here on purpose: Play services already serves a valid token from its own cache without a
 *   network round trip, and a token that has gone stale early is only detectable from the 401 the
 *   Sheets call returns — which the caller answers with [clearCachedToken] plus one retry.
 */
@Singleton
class GoogleAuthorizationManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val prefs = appPrefs(context, PREFS_GOOGLE_AUTHORIZATION)

    private val _hasConsent = MutableStateFlow(prefs.getBoolean(KEY_HAS_CONSENT, false))

    /** Durable "the owner connected Google Sheets", correct before any network call happens. */
    val hasConsent: StateFlow<Boolean> = _hasConsent.asStateFlow()

    /** The account the consent was granted for, so a switch of account is visible immediately. */
    fun consentedAccountEmail(): String? = prefs.getString(KEY_CONSENT_EMAIL, null)

    /**
     * Asks Play services for an access token for [accountEmail].
     *
     * When consent already exists this normally resolves offline-fast and without any UI, which is
     * what makes it safe to call at the start of every sync — including from the worker.
     */
    suspend fun getFreshAccessToken(accountEmail: String?): TokenResult {
        val builder = AuthorizationRequest.builder().setRequestedScopes(REQUESTED_SCOPES)
        if (!accountEmail.isNullOrBlank()) {
            // Pins the request to the signed-in account; without it the consent sheet offers its
            // own account picker and could authorize a different Google account than the one the
            // rest of the app believes it is backing up to.
            builder.setAccount(Account(accountEmail, GOOGLE_ACCOUNT_TYPE))
        }
        return try {
            val result = Identity.getAuthorizationClient(context)
                .authorize(builder.build())
                .await()
            if (result.hasResolution()) {
                val pendingIntent = result.pendingIntent
                    ?: return TokenResult.Failed(IllegalStateException("resolution without PendingIntent"))
                // Consent is deliberately not recorded here: it is granted only once the owner
                // comes back through onConsentResult with RESULT_OK.
                TokenResult.NeedsConsent(pendingIntent)
            } else {
                val token = result.accessToken
                    ?: return TokenResult.Failed(IllegalStateException("authorized without a token"))
                rememberConsent(accountEmail)
                TokenResult.Ok(token)
            }
        } catch (e: CancellationException) {
            // Structured cancellation of the calling scope, not a Google outcome.
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "authorize failed: ${e.javaClass.simpleName}", e)
            TokenResult.Failed(e)
        }
    }

    /**
     * Feed the ActivityResult of the launched consent PendingIntent back in here.
     *
     * Cancellation is decided by [resultCode] alone. Play services reports a dismissed consent
     * sheet through several different exception shapes and status numbers depending on version, so
     * inferring "the owner backed out" from an exception is how the app ended up showing a scary
     * error for a deliberate choice.
     */
    fun onConsentResult(resultCode: Int, data: Intent?, accountEmail: String?): TokenResult {
        if (resultCode != Activity.RESULT_OK) return TokenResult.Cancelled
        return try {
            val result = Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data)
            val token = result.accessToken
                ?: return TokenResult.Failed(IllegalStateException("consent returned no token"))
            rememberConsent(accountEmail)
            TokenResult.Ok(token)
        } catch (e: Exception) {
            Log.e(TAG, "consent result failed: ${e.javaClass.simpleName}", e)
            TokenResult.Failed(e)
        }
    }

    /**
     * Drops a token Play services still believes is good. The only way to learn that is a 401 from
     * the API itself, so this is always followed by exactly one retry.
     */
    suspend fun clearCachedToken(accessToken: String) {
        try {
            Identity.getAuthorizationClient(context)
                .clearToken(ClearTokenRequest.builder().setToken(accessToken).build())
                .await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Best effort: the retry re-authorizes anyway, and failing here must not fail the sync.
            Log.w(TAG, "clearToken failed: ${e.javaClass.simpleName}")
        }
    }

    /**
     * Disconnect. Revoking is best effort — the local state must end up disconnected whether or not
     * Google could be reached, or "Disconnect" would appear to do nothing offline.
     */
    suspend fun revokeAndClear(accountEmail: String?) {
        if (!accountEmail.isNullOrBlank()) {
            try {
                Identity.getAuthorizationClient(context)
                    .revokeAccess(
                        RevokeAccessRequest.builder()
                            .setAccount(Account(accountEmail, GOOGLE_ACCOUNT_TYPE))
                            .setScopes(REQUESTED_SCOPES)
                            .build()
                    )
                    .await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "revokeAccess failed: ${e.javaClass.simpleName}")
            }
        }
        forgetConsent()
    }

    fun forgetConsent() {
        prefs.edit {
            putBoolean(KEY_HAS_CONSENT, false)
            remove(KEY_CONSENT_EMAIL)
        }
        _hasConsent.value = false
    }

    private fun rememberConsent(accountEmail: String?) {
        prefs.edit {
            putBoolean(KEY_HAS_CONSENT, true)
            if (!accountEmail.isNullOrBlank()) putString(KEY_CONSENT_EMAIL, accountEmail)
        }
        _hasConsent.value = true
    }

    companion object {
        private const val TAG = "GoogleAuth"
        private const val KEY_HAS_CONSENT = "has_consent"
        private const val KEY_CONSENT_EMAIL = "consent_email"
        private const val GOOGLE_ACCOUNT_TYPE = "com.google"

        /**
         * Both scopes on purpose: `drive.file` is what spec section 7 asks for (access limited to
         * files this app created), and the Sheets scope is what the values endpoints need on that
         * file. This is the pair the owner's current install already works with; narrowing it would
         * force a fresh consent screen for no gain in a personal app.
         */
        private val REQUESTED_SCOPES: List<Scope> = listOf(
            Scope("https://www.googleapis.com/auth/drive.file"),
            Scope(SheetsScopes.SPREADSHEETS),
        )
    }
}
