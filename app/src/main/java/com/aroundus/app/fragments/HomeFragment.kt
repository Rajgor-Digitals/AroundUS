package com.aroundus.app.fragments

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.aroundus.app.R
import com.aroundus.app.databinding.FragmentHomeBinding

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupClickListeners()
        setupFilterTabs()
    }

    private fun setupClickListeners() {
        binding.btnMenu.setOnClickListener {
            Toast.makeText(requireContext(), "Menu opened", Toast.LENGTH_SHORT).show()
        }

        binding.btnSearch.setOnClickListener {
            findNavController().navigate(R.id.searchFragment)
        }

        binding.btnNotifications.setOnClickListener {
            findNavController().navigate(R.id.notificationsFragment)
        }

        binding.btnLocationPicker.setOnClickListener {
            Toast.makeText(requireContext(), "Location: Bhujpur, Kutch, Gujarat", Toast.LENGTH_SHORT).show()
        }

        binding.btnExploreNow.setOnClickListener {
            findNavController().navigate(R.id.communityFragment)
        }

        binding.catAnnouncements.setOnClickListener {
            Toast.makeText(requireContext(), "Announcements", Toast.LENGTH_SHORT).show()
        }

        binding.catHelpSupport.setOnClickListener {
            findNavController().navigate(R.id.communityFragment)
        }

        binding.catBuySell.setOnClickListener {
            findNavController().navigate(R.id.marketplaceFragment)
        }

        binding.catEvents.setOnClickListener {
            findNavController().navigate(R.id.eventsFragment)
        }

        binding.catServices.setOnClickListener {
            findNavController().navigate(R.id.marketplaceFragment)
        }

        binding.catMore.setOnClickListener {
            findNavController().navigate(R.id.settingsFragment)
        }

        binding.btnViewAllAnnouncements.setOnClickListener {
            Toast.makeText(requireContext(), "View all announcements", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupFilterTabs() {
        val activeGreen = Color.parseColor("#0D8A3F")
        val inactiveGray = Color.parseColor("#64748B")

        binding.tabRecent.setOnClickListener {
            binding.tvTabRecent.setTextColor(activeGreen)
            binding.indicatorRecent.visibility = View.VISIBLE
            binding.tabPopular.setTextColor(inactiveGray)
            binding.tabNearby.setTextColor(inactiveGray)
        }

        binding.tabPopular.setOnClickListener {
            binding.tvTabRecent.setTextColor(inactiveGray)
            binding.indicatorRecent.visibility = View.INVISIBLE
            binding.tabPopular.setTextColor(activeGreen)
            binding.tabNearby.setTextColor(inactiveGray)
        }

        binding.tabNearby.setOnClickListener {
            binding.tvTabRecent.setTextColor(inactiveGray)
            binding.indicatorRecent.visibility = View.INVISIBLE
            binding.tabPopular.setTextColor(inactiveGray)
            binding.tabNearby.setTextColor(activeGreen)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
