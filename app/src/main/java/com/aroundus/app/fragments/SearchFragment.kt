package com.aroundus.app.fragments

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.aroundus.app.R
import com.aroundus.app.adapters.PeopleAdapter
import com.aroundus.app.adapters.PostAdapter
import com.aroundus.app.databinding.FragmentSearchBinding
import com.aroundus.app.models.Post
import com.aroundus.app.models.UserEntity
import com.aroundus.app.repository.NetworkResult
import com.aroundus.app.repository.PostRepository
import com.aroundus.app.repository.UserRepository
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SearchFragment : Fragment() {

    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!

    private val postRepository = PostRepository()
    private val userRepository = UserRepository()

    private val allPosts = mutableListOf<Post>()
    private val searchPostsResults = mutableListOf<Post>()

    private val allUsers = mutableListOf<UserEntity>()
    private val searchUsersResults = mutableListOf<UserEntity>()

    private lateinit var postAdapter: PostAdapter
    private lateinit var peopleAdapter: PeopleAdapter

    private var searchDebounceJob: Job? = null
    private var selectedCategoryTab = "All"

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupAdapters()
        setupListeners()
        setupTabs()
        loadAllData()
    }

    private fun setupAdapters() {
        postAdapter = PostAdapter(
            posts = searchPostsResults,
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
                Toast.makeText(requireContext(), "Link copied!", Toast.LENGTH_SHORT).show()
            }
        )

        peopleAdapter = PeopleAdapter(
            users = searchUsersResults,
            onUserClick = { user ->
                val bundle = Bundle().apply { putString("userId", user.userId) }
                findNavController().navigate(R.id.myProfileFragment, bundle)
            },
            onFollowClick = { user, isFollowing ->
                lifecycleScope.launch {
                    if (isFollowing) {
                        userRepository.followUser(user.userId)
                        Toast.makeText(requireContext(), "Now following ${user.name}", Toast.LENGTH_SHORT).show()
                    } else {
                        userRepository.unfollowUser(user.userId)
                        Toast.makeText(requireContext(), "Unfollowed ${user.name}", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onMessageClick = { user ->
                val intent = android.content.Intent(requireContext(), com.aroundus.app.activities.ConversationActivity::class.java).apply {
                    putExtra("userId", user.userId)
                    putExtra("userName", user.name)
                }
                startActivity(intent)
            }
        )

        binding.rvResults.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = postAdapter
        }
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.btnClearSearch.setOnClickListener {
            binding.etSearchInput.text?.clear()
        }

        binding.etSearchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.trim() ?: ""
                binding.btnClearSearch.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE

                searchDebounceJob?.cancel()
                searchDebounceJob = lifecycleScope.launch {
                    delay(250)
                    performSearch(query)
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun setupTabs() {
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                selectedCategoryTab = tab?.text?.toString() ?: "All"
                performSearch(binding.etSearchInput.text.toString().trim())
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun loadAllData() {
        lifecycleScope.launch {
            // Load posts
            when (val postResult = postRepository.fetchPostsWithAuthors()) {
                is NetworkResult.Success -> {
                    allPosts.clear()
                    allPosts.addAll(postResult.data)
                }
                is NetworkResult.Error -> {}
            }

            // Load users from Supabase users table
            try {
                val userResp = com.aroundus.app.network.SupabaseClient.getService().getUsers()
                if (userResp.isSuccessful && userResp.body() != null) {
                    allUsers.clear()
                    allUsers.addAll(userResp.body()!!)
                }
            } catch (_: Exception) {}

            performSearch(binding.etSearchInput.text.toString().trim())
        }
    }

    private fun performSearch(query: String) {
        if (query.isBlank()) {
            searchPostsResults.clear()
            searchUsersResults.clear()
            postAdapter.notifyDataSetChanged()
            peopleAdapter.notifyDataSetChanged()
            return
        }

        if (selectedCategoryTab == "People") {
            // Display People Search Results
            binding.rvResults.adapter = peopleAdapter
            val filteredPeople = allUsers.filter { user ->
                user.name.contains(query, ignoreCase = true) ||
                user.email.contains(query, ignoreCase = true) ||
                (user.location != null && user.location.contains(query, ignoreCase = true))
            }
            searchUsersResults.clear()
            searchUsersResults.addAll(filteredPeople)
            peopleAdapter.notifyDataSetChanged()
        } else {
            // Display Post / Category Search Results
            binding.rvResults.adapter = postAdapter
            val filteredPosts = allPosts.filter { post ->
                val matchesQuery = post.text.contains(query, ignoreCase = true) ||
                        post.userName.contains(query, ignoreCase = true) ||
                        post.category.contains(query, ignoreCase = true) ||
                        post.neighborhood.contains(query, ignoreCase = true)

                val matchesTab = when (selectedCategoryTab) {
                    "Posts" -> true
                    "Groups" -> post.category.contains("Group", ignoreCase = true)
                    "Events" -> post.category.contains("Event", ignoreCase = true)
                    else -> true
                }

                matchesQuery && matchesTab
            }
            searchPostsResults.clear()
            searchPostsResults.addAll(filteredPosts)
            postAdapter.notifyDataSetChanged()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        searchDebounceJob?.cancel()
        _binding = null
    }
}
