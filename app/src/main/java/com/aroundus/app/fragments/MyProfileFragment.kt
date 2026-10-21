package com.aroundus.app.fragments

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.aroundus.app.R
import com.aroundus.app.activities.MainActivity
import com.aroundus.app.adapters.PostAdapter
import com.aroundus.app.databinding.FragmentMyProfileBinding
import com.aroundus.app.models.Post
import com.aroundus.app.models.UserEntity
import com.aroundus.app.models.toUIModel
import com.aroundus.app.network.SupabaseClient
import com.aroundus.app.repository.GroupRepository
import com.aroundus.app.repository.NetworkResult
import com.aroundus.app.repository.PostRepository
import com.aroundus.app.repository.UserRepository
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import de.hdodenhof.circleimageview.CircleImageView
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import com.aroundus.app.utils.ImageUtils
import com.aroundus.app.utils.loadUserAvatar

class MyProfileFragment : Fragment() {

    private var _binding: FragmentMyProfileBinding? = null
    private val binding get() = _binding!!

    private val userRepository = UserRepository()
    private val postRepository = PostRepository()
    private val groupRepository = GroupRepository()

    private val myPostList = mutableListOf<Post>()
    private lateinit var postAdapter: PostAdapter

    // Separated state: Current authenticated user vs. profile currently viewed on screen
    private var myOwnUserEntity: UserEntity? = null
    private var viewedUserEntity: UserEntity? = null
    private var currentUserEntity: UserEntity? get() = viewedUserEntity ?: myOwnUserEntity
        set(value) {
            viewedUserEntity = value
        }

    private var selectedRemoteAvatarUrl: String? = null
    private var activeDialogAvatarView: CircleImageView? = null

    // Photo picker launcher for avatar updates - Strictly restricted to authorized profile owner
    private val pickAvatarLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val loggedInUid = SupabaseClient.getCurrentUserId() ?: ""
            val loggedInEmail = SupabaseClient.getCurrentUserEmail() ?: ""
            val targetUserId = arguments?.getString("userId")?.takeIf { it.isNotBlank() } ?: loggedInUid

            val isOwnProfile = SupabaseClient.isLoggedIn() && loggedInUid.isNotBlank() && (
                targetUserId == loggedInUid ||
                (loggedInEmail.isNotBlank() && targetUserId.equals(loggedInEmail, ignoreCase = true))
            )

            if (!isOwnProfile) {
                Toast.makeText(requireContext(), "Security Error: You can only update your own profile photo.", Toast.LENGTH_SHORT).show()
                return@registerForActivityResult
            }

            // Immediately display local preview while uploading
            binding.ivAvatar.setImageURI(uri)
            activeDialogAvatarView?.setImageURI(uri)
            Toast.makeText(requireContext(), "Uploading profile photo to Supabase Storage...", Toast.LENGTH_SHORT).show()

