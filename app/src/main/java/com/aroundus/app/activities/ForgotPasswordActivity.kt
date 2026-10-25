package com.aroundus.app.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.aroundus.app.databinding.ActivityForgotPasswordBinding
import com.aroundus.app.repository.NetworkResult
import com.aroundus.app.repository.UserRepository
import com.aroundus.app.utils.OAuthLoopbackServer
import kotlinx.coroutines.launch

/**
 * Activity handling Supabase password reset flow:
 * 1. Request Step: User enters email, clicks "Send Reset Link".
 *    Starts local port 3000 loopback listener and requests Supabase recovery email with redirect.
 * 2. Link Sent Step: Informs user that the reset link has been dispatched to their email.
 * 3. Set New Password Step: Opened automatically when user clicks the reset link in their email.
 *    User enters new password and confirms without typing any OTP codes.
 * 4. Success Step: Confirmation and return to Sign In.
 */
class ForgotPasswordActivity : AppCompatActivity() {

    private lateinit var binding: ActivityForgotPasswordBinding
    private val userRepository = UserRepository()

    private var currentEmail: String = ""
    private var recoveryAccessToken: String? = null
    private var resendTimer: CountDownTimer? = null
    private var isResendEnabled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityForgotPasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupListeners()
        handleIncomingIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    override fun onDestroy() {
        resendTimer?.cancel()
        super.onDestroy()
    }

    private fun handleIncomingIntent(intent: Intent?) {
        val isRecoveryFlow = intent?.getBooleanExtra("is_recovery_flow", false) ?: false
        val token = intent?.getStringExtra("access_token")
        val email = intent?.getStringExtra("email")

        if (isRecoveryFlow && !token.isNullOrBlank()) {
            recoveryAccessToken = token
            currentEmail = email ?: ""
            showSetNewPasswordStep(email ?: "")
        } else {
            val passedEmail = intent?.getStringExtra("email")?.trim()
            if (!passedEmail.isNullOrBlank()) {
                binding.etEmail.setText(passedEmail)
                binding.etEmail.setSelection(passedEmail.length)
            }
            showRequestLinkStep()
        }
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener { finish() }
        binding.btnBackToLogin.setOnClickListener { finish() }
        binding.btnBackFromSent.setOnClickListener { finish() }
        binding.btnCancelUpdate.setOnClickListener { finish() }

        // --- STEP 1: SEND RESET LINK ---
        binding.btnSendReset.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            if (email.isEmpty()) {
                binding.tilEmail.error = "Email address is required"
                binding.etEmail.requestFocus()
                return@setOnClickListener
            }
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                binding.tilEmail.error = "Please enter a valid email address"
                binding.etEmail.requestFocus()
                return@setOnClickListener
            }
            binding.tilEmail.error = null

            setLoadingSend(true)

            // Start loopback server on port 3000 to catch any fallback redirect from browser
            OAuthLoopbackServer.start(this@ForgotPasswordActivity, 1_800_000L)

