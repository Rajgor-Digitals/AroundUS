package com.aroundus.app.activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.aroundus.app.R
import com.aroundus.app.databinding.ActivityMainBinding
import com.aroundus.app.network.SupabaseClient
import com.aroundus.app.utils.loadUserAvatar
import java.io.File

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
        setupDrawer()
    }

    override fun onResume() {
        super.onResume()
        refreshDrawerUserData()
    }

    // --- DRAWER OPEN / CLOSE FUNCTIONS ---

    fun openDrawer() {
        refreshDrawerUserData()
        binding.drawerLayout.openDrawer(GravityCompat.START)
    }

    fun closeDrawer() {
        binding.drawerLayout.closeDrawer(GravityCompat.START)
    }

    fun isDrawerOpen(): Boolean {
        return binding.drawerLayout.isDrawerOpen(GravityCompat.START)
    }

    fun toggleDrawer() {
        if (isDrawerOpen()) {
            closeDrawer()
        } else {
            openDrawer()
        }
    }

    private fun setupDrawer() {
        // Close button (X) inside the drawer
        binding.navigationDrawer.btnCloseDrawer.setOnClickListener {
            closeDrawer()
        }

        // Navigation drawer menu items
        binding.navigationDrawer.menuDrawerHome.setOnClickListener {
            closeDrawer()
            resetNavTabs()
            highlightHomeTab()
            navController.navigate(R.id.homeFragment)
        }

        binding.navigationDrawer.menuDrawerCommunity.setOnClickListener {
            closeDrawer()
            resetNavTabs()
            highlightGroupsTab()
            navController.navigate(R.id.communityFragment)
        }

        binding.navigationDrawer.menuDrawerMarketplace.setOnClickListener {
            closeDrawer()
            resetNavTabs()
            navController.navigate(R.id.marketplaceFragment)
        }

        binding.navigationDrawer.menuDrawerEvents.setOnClickListener {
            closeDrawer()
            resetNavTabs()
            navController.navigate(R.id.eventsFragment)
        }

        binding.navigationDrawer.menuDrawerMessages.setOnClickListener {
            closeDrawer()
            resetNavTabs()
            highlightMessagesTab()
            navController.navigate(R.id.chatListFragment)
        }

        binding.navigationDrawer.menuDrawerNotifications.setOnClickListener {
            closeDrawer()
            resetNavTabs()
            navController.navigate(R.id.notificationsFragment)
        }

        binding.navigationDrawer.menuDrawerProfile.setOnClickListener {
            closeDrawer()
            resetNavTabs()
            highlightProfileTab()
            navController.navigate(R.id.myProfileFragment)
        }

        binding.navigationDrawer.menuDrawerSettings.setOnClickListener {
            closeDrawer()
            resetNavTabs()
            navController.navigate(R.id.settingsFragment)
        }

        binding.navigationDrawer.menuDrawerLogout.setOnClickListener {
            closeDrawer()
            performLogout()
        }

        // Handle back button: Close drawer first if open
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isDrawerOpen()) {
                    closeDrawer()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        refreshDrawerUserData()
    }

    private fun refreshDrawerUserData() {
        try {
            val email = SupabaseClient.getCurrentUserEmail() ?: "user@aroundus.app"
            val currentUserId = SupabaseClient.getCurrentUserId() ?: ""
            val name = SupabaseClient.getCurrentUserName() ?: email.substringBefore("@").replaceFirstChar { it.uppercase() }

            binding.navigationDrawer.tvDrawerName.text = name
            val handle = email.substringBefore("@")
            binding.navigationDrawer.tvDrawerHandle.text = "@$handle"
            binding.navigationDrawer.tvDrawerLocation.text = "Bhujpur, Kutch"

            // Set Avatar - prioritize remote URL from Supabase Storage
            val cachedAvatar = SupabaseClient.getAvatarPath(currentUserId)
            val avatarPath = if (!cachedAvatar.isNullOrBlank() && (cachedAvatar.startsWith("http://") || cachedAvatar.startsWith("https://"))) {
                cachedAvatar
            } else {
                val localAvatarFile = File(filesDir, "avatar_${currentUserId}.jpg")
                if (localAvatarFile.exists() && localAvatarFile.length() > 0L) {
                    localAvatarFile.absolutePath
                } else {
                    cachedAvatar
                }
            }
            binding.navigationDrawer.ivDrawerAvatar.loadUserAvatar(avatarPath)
        } catch (_: Exception) {}
    }

    private fun performLogout() {
        SupabaseClient.clearSession()
        Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show()
        val intent = Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }

    // --- BOTTOM NAVIGATION ---

    private fun setupBottomNavigation() {
        binding.navHome.setOnClickListener {
            resetNavTabs()
            highlightHomeTab()
            navController.navigate(R.id.homeFragment)
        }

        binding.navGroups.setOnClickListener {
            resetNavTabs()
            highlightGroupsTab()
            navController.navigate(R.id.communityFragment)
        }

        binding.navPost.setOnClickListener {
            navController.navigate(R.id.createPostFragment)
        }

        binding.navMessages.setOnClickListener {
            resetNavTabs()
            highlightMessagesTab()
            navController.navigate(R.id.chatListFragment)
        }

        binding.navProfile.setOnClickListener {
            resetNavTabs()
            highlightProfileTab()
            navController.navigate(R.id.myProfileFragment)
        }
    }

    private fun highlightHomeTab() {
        binding.ivNavHome.setColorFilter(getColor(R.color.primary))
        binding.tvNavHome.setTextColor(getColor(R.color.primary))
    }

    private fun highlightGroupsTab() {
        binding.ivNavGroups.setColorFilter(getColor(R.color.primary))
        binding.tvNavGroups.setTextColor(getColor(R.color.primary))
    }

    private fun highlightMessagesTab() {
        binding.ivNavMessages.setColorFilter(getColor(R.color.primary))
        binding.tvNavMessages.setTextColor(getColor(R.color.primary))
    }

    private fun highlightProfileTab() {
        binding.ivNavProfile.setColorFilter(getColor(R.color.primary))
        binding.tvNavProfile.setTextColor(getColor(R.color.primary))
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
