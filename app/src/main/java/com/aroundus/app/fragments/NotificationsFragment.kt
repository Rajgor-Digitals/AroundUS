package com.aroundus.app.fragments

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.aroundus.app.R
import com.aroundus.app.activities.ConversationActivity
import com.aroundus.app.adapters.NotificationAdapter
import com.aroundus.app.adapters.NotificationListItem
import com.aroundus.app.databinding.FragmentNotificationsBinding
import com.aroundus.app.models.Notification
import com.aroundus.app.repository.NetworkResult
import com.aroundus.app.repository.NotificationRepository
import com.aroundus.app.utils.AppRefreshManager
import com.aroundus.app.utils.RefreshTarget
import com.aroundus.app.utils.applyAroundUsTheme
import kotlinx.coroutines.launch

class NotificationsFragment : Fragment() {

    private var _binding: FragmentNotificationsBinding? = null
    private val binding get() = _binding!!

    private val notificationRepository = NotificationRepository()
    private val masterNotificationList = mutableListOf<Notification>()
    private lateinit var notificationAdapter: NotificationAdapter

    private var selectedCategoryFilter: String = "All"

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentNotificationsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupHeader()
        setupCategoryFilters()
        setupRecyclerView()
        setupSwipeRefresh()
        observeRefreshEvents()

