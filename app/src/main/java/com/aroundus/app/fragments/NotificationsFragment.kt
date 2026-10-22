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
                    notificationRepository.markAllAsRead(requireContext(), allIds)
                    for (i in notificationList.indices) {
                        notificationList[i] = notificationList[i].copy(isUnread = false)
                    }
                    notificationAdapter.notifyDataSetChanged()
                    AppRefreshManager.notifyDataChanged(RefreshTarget.PROFILE)
                    Toast.makeText(requireContext(), "All marked as read", Toast.LENGTH_SHORT).show()
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
                if (notif.isUnread) {
                    notificationRepository.markAsRead(requireContext(), notif.id)
                    val idx = notificationList.indexOfFirst { it.id == notif.id }
                    if (idx != -1) {
                        notificationList[idx] = notif.copy(isUnread = false)
                        notificationAdapter.notifyItemChanged(idx)
                        AppRefreshManager.notifyDataChanged(RefreshTarget.PROFILE)
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
        if (showLoadingIndicator && notificationList.isEmpty()) {
            binding.swipeRefreshLayout.isRefreshing = true
        }

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                when (val result = notificationRepository.fetchNotifications(requireContext())) {
                    is NetworkResult.Success -> {
                        notificationList.clear()
                        notificationList.addAll(result.data)
                        notificationAdapter.notifyDataSetChanged()

                        val hasItems = notificationList.isNotEmpty()
                        binding.rvNotifications.visibility = if (hasItems) View.VISIBLE else View.GONE
                        binding.layoutEmptyNotifications.visibility = if (hasItems) View.GONE else View.VISIBLE

                        AppRefreshManager.markRefreshed(RefreshTarget.NOTIFICATIONS)
                    }
                    is NetworkResult.Error -> {
                        if (notificationList.isEmpty()) {
                            binding.layoutEmptyNotifications.visibility = View.VISIBLE
                        }
                    }
                }
            } finally {
                binding.swipeRefreshLayout.isRefreshing = false
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
