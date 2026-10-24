package com.aroundus.app.fragments

import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
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
import kotlinx.coroutines.launch

class EventsFragment : Fragment() {

    private var _binding: FragmentEventsBinding? = null
    private val binding get() = _binding!!

    private val eventRepository = EventRepository()
    private val allEvents = mutableListOf<Event>()
    private val displayedEvents = mutableListOf<Event>()
    private lateinit var eventAdapter: EventAdapter

    private var currentTabPosition = 0
    private var searchQuery = ""
    private var selectedCategoryFilter = "All"

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentEventsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnBack.setOnClickListener {
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
                    putString("eventCategory", event.category)
                    putString("eventDescription", event.description)
                }
                findNavController().navigate(R.id.action_events_to_eventDetail, bundle)
            },
            onAttendClick = { event ->
                val willAttend = !event.isAttending
                val newCount = (event.attendeesCount + if (willAttend) 1 else -1).coerceAtLeast(1)

                SupabaseClient.setEventAttended(event.id, willAttend)

                // Update in memory list
                val idx = allEvents.indexOfFirst { it.id == event.id }
                if (idx != -1) {
                    allEvents[idx] = allEvents[idx].copy(isAttending = willAttend, attendeesCount = newCount)
                }

                filterEventsForCurrentTab()

                val msg = if (willAttend) "🎉 You're attending ${event.title}!" else "RSVP removed"
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()

                lifecycleScope.launch {
                    eventRepository.toggleAttendance(event.id, newCount)
                    AppRefreshManager.notifyDataChanged(RefreshTarget.EVENTS)
                }
            }
        )

        binding.rvEvents.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = eventAdapter
        }

        setupTabs()
        setupSearch()
        setupCategoryFilters()
        setupSwipeRefresh()
        observeRefreshEvents()

        val showCreateDialog = {
            val sheet = CreateEventBottomSheetFragment.newInstance()
            sheet.onEventCreated = {
                loadSupabaseEvents()
            }
            sheet.show(childFragmentManager, CreateEventBottomSheetFragment.TAG)
        }

        binding.btnHeaderCreateEvent.setOnClickListener { showCreateDialog() }
        binding.btnEmptyCreateEvent.setOnClickListener { showCreateDialog() }
        binding.btnBrowseNearbyEvents.setOnClickListener {
            selectCategoryFilter("All")
            selectTab(0)
            if (allEvents.isEmpty()) {
                showCreateDialog()
            }
        }

        binding.btnFilterEvents.setOnClickListener {
            val isVisible = binding.scrollCategoryFilters.visibility == View.VISIBLE
            binding.scrollCategoryFilters.visibility = if (isVisible) View.GONE else View.VISIBLE
            Toast.makeText(
                requireContext(),
                if (isVisible) "Filters hidden" else "Filter by category below",
                Toast.LENGTH_SHORT
            ).show()
        }

        loadSupabaseEvents()
    }

    override fun onResume() {
        super.onResume()
        if (AppRefreshManager.isStale(RefreshTarget.EVENTS)) {
            loadSupabaseEvents()
        } else {
            // Re-sync local attendance states
            allEvents.forEachIndexed { i, event ->
                val attended = SupabaseClient.isEventAttended(event.id)
                if (event.isAttending != attended) {
                    allEvents[i] = event.copy(isAttending = attended)
                }
            }
            filterEventsForCurrentTab()
        }
    }

    private fun setupTabs() {
        binding.tabUpcoming.setOnClickListener { selectTab(0) }
        binding.tabThisWeek.setOnClickListener { selectTab(1) }
        binding.tabMyEvents.setOnClickListener { selectTab(2) }
        updateTabUI(0)
    }

    private fun selectTab(position: Int) {
        currentTabPosition = position
        updateTabUI(position)
        filterEventsForCurrentTab()
    }

    private fun updateTabUI(selectedPos: Int) {
        val selectedBg = R.drawable.bg_tab_pill_selected
        val unselectedBg = R.drawable.bg_tab_pill_unselected
        val greenColor = Color.parseColor("#0D8A3F")
        val grayColor = Color.parseColor("#64748B")

        // Tab 0: Upcoming
        binding.tabUpcoming.setBackgroundResource(if (selectedPos == 0) selectedBg else unselectedBg)
        binding.ivTabUpcoming.setColorFilter(if (selectedPos == 0) greenColor else grayColor)
        binding.tvTabUpcoming.setTextColor(if (selectedPos == 0) greenColor else grayColor)

        // Tab 1: This Week
        binding.tabThisWeek.setBackgroundResource(if (selectedPos == 1) selectedBg else unselectedBg)
        binding.ivTabThisWeek.setColorFilter(if (selectedPos == 1) greenColor else grayColor)
        binding.tvTabThisWeek.setTextColor(if (selectedPos == 1) greenColor else grayColor)

        // Tab 2: My Events
        binding.tabMyEvents.setBackgroundResource(if (selectedPos == 2) selectedBg else unselectedBg)
        binding.ivTabMyEvents.setColorFilter(if (selectedPos == 2) greenColor else grayColor)
        binding.tvTabMyEvents.setTextColor(if (selectedPos == 2) greenColor else grayColor)
    }

    private fun setupSearch() {
        binding.etSearchEvents.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s?.toString()?.trim() ?: ""
                filterEventsForCurrentTab()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun setupCategoryFilters() {
        val chips = listOf(
            binding.filterChipAll to "All",
            binding.filterChipCommunity to "Community",
            binding.filterChipMusic to "Music",
            binding.filterChipSports to "Sports",
            binding.filterChipWorkshop to "Workshop",
            binding.filterChipFood to "Food",
            binding.filterChipVolunteering to "Volunteering",
            binding.filterChipCultural to "Cultural"
        )

        for ((chipView, catName) in chips) {
            chipView.setOnClickListener {
                selectCategoryFilter(catName)
            }
        }
    }

    private fun selectCategoryFilter(category: String) {
        selectedCategoryFilter = category
        val chips = listOf(
            binding.filterChipAll to "All",
            binding.filterChipCommunity to "Community",
            binding.filterChipMusic to "Music",
            binding.filterChipSports to "Sports",
            binding.filterChipWorkshop to "Workshop",
            binding.filterChipFood to "Food",
            binding.filterChipVolunteering to "Volunteering",
            binding.filterChipCultural to "Cultural"
        )

        val selectedBg = R.drawable.bg_chip_category_selected
        val unselectedBg = R.drawable.bg_chip_category_unselected
        val greenColor = Color.parseColor("#0D8A3F")
        val grayColor = Color.parseColor("#475569")

        for ((chipView, catName) in chips) {
            val isSelected = catName.equals(category, ignoreCase = true)
            chipView.setBackgroundResource(if (isSelected) selectedBg else unselectedBg)
            chipView.setTextColor(if (isSelected) greenColor else grayColor)
        }

        filterEventsForCurrentTab()
    }

    private fun filterEventsForCurrentTab() {
        val currentUserId = SupabaseClient.getCurrentUserId() ?: ""
        displayedEvents.clear()

        // 1. Tab filter
        val tabFiltered = when (currentTabPosition) {
            0 -> allEvents
            1 -> {
                val weekKeywords = listOf("TODAY", "TOMORROW", "MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")
                val filtered = allEvents.filter { event ->
                    weekKeywords.any { kw -> event.time.uppercase().contains(kw) } || event.isAttending
                }
                if (filtered.isNotEmpty()) filtered else allEvents.take(5)
            }
            2 -> {
                allEvents.filter { event ->
                    (event.creatorId != null && event.creatorId == currentUserId) ||
                    event.isAttending ||
                    SupabaseClient.isEventAttended(event.id)
                }
            }
            else -> allEvents
        }

        // 2. Category filter
        val catFiltered = if (selectedCategoryFilter.equals("All", ignoreCase = true)) {
            tabFiltered
        } else {
            tabFiltered.filter { it.category.equals(selectedCategoryFilter, ignoreCase = true) }
        }

        // 3. Search query filter
        val searchFiltered = if (searchQuery.isNotBlank()) {
            catFiltered.filter { event ->
                event.title.contains(searchQuery, ignoreCase = true) ||
                event.location.contains(searchQuery, ignoreCase = true) ||
                event.time.contains(searchQuery, ignoreCase = true) ||
                event.category.contains(searchQuery, ignoreCase = true)
            }
        } else {
            catFiltered
        }

        displayedEvents.addAll(searchFiltered)
        eventAdapter.notifyDataSetChanged()
        updateEmptyState()
    }

    private fun updateEmptyState() {
        if (displayedEvents.isEmpty()) {
            binding.layoutEmptyEvents.visibility = View.VISIBLE
            binding.rvEvents.visibility = View.GONE
            when {
                searchQuery.isNotBlank() -> {
                    binding.tvEmptyTitle.text = "No Matching Events"
                    binding.tvEmptySubtitle.text = "No events matched \"$searchQuery\". Try a different search."
                }
                !selectedCategoryFilter.equals("All", ignoreCase = true) -> {
                    binding.tvEmptyTitle.text = "No $selectedCategoryFilter Events"
                    binding.tvEmptySubtitle.text = "There are no $selectedCategoryFilter events scheduled right now."
                }
                currentTabPosition == 1 -> {
                    binding.tvEmptyTitle.text = "No Events This Week"
                    binding.tvEmptySubtitle.text = "Check back soon or create an event for your neighbors this week!"
                }
                currentTabPosition == 2 -> {
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
            binding.rvEvents.visibility = View.VISIBLE
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
