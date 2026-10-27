package com.aroundus.app.fragments

import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.aroundus.app.R
import com.aroundus.app.adapters.HelpRequestAdapter
import com.aroundus.app.databinding.BottomSheetCreateHelpRequestBinding
import com.aroundus.app.databinding.FragmentHelpSupportBinding
import com.aroundus.app.models.Post
import com.aroundus.app.repository.LikeRepository
import com.aroundus.app.repository.NetworkResult
import com.aroundus.app.repository.PostRepository
import com.aroundus.app.repository.UserRepository
import com.aroundus.app.utils.AppRefreshManager
import com.aroundus.app.utils.PostActionUtils
import com.aroundus.app.utils.RefreshTarget
import com.aroundus.app.utils.applyAroundUsTheme
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class HelpSupportFragment : Fragment() {

    private var _binding: FragmentHelpSupportBinding? = null
    private val binding get() = _binding!!

    private val postRepository = PostRepository()
    private val userRepository = UserRepository()
    private val likeRepository = LikeRepository()

    private val allHelpPosts = mutableListOf<Post>()
    private val displayedHelpPosts = mutableListOf<Post>()
    private lateinit var helpAdapter: HelpRequestAdapter

    private var selectedCategory: String = "All"
    private var searchQuery: String = ""
    private var searchDebounceJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHelpSupportBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setupCategoryChips()
        setupSearch()
        setupRecyclerView()
        setupSwipeRefresh()
        observeRefreshEvents()

        loadHelpPosts(showLoading = true)
    }

    private fun setupToolbar() {
        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.btnComposeHelp.setOnClickListener {
            showCreateHelpRequestBottomSheet()
        }

        binding.btnEmptyCreateRequest.setOnClickListener {
            showCreateHelpRequestBottomSheet()
        }

        binding.btnFilter.setOnClickListener {
            // Reset filters to All and clear search
            selectedCategory = "All"
            binding.etSearchHelp.text?.clear()
            highlightActiveCategoryChip()
            filterPosts()
            Toast.makeText(requireContext(), "Showing all community requests", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupSearch() {
        binding.btnClearSearch.setOnClickListener {
            binding.etSearchHelp.text?.clear()
        }

        binding.etSearchHelp.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s?.toString()?.trim() ?: ""
                binding.btnClearSearch.visibility = if (searchQuery.isNotEmpty()) View.VISIBLE else View.GONE

                searchDebounceJob?.cancel()
                searchDebounceJob = viewLifecycleOwner.lifecycleScope.launch {
                    delay(200)
                    filterPosts()
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun setupCategoryChips() {
        binding.chipAll.setOnClickListener {
            selectCategory("All")
        }
        binding.chipGeneralHelp.setOnClickListener {
            selectCategory("General Help")
        }
        binding.chipLostFound.setOnClickListener {
            selectCategory("Lost & Found")
        }
        binding.chipRecommendations.setOnClickListener {
            selectCategory("Recommendations")
        }
        binding.chipEmergency.setOnClickListener {
            selectCategory("Emergency")
        }
        binding.chipServices.setOnClickListener {
            selectCategory("Services")
        }
        binding.chipOthers.setOnClickListener {
            selectCategory("Others")
        }
    }

    private fun selectCategory(cat: String) {
        selectedCategory = cat
        highlightActiveCategoryChip()
        filterPosts()
    }

    private fun highlightActiveCategoryChip() {
        val activeGreen = Color.parseColor("#0D8A3F")
        val defaultDark = Color.parseColor("#1E293B")
        val white = Color.WHITE

        // Reset all to default inactive style
        binding.chipAll.setBackgroundResource(R.drawable.bg_help_category_chip_inactive)
        binding.chipAll.setTextColor(defaultDark)

        val chips = listOf(
            Triple(binding.chipGeneralHelp, binding.ivIconGeneralHelp, binding.tvChipGeneralHelp),
            Triple(binding.chipLostFound, binding.ivIconLostFound, binding.tvChipLostFound),
            Triple(binding.chipRecommendations, binding.ivIconRecommendations, binding.tvChipRecommendations),
            Triple(binding.chipEmergency, binding.ivIconEmergency, binding.tvChipEmergency),
            Triple(binding.chipServices, binding.ivIconServices, binding.tvChipServices),
            Triple(binding.chipOthers, binding.ivIconOthers, binding.tvChipOthers)
        )

        for ((container, icon, label) in chips) {
            container.setBackgroundResource(R.drawable.bg_help_category_chip_inactive)
            label.setTextColor(defaultDark)
            icon.setColorFilter(defaultDark)
        }

        // Apply active green style to selected category
        when (selectedCategory) {
            "All" -> {
                binding.chipAll.setBackgroundResource(R.drawable.bg_help_category_all_active)
                binding.chipAll.setTextColor(white)
            }
            "General Help" -> activateChip(binding.chipGeneralHelp, binding.ivIconGeneralHelp, binding.tvChipGeneralHelp)
            "Lost & Found" -> activateChip(binding.chipLostFound, binding.ivIconLostFound, binding.tvChipLostFound)
            "Recommendations" -> activateChip(binding.chipRecommendations, binding.ivIconRecommendations, binding.tvChipRecommendations)
            "Emergency" -> activateChip(binding.chipEmergency, binding.ivIconEmergency, binding.tvChipEmergency)
            "Services" -> activateChip(binding.chipServices, binding.ivIconServices, binding.tvChipServices)
            "Others" -> activateChip(binding.chipOthers, binding.ivIconOthers, binding.tvChipOthers)
        }
    }

    private fun activateChip(container: View, icon: ImageView, label: TextView) {
        container.setBackgroundResource(R.drawable.bg_help_category_all_active)
        label.setTextColor(Color.WHITE)
        icon.setColorFilter(Color.WHITE)
    }

    private fun setupRecyclerView() {
        helpAdapter = HelpRequestAdapter(
            posts = displayedHelpPosts,
            onPostClick = { post ->
                val bundle = Bundle().apply { putString("postId", post.id) }
                findNavController().navigate(R.id.action_global_postDetail, bundle)
            },
            onLikeClick = { post ->
                viewLifecycleOwner.lifecycleScope.launch {
                    likeRepository.toggleLike(post.id, post.isLiked)
                    AppRefreshManager.notifyDataChanged(RefreshTarget.HOME_FEED)
                }
            },
            onCommentClick = { post ->
                val bundle = Bundle().apply { putString("postId", post.id) }
                findNavController().navigate(R.id.action_global_postDetail, bundle)
            },
            onShareClick = { post ->
                PostActionUtils.sharePost(requireContext(), post)
            },
            onAuthorClick = { post ->
                if (!post.authorId.isNullOrBlank()) {
                    val bundle = Bundle().apply { putString("userId", post.authorId) }
                    findNavController().navigate(R.id.action_global_profile, bundle)
                }
            }
        )

        binding.rvHelpRequests.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = helpAdapter
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefreshLayout.applyAroundUsTheme()
        binding.swipeRefreshLayout.setOnRefreshListener {
            loadHelpPosts(showLoading = false)
        }
    }

    private fun observeRefreshEvents() {
        viewLifecycleOwner.lifecycleScope.launch {
            AppRefreshManager.refreshEvents.collect { target ->
                if (target == RefreshTarget.HOME_FEED || target == RefreshTarget.ALL) {
                    loadHelpPosts(showLoading = false)
                }
            }
        }
    }

    private fun loadHelpPosts(showLoading: Boolean = false) {
        if (showLoading && allHelpPosts.isEmpty()) {
            _binding?.pbLoading?.visibility = View.VISIBLE
        }

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                when (val result = postRepository.fetchPostsWithAuthors(context?.applicationContext)) {
                    is NetworkResult.Success -> {
                        val helpCategories = setOf(
                            "general help", "lost & found", "lost and found", "recommendations",
                            "emergency", "services", "others", "help & support", "help"
                        )

                        val postsFromDb = result.data.filter { p ->
                            val cat = p.category.trim().lowercase()
                            helpCategories.contains(cat) ||
                            cat.contains("help") || cat.contains("recommend") ||
                            cat.contains("lost") || cat.contains("service")
                        }

                        allHelpPosts.clear()
                        allHelpPosts.addAll(postsFromDb)
                        filterPosts()
                    }
                    is NetworkResult.Error -> {
                        filterPosts()
                    }
                }
            } finally {
                _binding?.pbLoading?.visibility = View.GONE
                _binding?.swipeRefreshLayout?.isRefreshing = false
            }
        }
    }

    private fun filterPosts() {
        val query = searchQuery.lowercase()

        val filtered = allHelpPosts.filter { post ->
            val matchesCategory = if (selectedCategory == "All") {
                true
            } else {
                val cat = post.category.lowercase()
                when (selectedCategory) {
                    "General Help" -> cat.contains("general") || cat.contains("help")
                    "Lost & Found" -> cat.contains("lost") || cat.contains("found")
                    "Recommendations" -> cat.contains("recommend")
                    "Emergency" -> cat.contains("emerg") || cat.contains("alert")
                    "Services" -> cat.contains("service") || cat.contains("plumb") || cat.contains("electr")
                    "Others" -> cat.contains("other") || (!cat.contains("general") && !cat.contains("lost") && !cat.contains("recommend") && !cat.contains("service") && !cat.contains("emerg"))
                    else -> true
                }
            }

            val matchesSearch = query.isEmpty() ||
                    (post.title?.lowercase()?.contains(query) == true) ||
                    post.text.lowercase().contains(query) ||
                    post.userName.lowercase().contains(query) ||
                    post.neighborhood.lowercase().contains(query) ||
                    post.category.lowercase().contains(query)

            matchesCategory && matchesSearch
        }

        displayedHelpPosts.clear()
        displayedHelpPosts.addAll(filtered)
        helpAdapter.notifyDataSetChanged()

        _binding?.let { b ->
            val isEmpty = displayedHelpPosts.isEmpty()
            b.rvHelpRequests.visibility = if (isEmpty) View.GONE else View.VISIBLE
            b.layoutEmptyHelp.visibility = if (isEmpty) View.VISIBLE else View.GONE
        }
    }

    private fun showCreateHelpRequestBottomSheet() {
        val bottomSheet = BottomSheetDialog(requireContext())
        val sheetBinding = BottomSheetCreateHelpRequestBinding.inflate(layoutInflater)
        bottomSheet.setContentView(sheetBinding.root)

        sheetBinding.btnSubmitHelp.setOnClickListener {
            val title = sheetBinding.etHelpTitle.text?.toString()?.trim() ?: ""
            val desc = sheetBinding.etHelpDesc.text?.toString()?.trim() ?: ""

            if (title.isBlank()) {
                sheetBinding.tilHelpTitle.error = "Please enter a title for your request"
                return@setOnClickListener
            }
            sheetBinding.tilHelpTitle.error = null

            if (desc.isBlank()) {
                sheetBinding.tilHelpDesc.error = "Please describe what you need"
                return@setOnClickListener
            }
            sheetBinding.tilHelpDesc.error = null

            val selectedCategory = when (sheetBinding.chipGroupCategory.checkedChipId) {
                R.id.chipCatLost -> "Lost & Found"
                R.id.chipCatRecommendations -> "Recommendations"
                R.id.chipCatEmergency -> "Emergency"
                R.id.chipCatServices -> "Services"
                R.id.chipCatOthers -> "Others"
                else -> "General Help"
            }

            sheetBinding.btnSubmitHelp.isEnabled = false
            sheetBinding.btnSubmitHelp.text = "Submitting..."

            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val fullText = "$title\n$desc"
                    val location = com.aroundus.app.utils.UserLocationManager.getSelectedLocation(requireContext())?.displayName
                        ?: "Bhujpur, Kutch"

                    when (val res = postRepository.createPost(
                        text = fullText,
                        type = selectedCategory,
                        media = null,
                        location = location,
                        context = requireContext()
                    )) {
                        is NetworkResult.Success -> {
                            Toast.makeText(requireContext(), "Help request submitted to your community!", Toast.LENGTH_SHORT).show()
                            bottomSheet.dismiss()
                            AppRefreshManager.notifyDataChanged(RefreshTarget.HOME_FEED)
                            loadHelpPosts(showLoading = false)
                        }
                        is NetworkResult.Error -> {
                            Toast.makeText(requireContext(), "Could not submit: ${res.message}", Toast.LENGTH_LONG).show()
                            sheetBinding.btnSubmitHelp.isEnabled = true
                            sheetBinding.btnSubmitHelp.text = "Submit Help Request"
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                    sheetBinding.btnSubmitHelp.isEnabled = true
                    sheetBinding.btnSubmitHelp.text = "Submit Help Request"
                }
            }
        }

        bottomSheet.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        searchDebounceJob?.cancel()
        _binding = null
    }
}
