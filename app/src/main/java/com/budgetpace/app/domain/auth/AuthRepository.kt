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
}
