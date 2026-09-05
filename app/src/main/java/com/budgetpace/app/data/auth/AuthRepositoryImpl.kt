package com.budgetpace.app.data.auth

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.CustomCredential
import com.budgetpace.app.core.security.PREFS_AUTH_SESSION
import com.budgetpace.app.core.security.appPrefs
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.budgetpace.app.domain.auth.AuthRepository
import com.budgetpace.app.domain.auth.UserSession
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : AuthRepository {

    private val credentialManager = CredentialManager.create(context)

    // Only email/display name are persisted, encrypted — not the ID token itself, which is
    // short-lived and shouldn't be stored long-term. refreshSessionSilently() hydrates a real
    // token behind a restored session without the user seeing any UI for it.
    private val prefs = appPrefs(context, PREFS_AUTH_SESSION)

    private val _currentSession = MutableStateFlow(restoreSession())
    override val currentSession: StateFlow<UserSession?> = _currentSession.asStateFlow()

    private fun restoreSession(): UserSession? {
        val email = prefs.getString(KEY_EMAIL, null) ?: return null
        return UserSession(email = email, displayName = prefs.getString(KEY_DISPLAY_NAME, null), idToken = "")
    }

    private fun persistSession(session: UserSession) {
        prefs.edit()
            .putString(KEY_EMAIL, session.email)
            .putString(KEY_DISPLAY_NAME, session.displayName)
            .apply()
    }

    override suspend fun refreshSessionSilently() {
        if (_currentSession.value == null) return
        try {
            val googleIdOption = GetGoogleIdOption.Builder()
                // Only accounts already used with this app, and pick one automatically — this is
                // what makes the request resolve with no UI shown, unlike signInWithGoogle's.
                .setFilterByAuthorizedAccounts(true)
                .setAutoSelectEnabled(true)
                .setServerClientId(com.budgetpace.app.BuildConfig.GOOGLE_CLIENT_ID)
                .build()
            val request = GetCredentialRequest.Builder().addCredentialOption(googleIdOption).build()
            val result = credentialManager.getCredential(context, request)
            val credential = result.credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val session = UserSession(
                    email = googleIdTokenCredential.id,
                    displayName = googleIdTokenCredential.displayName,
                    idToken = googleIdTokenCredential.idToken
                )
                _currentSession.value = session
                persistSession(session)
            }
        } catch (e: Exception) {
            // Not fatal — the restored (token-less) session still shows "signed in" in the UI,
            // matching the last known state; an actual sign-in attempt will surface a fresh error
            // if the credential is really gone. Logged the same way signInWithGoogle's is.
            Log.e("AuthRepository", "Silent session refresh failed: ${e.javaClass.simpleName}: ${e.message}", e)
        }
    }

    override suspend fun signInWithGoogle(context: Context): Result<UserSession> {
        return try {
            // Spec §7: the web client ID identifies *this app's* OAuth client to Google and must
            // come from the developer's own Google Cloud project — read it from BuildConfig
            // (sourced from local.properties' GOOGLE_CLIENT_ID) rather than a hardcoded value
            // that would only work for whoever's project it was copied from.
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(com.budgetpace.app.BuildConfig.GOOGLE_CLIENT_ID)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(context, request)
            val credential = result.credential

            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)

                val session = UserSession(
                    email = googleIdTokenCredential.id, // The user's Google ID (often maps to email in basic setups or requires extra scope)
                    displayName = googleIdTokenCredential.displayName,
                    idToken = googleIdTokenCredential.idToken
                )
                _currentSession.value = session
                persistSession(session)
                Result.success(session)
            } else {
                Result.failure(Exception("Unexpected credential type"))
            }
        } catch (e: Exception) {
            // Never shown raw to the user (spec §61) — but this is the only way to diagnose
            // Credential Manager / OAuth client misconfiguration from a real device's logcat.
            Log.e("AuthRepository", "Google sign-in failed: ${e.javaClass.simpleName}: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun signOut() {
        _currentSession.value = null
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_EMAIL = "email"
        private const val KEY_DISPLAY_NAME = "display_name"
    }
}
