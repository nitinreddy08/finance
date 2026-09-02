package com.budgetpace.app.domain.auth

import android.content.Context
import kotlinx.coroutines.flow.StateFlow

data class UserSession(
    val email: String,
    val displayName: String?,
    val idToken: String
)

interface AuthRepository {
    val currentSession: StateFlow<UserSession?>

    suspend fun signInWithGoogle(context: Context): Result<UserSession>
    suspend fun signOut()

    /**
     * Call once at app startup. A restored session (from [currentSession] surviving a process
     * restart) only has an email/display name, not a real ID token — this attempts a silent
     * (no UI) Credential Manager re-auth to hydrate a fresh one. Safe to call with no restored
     * session; does nothing then.
     */
    suspend fun refreshSessionSilently()
}
