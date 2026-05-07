package com.example.smartcalendar.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.smartcalendar.MainActivity
import com.example.smartcalendar.R
import com.example.smartcalendar.data.repository.AuthRepository
import com.example.smartcalendar.databinding.ActivityVerifyEmailBinding
import kotlinx.coroutines.launch

/**
 * Step 2 of signup: user enters the 6-digit code from the confirmation email.
 *
 * Started by [LoginActivity] with [EXTRA_EMAIL] set to the address being verified.
 */
class VerifyEmailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVerifyEmailBinding
    private lateinit var authRepository: AuthRepository
    private lateinit var email: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVerifyEmailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        email = intent.getStringExtra(EXTRA_EMAIL).orEmpty()
        if (email.isBlank()) {
            // Shouldn't happen — bail back to login.
            finish()
            return
        }

        authRepository = AuthRepository.getInstance()
        binding.subtitleText.text = getString(R.string.verify_email_subtitle, email)

        binding.verifyButton.setOnClickListener {
            val code = binding.otpEditText.text?.toString()?.trim().orEmpty()
            // Length varies by Supabase project config (commonly 6 or 8). Server is source of truth.
            if (code.length < 4 || !code.all { it.isDigit() }) {
                showError(getString(R.string.verify_otp_invalid))
                return@setOnClickListener
            }
            verify(code)
        }

        binding.resendButton.setOnClickListener { resend() }
    }

    private fun verify(code: String) {
        showLoading(true)
        hideError()

        lifecycleScope.launch {
            val result = authRepository.verifyEmailOtp(email, code)
            showLoading(false)
            result.fold(
                onSuccess = {
                    Toast.makeText(
                        this@VerifyEmailActivity,
                        R.string.verify_success,
                        Toast.LENGTH_SHORT
                    ).show()
                    navigateToMain()
                },
                onFailure = { e ->
                    showError(e.message ?: getString(R.string.verify_failed))
                }
            )
        }
    }

    private fun resend() {
        showLoading(true)
        hideError()

        lifecycleScope.launch {
            val result = authRepository.resendSignupEmail(email)
            showLoading(false)
            result.fold(
                onSuccess = {
                    Toast.makeText(
                        this@VerifyEmailActivity,
                        R.string.code_resent,
                        Toast.LENGTH_SHORT
                    ).show()
                },
                onFailure = { e ->
                    showError(e.message ?: getString(R.string.resend_failed))
                }
            )
        }
    }

    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
        binding.verifyButton.isEnabled = !show
        binding.resendButton.isEnabled = !show
        binding.otpEditText.isEnabled = !show
    }

    private fun showError(message: String) {
        binding.errorText.text = message
        binding.errorText.visibility = View.VISIBLE
    }

    private fun hideError() {
        binding.errorText.visibility = View.GONE
    }

    private fun navigateToMain() {
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )
        finish()
    }

    companion object {
        const val EXTRA_EMAIL = "extra_email"

        fun start(from: AppCompatActivity, email: String) {
            from.startActivity(
                Intent(from, VerifyEmailActivity::class.java).putExtra(EXTRA_EMAIL, email)
            )
        }
    }
}
