package com.aroundus.app.fragments

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
import com.aroundus.app.adapters.CommentAdapter
import com.aroundus.app.databinding.FragmentCommentsBinding
import com.aroundus.app.models.Comment
import com.aroundus.app.network.SupabaseClient
import com.aroundus.app.repository.CommentRepository
import com.aroundus.app.repository.NetworkResult
import com.aroundus.app.repository.UserRepository
import com.aroundus.app.utils.loadUserAvatar
import kotlinx.coroutines.launch
import java.io.File

class CommentsFragment : Fragment() {

    private var _binding: FragmentCommentsBinding? = null
    private val binding get() = _binding!!
    private val commentRepository = CommentRepository()
    private val userRepository = UserRepository()

    private var postId: String = "p_1"
    private val commentList = mutableListOf<Comment>()
    private lateinit var commentAdapter: CommentAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCommentsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }

        arguments?.getString("postId")?.let {
            postId = it
        }

        setupRecyclerView()
        setupUserAvatar()
        setupSendComment()
        loadComments()
    }

    private fun setupRecyclerView() {
        commentAdapter = CommentAdapter(
            comments = commentList,
            onUserClick = { comment ->
                if (!comment.userId.isNullOrBlank()) {
                    val bundle = Bundle().apply { putString("userId", comment.userId) }
                    findNavController().navigate(R.id.myProfileFragment, bundle)
                }
            }
        )
        binding.rvComments.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = commentAdapter
        }
    }

    private fun setupUserAvatar() {
        val currentUserId = SupabaseClient.getCurrentUserId() ?: ""
        val localAvatarFile = if (currentUserId.isNotBlank()) {
            File(requireContext().filesDir, "avatar_${currentUserId}.jpg")
        } else null

        val localAvatarPath = if (localAvatarFile != null && localAvatarFile.exists() && localAvatarFile.length() > 0L) {
            localAvatarFile.absolutePath
        } else {
            SupabaseClient.getAvatarPath(currentUserId)
        }
        binding.ivUserAvatar.loadUserAvatar(localAvatarPath)

        if (currentUserId.isNotBlank()) {
            lifecycleScope.launch {
                when (val result = userRepository.getUserProfile(currentUserId)) {
                    is NetworkResult.Success -> {
                        val profile = result.data
                        if (!profile.profilePhoto.isNullOrBlank()) {
                            binding.ivUserAvatar.loadUserAvatar(profile.profilePhoto)
                        }
                    }
                    is NetworkResult.Error -> {}
                }
            }
        }
    }

    private fun setupSendComment() {
        binding.btnSendComment.setOnClickListener {
            val text = binding.etComment.text.toString().trim()
            if (text.isNotEmpty()) {
                lifecycleScope.launch {
                    binding.btnSendComment.isEnabled = false
                    when (val result = commentRepository.addComment(postId, text)) {
                        is NetworkResult.Success -> {
                            binding.etComment.text?.clear()
                            Toast.makeText(requireContext(), "Comment posted", Toast.LENGTH_SHORT).show()
                            loadComments()
                        }
                        is NetworkResult.Error -> {
                            Toast.makeText(requireContext(), result.message, Toast.LENGTH_SHORT).show()
                        }
                    }
                    binding.btnSendComment.isEnabled = true
                }
            }
        }
    }

    private fun loadComments() {
        lifecycleScope.launch {
            when (val result = commentRepository.fetchCommentsWithAuthors(postId)) {
                is NetworkResult.Success -> {
                    commentList.clear()
                    commentList.addAll(result.data)
                    commentAdapter.notifyDataSetChanged()
                    if (commentList.isNotEmpty()) {
                        binding.rvComments.scrollToPosition(commentList.size - 1)
                    }
                }
                is NetworkResult.Error -> {}
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
