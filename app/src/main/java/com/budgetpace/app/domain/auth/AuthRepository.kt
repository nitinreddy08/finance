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

    /** True while a [signInWithGoogle] call is in flight, so the sign-in button can disable itself. */
    val isSigningIn: StateFlow<Boolean>

    /**
     * A classified failure from the most recent [signInWithGoogle] call — a signal (buffered
     * [kotlinx.coroutines.flow.SharedFlow], not a state), because two failed attempts in a row
     * must both reach a collector even if nothing else changed in between. Check
     * [SignInProblem.isSilent] before showing anything: a cancelled account picker is not an error.
     */
    val signInProblem: kotlinx.coroutines.flow.SharedFlow<SignInProblem>

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
