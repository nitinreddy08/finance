package com.budgetpace.app.data.google.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleAuthManager @Inject constructor(
    private val context: Context
) {
    // In a real app, you would configure this in strings.xml via Google Cloud Console
    private val webClientId = "YOUR_WEB_CLIENT_ID_PLACEHOLDER"

    private val _authState = MutableStateFlow<AuthState>(AuthState.Unauthenticated)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    suspend fun signIn(): AuthState {
        try {
            val credentialManager = CredentialManager.create(context)
            
            val googleIdOption: GetGoogleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(webClientId)
                .setAutoSelectEnabled(true)
                .build()

            val request: GetCredentialRequest = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(
                request = request,
                context = context
            )
            
            return handleSignIn(result)
            
        } catch (e: Exception) {
            _authState.value = AuthState.Error(e.message ?: "Sign in failed")
            return _authState.value
        }
    }

    private fun handleSignIn(result: GetCredentialResponse): AuthState {
        val credential = result.credential

        if (credential is GoogleIdTokenCredential) {
            val idToken = credential.idToken
            val email = credential.id
            val name = credential.displayName
            
            // Spec §7: Do not assume that signing in with Google automatically grants Drive/Sheets access.
            // A separate OAuth authorization flow is needed for the `drive.file` scope.
            
            _authState.value = AuthState.Authenticated(
                email = email,
                name = name,
                hasDriveScope = false // Requires secondary auth step
            )
        } else {
            _authState.value = AuthState.Error("Unexpected credential type")
        }
        return _authState.value
    }
    
    fun signOut() {
        _authState.value = AuthState.Unauthenticated
    }
}

sealed class AuthState {
    object Unauthenticated : AuthState()
    data class Authenticated(
        val email: String, 
        val name: String?, 
        val hasDriveScope: Boolean
    ) : AuthState()
    data class Error(val message: String) : AuthState()
}
