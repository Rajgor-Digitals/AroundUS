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
import com.aroundus.app.adapters.CommentAdapter
import com.aroundus.app.databinding.FragmentPostDetailBinding
import com.aroundus.app.models.Comment
import com.aroundus.app.models.Post
import com.aroundus.app.network.SupabaseClient
import com.aroundus.app.repository.CommentRepository
import com.aroundus.app.repository.NetworkResult
import com.aroundus.app.repository.PostRepository
import com.aroundus.app.repository.UserRepository
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import com.aroundus.app.utils.loadUserAvatar
import com.aroundus.app.utils.loadPostImage
import com.bumptech.glide.Glide
import kotlinx.coroutines.launch
import java.io.File

class PostDetailFragment : Fragment() {

    private var _binding: FragmentPostDetailBinding? = null
    private val binding get() = _binding!!

    private val postRepository = PostRepository()
    private val userRepository = UserRepository()
    private val commentRepository = CommentRepository()

    private var currentPost: Post? = null
    private var postId: String = ""
    private var isLiked = false
    private var likeCount = 0

    private val commentList = mutableListOf<Comment>()
    private lateinit var commentAdapter: CommentAdapter
    private var exoPlayer: ExoPlayer? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPostDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }

        postId = arguments?.getString("postId") ?: ""

        setupCommentsRecyclerView()
        setupCommentInput()
        setupActionButtons()
        loadPostAndAuthor()
    }

    private fun setupCommentsRecyclerView() {
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

    private fun setupCommentInput() {
        val currentUserId = SupabaseClient.getCurrentUserId() ?: ""
        val localAvatarFile = if (currentUserId.isNotBlank()) {
            File(requireContext().filesDir, "avatar_${currentUserId}.jpg")
        } else null

        val localAvatarPath = if (localAvatarFile != null && localAvatarFile.exists() && localAvatarFile.length() > 0L) {
            localAvatarFile.absolutePath
        } else {
            SupabaseClient.getAvatarPath(currentUserId)
        }
        binding.ivCommentUserAvatar.loadUserAvatar(localAvatarPath)

        if (currentUserId.isNotBlank()) {
            lifecycleScope.launch {
                when (val result = userRepository.getUserProfile(currentUserId)) {
                    is NetworkResult.Success -> {
                        val profile = result.data
                        if (!profile.profilePhoto.isNullOrBlank()) {
                            binding.ivCommentUserAvatar.loadUserAvatar(profile.profilePhoto)
                        }
                    }
                    is NetworkResult.Error -> {}
                }
            }
        }

        binding.btnSubmitComment.setOnClickListener {
            val text = binding.etCommentInput.text?.toString()?.trim() ?: ""
            if (text.isBlank()) {
                Toast.makeText(requireContext(), "Please write a comment", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (postId.isBlank()) return@setOnClickListener

            lifecycleScope.launch {
                binding.btnSubmitComment.isEnabled = false
                when (val result = commentRepository.addComment(postId, text)) {
                    is NetworkResult.Success -> {
                        binding.etCommentInput.text?.clear()
                        Toast.makeText(requireContext(), "Comment added!", Toast.LENGTH_SHORT).show()
                        loadComments()
                    }
                    is NetworkResult.Error -> {
                        Toast.makeText(requireContext(), result.message, Toast.LENGTH_SHORT).show()
                    }
                }
                binding.btnSubmitComment.isEnabled = true
            }
        }
    }

    private fun setupActionButtons() {
        val currentUserId = SupabaseClient.getCurrentUserId() ?: ""

        // Like button
        binding.btnLike.setOnClickListener {
            isLiked = !isLiked
            if (isLiked) {
                likeCount++
                binding.ivLikeIcon.setImageResource(R.drawable.ic_favorite)
                binding.ivLikeIcon.setColorFilter(Color.parseColor("#EF4444"))
                binding.tvLikesCount.setTextColor(Color.parseColor("#EF4444"))
                binding.tvLikesCount.text = "$likeCount likes"
            } else {
                likeCount = (likeCount - 1).coerceAtLeast(0)
                binding.ivLikeIcon.setImageResource(R.drawable.ic_favorite_border)
                binding.ivLikeIcon.setColorFilter(Color.parseColor("#64748B"))
                binding.tvLikesCount.setTextColor(Color.parseColor("#64748B"))
                binding.tvLikesCount.text = "$likeCount likes"
            }
        }

        // Share button
        binding.btnShare.setOnClickListener {
            Toast.makeText(requireContext(), "Post link copied to clipboard!", Toast.LENGTH_SHORT).show()
        }

        // Save button
        var isSaved = if (postId.isNotBlank() && currentUserId.isNotBlank()) {
            SupabaseClient.getSavedPostIds(currentUserId).contains(postId)
        } else false

        if (isSaved) {
            binding.btnSave.setColorFilter(Color.parseColor("#0D8A3F"))
        }

        binding.btnSave.setOnClickListener {
            if (currentUserId.isNotBlank() && postId.isNotBlank()) {
                isSaved = SupabaseClient.toggleSavedPost(currentUserId, postId)
                if (isSaved) {
                    binding.btnSave.setColorFilter(Color.parseColor("#0D8A3F"))
                    Toast.makeText(requireContext(), "Saved to bookmarks", Toast.LENGTH_SHORT).show()
                } else {
                    binding.btnSave.setColorFilter(Color.parseColor("#64748B"))
                    Toast.makeText(requireContext(), "Removed from bookmarks", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Follow button
        binding.btnFollow.setOnClickListener {
            val authorId = currentPost?.authorId ?: ""
            if (authorId.isNotBlank()) {
                lifecycleScope.launch {
                    when (userRepository.followUser(authorId)) {
                        is NetworkResult.Success -> {
                            binding.btnFollow.text = "Following"
                            binding.btnFollow.isEnabled = false
                            Toast.makeText(requireContext(), "Followed successfully", Toast.LENGTH_SHORT).show()
                        }
                        is NetworkResult.Error -> {}
                    }
                }
            }
        }

        // Click author to view their profile
        binding.ivAvatar.setOnClickListener { openAuthorProfile() }
        binding.tvUserName.setOnClickListener { openAuthorProfile() }
    }

    private fun openAuthorProfile() {
        val authorId = currentPost?.authorId ?: ""
        if (authorId.isNotBlank()) {
            val bundle = Bundle().apply { putString("userId", authorId) }
            findNavController().navigate(R.id.myProfileFragment, bundle)
        }
    }

    private fun loadPostAndAuthor() {
        if (postId.isBlank()) return

        // 1. Instant 0ms cache-first bind if post is cached
        val cached = postRepository.getCachedPosts(context).find { it.id == postId }
        if (cached != null) {
            bindPostData(cached)
        }

        // 2. Fetch fresh post and original author profile from Supabase database
        lifecycleScope.launch {
            when (val result = postRepository.getPostById(postId, context)) {
                is NetworkResult.Success -> {
                    bindPostData(result.data)
                }
                is NetworkResult.Error -> {}
            }
            loadComments()
        }
    }

    private fun bindPostData(post: Post) {
        currentPost = post
        likeCount = post.likesCount
        binding.tvUserName.text = post.userName?.ifBlank { "Neighbor" } ?: "Neighbor"
        val loc = post.neighborhood ?: "Bhujpur, Kutch"
        val time = post.timeAgo ?: "Just now"
        binding.tvLocationTime.text = "$loc · $time"
        binding.chipCategory.text = post.category ?: "General"
        binding.tvPostText.text = post.text ?: ""
        binding.tvLikesCount.text = "$likeCount likes"

        // Author profile photo resolution from Database
        val currentUserId = SupabaseClient.getCurrentUserId() ?: ""
        val currentName = SupabaseClient.getCurrentUserName() ?: ""
        val isCurrentAuthor = (!post.authorId.isNullOrBlank() && post.authorId == currentUserId) ||
                (!post.userName.isNullOrBlank() && post.userName.equals(currentName, ignoreCase = true))

        val localAvatarFile = if (isCurrentAuthor && currentUserId.isNotBlank()) {
            File(requireContext().filesDir, "avatar_${currentUserId}.jpg")
        } else null

        val cachedAvatar = if (!post.authorId.isNullOrBlank()) {
            SupabaseClient.getAvatarPath(post.authorId)
        } else if (isCurrentAuthor && currentUserId.isNotBlank()) {
            SupabaseClient.getAvatarPath(currentUserId)
        } else null

        val avatarPath = if (!post.userAvatarPath.isNullOrBlank()) {
            post.userAvatarPath
        } else if (!cachedAvatar.isNullOrBlank()) {
            cachedAvatar
        } else if (localAvatarFile != null && localAvatarFile.exists() && localAvatarFile.length() > 0L) {
            localAvatarFile.absolutePath
        } else {
            null
        }

        binding.ivAvatar.loadUserAvatar(avatarPath, post.userAvatar)

        // Query author directly from Supabase database to ensure 100% original profile photo
        if (!post.authorId.isNullOrBlank()) {
            lifecycleScope.launch {
                when (val userResult = userRepository.getUserProfile(post.authorId)) {
                    is NetworkResult.Success -> {
                        val author = userResult.data
                        if (!author.name.isNullOrBlank()) {
                            binding.tvUserName.text = author.name
                        }
                        if (!author.profilePhoto.isNullOrBlank()) {
                            binding.ivAvatar.loadUserAvatar(author.profilePhoto, post.userAvatar)
                            SupabaseClient.saveAvatarPath(post.authorId, author.profilePhoto)
                        }
                    }
                    is NetworkResult.Error -> {}
                }
            }
        }

        // Post Media (Image or Video)
        val media = post.mediaUrl
        if (!media.isNullOrBlank()) {
            binding.cardMediaContainer.visibility = View.VISIBLE
            if (post.isVideo) {
                binding.badgeVideoTag.visibility = View.VISIBLE
                binding.ivDetailPlayIcon.visibility = View.VISIBLE
                binding.progressVideoLoading.visibility = View.GONE
                binding.ivPostImage.visibility = View.VISIBLE
                binding.ivPostImage.loadPostImage(media)

                val startVideoAction = View.OnClickListener {
                    playVideo(media)
                }

                binding.ivDetailPlayIcon.setOnClickListener(startVideoAction)
                binding.cardMediaContainer.setOnClickListener(startVideoAction)
            } else {
                releasePlayer()
                binding.playerView.visibility = View.GONE
                binding.ivDetailPlayIcon.visibility = View.GONE
                binding.badgeVideoTag.visibility = View.GONE
                binding.progressVideoLoading.visibility = View.GONE
                binding.ivPostImage.visibility = View.VISIBLE
                binding.ivPostImage.loadPostImage(media)
            }
        } else if (post.imageRes != null) {
            releasePlayer()
            binding.cardMediaContainer.visibility = View.VISIBLE
            binding.playerView.visibility = View.GONE
            binding.ivDetailPlayIcon.visibility = View.GONE
            binding.badgeVideoTag.visibility = View.GONE
            binding.progressVideoLoading.visibility = View.GONE
            binding.ivPostImage.visibility = View.VISIBLE
            binding.ivPostImage.setImageResource(post.imageRes)
        } else {
            releasePlayer()
            binding.cardMediaContainer.visibility = View.GONE
        }
    }

    private fun playVideo(videoUrl: String) {
        binding.ivDetailPlayIcon.visibility = View.GONE
        binding.progressVideoLoading.visibility = View.VISIBLE
        binding.playerView.visibility = View.VISIBLE

        try {
            // Release existing player instance safely
            releasePlayer()

            // Optimized progressive buffering: starts playing after just 500ms of buffer
            val loadControl = DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    /* minBufferMs = */ 2500,
                    /* maxBufferMs = */ 30000,
                    /* bufferForPlaybackMs = */ 500,
                    /* bufferForPlaybackAfterRebufferMs = */ 1000
                )
                .build()

            val player = ExoPlayer.Builder(requireContext())
                .setLoadControl(loadControl)
                .build()

            exoPlayer = player
            binding.playerView.player = player

            val mediaItem = MediaItem.fromUri(Uri.parse(videoUrl.trim()))
            player.setMediaItem(mediaItem)
            player.repeatMode = Player.REPEAT_MODE_ALL
            player.prepare()
            player.playWhenReady = true

            player.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_BUFFERING -> {
                            binding.progressVideoLoading.visibility = View.VISIBLE
                        }
                        Player.STATE_READY -> {
                            binding.progressVideoLoading.visibility = View.GONE
                            binding.ivPostImage.visibility = View.GONE
                        }
                        Player.STATE_ENDED -> {
                            binding.progressVideoLoading.visibility = View.GONE
                        }
                        Player.STATE_IDLE -> {
                            binding.progressVideoLoading.visibility = View.GONE
                        }
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    binding.progressVideoLoading.visibility = View.GONE
                    binding.ivDetailPlayIcon.visibility = View.VISIBLE
                    binding.ivPostImage.visibility = View.VISIBLE
                    Toast.makeText(requireContext(), "Playback notice: ${error.message ?: "Retrying video stream"}", Toast.LENGTH_SHORT).show()
                }
            })
        } catch (e: Exception) {
            binding.progressVideoLoading.visibility = View.GONE
            binding.ivDetailPlayIcon.visibility = View.VISIBLE
            Toast.makeText(requireContext(), "Error initializing video: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun releasePlayer() {
        try {
            exoPlayer?.stop()
            exoPlayer?.release()
            exoPlayer = null
            _binding?.playerView?.player = null
        } catch (_: Exception) {}
    }

    private fun loadComments() {
        if (postId.isBlank()) return

        lifecycleScope.launch {
            when (val result = commentRepository.fetchCommentsWithAuthors(postId)) {
                is NetworkResult.Success -> {
                    commentList.clear()
                    commentList.addAll(result.data)
                    commentAdapter.notifyDataSetChanged()
                    binding.tvCommentsTitle.text = "Comments (${commentList.size})"
                }
                is NetworkResult.Error -> {}
            }
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            exoPlayer?.pause()
        } catch (_: Exception) {}
    }

    override fun onDestroyView() {
        releasePlayer()
        super.onDestroyView()
        _binding = null
    }
}
