package com.aroundus.app.activities

import android.content.Intent
import android.os.Bundle
import android.view.animation.AlphaAnimation
import android.view.animation.AnimationSet
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.view.animation.ScaleAnimation
import android.view.animation.TranslateAnimation
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.aroundus.app.databinding.ActivityLoginBinding
import com.aroundus.app.repository.NetworkResult
import com.aroundus.app.repository.UserRepository
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val userRepository = UserRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupAnimations()
        setupListeners()
    }

    private fun setupAnimations() {
        // Ambient glow fade-in
        val glowAnim = AlphaAnimation(0f, 1f).apply {
            duration = 800
            interpolator = DecelerateInterpolator()
        }
        binding.vAmbientGlow.startAnimation(glowAnim)

        // Logo & Header scale-in with spring overshoot
        val headerAnim = AnimationSet(true).apply {
            addAnimation(AlphaAnimation(0f, 1f).apply { duration = 500 })
            addAnimation(ScaleAnimation(0.85f, 1f, 0.85f, 1f, ScaleAnimation.RELATIVE_TO_SELF, 0.5f, ScaleAnimation.RELATIVE_TO_SELF, 0.5f).apply {
                duration = 600
                interpolator = OvershootInterpolator(1.2f)
            })
        }
        binding.headerContainer.startAnimation(headerAnim)

        // Login card smooth spring slide-up + fade-in
        val cardAnim = AnimationSet(true).apply {
            addAnimation(AlphaAnimation(0f, 1f).apply { duration = 650; startOffset = 120 })
            addAnimation(TranslateAnimation(0f, 0f, 50f, 0f).apply {
                duration = 650
                startOffset = 120
                interpolator = DecelerateInterpolator(1.5f)
            })
        }
        binding.loginCard.startAnimation(cardAnim)
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener { finish() }

        binding.tvNeedHelp.setOnClickListener {
            Toast.makeText(this, "Help center & support coming soon!", Toast.LENGTH_SHORT).show()
        }

        binding.btnForgotPassword.setOnClickListener {
            startActivity(Intent(this, ForgotPasswordActivity::class.java))
        }

        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString()

            if (email.isEmpty()) {
                binding.tilEmail.error = "Email or phone number is required"
                return@setOnClickListener
            }
            if (password.isEmpty()) {
                binding.tilPassword.error = "Password is required"
                return@setOnClickListener
            }

            binding.tilEmail.error = null
            binding.tilPassword.error = null

            lifecycleScope.launch {
                binding.btnLogin.isEnabled = false
                val result = userRepository.signIn(email, password)
                binding.btnLogin.isEnabled = true

                when (result) {
                    is NetworkResult.Success -> {
                        val name = result.data.name.ifBlank { "User" }
                        Toast.makeText(this@LoginActivity, "Welcome back, $name!", Toast.LENGTH_SHORT).show()
                        navigateToMain()
                    }
                    is NetworkResult.Error -> {
                        Toast.makeText(this@LoginActivity, result.message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        binding.btnGoogle.setOnClickListener {
            com.aroundus.app.utils.GoogleAuthManager.startGoogleSignIn(this)
        }

        binding.btnRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finishAffinity()
    }
}
