package com.aroundus.app.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.aroundus.app.activities.LoginActivity
import com.aroundus.app.databinding.FragmentSettingsBinding

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }

        binding.switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
            AppCompatDelegate.setDefaultNightMode(
                if (isChecked) AppCompatDelegate.MODE_NIGHT_YES
                else AppCompatDelegate.MODE_NIGHT_NO
            )
        }

        binding.itemLogout.setOnClickListener {
            startActivity(Intent(requireContext(), LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
        }

        binding.itemEditProfile.setOnClickListener { }
        binding.itemPrivacy.setOnClickListener { }
        binding.itemNotifSettings.setOnClickListener { }
        binding.itemHelp.setOnClickListener { }
        binding.itemFeedback.setOnClickListener { }
        binding.itemAbout.setOnClickListener { }
        binding.itemTerms.setOnClickListener {
            val intent = Intent(requireContext(), com.aroundus.app.activities.TermsAndConditionsActivity::class.java)
            startActivity(intent)
            activity?.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
