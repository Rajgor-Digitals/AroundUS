package com.aroundus.app.fragments

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.aroundus.app.R
import com.aroundus.app.adapters.AnnouncementAdapter
import com.aroundus.app.databinding.FragmentAnnouncementsBinding
import com.aroundus.app.models.Announcement
import com.aroundus.app.models.Post
import com.aroundus.app.repository.CommentRepository
import com.aroundus.app.repository.EventRepository
import com.aroundus.app.repository.NetworkResult
import com.aroundus.app.repository.PostRepository
import com.aroundus.app.utils.AppRefreshManager
import com.aroundus.app.utils.RefreshTarget
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

class AnnouncementsFragment : Fragment() {

    private var _binding: FragmentAnnouncementsBinding? = null
    private val binding get() = _binding!!

    private lateinit var announcementAdapter: AnnouncementAdapter
    private val postRepository = PostRepository()
    private val commentRepository = CommentRepository()
    private val eventRepository = EventRepository()

    private val masterAnnouncements = mutableListOf<Announcement>()
    private val displayedAnnouncements = mutableListOf<Announcement>()

    private enum class CategoryFilter {
        ALL, COMMUNITY, EVENTS, ALERTS
    }

    private var selectedCategory: CategoryFilter = CategoryFilter.ALL
    private var currentSearchQuery: String = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAnnouncementsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initMasterList()
        setupTopBar()
        setupRecyclerView()
        setupCategoryChips()
        setupSearch()
        setupSwipeRefresh()
        setupCreateActions()
        observeRefreshEvents()

