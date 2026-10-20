package com.aroundus.app.fragments

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
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
import com.aroundus.app.network.SupabaseClient
import com.aroundus.app.repository.MessageRepository
import com.aroundus.app.repository.NetworkResult
import com.aroundus.app.utils.CryptoManager
import kotlinx.coroutines.launch

class ChatListFragment : Fragment() {

    private var _binding: FragmentChatListBinding? = null
    private val binding get() = _binding!!
    private val messageRepository = MessageRepository()
    private val allChatsList = mutableListOf<ChatItem>()
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
        loadSupabaseConversations()
    }

    private fun initChatList() {
        allChatsList.clear()
        allChatsList.addAll(
            listOf(
                ChatItem(
                    id = "u_priya",
                    userName = "Priya Sharma",
                    avatarRes = R.drawable.img_avatar_priya_sharma,
                    lastMessage = "Hey Gaurav, thanks for...",
                    time = "9:40 AM",
                    unreadCount = 2,
                    isOnline = true
                ),
                ChatItem(
                    id = "u_ramesh",
                    userName = "Ramesh Patel",
                    avatarRes = 0,
                    lastMessage = "I found the electrician you...",
                    time = "9:15 AM",
                    unreadCount = 1,
                    isOnline = true
                ),
                ChatItem(
                    id = "u_amit",
                    userName = "Amit Joshi",
                    avatarRes = R.drawable.img_avatar_amit_joshi,
                    lastMessage = "Let's meet tomorrow at the...",
                    time = "Yesterday",
                    unreadCount = 0,
                    isOnline = false
                ),
                ChatItem(
                    id = "g_bhujpur_residents",
                    userName = "Bhujpur Residents",
                    avatarRes = R.drawable.img_group_bhujpur_residents,
                    lastMessage = "Meena: Garbage collection...",
                    time = "Yesterday",
                    unreadCount = 5,
                    isOnline = false
                ),
                ChatItem(
                    id = "u_neha",
                    userName = "Neha Verma",
                    avatarRes = R.drawable.img_avatar_priya_sharma,
                    lastMessage = "Hey, is the yoga session still...",
                    time = "Mon",
                    unreadCount = 0,
                    isOnline = false
                ),
                ChatItem(
                    id = "u_sunil",
                    userName = "Sunil Prajapati",
                    avatarRes = 0,
                    lastMessage = "Thanks for the update!",
                    time = "Mon",
                    unreadCount = 0,
                    isOnline = false
                ),
                ChatItem(
                    id = "g_milk_supply",
                    userName = "Milk Supply Group",
                    avatarRes = 0,
                    lastMessage = "Mahesh: Tomorrow timings...",
                    time = "Sun",
                    unreadCount = 3,
                    isOnline = false
                ),
                ChatItem(
                    id = "g_help_each_other",
                    userName = "Help Each Other",
                    avatarRes = R.drawable.img_group_help_each_other,
                    lastMessage = "Ravi: Need a ladder for home...",
                    time = "Sun",
                    unreadCount = 0,
                    isOnline = false
                ),
                ChatItem(
                    id = "u_pooja",
                    userName = "Pooja Shah",
                    avatarRes = 0,
                    lastMessage = "Okay, will let you know.",
                    time = "Sat",
                    unreadCount = 0,
                    isOnline = false
                ),
                ChatItem(
                    id = "g_buy_sell",
                    userName = "Buy & Sell Bhujpur",
                    avatarRes = R.drawable.img_group_bhujpur_buysell,
                    lastMessage = "Is this cycle still available?",
                    time = "Sat",
                    unreadCount = 0,
                    isOnline = false
                )
            )
        )
        filterListByCurrentTab()
    }

    private fun setupRecyclerView() {
        chatAdapter = ChatAdapter(displayedChatsList) { chat ->
            val intent = Intent(requireContext(), ConversationActivity::class.java).apply {
                putExtra("userId", chat.id)
                putExtra("userName", chat.userName)
                putExtra("avatarRes", chat.avatarRes)
                putExtra("isOnline", chat.isOnline)
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
            Toast.makeText(requireContext(), "Start a new conversation", Toast.LENGTH_SHORT).show()
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
            }
            TabType.GROUPS -> {
                displayedChatsList.addAll(allChatsList.filter { it.id.startsWith("g_") })
            }
            TabType.ANNOUNCEMENTS -> {
                displayedChatsList.addAll(
                    listOf(
                        ChatItem(
                            id = "a_panchayat",
                            userName = "Bhujpur Gram Panchayat Notice",
                            avatarRes = R.drawable.img_group_bhujpur_residents,
                            lastMessage = "Water pipeline maintenance tomorrow 9 AM to 1 PM.",
                            time = "Today",
                            unreadCount = 1,
                            isOnline = false
                        ),
                        ChatItem(
                            id = "a_health",
                            userName = "Community Health Camp",
                            avatarRes = R.drawable.img_group_clean_green,
                            lastMessage = "Free eye & dental checkup this Sunday at Community Hall.",
                            time = "Yesterday",
                            unreadCount = 0,
                            isOnline = false
                        )
                    )
                )
            }
        }
        if (::chatAdapter.isInitialized) {
            chatAdapter.notifyDataSetChanged()
        }
    }

    private fun filterSearch(query: String) {
        if (query.isBlank()) {
            filterListByCurrentTab()
        } else {
            val filtered = allChatsList.filter {
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
        val currentUserId = SupabaseClient.getCurrentUserId() ?: return
        lifecycleScope.launch {
            when (val result = messageRepository.fetchConversationMessages("u_priya")) {
                is NetworkResult.Success -> {
                    if (result.data.isNotEmpty()) {
                        val lastEntity = result.data.last()
                        val secretKey = CryptoManager.deriveKey(CryptoManager.generateSharedSecret(currentUserId, "u_priya"))
                        val decryptedText = CryptoManager.decrypt(lastEntity.text, secretKey)

                        val priyaIndex = allChatsList.indexOfFirst { it.id == "u_priya" }
                        if (priyaIndex != -1) {
                            val updatedItem = allChatsList[priyaIndex].copy(
                                lastMessage = decryptedText,
                                time = "Now"
                            )
                            allChatsList[priyaIndex] = updatedItem
                            if (currentTab == TabType.CHATS) {
                                val dispIndex = displayedChatsList.indexOfFirst { it.id == "u_priya" }
                                if (dispIndex != -1) {
                                    displayedChatsList[dispIndex] = updatedItem
                                    chatAdapter.notifyItemChanged(dispIndex)
                                }
                            }
                        }
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
