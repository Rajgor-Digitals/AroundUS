package com.aroundus.app.fragments

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.aroundus.app.R
import com.aroundus.app.activities.ConversationActivity
import com.aroundus.app.databinding.FragmentGroupDetailBinding
import com.aroundus.app.models.GroupEntity
import com.aroundus.app.models.resolveGroupCoverRes
import com.aroundus.app.network.SupabaseClient
import com.aroundus.app.repository.GroupRepository
import com.aroundus.app.repository.NetworkResult
import com.aroundus.app.repository.UserRepository
import com.aroundus.app.utils.AppRefreshManager
import com.aroundus.app.utils.RefreshTarget
import com.aroundus.app.utils.applyAroundUsTheme
import com.bumptech.glide.Glide
import kotlinx.coroutines.launch

class GroupDetailFragment : Fragment() {

    private var _binding: FragmentGroupDetailBinding? = null
    private val binding get() = _binding!!

    private val groupRepository = GroupRepository()
    private val userRepository = UserRepository()

    private var groupId: String = ""
    private var groupName: String = "Group"
    private var groupCategory: String = "Local"
    private var groupDescription: String = ""
    private var groupMembersText: String = "1 Member"
    private var isJoined: Boolean = false
    private var coverRes: Int = R.drawable.img_group_bhujpur_residents
    private var imageUrl: String? = null

    private var currentUserId: String = ""
    private var currentGroupEntity: GroupEntity? = null

    private val pickCoverLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            uploadAndSetGroupCover(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            groupId = it.getString("groupId", "")
            groupName = it.getString("groupName", "Group")
            groupCategory = it.getString("category", "Local")
            groupDescription = it.getString("description", "")
            groupMembersText = it.getString("membersText", "1 Member")
            isJoined = it.getBoolean("isJoined", false)
            coverRes = it.getInt("coverRes", resolveGroupCoverRes(groupCategory, groupName))
            imageUrl = it.getString("imageUrl")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGroupDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initViews()
        setupListeners()
        setupSwipeRefresh()
        loadLatestGroupData()
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefreshLayout.applyAroundUsTheme()
        binding.swipeRefreshLayout.setOnRefreshListener {
            loadLatestGroupData()
        }
    }

    private fun initViews() {
        binding.tvHeaderGroupName.text = groupName
        binding.tvDetailGroupName.text = groupName
        binding.tvCategoryBadge.text = groupCategory
        binding.tvDetailMembersText.text = groupMembersText
        binding.tvDetailDescription.text = groupDescription.ifBlank {
            "Welcome to $groupName. A community space for neighbors in Bhujpur to connect, collaborate, and share."
        }
        if (!imageUrl.isNullOrBlank()) {
            Glide.with(this)
                .load(imageUrl)
                .placeholder(coverRes)
                .error(coverRes)
                .centerCrop()
                .into(binding.ivGroupCover)
        } else {
            binding.ivGroupCover.setImageResource(coverRes)
        }

        updateJoinButtonUI()
    }

    private fun updateJoinButtonUI() {
        val context = requireContext()
        if (isJoined) {
            binding.btnJoinToggle.text = "Joined"
            binding.btnJoinToggle.setBackgroundResource(R.drawable.bg_button_joined)
            binding.btnJoinToggle.setTextColor(ContextCompat.getColor(context, R.color.app_green))
        } else {
            binding.btnJoinToggle.text = "Join Group"
            binding.btnJoinToggle.setBackgroundResource(R.drawable.bg_button_join_solid)
            binding.btnJoinToggle.setTextColor(Color.WHITE)
        }
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.btnShareGroup()

        binding.btnChangeGroupCover.setOnClickListener {
            pickCoverLauncher.launch("image/*")
        }

        binding.tvInviteMembers.setOnClickListener {
            shareGroupInvite()
        }

        binding.btnJoinToggle.setOnClickListener {
            toggleJoinStatus()
        }

        binding.btnGroupChat.setOnClickListener {
            openGroupChat()
        }
    }

