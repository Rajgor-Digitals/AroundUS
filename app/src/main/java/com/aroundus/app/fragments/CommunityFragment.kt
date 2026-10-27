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
import com.aroundus.app.activities.MainActivity
import com.aroundus.app.adapters.DiscoverGroupsAdapter
import com.aroundus.app.adapters.GroupCategoryAdapter
import com.aroundus.app.adapters.MyGroupsAdapter
import com.aroundus.app.databinding.FragmentCommunityBinding
import com.aroundus.app.models.Group
import com.aroundus.app.models.GroupCategory
import com.aroundus.app.models.GroupEntity
import com.aroundus.app.models.toUIModel
import com.aroundus.app.network.SupabaseClient
import com.aroundus.app.repository.GroupRepository
import com.aroundus.app.repository.NetworkResult
import com.aroundus.app.repository.UserRepository
import com.aroundus.app.utils.AppRefreshManager
import com.aroundus.app.utils.RefreshTarget
import com.aroundus.app.utils.applyAroundUsTheme
import kotlinx.coroutines.launch

class CommunityFragment : Fragment() {

    private var _binding: FragmentCommunityBinding? = null
    private val binding get() = _binding!!

    private lateinit var categoryAdapter: GroupCategoryAdapter
    private lateinit var myGroupsAdapter: MyGroupsAdapter
    private lateinit var discoverGroupsAdapter: DiscoverGroupsAdapter

    private val groupRepository = GroupRepository()
    private val userRepository = UserRepository()

    private val allFetchedGroups = mutableListOf<GroupEntity>()
    private val myGroupsList = mutableListOf<Group>()
    private val discoverGroupsList = mutableListOf<Group>()

    private var currentUserId: String = ""
    private var currentCategory = "All"
    private var searchQuery = ""

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCommunityBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupHeader()
        setupCategories()
        setupMyGroups()
        setupDiscoverGroups()
        setupSearch()
        setupCreateGroupActions()
        setupSwipeRefresh()
        observeRefreshEvents()

