package com.aroundus.app.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.aroundus.app.R
import com.aroundus.app.adapters.MyCommentsAdapter
import com.aroundus.app.databinding.FragmentMyCommentsBinding
import com.aroundus.app.models.MyCommentItem
import com.aroundus.app.network.SupabaseClient
import com.aroundus.app.repository.CommentRepository
import com.aroundus.app.repository.NetworkResult
import com.aroundus.app.repository.UserRepository
import com.aroundus.app.utils.AppRefreshManager
import com.aroundus.app.utils.RefreshTarget
import kotlinx.coroutines.launch

class MyCommentsFragment : Fragment() {

    private var _binding: FragmentMyCommentsBinding? = null
    private val binding get() = _binding!!

    private val commentRepository = CommentRepository()
    private val userRepository = UserRepository()

    private lateinit var adapter: MyCommentsAdapter
    private val commentsList = mutableListOf<MyCommentItem>()

    private var targetUserId: String? = null
    private var targetUserName: String? = null

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
        _binding = FragmentMyCommentsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setupRecyclerView()
        setupSwipeRefresh()
        observeRefreshEvents()

        loadMyComments()
    }

    private fun setupToolbar() {
        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }
    }

    private fun setupRecyclerView() {
        adapter = MyCommentsAdapter(
            items = commentsList,
            onCommentClick = { commentItem ->
                if (commentItem.postId.isNotBlank()) {
                    val bundle = Bundle().apply {
                        putString("postId", commentItem.postId)
                    }
                    findNavController().navigate(R.id.action_global_postDetail, bundle)
                }
            }
        )

        binding.rvMyComments.layoutManager = LinearLayoutManager(requireContext())
        binding.rvMyComments.adapter = adapter
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefreshLayout.setColorSchemeResources(R.color.primary)
        binding.swipeRefreshLayout.setOnRefreshListener {
            loadMyComments(isSwipe = true)
        }
    }

    private fun observeRefreshEvents() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                AppRefreshManager.refreshEvents.collect { target ->
                    if (target == RefreshTarget.PROFILE || target == RefreshTarget.HOME_FEED || target == RefreshTarget.ALL) {
                        loadMyComments(silent = true)
                    }
                }
            }
        }
    }

    private fun loadMyComments(isSwipe: Boolean = false, silent: Boolean = false) {
        if (!isSwipe && !silent && commentsList.isEmpty()) {
            binding.progressBar.visibility = View.VISIBLE
        }

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val effectiveUserId = targetUserId
                    ?: userRepository.resolveValidAuthorId()
                    ?: SupabaseClient.getCurrentUserId()
                    ?: ""

                when (val result = commentRepository.fetchMyCommentsWithPosts(requireContext().applicationContext, effectiveUserId)) {
                    is NetworkResult.Success -> {
                        commentsList.clear()
                        commentsList.addAll(result.data)

                        if (commentsList.isEmpty()) {
                            binding.layoutEmpty.visibility = View.VISIBLE
                            binding.rvMyComments.visibility = View.GONE
                        } else {
                            binding.layoutEmpty.visibility = View.GONE
                            binding.rvMyComments.visibility = View.VISIBLE
                            adapter.updateData(commentsList)
                        }
                    }
                    is NetworkResult.Error -> {
                        if (commentsList.isEmpty()) {
                            binding.layoutEmpty.visibility = View.VISIBLE
                            binding.rvMyComments.visibility = View.GONE
                        }
                        if (!silent) {
                            Toast.makeText(
                                requireContext(),
                                "Could not load comments: ${result.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            } finally {
                binding.progressBar.visibility = View.GONE
                binding.swipeRefreshLayout.isRefreshing = false
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

