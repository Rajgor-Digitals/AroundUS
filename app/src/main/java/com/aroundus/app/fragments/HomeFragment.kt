
package com.aroundus.app.fragments

import android.Manifest
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
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
import com.aroundus.app.repository.LikeRepository
import com.aroundus.app.repository.UserRepository
import com.aroundus.app.utils.AppRefreshManager
import com.aroundus.app.utils.LocationState
import com.aroundus.app.utils.PostActionUtils
import com.aroundus.app.utils.RefreshTarget
import com.aroundus.app.utils.UserLocationData
import com.aroundus.app.utils.UserLocationManager
import com.aroundus.app.utils.applyAroundUsTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val postRepository = PostRepository()
    private val likeRepository = LikeRepository()
    private val userRepository = UserRepository()
    private val postList = mutableListOf<Post>()
    private val announcementList = mutableListOf<Post>()
    private lateinit var postAdapter: PostAdapter
    private lateinit var announcementAdapter: PostAdapter
    private var autoRefreshJob: Job? = null

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        if (fineGranted || coarseGranted) {
            startLocationDetection(forceRefresh = true)
        } else {
            val shouldShowRationale = shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) ||
                                      shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (!shouldShowRationale) {
                Toast.makeText(requireContext(), "Location permission denied. Tap location bar to set manually.", Toast.LENGTH_LONG).show()
            }
            updateLocationUI()
        }
    }

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
        setupLocationBar()
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
            onLikeClick = { post ->
                viewLifecycleOwner.lifecycleScope.launch {
                    when (val res = likeRepository.toggleLike(post.id, post.isLiked)) {
                        is NetworkResult.Success -> {
                            AppRefreshManager.notifyDataChanged(RefreshTarget.HOME_FEED)
                            AppRefreshManager.notifyDataChanged(RefreshTarget.PROFILE)
                            AppRefreshManager.notifyDataChanged(RefreshTarget.ANNOUNCEMENTS)
                        }
                        is NetworkResult.Error -> {
                            Toast.makeText(context, "Could not update like: ${res.message}", Toast.LENGTH_SHORT).show()
                            postAdapter.rollbackLike(post.id, post.isLiked, post.likesCount)
                        }
                    }
                }
            },
            onCommentClick = { post ->
                val bundle = Bundle().apply { putString("postId", post.id) }
                findNavController().navigate(R.id.commentsFragment, bundle)
            },
            onShareClick = { post ->
                PostActionUtils.sharePost(requireContext(), post)
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
            onLikeClick = { post ->
                viewLifecycleOwner.lifecycleScope.launch {
                    when (val res = likeRepository.toggleLike(post.id, post.isLiked)) {
                        is NetworkResult.Success -> {
                            AppRefreshManager.notifyDataChanged(RefreshTarget.HOME_FEED)
                            AppRefreshManager.notifyDataChanged(RefreshTarget.PROFILE)
                            AppRefreshManager.notifyDataChanged(RefreshTarget.ANNOUNCEMENTS)
                        }
                        is NetworkResult.Error -> {
                            Toast.makeText(context, "Could not update like: ${res.message}", Toast.LENGTH_SHORT).show()
                            announcementAdapter.rollbackLike(post.id, post.isLiked, post.likesCount)
                        }
                    }
                }
            },
            onCommentClick = { post ->
                val bundle = Bundle().apply { putString("postId", post.id) }
                findNavController().navigate(R.id.commentsFragment, bundle)
            },
            onShareClick = { post ->
                PostActionUtils.sharePost(requireContext(), post)
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
            openLocationBottomSheet()
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

    private fun setupLocationBar() {
        updateLocationUI()

        binding.btnLocationPicker.setOnClickListener {
            openLocationBottomSheet()
        }

        binding.btnLocationRefresh.setOnClickListener {
            checkPermissionsAndRefreshLocation()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            UserLocationManager.currentSelectedLocation.collect { loc ->
                updateLocationUI()
            }
        }

        // Auto-detect on launch if no manual selection exists
        val ctx = context ?: return
        if (!UserLocationManager.isManualSelection(ctx)) {
            if (UserLocationManager.hasLocationPermission(ctx)) {
                startLocationDetection(forceRefresh = false)
            } else if (UserLocationManager.getSelectedLocation(ctx) == null) {
                // First-time user: request permission
                locationPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        }
    }

    private fun updateLocationUI() {
        val b = _binding ?: return
        val ctx = context ?: return
        val selected = UserLocationManager.getSelectedLocation(ctx)
        if (selected != null && selected.displayName.isNotBlank()) {
            b.tvLocation.text = selected.displayName
        } else {
            b.tvLocation.text = "Tap to set location"
        }
    }

    private fun checkPermissionsAndRefreshLocation() {
        val ctx = context ?: return
        if (!UserLocationManager.hasLocationPermission(ctx)) {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
            return
        }

        // Instant toggle: if user is currently on manual location, switch immediately in 0ms!
        if (UserLocationManager.isManualSelection(ctx)) {
            val instantLoc = UserLocationManager.switchToDeviceLocation(ctx)
            if (instantLoc != null) {
                updateLocationUI()
                AppRefreshManager.notifyDataChanged(RefreshTarget.HOME_FEED)
                Toast.makeText(ctx, "📍 Switched to: ${instantLoc.displayName}", Toast.LENGTH_SHORT).show()
            }
        }

        startLocationDetection(forceRefresh = true)
    }

    private fun startLocationDetection(forceRefresh: Boolean) {
        val ctx = context ?: return
        val b = _binding ?: return

        // Instant update if cached device location exists
        val cachedDevice = UserLocationManager.getLastDeviceLocation(ctx)
        if (cachedDevice != null && !UserLocationManager.isManualSelection(ctx)) {
            updateLocationUI()
        }

        b.pbLocationLoading.visibility = View.VISIBLE
        b.btnLocationRefresh.animate().rotationBy(360f).setDuration(600).start()

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                when (val result = UserLocationManager.detectLocation(ctx, forceRefresh)) {
                    is LocationState.Success -> {
                        if (isAdded) {
                            updateLocationUI()
                            AppRefreshManager.notifyDataChanged(RefreshTarget.HOME_FEED)
                        }
                    }
                    is LocationState.GpsDisabled -> {
                        if (forceRefresh && isAdded) {
                            Toast.makeText(ctx, "GPS is turned off. Tap location bar to enable.", Toast.LENGTH_SHORT).show()
                        }
                    }
                    is LocationState.PermissionRequired -> {
                        if (forceRefresh && isAdded) {
                            locationPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        }
                    }
                    is LocationState.Error -> {
                        if (forceRefresh && isAdded) {
                            Toast.makeText(ctx, result.message, Toast.LENGTH_SHORT).show()
                        }
                    }
                    else -> {}
                }
            } finally {
                _binding?.pbLocationLoading?.visibility = View.GONE
            }
        }
    }

    private fun openLocationBottomSheet() {
        val sheet = LocationBottomSheetFragment.newInstance()
        sheet.onLocationSelected = { loc ->
            updateLocationUI()
        }
        sheet.show(parentFragmentManager, LocationBottomSheetFragment.TAG)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        autoRefreshJob?.cancel()
        _binding = null
    }
}