        loadSupabaseGroups(showLoading = true)
    }

    override fun onResume() {
        super.onResume()
        if (AppRefreshManager.isStale(RefreshTarget.GROUPS)) {
            loadSupabaseGroups(showLoading = false)
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefreshLayout.applyAroundUsTheme()
        binding.swipeRefreshLayout.setOnRefreshListener {
            loadSupabaseGroups(showLoading = false)
        }
    }

    private fun observeRefreshEvents() {
        viewLifecycleOwner.lifecycleScope.launch {
            AppRefreshManager.refreshEvents.collect { target ->
                if (target == RefreshTarget.GROUPS || target == RefreshTarget.ALL) {
                    loadSupabaseGroups(showLoading = false)
                }
            }
        }
    }

    private fun setupCreateGroupActions() {
        val showCreateDialog = {
            val bottomSheet = CreateGroupBottomSheetFragment.newInstance()
            bottomSheet.onGroupCreated = { createdGroup ->
                com.aroundus.app.utils.GroupJoinManager.recordGroupJoined(context, createdGroup.groupId, System.currentTimeMillis())
                loadSupabaseGroups()
            }
            bottomSheet.show(childFragmentManager, CreateGroupBottomSheetFragment.TAG)
        }

        binding.btnHeaderCreateGroup.setOnClickListener {
            showCreateDialog()
        }

        binding.fabCreateGroup.setOnClickListener {
            showCreateDialog()
        }
    }

    private fun loadSupabaseGroups(showLoading: Boolean = false) {
        if (showLoading && allFetchedGroups.isEmpty()) {
            binding.pbGroupsLoading.visibility = View.VISIBLE
        }
        lifecycleScope.launch {
            try {
                currentUserId = userRepository.resolveValidAuthorId().ifBlank {
                    SupabaseClient.getCurrentUserId() ?: ""
                }

                when (val result = groupRepository.fetchGroups()) {
                    is NetworkResult.Success -> {
                        binding.pbGroupsLoading.visibility = View.GONE
                        allFetchedGroups.clear()
                        allFetchedGroups.addAll(result.data)

                        processGroupsData(result.data)
                        AppRefreshManager.markRefreshed(RefreshTarget.GROUPS)
                    }
                    is NetworkResult.Error -> {
                        binding.pbGroupsLoading.visibility = View.GONE
                        if (allFetchedGroups.isEmpty()) {
                            binding.layoutEmptyDiscover.visibility = View.VISIBLE
                        }
                    }
                }
            } finally {
                binding.pbGroupsLoading.visibility = View.GONE
                binding.swipeRefreshLayout.isRefreshing = false
            }
        }
    }

    private fun processGroupsData(groups: List<GroupEntity>) {
        myGroupsList.clear()
        discoverGroupsList.clear()

        for (entity in groups) {
            val uiModel = entity.toUIModel(currentUserId)
            if (uiModel.isJoined) {
                myGroupsList.add(uiModel)
            } else {
                discoverGroupsList.add(uiModel)
            }
        }

        // Sort My Groups newest to lowest (most recently joined first, fallback to creation time)
        myGroupsList.sortWith(compareByDescending<Group> { group ->
            val joinedTime = com.aroundus.app.utils.GroupJoinManager.getGroupJoinedTime(context, group.id)
            if (joinedTime > 0L) joinedTime else group.createdAtMillis
        })

        // Sort Discover Groups newest to lowest (newest created first)
        discoverGroupsList.sortByDescending { it.createdAtMillis }

        binding.tvMyGroupsTitle.text = "My Groups (${myGroupsList.size})"
        binding.tvDiscoverGroupsTitle.text = "Discover Groups (${discoverGroupsList.size})"

        filterGroups()
    }

    private fun setupHeader() {
        binding.btnHeaderMenu.setOnClickListener {
            (activity as? MainActivity)?.toggleDrawer()
        }

        binding.btnHeaderNotifications.setOnClickListener {
            findNavController().navigate(R.id.action_global_notifications)
        }

        binding.btnFilter.setOnClickListener {
            binding.etSearchGroups.text?.clear()
            categoryAdapter.setSelectedCategory("All")
            currentCategory = "All"
            filterGroups()
            Toast.makeText(requireContext(), "Reset filters", Toast.LENGTH_SHORT).show()
        }

        binding.tvViewAllMyGroups.setOnClickListener {
            if (myGroupsList.isNotEmpty()) {
                openGroupDetail(myGroupsList.first())
            } else {
                Toast.makeText(requireContext(), "Join groups from Discover below", Toast.LENGTH_SHORT).show()
            }
        }

        binding.tvSeeAllDiscover.setOnClickListener {
            if (discoverGroupsList.isNotEmpty()) {
                binding.rvDiscoverGroups.smoothScrollToPosition(0)
            }
        }
    }

    private fun setupCategories() {
        val groupCategories = listOf(
            GroupCategory("1", "All", R.drawable.ic_grid_category, isSelected = true),
            GroupCategory("2", "Local", R.drawable.ic_home, isSelected = false),
            GroupCategory("3", "Buy / Sell", R.drawable.ic_sell, isSelected = false),
            GroupCategory("4", "Help & Support", R.drawable.ic_support_heart, isSelected = false),
            GroupCategory("5", "Activities", R.drawable.ic_event, isSelected = false)
        )
        categoryAdapter = GroupCategoryAdapter(groupCategories) { selectedCategory ->
            currentCategory = selectedCategory.name
            filterGroups()
        }
        binding.rvGroupCategories.apply {
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            adapter = categoryAdapter
        }
    }

    private fun setupMyGroups() {
        myGroupsAdapter = MyGroupsAdapter(
            groups = myGroupsList,
            onGroupClick = { group ->
                openGroupDetail(group)
            },
            onBellToggle = { group ->
                val status = if (group.isNotificationOn) "enabled" else "muted"
                Toast.makeText(requireContext(), "Notifications $status for ${group.name}", Toast.LENGTH_SHORT).show()
            },
            onLeaveClick = { group ->
                handleLeaveGroup(group)
            }
        )
        binding.rvMyGroups.apply {
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            adapter = myGroupsAdapter
        }
    }

    private fun setupDiscoverGroups() {
        discoverGroupsAdapter = DiscoverGroupsAdapter(
            groups = discoverGroupsList,
            onGroupClick = { group ->
                openGroupDetail(group)
            },
            onJoinClick = { group ->
                handleJoinGroup(group)
            }
        )
        binding.rvDiscoverGroups.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = discoverGroupsAdapter
        }
    }

    private fun handleJoinGroup(group: Group) {
        lifecycleScope.launch {
            com.aroundus.app.utils.GroupJoinManager.recordGroupJoined(context, group.id, System.currentTimeMillis())
            when (val res = groupRepository.joinGroup(group.id)) {
                is NetworkResult.Success -> {
                    Toast.makeText(requireContext(), "Joined ${group.name}!", Toast.LENGTH_SHORT).show()
                    loadSupabaseGroups()
                }
                is NetworkResult.Error -> {
                    Toast.makeText(requireContext(), res.message, Toast.LENGTH_LONG).show()
                    loadSupabaseGroups()
                }
            }
        }
    }

    private fun handleLeaveGroup(group: Group) {
        lifecycleScope.launch {
            com.aroundus.app.utils.GroupJoinManager.removeGroupJoined(context, group.id)
            when (val res = groupRepository.leaveGroup(group.id)) {
                is NetworkResult.Success -> {
                    Toast.makeText(requireContext(), "Left ${group.name}", Toast.LENGTH_SHORT).show()
                    loadSupabaseGroups()
                }
                is NetworkResult.Error -> {
                    Toast.makeText(requireContext(), res.message, Toast.LENGTH_LONG).show()
                    loadSupabaseGroups()
                }
            }
        }
    }

    private fun openGroupDetail(group: Group) {
        val bundle = Bundle().apply {
            putString("groupId", group.id)
            putString("groupName", group.name)
            putString("category", group.category)
            putString("description", group.description)
            putString("membersText", group.membersText)
            putBoolean("isJoined", group.isJoined)
            putInt("coverRes", group.coverRes)
            putString("imageUrl", group.imageUrl)
        }
        findNavController().navigate(R.id.action_community_to_groupDetail, bundle)
    }

    private fun setupSearch() {
        binding.etSearchGroups.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s?.toString()?.trim() ?: ""
                filterGroups()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun filterGroups() {
        // Filter My Groups
        val filteredMy = myGroupsList.filter { group ->
            val matchesCat = currentCategory == "All" || group.category.equals(currentCategory, ignoreCase = true)
            val matchesSearch = searchQuery.isEmpty() ||
                group.name.contains(searchQuery, ignoreCase = true) ||
                group.description.contains(searchQuery, ignoreCase = true)
            matchesCat && matchesSearch
        }
        myGroupsAdapter.updateList(filteredMy)

        // Show/hide My Groups empty state
        if (myGroupsList.isEmpty() && searchQuery.isEmpty()) {
            binding.layoutEmptyMyGroups.visibility = View.VISIBLE
            binding.rvMyGroups.visibility = View.GONE
        } else if (filteredMy.isEmpty() && searchQuery.isNotEmpty()) {
            binding.layoutEmptyMyGroups.visibility = View.GONE
            binding.rvMyGroups.visibility = View.GONE
        } else {
            binding.layoutEmptyMyGroups.visibility = View.GONE
            binding.rvMyGroups.visibility = View.VISIBLE
        }

        // Filter Discover Groups
        val filteredDiscover = discoverGroupsList.filter { group ->
            val matchesCat = currentCategory == "All" || group.category.equals(currentCategory, ignoreCase = true)
            val matchesSearch = searchQuery.isEmpty() ||
                group.name.contains(searchQuery, ignoreCase = true) ||
                group.description.contains(searchQuery, ignoreCase = true)
            matchesCat && matchesSearch
        }
        discoverGroupsAdapter.updateList(filteredDiscover)

        // Show/hide Discover Groups empty state
        if (filteredDiscover.isEmpty() && filteredMy.isEmpty()) {
            binding.layoutEmptyDiscover.visibility = View.VISIBLE
        } else {
            binding.layoutEmptyDiscover.visibility = View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
