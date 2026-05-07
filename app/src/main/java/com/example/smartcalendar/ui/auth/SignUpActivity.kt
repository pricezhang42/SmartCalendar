package com.example.smartcalendar.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.smartcalendar.MainActivity
import com.example.smartcalendar.data.repository.AuthRepository
import com.example.smartcalendar.data.repository.SignUpResult
import com.example.smartcalendar.databinding.ActivitySignUpBinding
import kotlinx.coroutines.launch

/**
 * Account creation screen. On success either:
 *  - navigates to [VerifyEmailActivity] (if Supabase requires email confirmation), or
 *  - navigates to [MainActivity] (if email confirmation is disabled).
 */
class SignUpActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySignUpBinding
    private lateinit var authRepository: AuthRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignUpBinding.inflate(layoutInflater)
        setContentView(binding.root)

        authRepository = AuthRepository.getInstance()

        binding.signUpButton.setOnClickListener {
            val email = binding.emailEditText.text.toString().trim()
            val password = binding.passwordEditText.text.toString()
            if (validateInput(email, password)) {
                signUp(email, password)
            }
        }

        binding.haveAccountLink.setOnClickListener {
            // LoginActivity is the parent; just pop back.
            finish()
        }
    }

    private fun validateInput(email: String, password: String): Boolean {
        if (email.isEmpty()) {
            showError("Email is required")
            return false
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showError("Invalid email format")
            return false
        }
        if (password.isEmpty()) {
            showError("Password is required")
            return false
        }
        if (password.length < 6) {
            showError("Password must be at least 6 characters")
            return false
        }
        return true
    }

    private fun signUp(email: String, password: String) {
        showLoading(true)
        hideError()

        lifecycleScope.launch {
            val result = authRepository.signUpWithEmail(email, password)
            showLoading(false)

            result.fold(
                onSuccess = { signUpResult ->
                    when (signUpResult) {
                        is SignUpResult.LoggedIn -> {
                            // Email confirmation is OFF in Supabase — user is already logged in.
                            startActivity(
                                Intent(this@SignUpActivity, MainActivity::class.java).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                }
                            )
                            finish()
                        }
                        is SignUpResult.VerificationRequired -> {
                            Toast.makeText(
                                this@SignUpActivity,
                                "Check your email for the 6-digit code.",
                                Toast.LENGTH_LONG
                            ).show()
                            VerifyEmailActivity.start(this@SignUpActivity, signUpResult.email)
                            finish()
                        }
                    }
                },
                onFailure = { e ->
                    showError(e.message ?: "Sign up failed")
                }
            )
        }
    }

    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
        binding.signUpButton.isEnabled = !show
        binding.emailEditText.isEnabled = !show
        binding.passwordEditText.isEnabled = !show
    }

    private fun showError(message: String) {
        binding.errorText.text = message
        binding.errorText.visibility = View.VISIBLE
    }

    private fun hideError() {
        binding.errorText.visibility = View.GONE
    }
}
