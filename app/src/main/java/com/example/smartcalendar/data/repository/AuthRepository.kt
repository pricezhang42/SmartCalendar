package com.example.smartcalendar.data.repository

import com.example.smartcalendar.data.remote.SupabaseClient
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.user.UserInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Repository for authentication operations using Supabase Auth.
 */
class AuthRepository {
    
    private val auth = SupabaseClient.auth
    
    /**
     * Check if user is currently signed in
     */
    fun isSignedIn(): Boolean = auth.currentUserOrNull() != null
    
    /**
     * Get current user
     */
    fun getCurrentUser(): UserInfo? = auth.currentUserOrNull()
    
    /**
     * Get current user ID
     */
    fun getCurrentUserId(): String? = auth.currentUserOrNull()?.id
    
    /**
     * Observe session state changes
     */
    fun observeAuthState(): Flow<Boolean> = auth.sessionStatus.map { 
        auth.currentUserOrNull() != null 
    }
    
    /**
     * Sign in with email and password
     */
    suspend fun signInWithEmail(email: String, password: String): Result<UserInfo> {
        return try {
            auth.signInWith(Email) {
                this.email = email
                this.password = password
            }
            val user = auth.currentUserOrNull()
            if (user != null) {
                Result.success(user)
            } else {
                Result.failure(Exception("Sign in failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Sign up with email and password
     */
    suspend fun signUpWithEmail(email: String, password: String): Result<UserInfo> {
        return try {
            auth.signUpWith(Email) {
                this.email = email
                this.password = password
            }
            val user = auth.currentUserOrNull()
            if (user != null) {
                Result.success(user)
            } else {
                Result.failure(Exception("Sign up failed - check email for verification"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Sign out
     */
    suspend fun signOut(): Result<Unit> {
        return try {
            auth.signOut()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Send password reset email
     */
    suspend fun resetPassword(email: String): Result<Unit> {
        return try {
            auth.resetPasswordForEmail(email)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    companion object {
        @Volatile
        private var instance: AuthRepository? = null
        
        fun getInstance(): AuthRepository {
            return instance ?: synchronized(this) {
                instance ?: AuthRepository().also { instance = it }
            }
        }
    }
}