        loadAnnouncementsFromSupabase(showLoading = true)
    }

    override fun onResume() {
        super.onResume()
        if (AppRefreshManager.isStale(RefreshTarget.ANNOUNCEMENTS)) {
            loadAnnouncementsFromSupabase(showLoading = false)
        }
    }

    private fun observeRefreshEvents() {
        viewLifecycleOwner.lifecycleScope.launch {
            AppRefreshManager.refreshEvents.collect { target ->
                if (target == RefreshTarget.ANNOUNCEMENTS || target == RefreshTarget.ALL) {
                    loadAnnouncementsFromSupabase(showLoading = false)
                }
            }
        }
    }

    private fun initMasterList() {
        masterAnnouncements.clear()
        displayedAnnouncements.clear()
    }

    private fun setupTopBar() {
        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.btnHeaderSearch.setOnClickListener {
            binding.etSearchAnnouncements.requestFocus()
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(binding.etSearchAnnouncements, InputMethodManager.SHOW_IMPLICIT)
        }

        binding.btnHeaderCreate.setOnClickListener {
            navigateToCreateAnnouncement()
        }
    }

    private fun setupCreateActions() {
        binding.fabCreateAnnouncement.setOnClickListener {
            navigateToCreateAnnouncement()
        }

        binding.btnEmptyCreate.setOnClickListener {
            navigateToCreateAnnouncement()
        }
    }

    private fun navigateToCreateAnnouncement() {
        val bundle = Bundle().apply {
            putString("category", "Announcement")
        }
        try {
            findNavController().navigate(R.id.createPostFragment, bundle)
        } catch (_: Exception) {}
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefreshAnnouncements.setColorSchemeColors(Color.parseColor("#0D8A3F"))
        binding.swipeRefreshAnnouncements.setOnRefreshListener {
            loadAnnouncementsFromSupabase(showLoading = false)
        }
    }

    private fun setupRecyclerView() {
        announcementAdapter = AnnouncementAdapter(
            announcements = displayedAnnouncements,
            onAnnouncementClick = { announcement ->
                val bundle = Bundle().apply { putString("postId", announcement.id) }
                try {
                    findNavController().navigate(R.id.postDetailFragment, bundle)
                } catch (_: Exception) {}
            },
            onCommentClick = { announcement ->
                val bundle = Bundle().apply { putString("postId", announcement.id) }
                try {
                    findNavController().navigate(R.id.commentsFragment, bundle)
                } catch (_: Exception) {}
            },
            onShareClick = { announcement ->
                val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(android.content.Intent.EXTRA_SUBJECT, announcement.title)
                    putExtra(
                        android.content.Intent.EXTRA_TEXT,
                        "${announcement.title}\n\n${announcement.content}\n\nVia AroundUS Bhujpur Community"
                    )
                }
                startActivity(android.content.Intent.createChooser(shareIntent, "Share Announcement"))
            },
            onAuthorClick = { announcement ->
                if (!announcement.authorId.isNullOrBlank()) {
                    val bundle = Bundle().apply { putString("userId", announcement.authorId) }
                    try {
                        findNavController().navigate(R.id.myProfileFragment, bundle)
                    } catch (_: Exception) {}
                }
            }
        )

        binding.rvAnnouncements.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = announcementAdapter
        }
    }

    private fun setupCategoryChips() {
        binding.chipAll.setOnClickListener {
            selectCategory(CategoryFilter.ALL)
        }

        binding.chipCommunity.setOnClickListener {
            selectCategory(CategoryFilter.COMMUNITY)
        }

        binding.chipEvents.setOnClickListener {
            selectCategory(CategoryFilter.EVENTS)
        }

        binding.chipAlerts.setOnClickListener {
            selectCategory(CategoryFilter.ALERTS)
        }
    }

    private fun selectCategory(category: CategoryFilter) {
        selectedCategory = category
        updateChipVisuals()
        filterAnnouncements()
    }

    private fun updateChipVisuals() {
        val selectedBg = R.drawable.bg_announcement_chip_selected
        val unselectedBg = R.drawable.bg_announcement_chip_unselected
        val activeTextColor = Color.parseColor("#FFFFFF")
        val inactiveTextColor = Color.parseColor("#334155")

        // 1. All Chip
        val isAll = selectedCategory == CategoryFilter.ALL
        binding.chipAll.setBackgroundResource(if (isAll) selectedBg else unselectedBg)
        binding.tvChipAll.setTextColor(if (isAll) activeTextColor else inactiveTextColor)

        // 2. Community Chip
        val isCommunity = selectedCategory == CategoryFilter.COMMUNITY
        binding.chipCommunity.setBackgroundResource(if (isCommunity) selectedBg else unselectedBg)
        binding.tvChipCommunity.setTextColor(if (isCommunity) activeTextColor else inactiveTextColor)
        binding.ivChipCommunity.setColorFilter(if (isCommunity) activeTextColor else inactiveTextColor)

        // 3. Events Chip
        val isEvents = selectedCategory == CategoryFilter.EVENTS
        binding.chipEvents.setBackgroundResource(if (isEvents) selectedBg else unselectedBg)
        binding.tvChipEvents.setTextColor(if (isEvents) activeTextColor else inactiveTextColor)
        binding.ivChipEvents.setColorFilter(if (isEvents) activeTextColor else inactiveTextColor)

        // 4. Alerts Chip
        val isAlerts = selectedCategory == CategoryFilter.ALERTS
        binding.chipAlerts.setBackgroundResource(if (isAlerts) selectedBg else unselectedBg)
        binding.tvChipAlerts.setTextColor(if (isAlerts) activeTextColor else inactiveTextColor)
        binding.ivChipAlerts.setColorFilter(if (isAlerts) activeTextColor else inactiveTextColor)
    }

    private fun setupSearch() {
        binding.etSearchAnnouncements.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                currentSearchQuery = s?.toString()?.trim() ?: ""
                filterAnnouncements()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.btnFilter.setOnClickListener {
            Toast.makeText(
                requireContext(),
                "Filtering: ${selectedCategory.name.lowercase().replaceFirstChar { it.uppercase() }}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun filterAnnouncements() {
        val filtered = masterAnnouncements.filter { item ->
            // Category check
            val matchesCategory = when (selectedCategory) {
                CategoryFilter.ALL -> true
                CategoryFilter.COMMUNITY -> item.category.equals("Community", ignoreCase = true)
                CategoryFilter.EVENTS -> item.category.equals("Events", ignoreCase = true) || item.category.equals("Event", ignoreCase = true)
                CategoryFilter.ALERTS -> item.category.equals("Alerts", ignoreCase = true) || item.category.equals("Alert", ignoreCase = true)
            }

            // Search query check
            val matchesSearch = if (currentSearchQuery.isBlank()) {
                true
            } else {
                item.title.contains(currentSearchQuery, ignoreCase = true) ||
                        item.content.contains(currentSearchQuery, ignoreCase = true) ||
                        item.authorName.contains(currentSearchQuery, ignoreCase = true) ||
                        item.category.contains(currentSearchQuery, ignoreCase = true)
            }

            matchesCategory && matchesSearch
        }

        announcementAdapter.updateList(filtered)
        val hasItems = filtered.isNotEmpty()
        binding.layoutEmpty.visibility = if (hasItems) View.GONE else View.VISIBLE
        binding.rvAnnouncements.visibility = if (hasItems) View.VISIBLE else View.GONE
    }

    private fun loadAnnouncementsFromSupabase(showLoading: Boolean) {
        if (showLoading && masterAnnouncements.isEmpty()) {
            binding.pbLoadingAnnouncements.visibility = View.VISIBLE
        }

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                coroutineScope {
                    val postsDeferred = async { postRepository.fetchPostsWithAuthors(context?.applicationContext) }
                    val commentsMapDeferred = async { commentRepository.fetchCommentsCountMap() }
                    val eventsDeferred = async { eventRepository.fetchEvents() }

                    val postsResult = postsDeferred.await()
                    val commentsCountMap = commentsMapDeferred.await()
                    val eventsResult = eventsDeferred.await()

                    val realAnnouncements = mutableListOf<Announcement>()

                    // 1. Process real Announcement posts from Supabase posts table
                    if (postsResult is NetworkResult.Success) {
                        val posts = postsResult.data
                        val filteredPosts = posts.filter { post ->
                            post.category.equals("Announcement", ignoreCase = true) ||
                                    post.category.equals("Announcements", ignoreCase = true) ||
                                    post.category.equals("Alert", ignoreCase = true) ||
                                    post.category.equals("Alerts", ignoreCase = true) ||
                                    post.category.equals("Event", ignoreCase = true) ||
                                    post.category.equals("Events", ignoreCase = true) ||
                                    post.category.equals("Notice", ignoreCase = true) ||
                                    post.category.equals("Emergency", ignoreCase = true)
                        }

                        val mappedAnnouncements = filteredPosts.map { post ->
                            createAnnouncementFromPost(post, commentsCountMap)
                        }
                        realAnnouncements.addAll(mappedAnnouncements)
                    }

                    // 2. Process real Events from Supabase events table
                    if (eventsResult is NetworkResult.Success) {
                        val events = eventsResult.data
                        val mappedEvents = events.map { event ->
                            Announcement(
                                id = event.eventId,
                                authorName = "Community Event",
                                authorAvatarUrl = null,
                                authorAvatarRes = null,
                                isVerified = true,
                                location = event.location?.ifBlank { "Bhujpur, Kutch" } ?: "Bhujpur, Kutch",
                                timeAgo = "Upcoming",
                                title = event.title.ifBlank { "Community Event" },
                                category = "Events",
                                content = "Event in ${event.location ?: "Bhujpur, Kutch"}: ${event.title}",
                                eventDate = event.date,
                                eventTime = event.date,
                                eventVenue = event.location ?: "Bhujpur, Kutch",
                                eventAddress = event.location ?: "Bhujpur, Kutch",
                                authorId = event.creatorId
                            )
                        }
                        realAnnouncements.addAll(mappedEvents)
                    }

                    // 3. Update master list with real items only (no duplicate IDs)
                    val uniqueAnnouncements = realAnnouncements.distinctBy { it.id }
                    masterAnnouncements.clear()
                    masterAnnouncements.addAll(uniqueAnnouncements)
                    filterAnnouncements()
                    AppRefreshManager.markRefreshed(RefreshTarget.ANNOUNCEMENTS)
                }
            } catch (_: Exception) {
            } finally {
                _binding?.let { b ->
                    b.pbLoadingAnnouncements.visibility = View.GONE
                    b.swipeRefreshAnnouncements.isRefreshing = false
                }
            }
        }
    }

    private fun createAnnouncementFromPost(
        post: Post,
        commentsCountMap: Map<String, Int>
    ): Announcement {
        val lines = post.text.trim().lines().filter { it.isNotBlank() }
        val title = when {
            lines.isEmpty() -> "Community Announcement"
            lines.size > 1 -> lines.first().trim()
            lines.first().length <= 50 -> lines.first().trim()
            else -> lines.first().take(47).trim() + "..."
        }

        val content = if (lines.size > 1) {
            lines.drop(1).joinToString("\n").trim()
        } else {
            post.text.trim().ifBlank { "Community Announcement" }
        }

        val cat = when {
            post.category.equals("Alert", ignoreCase = true) ||
                    post.category.equals("Alerts", ignoreCase = true) ||
                    post.category.equals("Emergency", ignoreCase = true) -> "Alerts"
            post.category.equals("Event", ignoreCase = true) ||
                    post.category.equals("Events", ignoreCase = true) -> "Events"
            post.category.equals("Community", ignoreCase = true) -> "Community"
            post.text.contains("alert", ignoreCase = true) ||
                    post.text.contains("fire", ignoreCase = true) ||
                    post.text.contains("warning", ignoreCase = true) ||
                    post.text.contains("emergency", ignoreCase = true) ||
                    post.text.contains("danger", ignoreCase = true) ||
                    post.text.contains("caution", ignoreCase = true) ||
                    post.text.contains("urgent", ignoreCase = true) -> "Alerts"
            post.text.contains("event", ignoreCase = true) ||
                    post.text.contains("meeting", ignoreCase = true) ||
                    post.text.contains("garba", ignoreCase = true) ||
                    post.text.contains("festival", ignoreCase = true) ||
                    post.text.contains("celebration", ignoreCase = true) ||
                    post.text.contains("tournament", ignoreCase = true) -> "Events"
            else -> "Community"
        }

        val realCommentsCount = commentsCountMap[post.id] ?: post.commentsCount

        return Announcement(
            id = post.id,
            authorName = post.userName.ifBlank { "Neighbor" },
            authorAvatarUrl = post.userAvatarPath,
            authorAvatarRes = null,
            isVerified = post.isVerified,
            location = post.neighborhood.ifBlank { "Bhujpur, Kutch" },
            timeAgo = post.timeAgo.ifBlank { "Recently" },
            title = title,
            category = cat,
            content = content,
            imageUrl = post.mediaUrl,
            imageRes = null,
            likesCount = post.likesCount,
            commentsCount = realCommentsCount,
            isLiked = post.isLiked,
            isSaved = post.isSaved,
            authorId = post.authorId
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
