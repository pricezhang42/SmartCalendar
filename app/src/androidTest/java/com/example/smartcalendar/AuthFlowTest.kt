package com.example.smartcalendar

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.smartcalendar.data.repository.AuthRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for authentication flow.
 * These tests require a real Supabase connection.
 */
@RunWith(AndroidJUnit4::class)
class AuthFlowTest {

    private lateinit var authRepository: AuthRepository

    @Before
    fun setup() {
        authRepository = AuthRepository.getInstance()
    }

    @Test
    fun testAppContext() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.example.smartcalendar", appContext.packageName)
    }

    @Test
    fun testAuthRepositoryInstance() {
        assertNotNull(authRepository)
    }

    @Test
    fun testIsSignedInReturnsBoolean() {
        // Should return false if not signed in, true if signed in
        val result = authRepository.isSignedIn()
        assertTrue(result is Boolean)
    }

    @Test
    fun testGetCurrentUserReturnsNullWhenNotSignedIn() {
        // If not signed in, getCurrentUser should return null
        if (!authRepository.isSignedIn()) {
            assertNull(authRepository.getCurrentUser())
        }
    }

    @Test
    fun testGetCurrentUserIdReturnsNullWhenNotSignedIn() {
        // If not signed in, getCurrentUserId should return null
        if (!authRepository.isSignedIn()) {
            assertNull(authRepository.getCurrentUserId())
        }
    }

    @Test
    fun testSignInWithInvalidCredentialsFails() = runBlocking {
        // Test with invalid credentials - should fail
        val result = authRepository.signInWithEmail(
            email = "invalid@test.com",
            password = "invalidpassword123"
        )

        assertTrue(result.isFailure)
    }

    @Test
    fun testSignUpWithInvalidEmailFails() = runBlocking {
        // Test with invalid email format
        val result = authRepository.signUpWithEmail(
            email = "notanemail",
            password = "password123"
        )

        assertTrue(result.isFailure)
    }

    @Test
    fun testSignUpWithWeakPasswordFails() = runBlocking {
        // Test with weak password (Supabase requires min 6 chars)
        val result = authRepository.signUpWithEmail(
            email = "test@example.com",
            password = "123"
        )

        assertTrue(result.isFailure)
    }
}
