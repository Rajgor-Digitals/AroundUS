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
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.aroundus.app.R
import com.aroundus.app.adapters.SavedPostsAdapter
import com.aroundus.app.databinding.FragmentSavedPostsBinding
import com.aroundus.app.models.Post
import com.aroundus.app.network.SupabaseClient
import com.aroundus.app.repository.NetworkResult
import com.aroundus.app.repository.PostRepository
import com.aroundus.app.repository.UserRepository
import com.aroundus.app.utils.AppRefreshManager
import com.aroundus.app.utils.RefreshTarget
import kotlinx.coroutines.launch

class SavedPostsFragment : Fragment() {

    private var _binding: FragmentSavedPostsBinding? = null
    private val binding get() = _binding!!

    private val postRepository = PostRepository()
    private val userRepository = UserRepository()

    private lateinit var savedPostsAdapter: SavedPostsAdapter
    private var isGridView = true

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSavedPostsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setupRecyclerView()
        setupViewSwitcher()
        setupSwipeRefresh()

        loadSavedPosts()
    }

    private fun setupToolbar() {
        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.btnExploreFeed.setOnClickListener {
            findNavController().navigate(R.id.homeFragment)
        }
    }

    private fun setupRecyclerView() {
        savedPostsAdapter = SavedPostsAdapter(
            onPostClick = { post ->
                val bundle = Bundle().apply {
                    putString("postId", post.id)
                }
                findNavController().navigate(R.id.action_global_postDetail, bundle)
            },
            onBookmarkClick = { post, position ->
                handleUnsavePost(post, position)
            }
        )

        binding.rvSavedPosts.apply {
            layoutManager = GridLayoutManager(requireContext(), 2)
            adapter = savedPostsAdapter
            setHasFixedSize(false)
        }
    }

    private fun setupViewSwitcher() {
        binding.btnViewGrid.setOnClickListener {
            if (!isGridView) {
                switchToGridView()
            }
        }

        binding.btnViewList.setOnClickListener {
            if (isGridView) {
                switchToListView()
            }
        }
    }

    private fun switchToGridView() {
        isGridView = true
        savedPostsAdapter.isGridView = true

        binding.btnViewGrid.setBackgroundResource(R.drawable.bg_toggle_active)
        binding.ivViewGridIcon.setColorFilter(Color.WHITE)

        binding.btnViewList.setBackgroundResource(R.drawable.bg_toggle_inactive)
        binding.ivViewListIcon.setColorFilter(Color.parseColor("#64748B"))

        binding.rvSavedPosts.layoutManager = GridLayoutManager(requireContext(), 2)
    }

    private fun switchToListView() {
        isGridView = false
        savedPostsAdapter.isGridView = false

        binding.btnViewList.setBackgroundResource(R.drawable.bg_toggle_active)
        binding.ivViewListIcon.setColorFilter(Color.WHITE)

        binding.btnViewGrid.setBackgroundResource(R.drawable.bg_toggle_inactive)
        binding.ivViewGridIcon.setColorFilter(Color.parseColor("#64748B"))

        binding.rvSavedPosts.layoutManager = LinearLayoutManager(requireContext())
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefreshLayout.setColorSchemeResources(R.color.primary)
        binding.swipeRefreshLayout.setOnRefreshListener {
            loadSavedPosts(isRefreshing = true)
        }
    }

    private fun loadSavedPosts(isRefreshing: Boolean = false) {
        val currentUserId = SupabaseClient.getCurrentUserId() ?: ""
        val savedPostIds = SupabaseClient.getSavedPostIds(currentUserId)

        if (savedPostIds.isEmpty()) {
            binding.swipeRefreshLayout.isRefreshing = false
            binding.progressBar.visibility = View.GONE
            savedPostsAdapter.submitList(emptyList())
            showEmptyState(true)
            return
        }

        if (!isRefreshing) {
            // First check memory/disk cache for instant 0ms preview
            val cachedPosts = postRepository.getCachedPosts(requireContext())
                .filter { savedPostIds.contains(it.id) }
            if (cachedPosts.isNotEmpty()) {
                savedPostsAdapter.submitList(cachedPosts)
                showEmptyState(false)
            } else {
                binding.progressBar.visibility = View.VISIBLE
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val resolvedUid = try {
                    userRepository.resolveValidAuthorId()
                } catch (_: Exception) {
                    currentUserId
                }
                val targetUid = if (resolvedUid.isNotBlank()) resolvedUid else currentUserId

                when (val result = postRepository.fetchPostsWithAuthors(requireContext())) {
                    is NetworkResult.Success -> {
                        val currentSavedIds = SupabaseClient.getSavedPostIds(targetUid).ifEmpty {
                            SupabaseClient.getSavedPostIds(currentUserId)
                        }
                        val savedPosts = result.data.filter { currentSavedIds.contains(it.id) }
                        savedPosts.forEach { it.isSaved = true }

                        if (savedPosts.isEmpty()) {
                            savedPostsAdapter.submitList(emptyList())
                            showEmptyState(true)
                        } else {
                            savedPostsAdapter.submitList(savedPosts)
                            showEmptyState(false)
                        }
                    }
                    is NetworkResult.Error -> {
                        val fallback = postRepository.getCachedPosts(requireContext())
                            .filter { savedPostIds.contains(it.id) }
                        if (fallback.isNotEmpty()) {
                            savedPostsAdapter.submitList(fallback)
                            showEmptyState(false)
                        } else {
                            showEmptyState(true)
                            Toast.makeText(
                                requireContext(),
                                "Could not refresh saved posts: ${result.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            } catch (e: Exception) {
                if (savedPostsAdapter.itemCount == 0) {
                    showEmptyState(true)
                }
            } finally {
                _binding?.progressBar?.visibility = View.GONE
                _binding?.swipeRefreshLayout?.isRefreshing = false
            }
        }
    }

    private fun handleUnsavePost(post: Post, position: Int) {
        val currentUserId = SupabaseClient.getCurrentUserId() ?: ""
        val isStillSaved = SupabaseClient.toggleSavedPost(currentUserId, post.id)

        if (!isStillSaved) {
            savedPostsAdapter.removeItemAt(position)
            AppRefreshManager.notifyDataChanged(RefreshTarget.PROFILE)
            AppRefreshManager.notifyDataChanged(RefreshTarget.HOME_FEED)

            Toast.makeText(requireContext(), "Post removed from Saved Posts", Toast.LENGTH_SHORT).show()

            if (savedPostsAdapter.itemCount == 0) {
                showEmptyState(true)
            }
        }
    }

    private fun showEmptyState(isEmpty: Boolean) {
        _binding?.let { b ->
            if (isEmpty) {
                b.layoutEmptyState.visibility = View.VISIBLE
                b.rvSavedPosts.visibility = View.GONE
            } else {
                b.layoutEmptyState.visibility = View.GONE
                b.rvSavedPosts.visibility = View.VISIBLE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
