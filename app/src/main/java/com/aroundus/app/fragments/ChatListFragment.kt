package com.aroundus.app.fragments

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.aroundus.app.R
import com.aroundus.app.activities.ConversationActivity
import com.aroundus.app.activities.MainActivity
import com.aroundus.app.adapters.ChatAdapter
import com.aroundus.app.databinding.FragmentChatListBinding
import com.aroundus.app.models.ChatItem
import com.aroundus.app.models.resolveGroupCoverRes
import com.aroundus.app.models.toUIModel
import com.aroundus.app.network.SupabaseClient
import com.aroundus.app.repository.GroupRepository
import com.aroundus.app.repository.MessageRepository
import com.aroundus.app.repository.NetworkResult
import com.aroundus.app.repository.PostRepository
import com.aroundus.app.repository.UserRepository
import com.aroundus.app.utils.AppRefreshManager
import com.aroundus.app.utils.RefreshTarget
import com.aroundus.app.utils.applyAroundUsTheme
import kotlinx.coroutines.launch

class ChatListFragment : Fragment() {

    private var _binding: FragmentChatListBinding? = null
    private val binding get() = _binding!!
    private val messageRepository = MessageRepository()
    private val userRepository = UserRepository()
    private val groupRepository = GroupRepository()
    private val postRepository = PostRepository()

    private val allChatsList = mutableListOf<ChatItem>()
    private val allGroupsList = mutableListOf<ChatItem>()
    private val allAnnouncementsList = mutableListOf<ChatItem>()
    private val displayedChatsList = mutableListOf<ChatItem>()
    private lateinit var chatAdapter: ChatAdapter
    private var currentTab = TabType.CHATS

    enum class TabType {
        CHATS, GROUPS, ANNOUNCEMENTS
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentChatListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        initChatList()
        setupHeaderActions()
        setupSearchBar()
        setupTabs()
        setupSwipeRefresh()
        observeRefreshEvents()

        loadSupabaseConversations()
    }

    override fun onResume() {
        super.onResume()
        if (AppRefreshManager.isStale(RefreshTarget.MESSAGES)) {
            refreshCurrentTab()
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefreshLayout.applyAroundUsTheme()
        binding.swipeRefreshLayout.setOnRefreshListener {
            refreshCurrentTab()
        }
    }

    private fun observeRefreshEvents() {
        viewLifecycleOwner.lifecycleScope.launch {
            AppRefreshManager.refreshEvents.collect { target ->
                if (target == RefreshTarget.MESSAGES || target == RefreshTarget.ALL) {
                    refreshCurrentTab()
                }
            }
        }
    }

    private fun refreshCurrentTab() {
        when (currentTab) {
            TabType.CHATS -> loadSupabaseConversations()
            TabType.GROUPS, TabType.ANNOUNCEMENTS -> {
                filterListByCurrentTab()
                binding.swipeRefreshLayout.isRefreshing = false
                AppRefreshManager.markRefreshed(RefreshTarget.MESSAGES)
            }
        }
    }

    private fun initChatList() {
        allChatsList.clear()
        displayedChatsList.clear()
    }

    private fun setupRecyclerView() {
        chatAdapter = ChatAdapter(displayedChatsList) { chat ->
            val intent = Intent(requireContext(), ConversationActivity::class.java).apply {
                if (currentTab == TabType.GROUPS) {
                    putExtra("groupId", chat.id)
                    putExtra("userName", chat.userName)
                    putExtra("avatarRes", chat.avatarRes)
                    putExtra("avatarUrl", chat.avatarUrl)
                    putExtra("members", chat.time)
                    putExtra("isGroup", true)
                } else {
                    putExtra("userId", chat.id)
                    putExtra("userName", chat.userName)
                    putExtra("avatarUrl", chat.avatarUrl)
                    putExtra("avatarRes", chat.avatarRes)
                    putExtra("isOnline", chat.isOnline)
                }
            }
            startActivity(intent)
        }

        binding.rvChats.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = chatAdapter
        }
    }

    private fun setupHeaderActions() {
        binding.btnMenu.setOnClickListener {
            (activity as? MainActivity)?.openDrawer()
        }

        binding.btnCompose.setOnClickListener {
            showNewChatDialog()
        }

        binding.llFilterSelector.setOnClickListener {
            binding.tvFilterLabel.text = if (binding.tvFilterLabel.text == "All Conversations") "Unread First" else "All Conversations"
            if (binding.tvFilterLabel.text == "Unread First") {
                displayedChatsList.sortByDescending { it.unreadCount }
            } else {
                filterListByCurrentTab()
            }
            if (::chatAdapter.isInitialized) {
                chatAdapter.notifyDataSetChanged()
            }
        }

        binding.btnSort.setOnClickListener {
            displayedChatsList.reverse()
            if (::chatAdapter.isInitialized) {
                chatAdapter.notifyDataSetChanged()
            }
        }
    }

