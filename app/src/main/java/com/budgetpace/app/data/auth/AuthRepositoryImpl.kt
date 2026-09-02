package com.budgetpace.app.data.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.CustomCredential
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.budgetpace.app.domain.auth.AuthRepository
import com.budgetpace.app.domain.auth.UserSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val context: Context
) : AuthRepository {

    private val credentialManager = CredentialManager.create(context)
    
    private val _currentSession = MutableStateFlow<UserSession?>(null)
    override val currentSession: StateFlow<UserSession?> = _currentSession.asStateFlow()

    override suspend fun signInWithGoogle(context: Context): Result<UserSession> {
        return try {
            // Usually this requires a Web Client ID from Google Cloud Console
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId("579588306520-pup0r8t68vfd22nelv8neol8lqij82gj.apps.googleusercontent.com") // Placeholder for dev
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
            Result.failure(e)
        }
    }

    override suspend fun signOut() {
        _currentSession.value = null
        // Additional clear credential state logic could go here
    }
}
