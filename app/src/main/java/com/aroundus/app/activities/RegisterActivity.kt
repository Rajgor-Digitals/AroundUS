package com.aroundus.app.activities

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.UnderlineSpan
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.aroundus.app.databinding.ActivityRegisterBinding
import com.aroundus.app.repository.UserRepository
import kotlinx.coroutines.launch

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private val userRepository = UserRepository()
    private var isPasswordVisible = false
    private var isConfirmPasswordVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupTermsText()
        setupPasswordToggles()
        setupClickListeners()
    }

    private fun setupTermsText() {
        val fullText = "I agree to the Terms of Use and Privacy Policy."
        val spannable = SpannableString(fullText)
        val greenColor = Color.parseColor("#0D8A3F")

        val termsStart = fullText.indexOf("Terms of Use")
        if (termsStart >= 0) {
            val termsEnd = termsStart + "Terms of Use".length
            spannable.setSpan(ForegroundColorSpan(greenColor), termsStart, termsEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannable.setSpan(UnderlineSpan(), termsStart, termsEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        val privacyStart = fullText.indexOf("Privacy Policy")
        if (privacyStart >= 0) {
            val privacyEnd = privacyStart + "Privacy Policy".length
            spannable.setSpan(ForegroundColorSpan(greenColor), privacyStart, privacyEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannable.setSpan(UnderlineSpan(), privacyStart, privacyEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        binding.tvTerms.text = spannable
        binding.tvTerms.setOnClickListener {
            binding.cbTerms.isChecked = !binding.cbTerms.isChecked
        }
    }

    private fun setupPasswordToggles() {
        binding.btnTogglePassword.setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            val selection = binding.etPassword.selectionEnd
            if (isPasswordVisible) {
                binding.etPassword.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                binding.btnTogglePassword.alpha = 1.0f
            } else {
                binding.etPassword.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                binding.btnTogglePassword.alpha = 0.6f
            }
            binding.etPassword.setSelection(selection.coerceAtLeast(0))
        }

        binding.btnToggleConfirmPassword.setOnClickListener {
            isConfirmPasswordVisible = !isConfirmPasswordVisible
            val selection = binding.etConfirmPassword.selectionEnd
            if (isConfirmPasswordVisible) {
                binding.etConfirmPassword.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                binding.btnToggleConfirmPassword.alpha = 1.0f
            } else {
                binding.etConfirmPassword.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                binding.btnToggleConfirmPassword.alpha = 0.6f
            }
            binding.etConfirmPassword.setSelection(selection.coerceAtLeast(0))
        }
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener { finish() }

        binding.btnNeedHelp.setOnClickListener {
            Toast.makeText(this, "AroundUS Support: Contact help@aroundus.app", Toast.LENGTH_LONG).show()
        }

        binding.btnRegister.setOnClickListener {
            val name = binding.etFullName.text.toString().trim()
            val email = binding.etEmail.text.toString().trim()
            val phone = binding.etPhone.text.toString().trim()
            val password = binding.etPassword.text.toString()
            val confirmPass = binding.etConfirmPassword.text.toString()

            var isValid = true
            if (name.isEmpty()) {
                binding.etFullName.error = "Full Name required"
                binding.etFullName.requestFocus()
                isValid = false
            } else if (email.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                binding.etEmail.error = "Valid Email required"
                binding.etEmail.requestFocus()
                isValid = false
            } else if (phone.isEmpty()) {
                binding.etPhone.error = "Phone Number required"
                binding.etPhone.requestFocus()
                isValid = false
            } else if (password.isEmpty()) {
                binding.etPassword.error = "Password required"
                binding.etPassword.requestFocus()
                isValid = false
            } else if (password.length < 6) {
                binding.etPassword.error = "Password must be at least 6 characters"
                binding.etPassword.requestFocus()
                isValid = false
            } else if (password != confirmPass) {
                binding.etConfirmPassword.error = "Passwords don't match"
                binding.etConfirmPassword.requestFocus()
                isValid = false
            } else if (!binding.cbTerms.isChecked) {
                Toast.makeText(this, "Please agree to the Terms of Use and Privacy Policy", Toast.LENGTH_SHORT).show()
                isValid = false
            }

            if (isValid) {
                lifecycleScope.launch {
                    binding.btnRegister.isEnabled = false
                    val result = userRepository.signUp(email, password, name, phone)
                    binding.btnRegister.isEnabled = true

                    when (result) {
                        is com.aroundus.app.repository.NetworkResult.Success -> {
                            val otpRepository = com.aroundus.app.repository.OtpRepository()
                            val generatedOtp = otpRepository.generate6DigitOtp()
                            otpRepository.sendOtp(email, generatedOtp)

                            Toast.makeText(this@RegisterActivity, "OTP sent to $email", Toast.LENGTH_LONG).show()

                            val intent = Intent(this@RegisterActivity, OtpVerificationActivity::class.java).apply {
                                putExtra("email", email)
                                putExtra("otpCode", generatedOtp)
                            }
                            startActivity(intent)
                        }
                        is com.aroundus.app.repository.NetworkResult.Error -> {
                            Toast.makeText(this@RegisterActivity, result.message, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }

        binding.btnGoogle.setOnClickListener {
            Toast.makeText(this, "Signing up with Google...", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, OtpVerificationActivity::class.java))
        }

        binding.btnLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }
}
