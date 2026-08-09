package com.aroundus.app.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.aroundus.app.R
import com.aroundus.app.adapters.OnboardingAdapter
import com.aroundus.app.databinding.ActivityOnboardingBinding

class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding
    private lateinit var onboardingAdapter: OnboardingAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupViewPager()
        setupButtons()
    }

    private fun setupViewPager() {
        onboardingAdapter = OnboardingAdapter(this)
        binding.viewPager.adapter = onboardingAdapter
        binding.dotsIndicator.attachTo(binding.viewPager)
        binding.viewPager.isUserInputEnabled = true

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                val isLastPage = position == onboardingAdapter.itemCount - 1
                binding.btnNext.text = if (isLastPage) getString(R.string.btn_get_started) else getString(R.string.btn_next)
                binding.btnNext.setIconResource(if (isLastPage) R.drawable.ic_chevron_right else 0)
                binding.btnSkip.visibility = if (isLastPage) View.GONE else View.VISIBLE
                binding.tvTermsFooter.visibility = if (isLastPage) View.VISIBLE else View.GONE
            }
        })
    }

    private fun setupButtons() {
        binding.btnNext.setOnClickListener {
            if (binding.viewPager.currentItem < onboardingAdapter.itemCount - 1) {
                binding.viewPager.currentItem += 1
            } else {
                navigateToLogin()
            }
        }

        binding.btnSkip.setOnClickListener {
            navigateToLogin()
        }
    }

    private fun navigateToLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }
}
