package com.aroundus.app.activities

import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AlphaAnimation
import android.view.animation.AnimationSet
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.TranslateAnimation
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.aroundus.app.databinding.ActivitySplashBinding

class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        // Handle the splash screen transition
        val splashScreen = installSplashScreen()

        super.onCreate(savedInstanceState)

        try {
            binding = ActivitySplashBinding.inflate(layoutInflater)
            setContentView(binding.root)

            animateEntrance()
            animateProgressBar()

            // Navigate to MainActivity if logged in, or OnboardingActivity snappy after 900ms
            Handler(Looper.getMainLooper()).postDelayed({
                val targetActivity = if (com.aroundus.app.network.SupabaseClient.isLoggedIn()) {
                    MainActivity::class.java
                } else {
                    OnboardingActivity::class.java
                }
                val intent = Intent(this, targetActivity)
                startActivity(intent)
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                finish()
            }, 900)

        } catch (e: Exception) {
            // Fallback to avoid crash if binding fails
            e.printStackTrace()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun animateEntrance() {
        // Fast, smooth fade-in + slight slide-up for the logo section
        val fadeSlideUp = AnimationSet(true).apply {
            addAnimation(AlphaAnimation(0f, 1f).apply { duration = 350 })
            addAnimation(TranslateAnimation(0f, 0f, 20f, 0f).apply { duration = 350 })
            interpolator = DecelerateInterpolator()
        }
        binding.topSection.startAnimation(fadeSlideUp)

        // Illustration fades in slightly delayed
        val fadeIn = AlphaAnimation(0f, 1f).apply {
            duration = 400
            startOffset = 100
            interpolator = DecelerateInterpolator()
        }
        binding.ivNeighborhoodScene.startAnimation(fadeIn)

        // Bottom section fades in last
        val fadeInBottom = AlphaAnimation(0f, 1f).apply {
            duration = 350
            startOffset = 200
            interpolator = DecelerateInterpolator()
        }
        binding.bottomSection.startAnimation(fadeInBottom)
    }

    private fun animateProgressBar() {
        val progressBar: ProgressBar = binding.loadingIndicator
        progressBar.max = 100
        progressBar.progress = 0
        progressBar.isIndeterminate = false

        ObjectAnimator.ofInt(progressBar, "progress", 0, 100).apply {
            duration = 850
            interpolator = LinearInterpolator()
            start()
        }
    }
}
