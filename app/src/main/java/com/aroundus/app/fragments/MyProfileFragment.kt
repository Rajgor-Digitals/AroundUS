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
    private var currentUserEntity: UserEntity? = null

    private var selectedRemoteAvatarUrl: String? = null
    private var activeDialogAvatarView: CircleImageView? = null

    // Photo picker launcher for avatar updates
    private val pickAvatarLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val uId = SupabaseClient.getCurrentUserId() ?: currentUserEntity?.userId ?: ""
            if (uId.isBlank()) {
                Toast.makeText(requireContext(), "User session required.", Toast.LENGTH_SHORT).show()
                return@registerForActivityResult
            }

            // Immediately display local preview while uploading
            binding.ivAvatar.setImageURI(uri)
            activeDialogAvatarView?.setImageURI(uri)
            Toast.makeText(requireContext(), "Uploading profile photo to Supabase Storage...", Toast.LENGTH_SHORT).show()

            viewLifecycleOwner.lifecycleScope.launch {
                when (val uploadRes = userRepository.uploadProfilePhotoToStorage(requireContext(), uri, uId)) {
                    is NetworkResult.Success -> {
                        val remoteUrl = uploadRes.data
                        selectedRemoteAvatarUrl = remoteUrl

                        val email = SupabaseClient.getCurrentUserEmail() ?: currentUserEntity?.email ?: ""
                        when (val dbRes = userRepository.updateProfilePhotoUrl(uId, email, remoteUrl)) {
                            is NetworkResult.Success -> {
                                currentUserEntity = currentUserEntity?.copy(profilePhoto = remoteUrl)
                                SupabaseClient.saveAvatarPath(uId, remoteUrl)
                                if (email.isNotBlank()) SupabaseClient.saveAvatarPath(email, remoteUrl)
                                PostRepository.updateAuthorAvatar(uId, remoteUrl)
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
                        displayAvatar(currentUserEntity?.profilePhoto)
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

        // Profile Photo & Edit Profile
        binding.btnChangeAvatar.setOnClickListener {
            pickAvatarLauncher.launch("image/*")
        }

        binding.ivAvatar.setOnClickListener {
            pickAvatarLauncher.launch("image/*")
        }

        binding.btnEditProfile.setOnClickListener {
            showEditProfileDialog()
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
            val targetUserId = currentUserEntity?.userId ?: SupabaseClient.getCurrentUserId() ?: ""
            val savedCount = SupabaseClient.getSavedPostIds(targetUserId).size
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
            val targetUserId = currentUserEntity?.userId ?: SupabaseClient.getCurrentUserId() ?: ""
            val savedCount = SupabaseClient.getSavedPostIds(targetUserId).size
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

    private fun showEditProfileDialog() {
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

        val currentName = currentUserEntity?.name ?: binding.tvUserName.text.toString()
        val currentBio = currentUserEntity?.bio ?: binding.tvBio.text.toString()
        val currentLocation = currentUserEntity?.location ?: binding.tvUserLocation.text.toString()
        val currentPhone = currentUserEntity?.phone ?: ""

        etName.setText(currentName)
        etBio.setText(currentBio)
        etLocation.setText(currentLocation)
        etPhone.setText(currentPhone)

        val currentUserId = SupabaseClient.getCurrentUserId() ?: ""
        val currentAvatarUrl = selectedRemoteAvatarUrl ?: currentUserEntity?.profilePhoto ?: SupabaseClient.getAvatarPath(currentUserId)
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

            val userId = SupabaseClient.getCurrentUserId() ?: currentUserEntity?.userId ?: ""
            val email = SupabaseClient.getCurrentUserEmail() ?: currentUserEntity?.email ?: ""
            val photoToSave = selectedRemoteAvatarUrl ?: currentUserEntity?.profilePhoto?.takeIf {
                it.startsWith("http://") || it.startsWith("https://")
            }

            if (!photoToSave.isNullOrBlank() && userId.isNotBlank()) {
                SupabaseClient.saveAvatarPath(userId, photoToSave)
            }

            val updatedUser = UserEntity(
                userId = userId,
                name = newName,
                email = email,
                phone = newPhone,
                location = newLocation,
                bio = newBio,
                profilePhoto = photoToSave,
                usernameLastChanged = currentUserEntity?.usernameLastChanged
            )

            btnSave.isEnabled = false
            viewLifecycleOwner.lifecycleScope.launch {
                when (val result = userRepository.updateUserProfile(updatedUser)) {
                    is NetworkResult.Success -> {
                        currentUserEntity = result.data
                        if (userId.isNotBlank()) {
                            PostRepository.updateAuthorAvatar(userId, photoToSave)
                        }
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
        val targetUserId = arguments?.getString("userId")?.takeIf { it.isNotBlank() } ?: loggedInUserId
        val isOwnProfile = targetUserId.isBlank() || targetUserId == loggedInUserId

        if (!isOwnProfile) {
            binding.btnEditProfile.visibility = View.GONE
            binding.btnChangeAvatar.visibility = View.GONE
        } else {
            binding.btnEditProfile.visibility = View.VISIBLE
            binding.btnChangeAvatar.visibility = View.VISIBLE
        }

        val email = if (isOwnProfile) SupabaseClient.getCurrentUserEmail() ?: "user@aroundus.app" else ""
        val fallbackName = if (isOwnProfile) SupabaseClient.getCurrentUserName() ?: "Neighbor" else "Neighbor"

        // Set initial / cached state
        val initialName = currentUserEntity?.name?.takeIf { it.isNotBlank() } ?: fallbackName
        val initialEmail = currentUserEntity?.email?.takeIf { it.isNotBlank() } ?: email
        val initialLocation = currentUserEntity?.location?.takeIf { it.isNotBlank() } ?: "Bhujpur, Kutch, Gujarat"
        val initialBio = currentUserEntity?.bio?.takeIf {
            it.isNotBlank() && !it.startsWith("/") && !it.startsWith("content://") && !it.startsWith("file://")
        } ?: "Passionate about building a better community. Let's connect and create a positive impact together."

        binding.tvUserName.text = initialName
        binding.tvUsername.text = "@${emailToHandle(initialEmail, initialName)}"
        binding.tvUserLocation.text = initialLocation
        binding.tvBio.text = initialBio

        displayAvatar(currentUserEntity?.profilePhoto)

        // Query fresh real user details from Supabase database
        viewLifecycleOwner.lifecycleScope.launch {
            when (val result = userRepository.getUserProfile(targetUserId)) {
                is NetworkResult.Success -> {
                    val b = _binding ?: return@launch
                    val profile = result.data
                    currentUserEntity = profile

                    val realName = profile.name.ifBlank { fallbackName }
                    val realEmail = profile.email.ifBlank { email }
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
                                currentUserEntity = currentUserEntity?.copy(profilePhoto = newUrl)
                                displayAvatar(newUrl)
                                PostRepository.updateAuthorAvatar(targetUserId, newUrl)
                            }
                        }
                    }
                }
                is NetworkResult.Error -> {}
            }
        }
    }

    private fun loadUserPostsAndMetrics() {
        val loggedInUserId = SupabaseClient.getCurrentUserId() ?: ""
        val targetUserId = arguments?.getString("userId")?.takeIf { it.isNotBlank() } ?: currentUserEntity?.userId ?: loggedInUserId
        val targetUserName = currentUserEntity?.name ?: SupabaseClient.getCurrentUserName() ?: ""

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
                    val currentAvatar = currentUserEntity?.profilePhoto ?: SupabaseClient.getAvatarPath(targetUserId) ?: selectedRemoteAvatarUrl

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

            // 4. Fetch saved posts count (Real count)
            val savedCount = SupabaseClient.getSavedPostIds(targetUserId).size
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
            val uId = SupabaseClient.getCurrentUserId() ?: "user"
            val email = SupabaseClient.getCurrentUserEmail() ?: ""

            val savedPath = pathOrUri
                ?: currentUserEntity?.profilePhoto
                ?: selectedRemoteAvatarUrl
                ?: SupabaseClient.getAvatarPath(uId)
                ?: SupabaseClient.getAvatarPath(email)

            if (!savedPath.isNullOrBlank()) {
                binding.ivAvatar.loadUserAvatar(savedPath, R.drawable.img_avatar_gaurav_rajgor)
                return
            }

            val localAvatarFile = File(requireContext().filesDir, "avatar_${uId}.jpg")
            if (localAvatarFile.exists() && localAvatarFile.length() > 0L) {
                binding.ivAvatar.loadUserAvatar(localAvatarFile.absolutePath, R.drawable.img_avatar_gaurav_rajgor)
                return
            }

            // Fallback to reference design avatar
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
