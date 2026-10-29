package com.aroundus.app.activities



import android.content.Intent

import android.net.Uri

import android.os.Bundle

import android.widget.Toast

import androidx.activity.OnBackPressedCallback

import androidx.appcompat.app.AppCompatActivity

import androidx.core.view.GravityCompat

import androidx.lifecycle.lifecycleScope

import kotlinx.coroutines.launch

import androidx.navigation.NavController

import androidx.navigation.fragment.NavHostFragment

import com.aroundus.app.R

import com.aroundus.app.databinding.ActivityMainBinding

import com.aroundus.app.network.SupabaseClient

import com.aroundus.app.utils.UserPresenceManager

import com.aroundus.app.utils.loadUserAvatar

import com.google.android.material.dialog.MaterialAlertDialogBuilder

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

        setupUnreadMessagesBadgeObserver()

        requestNotificationPermission()

        handleOpenProductId(intent)

    }



    override fun onNewIntent(intent: Intent) {

        super.onNewIntent(intent)

        setIntent(intent)

        handleOpenProductId(intent)

    }



    private fun handleOpenProductId(targetIntent: Intent?) {

        val prodId = targetIntent?.getStringExtra("openProductId")

        if (!prodId.isNullOrBlank()) {

            targetIntent.removeExtra("openProductId")

            val bundle = Bundle().apply {

                putString("productId", prodId)

            }

            try {

                navController.navigate(R.id.productDetailFragment, bundle)

            } catch (_: Exception) {}

        }

    }



    private fun requestNotificationPermission() {

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {

            if (androidx.core.content.ContextCompat.checkSelfPermission(

                    this,

                    android.Manifest.permission.POST_NOTIFICATIONS

                ) != android.content.pm.PackageManager.PERMISSION_GRANTED

            ) {

                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1001)

            }

        }

    }



    override fun onResume() {

        super.onResume()

        refreshDrawerUserData()

        updateMessagesBadge(com.aroundus.app.utils.UnreadMessageManager.getTotalUnreadCount())

        val myId = com.aroundus.app.network.SupabaseClient.getCurrentUserId()

        if (!myId.isNullOrBlank()) {

            com.aroundus.app.utils.UserPresenceManager.recordUserActive(myId)

            com.aroundus.app.utils.UserPresenceManager.setUserOnline(myId, true)

        }

        com.aroundus.app.utils.MessageNotificationManager.startListeningForIncomingMessages(

            applicationContext,

            lifecycleScope

        )

    }



    override fun onPause() {

        super.onPause()

        val myId = com.aroundus.app.network.SupabaseClient.getCurrentUserId()

        if (!myId.isNullOrBlank()) {

            com.aroundus.app.utils.UserPresenceManager.recordUserActive(myId)

        }

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

            val bundle = Bundle().apply {

                putString("userId", SupabaseClient.getCurrentUserId() ?: "")

            }

            navController.navigate(R.id.myProfileFragment, bundle)

        }



        binding.navigationDrawer.menuDrawerSettings.setOnClickListener {

            closeDrawer()

            resetNavTabs()

            navController.navigate(R.id.settingsFragment)

        }



        binding.navigationDrawer.menuDrawerLogout.setOnClickListener {

            showLogoutConfirmationDialog()

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



    private fun showLogoutConfirmationDialog() {

        MaterialAlertDialogBuilder(this)

            .setTitle("Log Out")

            .setMessage("Are you sure you want to log out of AroundUS? You'll need to log back in to interact with your neighborhood.")

            .setPositiveButton("Log Out") { _, _ ->

                closeDrawer()

                performLogout()

            }

            .setNegativeButton("Cancel", null)

            .show()

    }



    private fun refreshDrawerUserData() {

        try {

            val email = SupabaseClient.getCurrentUserEmail() ?: "user@aroundus.app"

            val currentUserId = SupabaseClient.getCurrentUserId() ?: ""

            val name = SupabaseClient.getCurrentUserName() ?: email.substringBefore("@").replaceFirstChar { it.uppercase() }



            binding.navigationDrawer.tvDrawerName.text = name

            val handle = email.substringBefore("@")

            binding.navigationDrawer.tvDrawerHandle.text = "@$handle"

            val activeLocation = com.aroundus.app.utils.UserLocationManager.getSelectedLocation(this)?.displayName ?: "Location not set"

            binding.navigationDrawer.tvDrawerLocation.text = activeLocation

            binding.navigationDrawer.tvDrawerLocation.setOnClickListener {

                closeDrawer()

                val sheet = com.aroundus.app.fragments.LocationBottomSheetFragment.newInstance()

                sheet.onLocationSelected = { loc ->

                    binding.navigationDrawer.tvDrawerLocation.text = loc.displayName

                }

                sheet.show(supportFragmentManager, com.aroundus.app.fragments.LocationBottomSheetFragment.TAG)

            }



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

        val currentUid = SupabaseClient.getCurrentUserId() ?: ""

        if (currentUid.isNotBlank()) {

            UserPresenceManager.setUserOnline(currentUid, false)

        }

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

            val bundle = Bundle().apply {

                putString("userId", SupabaseClient.getCurrentUserId() ?: "")

            }

            navController.navigate(R.id.myProfileFragment, bundle)

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



    private fun setupUnreadMessagesBadgeObserver() {

        lifecycleScope.launch {

            com.aroundus.app.utils.UnreadMessageManager.totalUnreadCountFlow.collect { count ->

                updateMessagesBadge(count)

            }

        }

    }



    private fun updateMessagesBadge(count: Int) {

        if (count > 0) {

            binding.tvNavMessagesBadge.visibility = android.view.View.VISIBLE

            binding.tvNavMessagesBadge.text = if (count > 99) "99+" else count.toString()

            binding.navigationDrawer.tvDrawerMessagesBadge.visibility = android.view.View.VISIBLE

            binding.navigationDrawer.tvDrawerMessagesBadge.text = if (count > 99) "99+" else count.toString()

        } else {

            binding.tvNavMessagesBadge.visibility = android.view.View.GONE

            binding.navigationDrawer.tvDrawerMessagesBadge.visibility = android.view.View.GONE

        }

    }



    private fun resetNavTabs() {

        val inactiveColor = androidx.core.content.ContextCompat.getColor(this, R.color.nav_unselected)

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