    private fun showNewChatDialog() {
        NewConversationBottomSheetFragment.newInstance()
            .show(childFragmentManager, NewConversationBottomSheetFragment.TAG)
    }

    private fun setupSearchBar() {
        binding.etSearchMessages.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterSearch(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.btnFilter.setOnClickListener {
            Toast.makeText(requireContext(), "Filter conversations", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupTabs() {
        binding.tabChats.setOnClickListener { selectTab(TabType.CHATS) }
        binding.tabGroups.setOnClickListener { selectTab(TabType.GROUPS) }
        binding.tabAnnouncements.setOnClickListener { selectTab(TabType.ANNOUNCEMENTS) }
    }

    private fun selectTab(tab: TabType) {
        currentTab = tab
        val activeGreen = ContextCompat.getColor(requireContext(), R.color.primary)
        val inactiveGrey = ContextCompat.getColor(requireContext(), R.color.text_secondary)

        // Reset all tabs
        binding.ivTabChatsIcon.setColorFilter(inactiveGrey)
        binding.tvTabChatsText.setTextColor(inactiveGrey)
        binding.indicatorChats.visibility = View.INVISIBLE

        binding.ivTabGroupsIcon.setColorFilter(inactiveGrey)
        binding.tvTabGroupsText.setTextColor(inactiveGrey)
        binding.indicatorGroups.visibility = View.INVISIBLE

        binding.ivTabAnnouncementsIcon.setColorFilter(inactiveGrey)
        binding.tvTabAnnouncementsText.setTextColor(inactiveGrey)
        binding.indicatorAnnouncements.visibility = View.INVISIBLE

        when (tab) {
            TabType.CHATS -> {
                binding.ivTabChatsIcon.setColorFilter(activeGreen)
                binding.tvTabChatsText.setTextColor(activeGreen)
                binding.indicatorChats.visibility = View.VISIBLE
            }
            TabType.GROUPS -> {
                binding.ivTabGroupsIcon.setColorFilter(activeGreen)
                binding.tvTabGroupsText.setTextColor(activeGreen)
                binding.indicatorGroups.visibility = View.VISIBLE
            }
            TabType.ANNOUNCEMENTS -> {
                binding.ivTabAnnouncementsIcon.setColorFilter(activeGreen)
                binding.tvTabAnnouncementsText.setTextColor(activeGreen)
                binding.indicatorAnnouncements.visibility = View.VISIBLE
            }
        }

        filterListByCurrentTab()
    }

    private fun filterListByCurrentTab() {
        displayedChatsList.clear()
        when (currentTab) {
            TabType.CHATS -> {
                displayedChatsList.addAll(allChatsList)
                if (::chatAdapter.isInitialized) {
                    chatAdapter.notifyDataSetChanged()
                }
            }
            TabType.GROUPS -> {
                if (allGroupsList.isNotEmpty()) {
                    displayedChatsList.addAll(allGroupsList)
                    if (::chatAdapter.isInitialized) {
                        chatAdapter.notifyDataSetChanged()
                    }
                }
                lifecycleScope.launch {
                    val groupRes = groupRepository.fetchGroups()
                    if (groupRes is NetworkResult.Success && currentTab == TabType.GROUPS) {
                        allGroupsList.clear()
                        allGroupsList.addAll(groupRes.data.map { entity ->
                            val uiGroup = entity.toUIModel()
                            ChatItem(
                                id = uiGroup.id,
                                userName = uiGroup.name,
                                avatarRes = uiGroup.coverRes,
                                lastMessage = uiGroup.description.ifBlank { "Group conversation" },
                                time = uiGroup.membersText,
                                unreadCount = 0,
                                isOnline = false,
                                avatarUrl = uiGroup.imageUrl
                            )
                        })
                        displayedChatsList.clear()
                        displayedChatsList.addAll(allGroupsList)
                        if (::chatAdapter.isInitialized) {
                            chatAdapter.notifyDataSetChanged()
                        }
                    }
                }
            }
            TabType.ANNOUNCEMENTS -> {
                if (allAnnouncementsList.isNotEmpty()) {
                    displayedChatsList.addAll(allAnnouncementsList)
                    if (::chatAdapter.isInitialized) {
                        chatAdapter.notifyDataSetChanged()
                    }
                }
                lifecycleScope.launch {
                    val postRes = postRepository.fetchPostsWithAuthors(context?.applicationContext)
                    if (postRes is NetworkResult.Success && currentTab == TabType.ANNOUNCEMENTS) {
                        val announcementPosts = postRes.data.filter {
                            it.category.equals("Announcement", ignoreCase = true) ||
                            it.category.equals("Alert", ignoreCase = true) ||
                            it.category.equals("Notice", ignoreCase = true)
                        }
                        allAnnouncementsList.clear()
                        allAnnouncementsList.addAll(announcementPosts.map {
                            ChatItem(
                                id = it.id,
                                userName = it.userName,
                                avatarRes = it.userAvatar,
                                lastMessage = it.text,
                                time = it.timeAgo,
                                unreadCount = 0,
                                isOnline = false,
                                avatarUrl = it.userAvatarPath
                            )
                        })
                        displayedChatsList.clear()
                        displayedChatsList.addAll(allAnnouncementsList)
                        if (::chatAdapter.isInitialized) {
                            chatAdapter.notifyDataSetChanged()
                        }
                    }
                }
            }
        }
    }

    private fun filterSearch(query: String) {
        if (query.isBlank()) {
            filterListByCurrentTab()
        } else {
            val sourceList = when (currentTab) {
                TabType.CHATS -> allChatsList
                TabType.GROUPS -> allGroupsList
                TabType.ANNOUNCEMENTS -> allAnnouncementsList
            }
            val filtered = sourceList.filter {
                it.userName.contains(query, ignoreCase = true) ||
                it.lastMessage.contains(query, ignoreCase = true)
            }
            displayedChatsList.clear()
            displayedChatsList.addAll(filtered)
            if (::chatAdapter.isInitialized) {
                chatAdapter.notifyDataSetChanged()
            }
        }
    }

    private fun loadSupabaseConversations() {
        lifecycleScope.launch {
            try {
                val currentUserId = userRepository.resolveValidAuthorId().ifBlank { SupabaseClient.getCurrentUserId() ?: "" }
                when (val result = messageRepository.fetchUserConversations()) {
                is NetworkResult.Success -> {
                    allChatsList.clear()
                    allChatsList.addAll(result.data)

                    // If user has no active chats yet, provide registered community neighbors to initiate conversations
                    if (allChatsList.isEmpty()) {
                        val usersResult = userRepository.getAllUsers()
                        if (usersResult is NetworkResult.Success) {
                            val neighbors = usersResult.data.filter { it.userId != currentUserId }
                            val neighborChats = neighbors.map { user ->
                                ChatItem(
                                    id = user.userId,
                                    userName = user.name.ifBlank { "Neighbor" },
                                    avatarRes = 0,
                                    lastMessage = "Tap to start conversation",
                                    time = "New",
                                    unreadCount = 0,
                                    isOnline = true,
                                    avatarUrl = user.profilePhoto
                                )
                            }
                            allChatsList.addAll(neighborChats)
                        }
                    }

                    if (currentTab == TabType.CHATS) {
                        displayedChatsList.clear()
                        displayedChatsList.addAll(allChatsList)
                        if (::chatAdapter.isInitialized) {
                            chatAdapter.notifyDataSetChanged()
                        }
                    }
                }
                is NetworkResult.Error -> {
                    // Fallback to registered neighbors if conversations fetch fails
                    val usersResult = userRepository.getAllUsers()
                    if (usersResult is NetworkResult.Success) {
                        allChatsList.clear()
                        val neighbors = usersResult.data.filter { it.userId != currentUserId }
                        allChatsList.addAll(neighbors.map { user ->
                            ChatItem(
                                id = user.userId,
                                userName = user.name.ifBlank { "Neighbor" },
                                avatarRes = 0,
                                lastMessage = "Tap to start conversation",
                                time = "New",
                                unreadCount = 0,
                                isOnline = true,
                                avatarUrl = user.profilePhoto
                            )
                        })
                        if (currentTab == TabType.CHATS) {
                            displayedChatsList.clear()
                            displayedChatsList.addAll(allChatsList)
                            if (::chatAdapter.isInitialized) {
                                chatAdapter.notifyDataSetChanged()
                            }
                        }
                    }
                }
            }
        } finally {
            binding.swipeRefreshLayout.isRefreshing = false
            AppRefreshManager.markRefreshed(RefreshTarget.MESSAGES)
        }
    }
}

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
