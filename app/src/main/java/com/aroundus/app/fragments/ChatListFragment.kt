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
import com.aroundus.app.utils.UnreadMessageManager
import com.aroundus.app.utils.applyAroundUsTheme
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
    private var currentFilterMode = FilterMode.ALL

    enum class TabType {
        CHATS, GROUPS, ANNOUNCEMENTS
    }

    enum class FilterMode {
        ALL, UNREAD_ONLY, UNREAD_FIRST
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
        observeUnreadCounts()

        loadSupabaseConversations()
    }

    override fun onResume() {
        super.onResume()
        refreshCurrentTab()
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

    private fun observeUnreadCounts() {
        viewLifecycleOwner.lifecycleScope.launch {
            UnreadMessageManager.totalUnreadCountFlow.collect {
                updateUnreadBadges()
            }
        }
    }

    private fun refreshCurrentTab() {
        when (currentTab) {
            TabType.CHATS -> loadSupabaseConversations()
            TabType.GROUPS -> {
                lifecycleScope.launch {
                    val groupRes = messageRepository.fetchGroupConversations()
                    if (groupRes is NetworkResult.Success) {
                        allGroupsList.clear()
                        allGroupsList.addAll(groupRes.data)
                    }
                    filterListByCurrentTab()
                    updateUnreadBadges()
                    binding.swipeRefreshLayout.isRefreshing = false
                    AppRefreshManager.markRefreshed(RefreshTarget.MESSAGES)
                }
            }
            TabType.ANNOUNCEMENTS -> {
                filterListByCurrentTab()
                updateUnreadBadges()
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
        chatAdapter = ChatAdapter(
            chats = displayedChatsList,
            onChatClick = { chat ->
                val currentUserId = SupabaseClient.getCurrentUserId() ?: ""
                if (currentTab == TabType.GROUPS) {
                    UnreadMessageManager.markGroupAsRead(requireContext(), currentUserId, chat.id)
                } else {
                    UnreadMessageManager.markConversationAsRead(requireContext(), currentUserId, chat.id)
                }

                // Optimistically clear unread on clicked item for instant feel
                val idx = displayedChatsList.indexOfFirst { it.id == chat.id }
                if (idx != -1) {
                    displayedChatsList[idx] = displayedChatsList[idx].copy(unreadCount = 0)
                    chatAdapter.notifyItemChanged(idx)
                }
                updateUnreadBadges()

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
            },
            onChatLongClick = { chat ->
                showChatOptionsDialog(chat)
                true
            }
        )

        binding.rvChats.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = chatAdapter
        }
    }

    private fun showChatOptionsDialog(chat: ChatItem) {
        val currentUserId = SupabaseClient.getCurrentUserId() ?: ""
        val isUnread = chat.unreadCount > 0
        val toggleReadLabel = if (isUnread) "Mark as read" else "Mark as unread"

        val options = arrayOf(
            toggleReadLabel,
            "Mute notifications",
            "View profile / details"
        )

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(chat.userName)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        // Toggle Read/Unread
                        if (isUnread) {
                            if (currentTab == TabType.GROUPS) {
                                UnreadMessageManager.markGroupAsRead(requireContext(), currentUserId, chat.id)
                            } else {
                                UnreadMessageManager.markConversationAsRead(requireContext(), currentUserId, chat.id)
                            }
                            updateChatUnreadState(chat.id, 0)
                            Toast.makeText(requireContext(), "Marked as read", Toast.LENGTH_SHORT).show()
                        } else {
                            if (currentTab == TabType.GROUPS) {
                                UnreadMessageManager.markGroupAsUnread(requireContext(), currentUserId, chat.id)
                            } else {
                                UnreadMessageManager.markConversationAsUnread(requireContext(), currentUserId, chat.id)
                            }
                            updateChatUnreadState(chat.id, 1)
                            Toast.makeText(requireContext(), "Marked as unread", Toast.LENGTH_SHORT).show()
                        }
                    }
                    1 -> {
                        Toast.makeText(requireContext(), "Notifications muted for ${chat.userName}", Toast.LENGTH_SHORT).show()
                    }
                    2 -> {
                        // Open chat
                        binding.rvChats.findViewHolderForAdapterPosition(displayedChatsList.indexOfFirst { it.id == chat.id })?.itemView?.performClick()
                    }
                }
            }
            .show()
    }

    private fun updateChatUnreadState(chatId: String, newCount: Int) {
        val targetList = when (currentTab) {
            TabType.CHATS -> allChatsList
            TabType.GROUPS -> allGroupsList
            TabType.ANNOUNCEMENTS -> allAnnouncementsList
        }
        val targetIdx = targetList.indexOfFirst { it.id == chatId }
        if (targetIdx != -1) {
            targetList[targetIdx] = targetList[targetIdx].copy(unreadCount = newCount)
        }

        val dispIdx = displayedChatsList.indexOfFirst { it.id == chatId }
        if (dispIdx != -1) {
            displayedChatsList[dispIdx] = displayedChatsList[dispIdx].copy(unreadCount = newCount)
            chatAdapter.notifyItemChanged(dispIdx)
        }

        updateUnreadBadges()
    }

    private fun setupHeaderActions() {
        binding.btnMenu.setOnClickListener {
            (activity as? MainActivity)?.openDrawer()
        }

        binding.btnCompose.setOnClickListener {
            showNewChatDialog()
        }

        binding.llFilterSelector.setOnClickListener {
            showFilterMenu()
        }

        binding.btnSort.setOnClickListener {
            displayedChatsList.reverse()
            if (::chatAdapter.isInitialized) {
                chatAdapter.notifyDataSetChanged()
            }
        }
    }

    private fun showFilterMenu() {
        val currentUserId = SupabaseClient.getCurrentUserId() ?: ""
        val options = arrayOf(
            "All Conversations",
            "Unread Only",
            "Unread First",
            "Mark All as Read"
        )

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Filter Conversations")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        currentFilterMode = FilterMode.ALL
                        binding.tvFilterLabel.text = "All Conversations"
                        filterListByCurrentTab()
                    }
                    1 -> {
                        currentFilterMode = FilterMode.UNREAD_ONLY
                        binding.tvFilterLabel.text = "Unread Only"
                        filterListByCurrentTab()
                    }
                    2 -> {
                        currentFilterMode = FilterMode.UNREAD_FIRST
                        binding.tvFilterLabel.text = "Unread First"
                        filterListByCurrentTab()
                    }
                    3 -> {
                        // Mark all as read
                        UnreadMessageManager.markAllAsRead(
                            context = requireContext(),
                            currentUserId = currentUserId,
                            directPartnerIds = allChatsList.map { it.id },
                            groupIds = allGroupsList.map { it.id }
                        )
                        for (i in allChatsList.indices) {
                            allChatsList[i] = allChatsList[i].copy(unreadCount = 0)
                        }
                        for (i in allGroupsList.indices) {
                            allGroupsList[i] = allGroupsList[i].copy(unreadCount = 0)
                        }
                        filterListByCurrentTab()
                        updateUnreadBadges()
                        Toast.makeText(requireContext(), "All conversations marked as read", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .show()
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
            showFilterMenu()
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
        updateUnreadBadges()
    }

    private fun filterListByCurrentTab() {
        displayedChatsList.clear()
        when (currentTab) {
            TabType.CHATS -> {
                val list = when (currentFilterMode) {
                    FilterMode.ALL -> allChatsList
                    FilterMode.UNREAD_ONLY -> allChatsList.filter { it.unreadCount > 0 }
                    FilterMode.UNREAD_FIRST -> allChatsList.sortedByDescending { it.unreadCount }
                }
                displayedChatsList.addAll(list)
                if (::chatAdapter.isInitialized) {
                    chatAdapter.notifyDataSetChanged()
                }
            }
            TabType.GROUPS -> {
                if (allGroupsList.isNotEmpty()) {
                    val list = when (currentFilterMode) {
                        FilterMode.ALL -> allGroupsList
                        FilterMode.UNREAD_ONLY -> allGroupsList.filter { it.unreadCount > 0 }
                        FilterMode.UNREAD_FIRST -> allGroupsList.sortedByDescending { it.unreadCount }
                    }
                    displayedChatsList.addAll(list)
                    if (::chatAdapter.isInitialized) {
                        chatAdapter.notifyDataSetChanged()
                    }
                }
                lifecycleScope.launch {
                    val groupRes = messageRepository.fetchGroupConversations()
                    if (groupRes is NetworkResult.Success && currentTab == TabType.GROUPS) {
                        allGroupsList.clear()
                        allGroupsList.addAll(groupRes.data)
                        displayedChatsList.clear()
                        val list = when (currentFilterMode) {
                            FilterMode.ALL -> allGroupsList
                            FilterMode.UNREAD_ONLY -> allGroupsList.filter { it.unreadCount > 0 }
                            FilterMode.UNREAD_FIRST -> allGroupsList.sortedByDescending { it.unreadCount }
                        }
                        displayedChatsList.addAll(list)
                        if (::chatAdapter.isInitialized) {
                            chatAdapter.notifyDataSetChanged()
                        }
                        updateUnreadBadges()
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
        updateUnreadBadges()
    }

    private fun updateUnreadBadges() {
        val directUnread = allChatsList.sumOf { it.unreadCount }
        val groupsUnread = allGroupsList.sumOf { it.unreadCount }
        val totalUnread = directUnread + groupsUnread

        _binding?.let { b ->
            // 1. Header unread badge
            if (totalUnread > 0) {
                b.tvHeaderUnreadBadge.visibility = View.VISIBLE
                b.tvHeaderUnreadBadge.text = if (totalUnread > 99) "99+ new" else "$totalUnread new"
                b.tvHeaderSubtitle.text = "$totalUnread unread conversation${if (totalUnread > 1) "s" else ""} • Stay connected"
            } else {
                b.tvHeaderUnreadBadge.visibility = View.GONE
                b.tvHeaderSubtitle.text = "Stay connected with your community"
            }

            // 2. Tab Chats badge
            if (directUnread > 0) {
                b.tvBadgeTabChats.visibility = View.VISIBLE
                b.tvBadgeTabChats.text = if (directUnread > 99) "99+" else directUnread.toString()
            } else {
                b.tvBadgeTabChats.visibility = View.GONE
            }

            // 3. Tab Groups badge
            if (groupsUnread > 0) {
                b.tvBadgeTabGroups.visibility = View.VISIBLE
                b.tvBadgeTabGroups.text = if (groupsUnread > 99) "99+" else groupsUnread.toString()
            } else {
                b.tvBadgeTabGroups.visibility = View.GONE
            }

            // 4. Subheader filter chip
            val currentTabUnread = when (currentTab) {
                TabType.CHATS -> directUnread
                TabType.GROUPS -> groupsUnread
                TabType.ANNOUNCEMENTS -> 0
            }
            if (currentTabUnread > 0) {
                b.tvFilterUnreadCount.visibility = View.VISIBLE
                b.tvFilterUnreadCount.text = if (currentTabUnread > 99) "99+" else currentTabUnread.toString()
            } else {
                b.tvFilterUnreadCount.visibility = View.GONE
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
                val currentUserId = SupabaseClient.getCurrentUserId() ?: ""
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
                            val list = when (currentFilterMode) {
                                FilterMode.ALL -> allChatsList
                                FilterMode.UNREAD_ONLY -> allChatsList.filter { it.unreadCount > 0 }
                                FilterMode.UNREAD_FIRST -> allChatsList.sortedByDescending { it.unreadCount }
                            }
                            displayedChatsList.addAll(list)
                            if (::chatAdapter.isInitialized) {
                                chatAdapter.notifyDataSetChanged()
                            }
                        }
                        updateUnreadBadges()
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
                            updateUnreadBadges()
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
