package com.budgetpace.app.data.auth

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.budgetpace.app.core.security.PREFS_AUTH_SESSION
import com.budgetpace.app.core.security.appPrefs
import com.budgetpace.app.domain.auth.AuthRepository
import com.budgetpace.app.domain.auth.SignInProblem
import com.budgetpace.app.domain.auth.UserSession
import com.budgetpace.app.domain.auth.classifySignInFailure
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : AuthRepository {

    private val credentialManager = CredentialManager.create(context)

    // Only email/display name are persisted — not the ID token itself, which is short-lived and
    // shouldn't be stored long-term. refreshSessionSilently() hydrates a real token behind a
    // restored session without the user seeing any UI for it.
    private val prefs = appPrefs(context, PREFS_AUTH_SESSION)

    private val _currentSession = MutableStateFlow(restoreSession())
    override val currentSession: StateFlow<UserSession?> = _currentSession.asStateFlow()

    private val _isSigningIn = MutableStateFlow(false)
    override val isSigningIn: StateFlow<Boolean> = _isSigningIn.asStateFlow()

    // A signal, not a state: two failed attempts in a row (e.g. cancel, then a real failure) must
    // both reach a collector even though nothing else changed in between a plain StateFlow would
    // have collapsed them into one value.
    private val _signInProblem = MutableSharedFlow<SignInProblem>(extraBufferCapacity = 1)
    override val signInProblem: SharedFlow<SignInProblem> = _signInProblem.asSharedFlow()

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
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Not fatal — the restored (token-less) session still shows "signed in" in the UI,
            // matching the last known state; an actual sign-in attempt will surface a fresh error
            // if the credential is really gone. Never shown to the user (spec §61): this path has
            // no button waiting on it.
            Log.e(TAG, "Silent session refresh failed: ${e.javaClass.simpleName}: ${e.message}", e)
        }
    }

    override suspend fun signInWithGoogle(context: Context): Result<UserSession> {
        _isSigningIn.value = true
        return try {
            // Spec §7: the web client ID identifies *this app's* OAuth client to Google and must
            // come from the developer's own Google Cloud project — read it from BuildConfig
            // (sourced from local.properties' GOOGLE_CLIENT_ID) rather than a hardcoded value
            // that would only work for whoever's project it was copied from.
            val clientId = com.budgetpace.app.BuildConfig.GOOGLE_CLIENT_ID
            val signInWithGoogleOption = GetSignInWithGoogleOption.Builder(clientId)
                .setNonce(generateNonce())
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(signInWithGoogleOption)
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
                val problem = SignInProblem.Failed("unexpected_credential_type")
                _signInProblem.tryEmit(problem)
                Result.failure(IllegalStateException("Unexpected credential type"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Never shown raw to the user (spec §61) — but this is the only way to diagnose
            // Credential Manager / OAuth client misconfiguration from a real device's logcat.
            Log.e(TAG, "Google sign-in failed: ${e.javaClass.simpleName}: ${e.message}", e)
            val problem = classifySignInFailure(
                exceptionClassNames = causeChain(e).map { it.javaClass.name },
                message = e.message,
                clientIdIsPlaceholder = SignInProblem.isPlaceholderClientId(com.budgetpace.app.BuildConfig.GOOGLE_CLIENT_ID),
            )
            _signInProblem.tryEmit(problem)
            Result.failure(e)
        } finally {
            _isSigningIn.value = false
        }
    }

    override suspend fun signOut() {
        _currentSession.value = null
        prefs.edit().clear().apply()
    }

    /** Outermost first; guards against a (should-never-happen) self-referential cause cycle. */
    private fun causeChain(t: Throwable, maxDepth: Int = 12): List<Throwable> {
        val chain = mutableListOf<Throwable>()
        var current: Throwable? = t
        while (current != null && chain.size < maxDepth && chain.none { it === current }) {
            chain += current
            current = current.cause
        }
        return chain
    }

    /**
     * A high-entropy random string, not a predictable one, so a captured ID token can't be replayed
     * against a future request. There is no backend here to compare it against a server-issued
     * value, so unlike a typical OAuth nonce flow this is generated and never checked back — its
     * only job is to make each request's token unique.
     */
    private fun generateNonce(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val TAG = "AuthRepository"
        private const val KEY_EMAIL = "email"
        private const val KEY_DISPLAY_NAME = "display_name"
    }
}
