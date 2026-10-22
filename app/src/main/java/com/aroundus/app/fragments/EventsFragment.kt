package com.aroundus.app.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.aroundus.app.R
import com.aroundus.app.adapters.EventAdapter
import com.aroundus.app.databinding.FragmentEventsBinding
import com.aroundus.app.models.Event
import com.aroundus.app.models.toUIModel
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
    private val eventList = mutableListOf<Event>()
    private lateinit var eventAdapter: EventAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentEventsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        eventAdapter = EventAdapter(eventList) { event ->
            findNavController().navigate(R.id.action_events_to_eventDetail)
        }
        binding.rvEvents.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = eventAdapter
        }

        setupSwipeRefresh()
        observeRefreshEvents()

        loadSupabaseEvents()

        binding.fabCreateEvent.setOnClickListener {
            // Navigate to create event screen
        }
    }

    override fun onResume() {
        super.onResume()
        if (AppRefreshManager.isStale(RefreshTarget.EVENTS)) {
            loadSupabaseEvents()
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
                        eventList.clear()
                        eventList.addAll(remoteEvents)
                        eventAdapter.notifyDataSetChanged()
                        AppRefreshManager.markRefreshed(RefreshTarget.EVENTS)
                    }
                    is NetworkResult.Error -> {}
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
