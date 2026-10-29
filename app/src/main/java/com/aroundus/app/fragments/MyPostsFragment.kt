package com.aroundus.app.fragments

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.aroundus.app.R
import com.aroundus.app.adapters.MyPostsAdapter
import com.aroundus.app.databinding.FragmentMyPostsBinding
import com.aroundus.app.models.Post
import com.aroundus.app.network.SupabaseClient
import com.aroundus.app.repository.LikeRepository
import com.aroundus.app.repository.NetworkResult
import com.aroundus.app.repository.PostRepository
import com.aroundus.app.repository.UserRepository
import com.aroundus.app.utils.AppRefreshManager
import com.aroundus.app.utils.PostActionUtils
import com.aroundus.app.utils.RefreshTarget
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class MyPostsFragment : Fragment() {

    private var _binding: FragmentMyPostsBinding? = null
    private val binding get() = _binding!!

    private val postRepository = PostRepository()
    private val likeRepository = LikeRepository()
    private val userRepository = UserRepository()

    private lateinit var adapter: MyPostsAdapter
    private val allUserPosts = mutableListOf<Post>()
    private var currentFilter = FilterCategory.ALL

    private var targetUserId: String? = null
    private var targetUserName: String? = null

    enum class FilterCategory {
        ALL, POSTS, ANNOUNCEMENTS, HELP
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        targetUserId = arguments?.getString("userId")?.takeIf { it.isNotBlank() }
        targetUserName = arguments?.getString("userName")?.takeIf { it.isNotBlank() }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMyPostsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setupRecyclerView()
        setupFilterChips()
        setupListeners()
        observeRefreshEvents()

        loadUserPosts()
    }

    private fun setupToolbar() {
        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.btnCreate.setOnClickListener {
            findNavController().navigate(R.id.createPostFragment)
        }

        binding.btnEmptyCreate.setOnClickListener {
            findNavController().navigate(R.id.createPostFragment)
        }
    }

    private fun setupRecyclerView() {
        adapter = MyPostsAdapter(
            posts = emptyList(),
            onPostClick = { post ->
                val bundle = Bundle().apply {
                    putString("postId", post.id)
                }
                findNavController().navigate(R.id.postDetailFragment, bundle)
            },
            onEditClick = { post ->
                showEditPostDialog(post)
            },
            onDeleteClick = { post ->
                PostActionUtils.confirmAndDeletePost(requireContext(), post) {
                    allUserPosts.removeAll { it.id == post.id }
                    updateCategoryCounts()
                    applyCurrentFilter()
                }
            },
            onLikeClick = { post ->
                handleLikeToggle(post)
            },
            onCommentClick = { post ->
                val bundle = Bundle().apply {
                    putString("postId", post.id)
                }
                findNavController().navigate(R.id.postDetailFragment, bundle)
            },
            onShareClick = { post ->
                PostActionUtils.sharePost(requireContext(), post)
            }
        )

        binding.rvMyPosts.layoutManager = LinearLayoutManager(requireContext())
        binding.rvMyPosts.adapter = adapter
    }

    private fun setupFilterChips() {
        binding.chipAll.setOnClickListener {
            selectFilter(FilterCategory.ALL)
        }

        binding.chipPosts.setOnClickListener {
            selectFilter(FilterCategory.POSTS)
        }

        binding.chipAnnouncements.setOnClickListener {
            selectFilter(FilterCategory.ANNOUNCEMENTS)
        }

        binding.chipHelp.setOnClickListener {
            selectFilter(FilterCategory.HELP)
        }
    }

    private fun selectFilter(filter: FilterCategory) {
        if (currentFilter == filter) return
        currentFilter = filter
        updateChipStyles()
        applyCurrentFilter()
    }

    private fun updateChipStyles() {
        val selectedBg = R.drawable.bg_chip_selected_pill
        val unselectedBg = R.drawable.bg_chip_unselected_pill
        val colorWhite = Color.WHITE
        val colorDarkText = Color.parseColor("#334155")
        val colorGrayIcon = Color.parseColor("#64748B")

        // Helper to update one chip
        fun updateChip(
            container: View,
            icon: ImageView,
            text: TextView,
            isSelected: Boolean
        ) {
            container.setBackgroundResource(if (isSelected) selectedBg else unselectedBg)
            icon.imageTintList = ColorStateList.valueOf(if (isSelected) colorWhite else colorGrayIcon)
            text.setTextColor(if (isSelected) colorWhite else colorDarkText)
        }

        updateChip(binding.chipAll, binding.ivChipAllIcon, binding.tvChipAll, currentFilter == FilterCategory.ALL)
        updateChip(binding.chipPosts, binding.ivChipPostsIcon, binding.tvChipPosts, currentFilter == FilterCategory.POSTS)
        updateChip(binding.chipAnnouncements, binding.ivChipAnnouncementsIcon, binding.tvChipAnnouncements, currentFilter == FilterCategory.ANNOUNCEMENTS)
        updateChip(binding.chipHelp, binding.ivChipHelpIcon, binding.tvChipHelp, currentFilter == FilterCategory.HELP)
    }

    private fun setupListeners() {
        binding.swipeRefreshLayout.setColorSchemeResources(R.color.primary)
        binding.swipeRefreshLayout.setOnRefreshListener {
            loadUserPosts(isSwipe = true)
        }
    }

    private fun observeRefreshEvents() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                AppRefreshManager.refreshEvents.collect { target ->
                    if (target == RefreshTarget.PROFILE || target == RefreshTarget.HOME_FEED || target == RefreshTarget.ALL) {
                        loadUserPosts(silent = true)
                    }
                }
            }
        }
    }

    private fun loadUserPosts(isSwipe: Boolean = false, silent: Boolean = false) {
        if (!isSwipe && !silent && allUserPosts.isEmpty()) {
            binding.progressBar.visibility = View.VISIBLE
        }

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val effectiveUserId = targetUserId
                    ?: userRepository.resolveValidAuthorId()
                    ?: SupabaseClient.getCurrentUserId()
                    ?: ""
                val effectiveEmail = SupabaseClient.getCurrentUserEmail() ?: ""
                val effectiveName = targetUserName ?: SupabaseClient.getCurrentUserName() ?: ""

                when (val result = postRepository.fetchPostsWithAuthors(requireContext().applicationContext)) {
                    is NetworkResult.Success -> {
                        val posts = result.data

                        val filtered = posts.filter { post ->
                            val pAuthorId = post.authorId ?: ""
                            val pUserName = post.userName ?: ""

                            (effectiveUserId.isNotBlank() && pAuthorId == effectiveUserId) ||
                            (effectiveEmail.isNotBlank() && pAuthorId.equals(effectiveEmail, ignoreCase = true)) ||
                            (effectiveName.isNotBlank() && pUserName.equals(effectiveName, ignoreCase = true))
                        }

                        allUserPosts.clear()
                        allUserPosts.addAll(filtered)

                        updateCategoryCounts()
                        applyCurrentFilter()
                    }
                    is NetworkResult.Error -> {
                        if (!silent) {
                            Toast.makeText(requireContext(), "Could not load posts: ${result.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } finally {
                binding.progressBar.visibility = View.GONE
                binding.swipeRefreshLayout.isRefreshing = false
            }
        }
    }

    private fun isGeneralPost(post: Post): Boolean {
        val cat = post.category ?: "General"
        return cat.equals("General", ignoreCase = true) ||
               cat.equals("Post", ignoreCase = true) ||
               (!cat.contains("Announcement", ignoreCase = true) &&
                !cat.contains("Alert", ignoreCase = true) &&
                !cat.contains("Help", ignoreCase = true) &&
                !cat.contains("Support", ignoreCase = true) &&
                !cat.contains("Event", ignoreCase = true))
    }

    private fun isAnnouncementPost(post: Post): Boolean {
        val cat = post.category ?: ""
        return cat.contains("Announcement", ignoreCase = true) ||
               cat.contains("Alert", ignoreCase = true) ||
               cat.contains("Notice", ignoreCase = true)
    }

    private fun isHelpPost(post: Post): Boolean {
        val cat = post.category ?: ""
        return cat.contains("Help", ignoreCase = true) ||
               cat.contains("Support", ignoreCase = true)
    }

    private fun updateCategoryCounts() {
        val allCount = allUserPosts.size
        val postsCount = allUserPosts.count { isGeneralPost(it) }
        val announcementsCount = allUserPosts.count { isAnnouncementPost(it) }
        val helpCount = allUserPosts.count { isHelpPost(it) }

        binding.tvChipAll.text = "All ($allCount)"
        binding.tvChipPosts.text = "Posts ($postsCount)"
        binding.tvChipAnnouncements.text = "Announcements ($announcementsCount)"
        binding.tvChipHelp.text = "Help ($helpCount)"
    }

    private fun applyCurrentFilter() {
        val filteredList = when (currentFilter) {
            FilterCategory.ALL -> allUserPosts
            FilterCategory.POSTS -> allUserPosts.filter { isGeneralPost(it) }
            FilterCategory.ANNOUNCEMENTS -> allUserPosts.filter { isAnnouncementPost(it) }
            FilterCategory.HELP -> allUserPosts.filter { isHelpPost(it) }
        }

        adapter.updatePosts(filteredList)

        if (filteredList.isEmpty()) {
            binding.llEmptyState.visibility = View.VISIBLE
            binding.rvMyPosts.visibility = View.GONE

            when (currentFilter) {
                FilterCategory.ALL -> {
                    binding.tvEmptyTitle.text = "No Posts Yet"
                    binding.tvEmptySubtitle.text = "Share news, events, or request help with neighbors in your area."
                }
                FilterCategory.POSTS -> {
                    binding.tvEmptyTitle.text = "No General Posts"
                    binding.tvEmptySubtitle.text = "You haven't posted any general neighborhood discussions yet."
                }
                FilterCategory.ANNOUNCEMENTS -> {
                    binding.tvEmptyTitle.text = "No Announcements"
                    binding.tvEmptySubtitle.text = "You haven't shared any official notices or announcements."
                }
                FilterCategory.HELP -> {
                    binding.tvEmptyTitle.text = "No Help Requests"
                    binding.tvEmptySubtitle.text = "You haven't posted any help or support requests."
                }
            }
        } else {
            binding.llEmptyState.visibility = View.GONE
            binding.rvMyPosts.visibility = View.VISIBLE
        }
    }

    private fun handleLikeToggle(post: Post) {
        val newLikedState = !post.isLiked
        val delta = if (newLikedState) 1 else -1
        val newLikesCount = (post.likesCount + delta).coerceAtLeast(0)

        // Optimistic UI update
        val updated = post.copy(isLiked = newLikedState, likesCount = newLikesCount)
        updatePostInList(updated)

        viewLifecycleOwner.lifecycleScope.launch {
            when (val res = likeRepository.toggleLike(post.id, post.isLiked)) {
                is NetworkResult.Success -> {
                    PostRepository.updateLocalLikeState(post.id, newLikedState, delta)
                }
                is NetworkResult.Error -> {
                    // Rollback
                    updatePostInList(post)
                    Toast.makeText(requireContext(), "Could not update like", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updatePostInList(updated: Post) {
        val idx = allUserPosts.indexOfFirst { it.id == updated.id }
        if (idx != -1) {
            allUserPosts[idx] = updated
            applyCurrentFilter()
        }
    }

    private fun showEditPostDialog(post: Post) {
        val input = EditText(requireContext()).apply {
            setText(post.text)
            setSelection(text.length)
            minLines = 4
            maxLines = 8
            isVerticalScrollBarEnabled = true
            setBackgroundResource(R.drawable.bg_post_input_box)
            setPadding(36, 28, 36, 28)
            setTextColor(Color.parseColor("#0F172A"))
            textSize = 14f
        }

        val container = FrameLayout(requireContext()).apply {
            setPadding(48, 20, 48, 12)
            addView(input)
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Edit Post")
            .setView(container)
            .setPositiveButton("Save Changes") { _, _ ->
                val newContent = input.text.toString().trim()
                if (newContent.isBlank()) {
                    Toast.makeText(requireContext(), "Post text cannot be empty.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                viewLifecycleOwner.lifecycleScope.launch {
                    val progressToast = Toast.makeText(requireContext(), "Saving changes...", Toast.LENGTH_SHORT)
                    progressToast.show()

                    when (val res = postRepository.updatePostContent(post.id, newContent, requireContext())) {
                        is NetworkResult.Success -> {
                            progressToast.cancel()
                            Toast.makeText(requireContext(), "Post updated successfully!", Toast.LENGTH_SHORT).show()
                            val updated = post.copy(text = newContent)
                            updatePostInList(updated)
                            AppRefreshManager.notifyDataChanged(RefreshTarget.HOME_FEED)
                            AppRefreshManager.notifyDataChanged(RefreshTarget.PROFILE)
                        }
                        is NetworkResult.Error -> {
                            progressToast.cancel()
                            Toast.makeText(requireContext(), "Failed to update: ${res.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