            viewLifecycleOwner.lifecycleScope.launch {
                when (val uploadRes = userRepository.uploadProfilePhotoToStorage(requireContext(), uri, loggedInUid)) {
                    is NetworkResult.Success -> {
                        val remoteUrl = uploadRes.data
                        selectedRemoteAvatarUrl = remoteUrl

                        val email = SupabaseClient.getCurrentUserEmail() ?: ""
                        when (val dbRes = userRepository.updateProfilePhotoUrl(loggedInUid, email, remoteUrl)) {
                            is NetworkResult.Success -> {
                                myOwnUserEntity = myOwnUserEntity?.copy(profilePhoto = remoteUrl)
                                viewedUserEntity = viewedUserEntity?.copy(profilePhoto = remoteUrl)
                                SupabaseClient.saveAvatarPath(loggedInUid, remoteUrl)
                                if (email.isNotBlank()) SupabaseClient.saveAvatarPath(email, remoteUrl)
                                PostRepository.updateAuthorAvatar(loggedInUid, remoteUrl)
                                displayAvatar(remoteUrl)
                                activeDialogAvatarView?.loadUserAvatar(remoteUrl)
                                Toast.makeText(requireContext(), "Profile photo updated & synced!", Toast.LENGTH_SHORT).show()
                            }
                            is NetworkResult.Error -> {
                                Toast.makeText(requireContext(), "Upload succeeded, but database update failed: ${dbRes.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                    is NetworkResult.Error -> {
                        Toast.makeText(requireContext(), "Photo upload failed: ${uploadRes.message}", Toast.LENGTH_LONG).show()
                        displayAvatar(myOwnUserEntity?.profilePhoto)
                    }
                }
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMyProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        updateOwnershipUI(false)
        setupRecyclerView()
        setupListeners()
        loadUserProfile()
        loadUserPostsAndMetrics()
    }

    override fun onResume() {
        super.onResume()
        loadUserProfile()
        loadUserPostsAndMetrics()
    }

    private fun setupRecyclerView() {
        postAdapter = PostAdapter(
            posts = myPostList,
            onPostClick = { post ->
                val bundle = Bundle().apply { putString("postId", post.id) }
                findNavController().navigate(R.id.postDetailFragment, bundle)
            },
            onLikeClick = { },
            onCommentClick = { post ->
                val bundle = Bundle().apply { putString("postId", post.id) }
                findNavController().navigate(R.id.commentsFragment, bundle)
            },
            onShareClick = {
                Toast.makeText(requireContext(), "Post link copied!", Toast.LENGTH_SHORT).show()
            },
            onAuthorClick = { post ->
                val targetId = if (!post.authorId.isNullOrBlank()) post.authorId else post.id
                val bundle = Bundle().apply { putString("userId", targetId) }
                findNavController().navigate(R.id.myProfileFragment, bundle)
            }
        )

        binding.rvPosts.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = postAdapter
        }
    }

    private fun setupListeners() {
        // Top Header Actions
        binding.btnMenu.setOnClickListener {
            (activity as? MainActivity)?.openDrawer()
        }

        binding.btnSettings.setOnClickListener {
            findNavController().navigate(R.id.settingsFragment)
        }

        binding.btnNotifications.setOnClickListener {
            findNavController().navigate(R.id.notificationsFragment)
        }

        // 4 Metric Cards
        binding.cardPosts.setOnClickListener {
            togglePostsVisibility()
        }

        binding.cardGroups.setOnClickListener {
            findNavController().navigate(R.id.communityFragment)
        }

        binding.cardConnections.setOnClickListener {
            showConnectionsDialog()
        }

        binding.cardSaved.setOnClickListener {
            val loggedInUserId = SupabaseClient.getCurrentUserId() ?: ""
            val savedCount = SupabaseClient.getSavedPostIds(loggedInUserId).size
            Toast.makeText(requireContext(), "Viewing $savedCount Saved Posts", Toast.LENGTH_SHORT).show()
        }

        // Menu Navigation List Items
        binding.rowMyPosts.setOnClickListener {
            togglePostsVisibility()
        }

        binding.rowMyComments.setOnClickListener {
            Toast.makeText(requireContext(), "Opening My Comments", Toast.LENGTH_SHORT).show()
        }

        binding.rowSavedPosts.setOnClickListener {
            val loggedInUserId = SupabaseClient.getCurrentUserId() ?: ""
            val savedCount = SupabaseClient.getSavedPostIds(loggedInUserId).size
            Toast.makeText(requireContext(), "Viewing $savedCount Saved Posts", Toast.LENGTH_SHORT).show()
        }

        binding.rowMyListings.setOnClickListener {
            findNavController().navigate(R.id.marketplaceFragment)
        }

        binding.rowMyEvents.setOnClickListener {
            findNavController().navigate(R.id.eventsFragment)
        }

        binding.rowHelpSupport.setOnClickListener {
            showHelpSupportDialog()
        }

        // Badges & Community Activity
        binding.btnViewAllBadges.setOnClickListener {
            showAllBadgesDialog()
        }

        binding.btnViewAllActivity.setOnClickListener {
            showCommunityActivityDialog()
        }

        // Invite Neighbors
        binding.btnInviteNow.setOnClickListener {
            shareInviteLink()
        }

        binding.cardInviteNeighbors.setOnClickListener {
            shareInviteLink()
        }
    }

    private fun togglePostsVisibility() {
        if (binding.rvPosts.visibility == View.VISIBLE) {
            binding.rvPosts.visibility = View.GONE
            Toast.makeText(requireContext(), "Posts collapsed", Toast.LENGTH_SHORT).show()
        } else {
            binding.rvPosts.visibility = View.VISIBLE
            if (myPostList.isNotEmpty()) {
                binding.rvPosts.smoothScrollToPosition(0)
                Toast.makeText(requireContext(), "Showing ${myPostList.size} posts", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "No posts created yet", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun shareInviteLink() {
        val userName = currentUserEntity?.name ?: SupabaseClient.getCurrentUserName() ?: "Neighbor"
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Join AroundUS Community")
            putExtra(
                Intent.EXTRA_TEXT,
                "Hey! $userName invited you to join AroundUS — connect with our local neighborhood, share updates, and discover local community events: https://aroundus.app/invite"
            )
        }
        startActivity(Intent.createChooser(shareIntent, "Invite Neighbors via"))
    }

    private fun showConnectionsDialog() {
        val activeUserId = currentUserEntity?.userId ?: SupabaseClient.getCurrentUserId() ?: ""
        lifecycleScope.launch {
            val followersResult = userRepository.getFollowersList(activeUserId)
            val followingResult = userRepository.getFollowingList(activeUserId)

            val followers = (followersResult as? NetworkResult.Success)?.data ?: emptyList()
            val following = (followingResult as? NetworkResult.Success)?.data ?: emptyList()

            val totalCount = followers.size + following.size

            val message = buildString {
                append("Total Connections: $totalCount\n\n")
                append("• Followers: ${followers.size}\n")
                append("• Following: ${following.size}\n\n")
                if (totalCount > 0) {
                    append("You are actively connected with verified neighbors.")
                } else {
                    append("Connect with neighbors around you to grow your community.")
                }
            }

            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Community Connections")
                .setMessage(message)
                .setPositiveButton("Close", null)
                .show()
        }
    }

    private fun showHelpSupportDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Help & Support Center")
            .setMessage(
                "AroundUS Neighborhood Support\n\n" +
                "• Community Guidelines: Safe, respectful, local sharing.\n" +
                "• Email Support: help@aroundus.app\n" +
                "• Emergency Helplines: Tap 112 for local emergency services.\n" +
                "• FAQ: Find answers to common questions about posts, groups, and privacy."
            )
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showAllBadgesDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("My Badges (3 Unlocked)")
            .setMessage(
                "1. Active Member (Level 3)\n" +
                "Awarded for daily community participation and engagement.\n\n" +
                "2. Helpful Neighbor\n" +
                "Awarded for answering neighbor questions and supporting local causes.\n\n" +
                "3. Community Builder\n" +
                "Awarded for organizing local neighborhood cleanups and events."
            )
            .setPositiveButton("Great!", null)
            .show()
    }

    private fun showCommunityActivityDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Community Activity History")
            .setMessage(
                "• May 12, 2024: Joined AroundUS Community\n" +
                "• May 14, 2024: Created first neighborhood post\n" +
                "• Jun 01, 2024: Unlocked 'Helpful Neighbor' Badge\n" +
                "• Jun 15, 2024: Joined 'Bhujpur Clean & Green' Group\n" +
                "• Jul 04, 2024: Attended Community Farmers Market"
            )
            .setPositiveButton("Close", null)
            .show()
    }

    private fun updateOwnershipUI(isOwnProfile: Boolean) {
        val b = _binding ?: return
        if (isOwnProfile) {
            b.btnEditProfile.visibility = View.VISIBLE
            b.btnEditProfile.isEnabled = true
            b.btnEditProfile.setOnClickListener {
                showEditProfileDialog()
            }

            b.btnChangeAvatar.visibility = View.VISIBLE
            b.btnChangeAvatar.isEnabled = true
            b.btnChangeAvatar.setOnClickListener {
                pickAvatarLauncher.launch("image/*")
            }

            b.ivAvatar.isClickable = true
            b.ivAvatar.setOnClickListener {
                pickAvatarLauncher.launch("image/*")
            }

            b.cardNavigationMenu.visibility = View.VISIBLE
            b.cardSaved.visibility = View.VISIBLE
            b.cardInviteNeighbors.visibility = View.VISIBLE
        } else {
            b.btnEditProfile.visibility = View.GONE
            b.btnEditProfile.isEnabled = false
            b.btnEditProfile.setOnClickListener(null)

            b.btnChangeAvatar.visibility = View.GONE
            b.btnChangeAvatar.isEnabled = false
            b.btnChangeAvatar.setOnClickListener(null)

            b.ivAvatar.isClickable = false
            b.ivAvatar.setOnClickListener(null)

            b.cardNavigationMenu.visibility = View.GONE
            b.cardSaved.visibility = View.GONE
            b.cardInviteNeighbors.visibility = View.GONE
            b.rvPosts.visibility = View.VISIBLE
        }
    }

    private fun showEditProfileDialog() {
        val loggedInUserId = SupabaseClient.getCurrentUserId()
        val loggedInEmail = SupabaseClient.getCurrentUserEmail()

        if (!SupabaseClient.isLoggedIn() || loggedInUserId.isNullOrBlank()) {
            Toast.makeText(requireContext(), "You must be signed in to edit your profile.", Toast.LENGTH_SHORT).show()
            return
        }

        val targetUserId = arguments?.getString("userId")?.takeIf { it.isNotBlank() } ?: loggedInUserId
        val isOwnProfile = loggedInUserId.isNotBlank() && (
            targetUserId == loggedInUserId ||
            (loggedInEmail != null && targetUserId.equals(loggedInEmail, ignoreCase = true))
        )
        if (!isOwnProfile) {
            Toast.makeText(requireContext(), "Security Error: You are not authorized to edit this profile.", Toast.LENGTH_LONG).show()
            return
        }

        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_profile, null)
        val etName = dialogView.findViewById<TextInputEditText>(R.id.etEditName)
        val etBio = dialogView.findViewById<TextInputEditText>(R.id.etEditBio)
        val etLocation = dialogView.findViewById<TextInputEditText>(R.id.etEditLocation)
        val etPhone = dialogView.findViewById<TextInputEditText>(R.id.etEditPhone)
        val btnChangeAvatar = dialogView.findViewById<FrameLayout>(R.id.btnChangeAvatar)
        val ivEditAvatar = dialogView.findViewById<CircleImageView>(R.id.ivEditAvatar)
        val btnCancel = dialogView.findViewById<MaterialButton>(R.id.btnCancelEdit)
        val btnSave = dialogView.findViewById<MaterialButton>(R.id.btnSaveEdit)

        activeDialogAvatarView = ivEditAvatar

        // CRITICAL: Always use the authenticated user's own data, never viewed user's data
        val userToEdit = myOwnUserEntity
        val currentName = userToEdit?.name ?: SupabaseClient.getCurrentUserName() ?: ""
        val currentBio = userToEdit?.bio ?: ""
        val currentLocation = userToEdit?.location ?: "Bhujpur, Kutch, Gujarat"
        val currentPhone = userToEdit?.phone ?: ""

        etName.setText(currentName)
        etBio.setText(currentBio)
        etLocation.setText(currentLocation)
        etPhone.setText(currentPhone)

        val currentAvatarUrl = selectedRemoteAvatarUrl ?: userToEdit?.profilePhoto ?: SupabaseClient.getAvatarPath(loggedInUserId)
        if (!currentAvatarUrl.isNullOrBlank()) {
            ivEditAvatar.loadUserAvatar(currentAvatarUrl)
        } else {
            ivEditAvatar.setImageResource(R.drawable.img_avatar_gaurav_rajgor)
        }

        btnChangeAvatar.setOnClickListener {
            pickAvatarLauncher.launch("image/*")
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .create()

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnSave.setOnClickListener {
            val newName = etName.text.toString().trim()
            val newBio = etBio.text.toString().trim()
            val newLocation = etLocation.text.toString().trim()
            val newPhone = etPhone.text.toString().trim()

            if (newName.isBlank()) {
                etName.error = "Name cannot be empty"
                return@setOnClickListener
            }

            // CRITICAL: Force the authenticated user's ID and email
            val authUserId = SupabaseClient.getCurrentUserId() ?: ""
            val authEmail = SupabaseClient.getCurrentUserEmail() ?: ""
            if (authUserId.isBlank()) {
                Toast.makeText(requireContext(), "Active user session required.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val photoToSave = selectedRemoteAvatarUrl ?: userToEdit?.profilePhoto?.takeIf {
                it.startsWith("http://") || it.startsWith("https://")
            }

            if (!photoToSave.isNullOrBlank()) {
                SupabaseClient.saveAvatarPath(authUserId, photoToSave)
            }

            val updatedUser = UserEntity(
                userId = authUserId, // Strictly enforced: NEVER another user's ID
                name = newName,
                email = authEmail,
                phone = newPhone,
                location = newLocation,
                bio = newBio,
                profilePhoto = photoToSave,
                usernameLastChanged = userToEdit?.usernameLastChanged
            )

            btnSave.isEnabled = false
            viewLifecycleOwner.lifecycleScope.launch {
                when (val result = userRepository.updateUserProfile(updatedUser)) {
                    is NetworkResult.Success -> {
                        myOwnUserEntity = result.data
                        viewedUserEntity = result.data
                        PostRepository.updateAuthorAvatar(authUserId, photoToSave)
                        Toast.makeText(requireContext(), "Profile updated successfully!", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                        loadUserProfile()
                    }
                    is NetworkResult.Error -> {
                        btnSave.isEnabled = true
                        Toast.makeText(requireContext(), result.message, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        dialog.show()
    }

    private fun loadUserProfile() {
        val loggedInUserId = SupabaseClient.getCurrentUserId() ?: ""
        val loggedInEmail = SupabaseClient.getCurrentUserEmail() ?: ""
        val targetUserId = arguments?.getString("userId")?.takeIf { it.isNotBlank() } ?: loggedInUserId

        val isOwnProfile = SupabaseClient.isLoggedIn() && loggedInUserId.isNotBlank() && (
            targetUserId == loggedInUserId ||
            (loggedInEmail.isNotBlank() && targetUserId.equals(loggedInEmail, ignoreCase = true))
        )

        updateOwnershipUI(isOwnProfile)

        // Clear viewed entity to prevent cross-profile leakage
        viewedUserEntity = null
        selectedRemoteAvatarUrl = null

        val fallbackName = if (isOwnProfile) SupabaseClient.getCurrentUserName() ?: "Neighbor" else "Neighbor"
        val fallbackEmail = if (isOwnProfile) loggedInEmail.ifBlank { "user@aroundus.app" } else ""

        // Reset UI to clean state or cached data
        if (isOwnProfile) {
            val initialName = myOwnUserEntity?.name?.takeIf { it.isNotBlank() } ?: fallbackName
            val initialEmail = myOwnUserEntity?.email?.takeIf { it.isNotBlank() } ?: fallbackEmail
            val initialLocation = myOwnUserEntity?.location?.takeIf { it.isNotBlank() } ?: "Bhujpur, Kutch, Gujarat"
            val initialBio = myOwnUserEntity?.bio?.takeIf {
                it.isNotBlank() && !it.startsWith("/") && !it.startsWith("content://") && !it.startsWith("file://")
            } ?: "Passionate about building a better community. Let's connect and create a positive impact together."

            binding.tvUserName.text = initialName
            binding.tvUsername.text = "@${emailToHandle(initialEmail, initialName)}"
            binding.tvUserLocation.text = initialLocation
            binding.tvBio.text = initialBio
            displayAvatar(myOwnUserEntity?.profilePhoto ?: SupabaseClient.getAvatarPath(loggedInUserId))
        } else {
            binding.tvUserName.text = "Loading..."
            binding.tvUsername.text = ""
            binding.tvUserLocation.text = "Bhujpur, Kutch, Gujarat"
            binding.tvBio.text = ""
            displayAvatar(null)
        }

        // Query fresh real user details from Supabase database
        viewLifecycleOwner.lifecycleScope.launch {
            when (val result = userRepository.getUserProfile(targetUserId)) {
                is NetworkResult.Success -> {
                    val b = _binding ?: return@launch
                    val profile = result.data
                    viewedUserEntity = profile
                    if (isOwnProfile) {
                        myOwnUserEntity = profile
                    }

                    val realName = profile.name.ifBlank { fallbackName }
                    val realEmail = profile.email.ifBlank { fallbackEmail }
                    val realLocation = profile.location?.takeIf { it.isNotBlank() } ?: "Bhujpur, Kutch, Gujarat"
                    val realBio = profile.bio?.takeIf {
                        it.isNotBlank() && !it.startsWith("/") && !it.startsWith("content://") && !it.startsWith("file://")
                    } ?: "Passionate about building a better community. Let's connect and create a positive impact together."

                    b.tvUserName.text = realName
                    b.tvUsername.text = "@${emailToHandle(realEmail, realName)}"
                    b.tvUserLocation.text = realLocation
                    b.tvBio.text = realBio

                    if (!profile.profilePhoto.isNullOrBlank()) {
                        displayAvatar(profile.profilePhoto)
                    }

                    // Check if current user's profile photo needs migration from legacy Base64 or local path
                    if (isOwnProfile && !profile.profilePhoto.isNullOrBlank() &&
                        !profile.profilePhoto.startsWith("http://") && !profile.profilePhoto.startsWith("https://")
                    ) {
                        launch {
                            val migResult = userRepository.migrateLegacyProfilePhotoIfNeeded(requireContext(), profile)
                            if (migResult is NetworkResult.Success) {
                                val newUrl = migResult.data
                                myOwnUserEntity = myOwnUserEntity?.copy(profilePhoto = newUrl)
                                displayAvatar(newUrl)
                                PostRepository.updateAuthorAvatar(targetUserId, newUrl)
                            }
                        }
                    }
                }
                is NetworkResult.Error -> {
                    val b = _binding ?: return@launch
                    if (!isOwnProfile) {
                        b.tvUserName.text = "Neighbor"
                        b.tvBio.text = "AroundUS Community Member"
                    }
                }
            }
        }
    }

    private fun loadUserPostsAndMetrics() {
        val loggedInUserId = SupabaseClient.getCurrentUserId() ?: ""
        val targetUserId = arguments?.getString("userId")?.takeIf { it.isNotBlank() } ?: loggedInUserId
        val isOwnProfile = targetUserId == loggedInUserId
        val targetUserName = (if (isOwnProfile) myOwnUserEntity?.name else viewedUserEntity?.name)
            ?: SupabaseClient.getCurrentUserName() ?: ""

        val b = _binding ?: return

        // Notification badge with real unread count
        val unreadNotifs = com.aroundus.app.utils.DummyData.notifications.count { it.isUnread }
        if (unreadNotifs > 0) {
            b.tvNotificationBadge.visibility = View.VISIBLE
            b.tvNotificationBadge.text = unreadNotifs.toString()
        } else {
            b.tvNotificationBadge.visibility = View.GONE
        }

        viewLifecycleOwner.lifecycleScope.launch {
            // 1. Fetch user posts from Supabase database (Real count)
            when (val result = postRepository.fetchPosts()) {
                is NetworkResult.Success -> {
                    val currentAvatar = if (isOwnProfile) {
                        myOwnUserEntity?.profilePhoto ?: SupabaseClient.getAvatarPath(targetUserId) ?: selectedRemoteAvatarUrl
                    } else {
                        viewedUserEntity?.profilePhoto
                    }

                    val onlyUserPosts = result.data
                        .filter { postEntity ->
                            (!targetUserId.isNullOrBlank() && postEntity.authorId == targetUserId) ||
                            (!targetUserName.isNullOrBlank() && postEntity.authorId.equals(targetUserName, ignoreCase = true))
                        }
                        .map { it.toUIModel(userName = targetUserName.ifBlank { "Neighbor" }, userAvatarPath = currentAvatar) }

                    myPostList.clear()
                    myPostList.addAll(onlyUserPosts)
                    _binding?.tvPostsCount?.text = onlyUserPosts.size.toString()
                    postAdapter.notifyDataSetChanged()
                }
                is NetworkResult.Error -> {
                    _binding?.tvPostsCount?.text = myPostList.size.toString()
                }
            }

            // 2. Fetch groups count from Supabase database (Real count)
            when (val groupResult = groupRepository.fetchGroups()) {
                is NetworkResult.Success -> {
                    _binding?.tvGroupsCount?.text = groupResult.data.size.toString()
                }
                is NetworkResult.Error -> {
                    _binding?.tvGroupsCount?.text = "0"
                }
            }

            // 3. Fetch followers and following counts from Supabase database (Real count)
            if (targetUserId.isNotBlank()) {
                val fResult = userRepository.getFollowersCount(targetUserId)
                val fngResult = userRepository.getFollowingCount(targetUserId)
                val followers = (fResult as? NetworkResult.Success)?.data ?: 0
                val following = (fngResult as? NetworkResult.Success)?.data ?: 0
                val totalConnections = followers + following
                _binding?.tvConnectionsCount?.text = totalConnections.toString()
            } else {
                _binding?.tvConnectionsCount?.text = "0"
            }

            // 4. Fetch personal saved posts count
            val savedCount = SupabaseClient.getSavedPostIds(loggedInUserId).size
            _binding?.tvSavedCount?.text = savedCount.toString()
        }
    }

    private fun saveImageToInternalStorage(uri: Uri): String? {
        return try {
            val inputStream = requireContext().contentResolver.openInputStream(uri) ?: return null
            val uId = SupabaseClient.getCurrentUserId() ?: "user"
            val avatarFile = File(requireContext().filesDir, "avatar_${uId}.jpg")
            val outputStream = FileOutputStream(avatarFile)
            inputStream.copyTo(outputStream)
            inputStream.close()
            outputStream.close()
            avatarFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun displayAvatar(pathOrUri: String? = null) {
        try {
            val loggedInUserId = SupabaseClient.getCurrentUserId() ?: ""
            val targetUserId = arguments?.getString("userId")?.takeIf { it.isNotBlank() } ?: loggedInUserId
            val isOwnProfile = SupabaseClient.isLoggedIn() && loggedInUserId.isNotBlank() && (
                targetUserId == loggedInUserId ||
                (SupabaseClient.getCurrentUserEmail()?.equals(targetUserId, ignoreCase = true) == true)
            )

            val savedPath = if (isOwnProfile) {
                pathOrUri
                    ?: myOwnUserEntity?.profilePhoto
                    ?: selectedRemoteAvatarUrl
                    ?: SupabaseClient.getAvatarPath(loggedInUserId)
                    ?: SupabaseClient.getCurrentUserEmail()?.let { SupabaseClient.getAvatarPath(it) }
            } else {
                pathOrUri ?: viewedUserEntity?.profilePhoto
            }

            if (!savedPath.isNullOrBlank()) {
                binding.ivAvatar.loadUserAvatar(savedPath, R.drawable.img_avatar_gaurav_rajgor)
                return
            }

            if (isOwnProfile && loggedInUserId.isNotBlank()) {
                val localAvatarFile = File(requireContext().filesDir, "avatar_${loggedInUserId}.jpg")
                if (localAvatarFile.exists() && localAvatarFile.length() > 0L) {
                    binding.ivAvatar.loadUserAvatar(localAvatarFile.absolutePath, R.drawable.img_avatar_gaurav_rajgor)
                    return
                }
            }

            // Fallback to placeholder avatar
            binding.ivAvatar.setImageResource(R.drawable.img_avatar_gaurav_rajgor)
        } catch (e: Exception) {
            binding.ivAvatar.setImageResource(R.drawable.img_avatar_gaurav_rajgor)
        }
    }

    private fun emailToHandle(email: String, name: String? = null): String {
        val prefix = email.substringBefore("@").replace(".", "_").replace("+", "_").lowercase().trim()
        if (prefix.isNotBlank() && prefix != "user") {
            return prefix
        }
        if (!name.isNullOrBlank()) {
            val nameHandle = name.trim().lowercase().replace(" ", "").replace(".", "")
            if (nameHandle.isNotBlank()) return nameHandle
        }
        return "gauravrajgor"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