    private fun uploadAndSetGroupCover(uri: Uri) {
        if (groupId.isBlank()) return

        // Instant visual feedback
        Glide.with(this)
            .load(uri)
            .centerCrop()
            .into(binding.ivGroupCover)

        Toast.makeText(requireContext(), "Uploading group cover...", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch {
            when (val uploadResult = groupRepository.uploadGroupImage(requireContext(), uri)) {
                is NetworkResult.Success -> {
                    val publicUrl = uploadResult.data
                    when (val updateResult = groupRepository.updateGroupImage(groupId, publicUrl)) {
                        is NetworkResult.Success -> {
                            imageUrl = publicUrl
                            AppRefreshManager.notifyDataChanged(RefreshTarget.GROUPS)
                            Toast.makeText(requireContext(), "Group cover updated!", Toast.LENGTH_SHORT).show()
                        }
                        is NetworkResult.Error -> {
                            Toast.makeText(requireContext(), "Failed to save cover: ${updateResult.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
                is NetworkResult.Error -> {
                    Toast.makeText(requireContext(), "Upload failed: ${uploadResult.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun FragmentGroupDetailBinding.btnShareGroup() {
        btnShareGroup.setOnClickListener {
            shareGroupInvite()
        }
    }

    private fun shareGroupInvite() {
        val shareText = "Join '$groupName' on AroundUS! Connect with our neighbors and stay updated on local happenings."
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, groupName)
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        startActivity(Intent.createChooser(intent, "Share Group"))
    }

    private fun openGroupChat() {
        val intent = Intent(requireContext(), ConversationActivity::class.java).apply {
            putExtra("groupId", groupId)
            putExtra("userName", groupName)
            putExtra("avatarRes", coverRes)
            putExtra("avatarUrl", imageUrl)
            putExtra("category", groupCategory)
            putExtra("members", groupMembersText)
        }
        startActivity(intent)
    }

    private fun loadLatestGroupData() {
        if (groupId.isBlank()) {
            binding.swipeRefreshLayout.isRefreshing = false
            return
        }

        lifecycleScope.launch {
            try {
                currentUserId = userRepository.resolveValidAuthorId().ifBlank {
                    SupabaseClient.getCurrentUserId() ?: ""
                }

                when (val result = groupRepository.fetchGroupById(groupId)) {
                    is NetworkResult.Success -> {
                        val entity = result.data
                        currentGroupEntity = entity
                        groupName = entity.name
                        groupCategory = entity.category ?: "Local"
                        
                        var cleanDesc = entity.description.orEmpty()
                        var remoteImageUrl: String? = null
                        if (cleanDesc.contains("[img:") && cleanDesc.contains("]")) {
                            remoteImageUrl = cleanDesc.substringAfter("[img:").substringBefore("]").trim()
                            cleanDesc = cleanDesc.substringBefore("[img:").trim()
                        }
                        if (!remoteImageUrl.isNullOrBlank()) {
                            imageUrl = remoteImageUrl
                        }
                        groupDescription = cleanDesc

                        groupMembersText = entity.formatMembersDisplay()
                        isJoined = entity.isMember(currentUserId)
                        coverRes = resolveGroupCoverRes(groupCategory, groupName)

                        initViews()
                        AppRefreshManager.markRefreshed(RefreshTarget.GROUPS)
                    }
                    is NetworkResult.Error -> {}
                }
            } finally {
                binding.swipeRefreshLayout.isRefreshing = false
            }
        }
    }

    private fun toggleJoinStatus() {
        if (groupId.isBlank()) return

        lifecycleScope.launch {
            binding.btnJoinToggle.isEnabled = false
            if (isJoined) {
                // Leave group
                when (val result = groupRepository.leaveGroup(groupId)) {
                    is NetworkResult.Success -> {
                        isJoined = false
                        currentGroupEntity = result.data
                        groupMembersText = result.data.formatMembersDisplay()
                        binding.tvDetailMembersText.text = groupMembersText
                        updateJoinButtonUI()
                        com.aroundus.app.utils.GroupJoinManager.removeGroupJoined(context, groupId)
                        AppRefreshManager.notifyDataChanged(RefreshTarget.GROUPS)
                        AppRefreshManager.notifyDataChanged(RefreshTarget.PROFILE)
                        Toast.makeText(requireContext(), "You left $groupName", Toast.LENGTH_SHORT).show()
                    }
                    is NetworkResult.Error -> {
                        Toast.makeText(requireContext(), result.message, Toast.LENGTH_LONG).show()
                    }
                }
            } else {
                // Join group
                when (val result = groupRepository.joinGroup(groupId)) {
                    is NetworkResult.Success -> {
                        isJoined = true
                        currentGroupEntity = result.data
                        groupMembersText = result.data.formatMembersDisplay()
                        binding.tvDetailMembersText.text = groupMembersText
                        updateJoinButtonUI()
                        com.aroundus.app.utils.GroupJoinManager.recordGroupJoined(context, groupId, System.currentTimeMillis())
                        AppRefreshManager.notifyDataChanged(RefreshTarget.GROUPS)
                        AppRefreshManager.notifyDataChanged(RefreshTarget.PROFILE)
                        Toast.makeText(requireContext(), "Welcome to $groupName!", Toast.LENGTH_SHORT).show()
                    }
                    is NetworkResult.Error -> {
                        Toast.makeText(requireContext(), result.message, Toast.LENGTH_LONG).show()
                    }
                }
            }
            binding.btnJoinToggle.isEnabled = true
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
