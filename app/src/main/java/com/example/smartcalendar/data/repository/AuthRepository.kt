package com.example.smartcalendar.data.repository

import com.example.smartcalendar.data.remote.SupabaseClient
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.user.UserInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Outcome of a signup call.
 *
 * - [LoggedIn] — Supabase project has email confirmation OFF, so the user is signed in immediately.
 * - [VerificationRequired] — confirmation is ON; the user must enter the 6-digit code from email.
 */
sealed class SignUpResult {
    data class LoggedIn(val user: UserInfo) : SignUpResult()
    data class VerificationRequired(val email: String) : SignUpResult()
}

/**
 * Repository for authentication operations using Supabase Auth.
 */
class AuthRepository {

    private val auth = SupabaseClient.auth

    fun isSignedIn(): Boolean = auth.currentUserOrNull() != null

    fun getCurrentUser(): UserInfo? = auth.currentUserOrNull()

    fun getCurrentUserId(): String? = auth.currentUserOrNull()?.id

    fun observeAuthState(): Flow<Boolean> = auth.sessionStatus.map {
        auth.currentUserOrNull() != null
    }

    /**
     * Sign in with email and password.
     *
     * If the account exists but email is not yet confirmed, Supabase throws an error
     * whose message contains "email_not_confirmed" or "Email not confirmed". Callers
     * should detect that case (see [isEmailNotConfirmedError]) and route the user to
     * the verification screen instead of showing a generic failure.
     */
    suspend fun signInWithEmail(email: String, password: String): Result<UserInfo> {
        return try {
            auth.signInWith(Email) {
                this.email = email
                this.password = password
            }
            val user = auth.currentUserOrNull()
            if (user != null) Result.success(user)
            else Result.failure(Exception("Sign in failed"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Sign up with email and password.
     *
     * When email confirmation is enabled in Supabase, this returns
     * [SignUpResult.VerificationRequired] without an active session.
     */
    suspend fun signUpWithEmail(email: String, password: String): Result<SignUpResult> {
        return try {
            auth.signUpWith(Email) {
                this.email = email
                this.password = password
            }
            val user = auth.currentUserOrNull()
            if (user != null) {
                Result.success(SignUpResult.LoggedIn(user))
            } else {
                // Confirmation required: Supabase created the user but no session.
                Result.success(SignUpResult.VerificationRequired(email))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Verify a signup with the 6-digit OTP from the confirmation email.
     * On success, the SDK populates the session and the user is signed in.
     */
    suspend fun verifyEmailOtp(email: String, token: String): Result<Unit> {
        return try {
            auth.verifyEmailOtp(
                type = OtpType.Email.SIGNUP,
                email = email,
                token = token.trim()
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Re-send the signup confirmation email (for users who lost / didn't receive the code).
     * Note: Supabase enforces a per-address rate limit (~3-4 / hour on free tier).
     */
    suspend fun resendSignupEmail(email: String): Result<Unit> {
        return try {
            auth.resendEmail(type = OtpType.Email.SIGNUP, email = email)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signOut(): Result<Unit> {
        return try {
            auth.signOut()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

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

        /**
         * True when the given throwable came from Supabase rejecting a sign-in
         * because the user hasn't confirmed their email yet.
         */
        fun isEmailNotConfirmedError(e: Throwable): Boolean {
            val msg = (e.message ?: "").lowercase()
            return "email_not_confirmed" in msg || "email not confirmed" in msg
        }
    }
}
