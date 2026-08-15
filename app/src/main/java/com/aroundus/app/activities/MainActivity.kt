package com.aroundus.app.activities

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.aroundus.app.R
import com.aroundus.app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.navHostFragment) as NavHostFragment
        navController = navHostFragment.navController

        setupBottomNavigation()
    }

    private fun setupBottomNavigation() {
        binding.navHome.setOnClickListener {
            resetNavTabs()
            binding.ivNavHome.setColorFilter(getColor(R.color.primary))
            binding.tvNavHome.setTextColor(getColor(R.color.primary))
            navController.navigate(R.id.homeFragment)
        }

        binding.navGroups.setOnClickListener {
            resetNavTabs()
            binding.ivNavGroups.setColorFilter(getColor(R.color.primary))
            binding.tvNavGroups.setTextColor(getColor(R.color.primary))
            navController.navigate(R.id.communityFragment)
        }

        binding.navPost.setOnClickListener {
            navController.navigate(R.id.createPostFragment)
        }

        binding.navMessages.setOnClickListener {
            resetNavTabs()
            binding.ivNavMessages.setColorFilter(getColor(R.color.primary))
            binding.tvNavMessages.setTextColor(getColor(R.color.primary))
            navController.navigate(R.id.chatListFragment)
        }

        binding.navProfile.setOnClickListener {
            resetNavTabs()
            binding.ivNavProfile.setColorFilter(getColor(R.color.primary))
            binding.tvNavProfile.setTextColor(getColor(R.color.primary))
            navController.navigate(R.id.myProfileFragment)
        }
    }

    private fun resetNavTabs() {
        val inactiveColor = android.graphics.Color.parseColor("#94A3B8")
        binding.ivNavHome.setColorFilter(inactiveColor)
        binding.tvNavHome.setTextColor(inactiveColor)
        binding.ivNavGroups.setColorFilter(inactiveColor)
        binding.tvNavGroups.setTextColor(inactiveColor)
        binding.ivNavMessages.setColorFilter(inactiveColor)
        binding.tvNavMessages.setTextColor(inactiveColor)
        binding.ivNavProfile.setColorFilter(inactiveColor)
        binding.tvNavProfile.setTextColor(inactiveColor)
    }
}
