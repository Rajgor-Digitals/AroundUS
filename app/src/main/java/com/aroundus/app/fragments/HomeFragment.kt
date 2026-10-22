
package com.aroundus.app.fragments

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.aroundus.app.R
import com.aroundus.app.activities.MainActivity
import com.aroundus.app.adapters.PostAdapter
import com.aroundus.app.databinding.FragmentHomeBinding
import com.aroundus.app.models.Post
import com.aroundus.app.models.toUIModel
import com.aroundus.app.network.SupabaseClient
import com.aroundus.app.repository.NetworkResult
import com.aroundus.app.repository.PostRepository
import com.aroundus.app.utils.AppRefreshManager
import com.aroundus.app.utils.RefreshTarget
import com.aroundus.app.utils.applyAroundUsTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val postRepository = PostRepository()
    private val postList = mutableListOf<Post>()
    private val announcementList = mutableListOf<Post>()
    private lateinit var postAdapter: PostAdapter
    private lateinit var announcementAdapter: PostAdapter
    private var autoRefreshJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupPostRecyclerView()
        setupAnnouncementsRecyclerView()
        setupClickListeners()
        setupFilterTabs()
        setupSwipeRefresh()
        observeRefreshEvents()

        // 1. Instant 0ms cache-first rendering so app opens without delay
        loadPostsFromCache()

        // 2. Fetch fresh posts on initial load
        viewLifecycleOwner.lifecycleScope.launch {
            fetchPostsAndCheckUpdate()
            AppRefreshManager.markRefreshed(RefreshTarget.HOME_FEED)
        }
    }

    override fun onResume() {
        super.onResume()
        loadPostsFromCache()
        if (AppRefreshManager.isStale(RefreshTarget.HOME_FEED)) {
            viewLifecycleOwner.lifecycleScope.launch {
                fetchPostsAndCheckUpdate()
                AppRefreshManager.markRefreshed(RefreshTarget.HOME_FEED)
            }
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefreshLayout.applyAroundUsTheme()
        binding.swipeRefreshLayout.setOnRefreshListener {
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    fetchPostsAndCheckUpdate()
                    AppRefreshManager.markRefreshed(RefreshTarget.HOME_FEED)
                } finally {
                    binding.swipeRefreshLayout.isRefreshing = false
                }
            }
        }
    }

    private fun observeRefreshEvents() {
        viewLifecycleOwner.lifecycleScope.launch {
            AppRefreshManager.refreshEvents.collect { target ->
                if (target == RefreshTarget.HOME_FEED || target == RefreshTarget.ALL) {
                    fetchPostsAndCheckUpdate()
                    AppRefreshManager.markRefreshed(RefreshTarget.HOME_FEED)
                }
            }
        }
    }

    private fun loadPostsFromCache() {
        val cached = postRepository.getCachedPosts(context)
        if (cached.isNotEmpty()) {
            postList.clear()
            postList.addAll(cached)
            postAdapter.notifyDataSetChanged()

            val announcements = cached.filter {
                it.category.equals("Announcement", ignoreCase = true) ||
                it.category.equals("Announcements", ignoreCase = true)
            }
            if (announcements.isNotEmpty()) {
                announcementList.clear()
                announcementList.addAll(announcements)
                announcementAdapter.notifyDataSetChanged()
            }
        }
    }

    private fun setupPostRecyclerView() {
        postAdapter = PostAdapter(
            posts = postList,
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

    private fun setupAnnouncementsRecyclerView() {
        announcementAdapter = PostAdapter(
            posts = announcementList,
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
                Toast.makeText(requireContext(), "Announcement link copied!", Toast.LENGTH_SHORT).show()
            }
        )

        binding.rvAnnouncements.apply {
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            adapter = announcementAdapter
        }
    }



    private suspend fun fetchPostsAndCheckUpdate() {
        when (val result = postRepository.fetchPostsWithAuthors(context?.applicationContext)) {
            is NetworkResult.Success -> {
                val fetchedPosts = result.data

                // Smart Diffing for Community Posts
                if (fetchedPosts != postList) {
                    postList.clear()
                    postList.addAll(fetchedPosts)
                    postAdapter.notifyDataSetChanged()
                }

                // Filter & Smart Diffing for Announcements
                val announcements = fetchedPosts.filter {
                    it.category.equals("Announcement", ignoreCase = true) ||
                    it.category.equals("Announcements", ignoreCase = true)
                }

                if (announcements != announcementList) {
                    announcementList.clear()
                    announcementList.addAll(announcements)
                    announcementAdapter.notifyDataSetChanged()
                }
            }
            is NetworkResult.Error -> {}
        }
    }

    private fun setupClickListeners() {
        binding.btnMenu.setOnClickListener {
            (activity as? MainActivity)?.toggleDrawer()
        }

        binding.btnSearch.setOnClickListener {
            findNavController().navigate(R.id.searchFragment)
        }

        binding.btnNotifications.setOnClickListener {
            findNavController().navigate(R.id.notificationsFragment)
        }

        binding.btnLocationPicker.setOnClickListener {
            Toast.makeText(requireContext(), "Location: Bhujpur, Kutch, Gujarat", Toast.LENGTH_SHORT).show()
        }

        binding.btnExploreNow.setOnClickListener {
            findNavController().navigate(R.id.communityFragment)
        }

        binding.catAnnouncements.setOnClickListener {
            findNavController().navigate(R.id.announcementsFragment)
        }

        binding.catHelpSupport.setOnClickListener {
            findNavController().navigate(R.id.communityFragment)
        }

        binding.catBuySell.setOnClickListener {
            findNavController().navigate(R.id.marketplaceFragment)
        }

        binding.catEvents.setOnClickListener {
            findNavController().navigate(R.id.eventsFragment)
        }

        binding.catServices.setOnClickListener {
            findNavController().navigate(R.id.marketplaceFragment)
        }

        binding.catMore.setOnClickListener {
            findNavController().navigate(R.id.settingsFragment)
        }

        binding.btnViewAllAnnouncements.setOnClickListener {
            findNavController().navigate(R.id.announcementsFragment)
        }
    }

    private fun setupFilterTabs() {
        val activeGreen = Color.parseColor("#0D8A3F")
        val inactiveGray = Color.parseColor("#64748B")

        binding.tabRecent.setOnClickListener {
            binding.tvTabRecent.setTextColor(activeGreen)
            binding.indicatorRecent.visibility = View.VISIBLE
            binding.tabPopular.setTextColor(inactiveGray)
            binding.tabNearby.setTextColor(inactiveGray)
        }

        binding.tabPopular.setOnClickListener {
            binding.tvTabRecent.setTextColor(inactiveGray)
            binding.indicatorRecent.visibility = View.INVISIBLE
            binding.tabPopular.setTextColor(activeGreen)
            binding.tabNearby.setTextColor(inactiveGray)
        }

        binding.tabNearby.setOnClickListener {
            binding.tvTabRecent.setTextColor(inactiveGray)
            binding.indicatorRecent.visibility = View.INVISIBLE
            binding.tabPopular.setTextColor(inactiveGray)
            binding.tabNearby.setTextColor(activeGreen)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        autoRefreshJob?.cancel()
        _binding = null
    }
}