            lifecycleScope.launch {
                val result = userRepository.sendPasswordResetLink(email)
                setLoadingSend(false)

                when (result) {
                    is NetworkResult.Success -> {
                        currentEmail = email
                        Toast.makeText(this@ForgotPasswordActivity, result.data, Toast.LENGTH_LONG).show()
                        showLinkSentStep(email)
                    }
                    is NetworkResult.Error -> {
                        binding.tilEmail.error = result.message
                        Toast.makeText(this@ForgotPasswordActivity, result.message, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        // --- STEP 2: LINK SENT ACTIONS ---
        binding.btnOpenEmailApp.setOnClickListener {
            try {
                val emailIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_APP_EMAIL)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(emailIntent)
            } catch (_: Exception) {
                val fallbackIntent = Intent(Intent.ACTION_VIEW).apply {
                    data = android.net.Uri.parse("mailto:")
                }
                try {
                    startActivity(fallbackIntent)
                } catch (_: Exception) {
                    Toast.makeText(this, "Please open your email application to tap the reset link.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        binding.tvResendLink.setOnClickListener {
            if (!isResendEnabled || currentEmail.isBlank()) return@setOnClickListener

            lifecycleScope.launch {
                binding.tvResendLink.isEnabled = false
                OAuthLoopbackServer.start(this@ForgotPasswordActivity, 1_800_000L)
                val result = userRepository.sendPasswordResetLink(currentEmail)
                when (result) {
                    is NetworkResult.Success -> {
                        Toast.makeText(this@ForgotPasswordActivity, "New reset link sent to $currentEmail", Toast.LENGTH_SHORT).show()
                        startResendTimer()
                    }
                    is NetworkResult.Error -> {
                        binding.tvResendLink.isEnabled = true
                        Toast.makeText(this@ForgotPasswordActivity, result.message, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        // --- STEP 3: SET NEW PASSWORD ---
        binding.btnUpdatePassword.setOnClickListener {
            val token = recoveryAccessToken
            if (token.isNullOrBlank()) {
                Toast.makeText(this, "Recovery session expired. Please request a new link.", Toast.LENGTH_LONG).show()
                showRequestLinkStep()
                return@setOnClickListener
            }

            val newPass = binding.etNewPassword.text.toString()
            val confirmPass = binding.etConfirmPassword.text.toString()

            var isValid = true
            if (newPass.length < 6) {
                binding.tilNewPassword.error = "Password must be at least 6 characters"
                binding.etNewPassword.requestFocus()
                isValid = false
            } else {
                binding.tilNewPassword.error = null
            }

            if (newPass != confirmPass) {
                binding.tilConfirmPassword.error = "Passwords do not match"
                if (isValid) binding.etConfirmPassword.requestFocus()
                isValid = false
            } else {
                binding.tilConfirmPassword.error = null
            }

            if (!isValid) return@setOnClickListener

            setLoadingUpdate(true)

            lifecycleScope.launch {
                val result = userRepository.updatePasswordWithRecoverySession(token, newPass)
                setLoadingUpdate(false)

                when (result) {
                    is NetworkResult.Success -> {
                        OAuthLoopbackServer.stop()
                        hideKeyboard()
                        showSuccessStep()
                    }
                    is NetworkResult.Error -> {
                        binding.tilNewPassword.error = result.message
                        Toast.makeText(this@ForgotPasswordActivity, result.message, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        // --- STEP 4: SUCCESS ACTION ---
        binding.btnSuccessSignIn.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java).apply {
                if (currentEmail.isNotBlank()) {
                    putExtra("prefilled_email", currentEmail)
                }
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(intent)
            finish()
        }
    }

    private fun showRequestLinkStep() {
        resendTimer?.cancel()
        binding.tvHeaderTitle.text = "Forgot Password?"
        binding.layoutLinkSent.visibility = View.GONE
        binding.layoutSetNewPassword.visibility = View.GONE
        binding.layoutSuccess.visibility = View.GONE
        crossFade(binding.layoutRequestLink)
        binding.etEmail.requestFocus()
    }

    private fun showLinkSentStep(email: String) {
        binding.tvHeaderTitle.text = "Reset Link Sent"
        binding.tvSentEmail.text = email
        binding.layoutRequestLink.visibility = View.GONE
        binding.layoutSetNewPassword.visibility = View.GONE
        binding.layoutSuccess.visibility = View.GONE
        crossFade(binding.layoutLinkSent)
        startResendTimer()
    }

    private fun showSetNewPasswordStep(email: String) {
        resendTimer?.cancel()
        binding.tvHeaderTitle.text = "Set New Password"
        binding.tvResetTargetEmail.text = if (email.isNotBlank()) "Account: $email" else "Choose a new password for your account."
        binding.etNewPassword.setText("")
        binding.etConfirmPassword.setText("")
        binding.tilNewPassword.error = null
        binding.tilConfirmPassword.error = null

        binding.layoutRequestLink.visibility = View.GONE
        binding.layoutLinkSent.visibility = View.GONE
        binding.layoutSuccess.visibility = View.GONE
        crossFade(binding.layoutSetNewPassword)
        binding.etNewPassword.requestFocus()
    }

    private fun showSuccessStep() {
        resendTimer?.cancel()
        binding.tvHeaderTitle.text = "Password Reset"
        binding.layoutRequestLink.visibility = View.GONE
        binding.layoutLinkSent.visibility = View.GONE
        binding.layoutSetNewPassword.visibility = View.GONE
        crossFade(binding.layoutSuccess)
    }

    private fun startResendTimer() {
        resendTimer?.cancel()
        isResendEnabled = false
        binding.tvResendLink.isClickable = false
        binding.tvResendLink.setTextColor(getColor(com.aroundus.app.R.color.text_secondary))

        resendTimer = object : CountDownTimer(60_000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = millisUntilFinished / 1000
                binding.tvResendLink.text = "Didn't get the email? Resend in ${seconds}s"
            }

            override fun onFinish() {
                isResendEnabled = true
                binding.tvResendLink.isClickable = true
                binding.tvResendLink.text = "Didn't get the email? Resend link"
                binding.tvResendLink.setTextColor(getColor(com.aroundus.app.R.color.primary))
            }
        }.start()
    }

    private fun setLoadingSend(loading: Boolean) {
        binding.btnSendReset.isEnabled = !loading
        binding.btnSendReset.text = if (loading) "" else "Send Reset Link"
        binding.pbLoadingSend.visibility = if (loading) View.VISIBLE else View.GONE
    }

    private fun setLoadingUpdate(loading: Boolean) {
        binding.btnUpdatePassword.isEnabled = !loading
        binding.btnUpdatePassword.text = if (loading) "" else "Save New Password"
        binding.pbLoadingUpdate.visibility = if (loading) View.VISIBLE else View.GONE
    }

    private fun crossFade(view: View) {
        view.visibility = View.VISIBLE
        val anim = AlphaAnimation(0f, 1f).apply {
            duration = 350
            interpolator = DecelerateInterpolator()
        }
        view.startAnimation(anim)
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(currentFocus?.windowToken ?: binding.root.windowToken, 0)
    }
}
