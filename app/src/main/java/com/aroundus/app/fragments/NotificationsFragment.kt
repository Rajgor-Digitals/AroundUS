package com.aroundus.app.fragments

import android.content.Intent
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
import com.aroundus.app.activities.ConversationActivity
import com.aroundus.app.adapters.NotificationAdapter
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
    private val notificationList = mutableListOf<Notification>()
    private lateinit var notificationAdapter: NotificationAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentNotificationsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
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

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }

        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_mark_all_read -> {
                    val allIds = notificationList.map { it.id }
                    val ctx = context ?: return@setOnMenuItemClickListener true
                    notificationRepository.markAllAsRead(ctx, allIds)
                    for (i in notificationList.indices) {
                        notificationList[i] = notificationList[i].copy(isUnread = false)
                    }
                    notificationAdapter.notifyDataSetChanged()
                    AppRefreshManager.notifyDataChanged(RefreshTarget.PROFILE)
                    AppRefreshManager.notifyDataChanged(RefreshTarget.NOTIFICATIONS)
                    Toast.makeText(ctx, "All marked as read", Toast.LENGTH_SHORT).show()
                    true
                }
                else -> false
            }
        }
    }

    private fun setupRecyclerView() {
        notificationAdapter = NotificationAdapter(
            notifications = notificationList,
            onNotificationClick = { notif ->
                val ctx = context ?: return@NotificationAdapter
                if (notif.isUnread) {
                    notificationRepository.markAsRead(ctx, notif.id)
                    val idx = notificationList.indexOfFirst { it.id == notif.id }
                    if (idx != -1) {
                        notificationList[idx] = notif.copy(isUnread = false)
                        notificationAdapter.notifyItemChanged(idx)
                        AppRefreshManager.notifyDataChanged(RefreshTarget.PROFILE)
                        AppRefreshManager.notifyDataChanged(RefreshTarget.NOTIFICATIONS)
                    }
                }

                // Route according to notification type
                when (notif.type) {
                    "message" -> {
                        val partnerId = notif.targetId
                        if (!partnerId.isNullOrBlank()) {
                            val displayName = when {
                                notif.text.startsWith("You sent a message to ", ignoreCase = true) ->
                                    notif.text.removePrefix("You sent a message to ").substringBefore(":").trim()
                                notif.text.contains(" sent you a message:") ->
                                    notif.text.substringBefore(" sent you a message:").trim()
                                else -> "Neighbor"
                            }
                            val intent = Intent(ctx, ConversationActivity::class.java).apply {
                                putExtra("userId", partnerId)
                                putExtra("userName", displayName)
                                putExtra("avatarUrl", notif.avatarUrl)
                            }
                            startActivity(intent)
                        }
                    }
                    "announcement", "event" -> {
                        val postId = notif.targetId
                        if (!postId.isNullOrBlank()) {
                            val bundle = Bundle().apply { putString("postId", postId) }
                            findNavController().navigate(R.id.postDetailFragment, bundle)
                        }
                    }
                }
            }
        )

        binding.rvNotifications.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = notificationAdapter
        }
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
        if (showLoadingIndicator && notificationList.isEmpty()) {
            b.swipeRefreshLayout.isRefreshing = true
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val ctx = context ?: return@launch
            try {
                when (val result = notificationRepository.fetchNotifications(ctx)) {
                    is NetworkResult.Success -> {
                        notificationList.clear()
                        notificationList.addAll(result.data)
                        notificationAdapter.notifyDataSetChanged()

                        val hasItems = notificationList.isNotEmpty()
                        _binding?.rvNotifications?.visibility = if (hasItems) View.VISIBLE else View.GONE
                        _binding?.layoutEmptyNotifications?.visibility = if (hasItems) View.GONE else View.VISIBLE

                        AppRefreshManager.markRefreshed(RefreshTarget.NOTIFICATIONS)
                    }
                    is NetworkResult.Error -> {
                        if (notificationList.isEmpty()) {
                            _binding?.layoutEmptyNotifications?.visibility = View.VISIBLE
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
