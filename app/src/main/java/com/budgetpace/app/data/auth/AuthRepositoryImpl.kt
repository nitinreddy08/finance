package com.budgetpace.app.data.auth

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.CustomCredential
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
    
    private val _currentSession = MutableStateFlow<UserSession?>(null)
    override val currentSession: StateFlow<UserSession?> = _currentSession.asStateFlow()

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
        // Additional clear credential state logic could go here
    }
}
