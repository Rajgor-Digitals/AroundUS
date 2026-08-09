package com.aroundus.app.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.aroundus.app.R
import com.aroundus.app.adapters.PostAdapter
import com.aroundus.app.adapters.StoryAdapter
import com.aroundus.app.databinding.FragmentHomeBinding
import com.aroundus.app.utils.DummyData

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupFeed()
        setupFab()
        setupRefresh()
        setupClickListeners()
    }

    private fun setupFeed() {
        val postAdapter = PostAdapter(
            DummyData.posts,
            onPostClick = { post ->
                findNavController().navigate(R.id.action_home_to_postDetail)
            },
            onLikeClick = { /* toggle like */ },
            onCommentClick = { findNavController().navigate(R.id.action_home_to_postDetail) },
            onShareClick = { /* share */ }
        )
        binding.rvFeed.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = postAdapter
        }
    }

    private fun setupFab() {
        binding.fabCreatePost.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_createPost)
        }
    }

    private fun setupRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            binding.swipeRefresh.isRefreshing = false
        }
    }

    private fun setupClickListeners() {
        binding.btnSearch.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_search)
        }
        binding.btnNotifications.setOnClickListener {
            findNavController().navigate(R.id.notificationsFragment)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
