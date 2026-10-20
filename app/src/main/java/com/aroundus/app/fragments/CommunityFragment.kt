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
import com.aroundus.app.models.toUIModel
import com.aroundus.app.repository.GroupRepository
import com.aroundus.app.repository.NetworkResult
import kotlinx.coroutines.launch

class CommunityFragment : Fragment() {

    private var _binding: FragmentCommunityBinding? = null
    private val binding get() = _binding!!

    private lateinit var categoryAdapter: GroupCategoryAdapter
    private lateinit var myGroupsAdapter: MyGroupsAdapter
    private lateinit var discoverGroupsAdapter: DiscoverGroupsAdapter

    private val groupRepository = GroupRepository()
    private val myGroupsList = mutableListOf<Group>()
    private val discoverGroupsList = mutableListOf<Group>()

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
        loadSupabaseGroups()
    }

    private fun loadSupabaseGroups() {
        lifecycleScope.launch {
            when (val result = groupRepository.fetchGroups()) {
                is NetworkResult.Success -> {
                    val remote = result.data.map { it.toUIModel() }
                    discoverGroupsList.clear()
                    discoverGroupsList.addAll(remote)
                    filterGroups()
                }
                is NetworkResult.Error -> {}
            }
        }
    }

    private fun setupHeader() {
        binding.btnHeaderMenu.setOnClickListener {
            (activity as? MainActivity)?.toggleDrawer()
        }

        binding.btnHeaderNotifications.setOnClickListener {
            findNavController().navigate(R.id.action_global_notifications)
        }

        binding.btnFilter.setOnClickListener {
            Toast.makeText(requireContext(), "Filter options", Toast.LENGTH_SHORT).show()
        }

        binding.tvViewAllMyGroups.setOnClickListener {
            Toast.makeText(requireContext(), "Viewing all My Groups", Toast.LENGTH_SHORT).show()
        }

        binding.tvSeeAllDiscover.setOnClickListener {
            Toast.makeText(requireContext(), "Viewing all Discover Groups", Toast.LENGTH_SHORT).show()
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
                findNavController().navigate(R.id.action_community_to_groupDetail)
            },
            onBellToggle = { group ->
                val status = if (group.isNotificationOn) "enabled" else "muted"
                Toast.makeText(requireContext(), "Notifications $status for ${group.name}", Toast.LENGTH_SHORT).show()
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
                findNavController().navigate(R.id.action_community_to_groupDetail)
            },
            onJoinClick = { group ->
                val status = if (group.isJoined) "Joined" else "Left"
                Toast.makeText(requireContext(), "$status ${group.name}", Toast.LENGTH_SHORT).show()
            }
        )
        binding.rvDiscoverGroups.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = discoverGroupsAdapter
        }
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
            val matchesSearch = searchQuery.isEmpty() || group.name.contains(searchQuery, ignoreCase = true) || group.description.contains(searchQuery, ignoreCase = true)
            matchesCat && matchesSearch
        }
        myGroupsAdapter.updateList(filteredMy)

        // Filter Discover Groups
        val filteredDiscover = discoverGroupsList.filter { group ->
            val matchesCat = currentCategory == "All" || group.category.equals(currentCategory, ignoreCase = true)
            val matchesSearch = searchQuery.isEmpty() || group.name.contains(searchQuery, ignoreCase = true) || group.description.contains(searchQuery, ignoreCase = true)
            matchesCat && matchesSearch
        }
        discoverGroupsAdapter.updateList(filteredDiscover)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
