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
import com.aroundus.app.adapters.EventAdapter
import com.aroundus.app.databinding.FragmentEventsBinding
import com.aroundus.app.models.Event
import com.aroundus.app.models.toUIModel
import com.aroundus.app.network.SupabaseClient
import com.aroundus.app.repository.EventRepository
import com.aroundus.app.repository.NetworkResult
import com.aroundus.app.utils.AppRefreshManager
import com.aroundus.app.utils.RefreshTarget
import com.aroundus.app.utils.applyAroundUsTheme
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.launch

class EventsFragment : Fragment() {

    private var _binding: FragmentEventsBinding? = null
    private val binding get() = _binding!!

    private val eventRepository = EventRepository()
    private val allEvents = mutableListOf<Event>()
    private val displayedEvents = mutableListOf<Event>()
    private lateinit var eventAdapter: EventAdapter

    private var currentTabPosition = 0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentEventsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        eventAdapter = EventAdapter(
            events = displayedEvents,
            onEventClick = { event ->
                val bundle = Bundle().apply {
                    putString("eventId", event.id)
                    putString("eventTitle", event.title)
                    putString("eventDate", event.time)
                    putString("eventLocation", event.location)
                    putString("eventAttendees", "${event.attendeesCount} Attending")
                }
                findNavController().navigate(R.id.action_events_to_eventDetail, bundle)
            },
            onAttendClick = { event ->
                lifecycleScope.launch {
                    when (eventRepository.toggleAttendance(event.id, event.attendeesCount)) {
                        is NetworkResult.Success -> {
                            Toast.makeText(requireContext(), "🎉 You're attending ${event.title}!", Toast.LENGTH_SHORT).show()
                            AppRefreshManager.notifyDataChanged(RefreshTarget.EVENTS)
                        }
                        is NetworkResult.Error -> {}
                    }
                }
            }
        )

        binding.rvEvents.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = eventAdapter
        }

        setupTabs()
        setupSwipeRefresh()
        observeRefreshEvents()

        val showCreateDialog = {
            val sheet = CreateEventBottomSheetFragment.newInstance()
            sheet.onEventCreated = {
                loadSupabaseEvents()
            }
            sheet.show(childFragmentManager, CreateEventBottomSheetFragment.TAG)
        }

        binding.fabCreateEvent.setOnClickListener { showCreateDialog() }
        binding.btnEmptyCreateEvent.setOnClickListener { showCreateDialog() }

        loadSupabaseEvents()
    }

    override fun onResume() {
        super.onResume()
        if (AppRefreshManager.isStale(RefreshTarget.EVENTS)) {
            loadSupabaseEvents()
        }
    }

    private fun setupTabs() {
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                currentTabPosition = tab?.position ?: 0
                filterEventsForCurrentTab()
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun filterEventsForCurrentTab() {
        val currentUserId = SupabaseClient.getCurrentUserId() ?: ""
        displayedEvents.clear()

        when (currentTabPosition) {
            0 -> {
                // Upcoming: all events
                displayedEvents.addAll(allEvents)
            }
            1 -> {
                // This Week
                val weekKeywords = listOf("TODAY", "TOMORROW", "MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")
                val thisWeekList = allEvents.filter { event ->
                    weekKeywords.any { kw -> event.time.uppercase().contains(kw) } || event.isAttending
                }
                if (thisWeekList.isNotEmpty()) {
                    displayedEvents.addAll(thisWeekList)
                } else {
                    displayedEvents.addAll(allEvents.take(5))
                }
            }
            2 -> {
                // My Events
                val myEvents = allEvents.filter { event ->
                    (event.creatorId != null && event.creatorId == currentUserId) || event.isAttending
                }
                displayedEvents.addAll(myEvents)
            }
        }

        eventAdapter.notifyDataSetChanged()
        updateEmptyState()
    }

    private fun updateEmptyState() {
        if (displayedEvents.isEmpty()) {
            binding.layoutEmptyEvents.visibility = View.VISIBLE
            when (currentTabPosition) {
                1 -> {
                    binding.tvEmptyTitle.text = "No Events This Week"
                    binding.tvEmptySubtitle.text = "Check back soon or create an event for your neighbors this week!"
                }
                2 -> {
                    binding.tvEmptyTitle.text = "No Saved or Hosted Events"
                    binding.tvEmptySubtitle.text = "You haven't created or RSVP'd to any events yet."
                }
                else -> {
                    binding.tvEmptyTitle.text = "No Community Events Yet"
                    binding.tvEmptySubtitle.text = "Be the first to create a community event for your neighborhood!"
                }
            }
        } else {
            binding.layoutEmptyEvents.visibility = View.GONE
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefreshLayout.applyAroundUsTheme()
        binding.swipeRefreshLayout.setOnRefreshListener {
            loadSupabaseEvents()
        }
    }

    private fun observeRefreshEvents() {
        viewLifecycleOwner.lifecycleScope.launch {
            AppRefreshManager.refreshEvents.collect { target ->
                if (target == RefreshTarget.EVENTS || target == RefreshTarget.ALL) {
                    loadSupabaseEvents()
                }
            }
        }
    }

    private fun loadSupabaseEvents() {
        lifecycleScope.launch {
            try {
                when (val result = eventRepository.fetchEvents()) {
                    is NetworkResult.Success -> {
                        val remoteEvents = result.data.map { it.toUIModel() }
                        allEvents.clear()
                        allEvents.addAll(remoteEvents)
                        filterEventsForCurrentTab()
                        AppRefreshManager.markRefreshed(RefreshTarget.EVENTS)
                    }
                    is NetworkResult.Error -> {
                        updateEmptyState()
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
