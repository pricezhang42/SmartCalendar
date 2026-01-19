package com.example.smartcalendar.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.smartcalendar.MainActivity
import com.example.smartcalendar.data.repository.AuthRepository
import com.example.smartcalendar.databinding.ActivityLoginBinding
import kotlinx.coroutines.launch

/**
 * Login/Registration activity using Supabase Auth.
 */
class LoginActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityLoginBinding
    private lateinit var authRepository: AuthRepository
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        authRepository = AuthRepository.getInstance()
        
        // Check if already signed in
        if (authRepository.isSignedIn()) {
            navigateToMain()
            return
        }
        
        setupListeners()
    }
    
    private fun setupListeners() {
        binding.signInButton.setOnClickListener {
            val email = binding.emailEditText.text.toString().trim()
            val password = binding.passwordEditText.text.toString()
            
            if (validateInput(email, password)) {
                signIn(email, password)
            }
        }
        
        binding.signUpButton.setOnClickListener {
            val email = binding.emailEditText.text.toString().trim()
            val password = binding.passwordEditText.text.toString()
            
            if (validateInput(email, password)) {
                signUp(email, password)
            }
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
    
    private fun signIn(email: String, password: String) {
        showLoading(true)
        hideError()
        
        lifecycleScope.launch {
            val result = authRepository.signInWithEmail(email, password)
            showLoading(false)
            
            result.fold(
                onSuccess = {
                    navigateToMain()
                },
                onFailure = { e ->
                    showError(e.message ?: "Sign in failed")
                }
            )
        }
    }
    
    private fun signUp(email: String, password: String) {
        showLoading(true)
        hideError()
        
        lifecycleScope.launch {
            val result = authRepository.signUpWithEmail(email, password)
            showLoading(false)
            
            result.fold(
                onSuccess = {
                    Toast.makeText(this@LoginActivity, 
                        "Account created! Check email for verification.", 
                        Toast.LENGTH_LONG).show()
                    navigateToMain()
                },
                onFailure = { e ->
                    showError(e.message ?: "Sign up failed")
                }
            )
        }
    }
    
    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
        binding.signInButton.isEnabled = !show
        binding.signUpButton.isEnabled = !show
    }
    
    private fun showError(message: String) {
        binding.errorText.text = message
        binding.errorText.visibility = View.VISIBLE
    }
    
    private fun hideError() {
        binding.errorText.visibility = View.GONE
    }
    
    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
