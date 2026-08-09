package com.aroundus.app.activities

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import androidx.appcompat.app.AppCompatActivity
import com.aroundus.app.databinding.ActivityOtpVerificationBinding

class OtpVerificationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOtpVerificationBinding
    private var countdownTimer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOtpVerificationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        startCountdown()

        binding.btnVerify.setOnClickListener {
            val otp = binding.etOtp.text.toString()
            if (otp.length < 6) {
                binding.tilOtp.error = "Please enter 6-digit OTP"
                return@setOnClickListener
            }
            binding.tilOtp.error = null
            startActivity(Intent(this, ProfileSetupActivity::class.java))
        }

        binding.btnResend.setOnClickListener {
            startCountdown()
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