        loadLiveNotifications(showLoadingIndicator = true)
    }

    override fun onResume() {
        super.onResume()
        if (AppRefreshManager.isStale(RefreshTarget.NOTIFICATIONS)) {
            loadLiveNotifications(showLoadingIndicator = false)
        }
    }

    private fun setupHeader() {
        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.btnSettings.setOnClickListener {
            try {
                findNavController().navigate(R.id.settingsFragment)
            } catch (_: Exception) {
                Toast.makeText(requireContext(), "Notification Settings", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupCategoryFilters() {
        binding.chipFilterAll.setOnClickListener { selectFilter("All") }
        binding.chipFilterAnnouncements.setOnClickListener { selectFilter("Announcements") }
        binding.chipFilterMessages.setOnClickListener { selectFilter("Messages") }
        binding.chipFilterEvents.setOnClickListener { selectFilter("Events") }
        binding.chipFilterHelp.setOnClickListener { selectFilter("Help") }
    }

    private fun selectFilter(filter: String) {
        selectedCategoryFilter = filter
        updateFilterChipStyles()
        rebuildAndSubmitList()
    }

    private fun updateFilterChipStyles() {
        val selectedBg = R.drawable.bg_notif_filter_selected
        val unselectedBg = R.drawable.bg_notif_filter_unselected

        val activeTextColor = Color.WHITE
        val inactiveTextColor = Color.parseColor("#334155")

        // 1. All
        val isAll = selectedCategoryFilter == "All"
        binding.chipFilterAll.setBackgroundResource(if (isAll) selectedBg else unselectedBg)
        binding.tvFilterAll.setTextColor(if (isAll) activeTextColor else inactiveTextColor)

        // 2. Announcements
        val isAnnounce = selectedCategoryFilter == "Announcements"
        styleFilterChip(binding.chipFilterAnnouncements, binding.ivFilterAnnouncements, binding.tvFilterAnnouncements, isAnnounce)

        // 3. Messages
        val isMessages = selectedCategoryFilter == "Messages"
        styleFilterChip(binding.chipFilterMessages, binding.ivFilterMessages, binding.tvFilterMessages, isMessages)

        // 4. Events
        val isEvents = selectedCategoryFilter == "Events"
        styleFilterChip(binding.chipFilterEvents, binding.ivFilterEvents, binding.tvFilterEvents, isEvents)

        // 5. Help
        val isHelp = selectedCategoryFilter == "Help"
        styleFilterChip(binding.chipFilterHelp, binding.ivFilterHelp, binding.tvFilterHelp, isHelp)
    }

    private fun styleFilterChip(chip: LinearLayout, icon: ImageView, text: TextView, isSelected: Boolean) {
        val selectedBg = R.drawable.bg_notif_filter_selected
        val unselectedBg = R.drawable.bg_notif_filter_unselected
        val activeColor = Color.WHITE
        val inactiveColor = Color.parseColor("#334155")

        chip.setBackgroundResource(if (isSelected) selectedBg else unselectedBg)
        text.setTextColor(if (isSelected) activeColor else inactiveColor)
        icon.setColorFilter(if (isSelected) activeColor else inactiveColor)
    }

    private fun setupRecyclerView() {
        notificationAdapter = NotificationAdapter(
            onNotificationClick = { notif ->
                handleNotificationClick(notif)
            },
            onMarkAllAsReadClick = {
                handleMarkAllAsRead()
            },
            onToggleReadStatusClick = { notif ->
                handleToggleReadStatus(notif)
            },
            onDismissNotificationClick = { notif ->
                handleDismissNotification(notif)
            }
        )

        binding.rvNotifications.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = notificationAdapter
        }
    }

    private fun handleNotificationClick(notif: Notification) {
        val ctx = context ?: return

        if (notif.isUnread) {
            notificationRepository.markAsRead(ctx, notif.id)
            val idx = masterNotificationList.indexOfFirst { it.id == notif.id }
            if (idx != -1) {
                masterNotificationList[idx] = notif.copy(isUnread = false)
                rebuildAndSubmitList()
                AppRefreshManager.notifyDataChanged(RefreshTarget.PROFILE)
                AppRefreshManager.notifyDataChanged(RefreshTarget.NOTIFICATIONS)
            }
        }

        when (notif.type) {
            "message" -> {
                val partnerId = notif.targetId
                if (!partnerId.isNullOrBlank()) {
                    val displayName = notif.authorName?.takeIf { it.isNotBlank() } ?: "Neighbor"
                    val intent = Intent(ctx, ConversationActivity::class.java).apply {
                        putExtra("userId", partnerId)
                        putExtra("userName", displayName)
                        putExtra("avatarUrl", notif.avatarUrl)
                    }
                    startActivity(intent)
                }
            }
            "announcement", "help", "like", "comment" -> {
                val postId = notif.targetId
                if (!postId.isNullOrBlank()) {
                    val bundle = Bundle().apply { putString("postId", postId) }
                    try {
                        findNavController().navigate(R.id.postDetailFragment, bundle)
                    } catch (_: Exception) {}
                }
            }
            "event" -> {
                val eventId = notif.targetId
                if (!eventId.isNullOrBlank()) {
                    val bundle = Bundle().apply { putString("eventId", eventId) }
                    try {
                        findNavController().navigate(R.id.eventDetailFragment, bundle)
                    } catch (_: Exception) {}
                }
            }
        }
    }

    private fun handleMarkAllAsRead() {
        val ctx = context ?: return
        val allIds = masterNotificationList.map { it.id }
        notificationRepository.markAllAsRead(ctx, allIds)
        for (i in masterNotificationList.indices) {
            masterNotificationList[i] = masterNotificationList[i].copy(isUnread = false)
        }
        rebuildAndSubmitList()
        AppRefreshManager.notifyDataChanged(RefreshTarget.PROFILE)
        AppRefreshManager.notifyDataChanged(RefreshTarget.NOTIFICATIONS)
        Toast.makeText(ctx, "All marked as read", Toast.LENGTH_SHORT).show()
    }

    private fun handleToggleReadStatus(notif: Notification) {
        val ctx = context ?: return
        val newStatus = !notif.isUnread
        if (newStatus) {
            notificationRepository.markAsUnread(ctx, notif.id)
        } else {
            notificationRepository.markAsRead(ctx, notif.id)
        }
        val idx = masterNotificationList.indexOfFirst { it.id == notif.id }
        if (idx != -1) {
            masterNotificationList[idx] = notif.copy(isUnread = newStatus)
            rebuildAndSubmitList()
            AppRefreshManager.notifyDataChanged(RefreshTarget.PROFILE)
            AppRefreshManager.notifyDataChanged(RefreshTarget.NOTIFICATIONS)
        }
    }

    private fun handleDismissNotification(notif: Notification) {
        val ctx = context ?: return
        notificationRepository.dismissNotification(ctx, notif.id)
        masterNotificationList.removeAll { it.id == notif.id }
        rebuildAndSubmitList()
        AppRefreshManager.notifyDataChanged(RefreshTarget.PROFILE)
        AppRefreshManager.notifyDataChanged(RefreshTarget.NOTIFICATIONS)
        Toast.makeText(ctx, "Notification removed", Toast.LENGTH_SHORT).show()
    }

    private fun rebuildAndSubmitList() {
        // 1. Filter by category
        val filtered = masterNotificationList.filter { notif ->
            when (selectedCategoryFilter) {
                "All" -> true
                "Announcements" -> notif.type.equals("announcement", ignoreCase = true)
                "Messages" -> notif.type.equals("message", ignoreCase = true)
                "Events" -> notif.type.equals("event", ignoreCase = true)
                "Help" -> notif.type.equals("help", ignoreCase = true)
                else -> true
            }
        }

        val hasItems = filtered.isNotEmpty()
        binding.rvNotifications.visibility = if (hasItems) View.VISIBLE else View.GONE
        binding.layoutEmptyNotifications.visibility = if (hasItems) View.GONE else View.VISIBLE

        if (!hasItems) {
            notificationAdapter.submitItems(emptyList())
            return
        }

        // 2. Partition into "Today" (within 24 hours) and "Earlier"
        val now = System.currentTimeMillis()
        val oneDayMillis = 24 * 3600_000L

        val todayList = filtered.filter { (now - it.timestampMs) < oneDayMillis }
        val earlierList = filtered.filter { (now - it.timestampMs) >= oneDayMillis }

        val listItems = mutableListOf<NotificationListItem>()

        if (todayList.isNotEmpty()) {
            val showMarkRead = todayList.any { it.isUnread }
            listItems.add(NotificationListItem.Header("Today", showMarkAllRead = showMarkRead))
            listItems.addAll(todayList.map { NotificationListItem.Item(it) })
        }

        if (earlierList.isNotEmpty()) {
            listItems.add(NotificationListItem.Header("Earlier", showMarkAllRead = false))
            listItems.addAll(earlierList.map { NotificationListItem.Item(it) })
        }

        notificationAdapter.submitItems(listItems)
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefreshLayout.applyAroundUsTheme()
        binding.swipeRefreshLayout.setOnRefreshListener {
            loadLiveNotifications(showLoadingIndicator = false)
        }
    }

    private fun observeRefreshEvents() {
        viewLifecycleOwner.lifecycleScope.launch {
            AppRefreshManager.refreshEvents.collect { target ->
                if (target == RefreshTarget.NOTIFICATIONS || target == RefreshTarget.ALL) {
                    loadLiveNotifications(showLoadingIndicator = false)
                }
            }
        }
    }

    private fun loadLiveNotifications(showLoadingIndicator: Boolean) {
        val b = _binding ?: return
        if (showLoadingIndicator && masterNotificationList.isEmpty()) {
            b.swipeRefreshLayout.isRefreshing = true
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val ctx = context ?: return@launch
            try {
                when (val result = notificationRepository.fetchNotifications(ctx)) {
                    is NetworkResult.Success -> {
                        masterNotificationList.clear()
                        masterNotificationList.addAll(result.data)
                        rebuildAndSubmitList()
                        AppRefreshManager.markRefreshed(RefreshTarget.NOTIFICATIONS)
                    }
                    is NetworkResult.Error -> {
                        if (masterNotificationList.isEmpty()) {
                            binding.layoutEmptyNotifications.visibility = View.VISIBLE
                        }
                    }
                }
            } finally {
                _binding?.swipeRefreshLayout?.isRefreshing = false
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
