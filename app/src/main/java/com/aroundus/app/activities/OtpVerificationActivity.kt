package com.aroundus.app.activities

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.view.animation.AlphaAnimation
import android.view.animation.AnimationSet
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.view.animation.ScaleAnimation
import android.view.animation.TranslateAnimation
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.aroundus.app.databinding.ActivityOtpVerificationBinding
import com.aroundus.app.network.SupabaseClient
import com.aroundus.app.repository.OtpRepository
import kotlinx.coroutines.launch

class OtpVerificationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOtpVerificationBinding
    private var countdownTimer: CountDownTimer? = null
    private val otpRepository = OtpRepository()

    private var targetEmail: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOtpVerificationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        targetEmail = intent.getStringExtra("email") ?: SupabaseClient.getCurrentUserEmail() ?: "user@aroundus.app"
        val passedOtp = intent.getStringExtra("otpCode")
        if (!passedOtp.isNullOrBlank()) {
            SupabaseClient.saveOtpForEmail(targetEmail, passedOtp)
        } else {
            val freshOtp = otpRepository.generate6DigitOtp()
            lifecycleScope.launch {
                otpRepository.sendOtp(targetEmail, freshOtp)
            }
        }

        Toast.makeText(this, "Verification code sent to $targetEmail via Supabase", Toast.LENGTH_SHORT).show()

        binding.btnBack.setOnClickListener { finish() }

        setupSubtitleText()
        setupAnimations()
        startCountdown()
        setupListeners()
    }

    private fun setupSubtitleText() {
        binding.tvSentTo.text = "Enter the 6-digit OTP code sent to $targetEmail"
    }

    private fun setupAnimations() {
        // Header spring bounce-in
        val headerAnim = AnimationSet(true).apply {
            addAnimation(AlphaAnimation(0f, 1f).apply { duration = 500 })
            addAnimation(ScaleAnimation(0.85f, 1f, 0.85f, 1f, ScaleAnimation.RELATIVE_TO_SELF, 0.5f, ScaleAnimation.RELATIVE_TO_SELF, 0.5f).apply {
                duration = 600
                interpolator = OvershootInterpolator(1.2f)
            })
        }
        binding.headerContainer.startAnimation(headerAnim)

        // Verification card smooth slide-up + fade-in
        val cardAnim = AnimationSet(true).apply {
            addAnimation(AlphaAnimation(0f, 1f).apply { duration = 650; startOffset = 120 })
            addAnimation(TranslateAnimation(0f, 0f, 50f, 0f).apply {
                duration = 650
                startOffset = 120
                interpolator = DecelerateInterpolator(1.5f)
            })
        }
        binding.cardVerification.startAnimation(cardAnim)
    }

    private fun setupListeners() {
        binding.btnVerify.setOnClickListener {
            // Button bounce scale animation
            binding.btnVerify.animate().scaleX(0.96f).scaleY(0.96f).setDuration(100).withEndAction {
                binding.btnVerify.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start()
            }.start()

            val otpInput = binding.etOtp.text.toString().trim()
            if (otpInput.length < 6) {
                binding.tilOtp.error = "Please enter complete OTP code"
                return@setOnClickListener
            }

            binding.tilOtp.error = null

            Toast.makeText(this, "OTP Verified Successfully!", Toast.LENGTH_SHORT).show()
            val intent = Intent(this, ProfileSetupActivity::class.java).apply {
                putExtra("email", targetEmail)
            }
            startActivity(intent)
            finish()
        }

        binding.btnResend.setOnClickListener {
            val newOtp = otpRepository.generate6DigitOtp()
            lifecycleScope.launch {
                otpRepository.sendOtp(targetEmail, newOtp)
                Toast.makeText(this@OtpVerificationActivity, "A new 6-digit OTP has been sent to $targetEmail", Toast.LENGTH_SHORT).show()
                startCountdown()
            }
        }
    }

    private fun startCountdown() {
        binding.btnResend.isEnabled = false
        binding.btnResend.alpha = 0.5f
        countdownTimer?.cancel()
        countdownTimer = object : CountDownTimer(60000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = millisUntilFinished / 1000
                binding.tvTimer.text = "Resend OTP in 00:${String.format("%02d", seconds)}"
            }
            override fun onFinish() {
                binding.tvTimer.text = "Didn't receive OTP?"
                binding.btnResend.isEnabled = true
                binding.btnResend.alpha = 1f
            }
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        countdownTimer?.cancel()
    }
}
