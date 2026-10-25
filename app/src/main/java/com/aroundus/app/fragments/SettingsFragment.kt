package com.aroundus.app.fragments

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.aroundus.app.R
import com.aroundus.app.activities.LoginActivity
import com.aroundus.app.activities.ProfileSetupActivity
import com.aroundus.app.activities.TermsAndConditionsActivity
import com.aroundus.app.databinding.FragmentSettingsBinding
import com.aroundus.app.network.SupabaseClient
import com.aroundus.app.utils.UserPresenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var prefs: SharedPreferences

    companion object {
        private const val PREFS_SETTINGS = "aroundus_settings_prefs"
        private const val KEY_DARK_MODE = "dark_mode"
        private const val KEY_LANGUAGE = "selected_language"
        private const val KEY_PRIVACY_ONLINE = "privacy_online_status"
        private const val KEY_PRIVACY_MESSAGES = "privacy_allow_messages"
        private const val KEY_PRIVACY_PUBLIC = "privacy_public_profile"
        private const val KEY_NOTIF_ANNOUNCEMENTS = "notif_announcements"
        private const val KEY_NOTIF_CHATS = "notif_chats"
        private const val KEY_NOTIF_EVENTS = "notif_events"
        private const val KEY_NOTIF_MARKET = "notif_marketplace"
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        prefs = requireContext().getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE)

        setupToolbar()
        setupDarkMode()
        setupLanguage()
        setupEditProfile()
        setupPrivacy()
        setupEncryptionInfo()
        setupNotificationSettings()
        setupHelpCenter()
        setupFeedback()
        setupAbout()
        setupTerms()
        setupLogout()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }
    }

    private fun setupDarkMode() {
        val isDark = prefs.getBoolean(KEY_DARK_MODE, false)
        binding.switchDarkMode.isChecked = isDark

        binding.switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_DARK_MODE, isChecked).apply()
            AppCompatDelegate.setDefaultNightMode(
                if (isChecked) AppCompatDelegate.MODE_NIGHT_YES
                else AppCompatDelegate.MODE_NIGHT_NO
            )
        }
    }

    private fun setupLanguage() {
        val currentLang = prefs.getString(KEY_LANGUAGE, "English") ?: "English"
        binding.tvSelectedLanguage.text = currentLang

        binding.itemLanguage.setOnClickListener {
            val languages = arrayOf("English", "हिन्दी (Hindi)", "ગુજરાતી (Gujarati)")
            val checkedIndex = languages.indexOfFirst { it.startsWith(currentLang) }.coerceAtLeast(0)

            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Select App Language")
                .setSingleChoiceItems(languages, checkedIndex) { dialog, which ->
                    val chosen = languages[which].split(" ").first()
                    prefs.edit().putString(KEY_LANGUAGE, chosen).apply()
                    binding.tvSelectedLanguage.text = chosen
                    Toast.makeText(requireContext(), "Language set to ${languages[which]}", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun setupEditProfile() {
        binding.itemEditProfile.setOnClickListener {
            val intent = Intent(requireContext(), ProfileSetupActivity::class.java).apply {
                putExtra("email", SupabaseClient.getCurrentUserEmail())
                putExtra("name", SupabaseClient.getCurrentUserName())
                putExtra("isFromSettings", true)
            }
            startActivity(intent)
        }
    }

    private fun setupPrivacy() {
        binding.itemPrivacy.setOnClickListener {
            val online = prefs.getBoolean(KEY_PRIVACY_ONLINE, true)
            val dms = prefs.getBoolean(KEY_PRIVACY_MESSAGES, true)
            val isPublic = prefs.getBoolean(KEY_PRIVACY_PUBLIC, true)

            val options = arrayOf(
                "Show real-time active / online status",
                "Allow direct messages from neighbors",
                "Public neighborhood profile"
            )
            val checkedItems = booleanArrayOf(online, dms, isPublic)

            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Privacy & Neighborhood Visibility")
                .setMultiChoiceItems(options, checkedItems) { _, which, isChecked ->
                    checkedItems[which] = isChecked
                }
                .setPositiveButton("Save") { _, _ ->
                    prefs.edit()
                        .putBoolean(KEY_PRIVACY_ONLINE, checkedItems[0])
                        .putBoolean(KEY_PRIVACY_MESSAGES, checkedItems[1])
                        .putBoolean(KEY_PRIVACY_PUBLIC, checkedItems[2])
                        .apply()

                    val currentUid = SupabaseClient.getCurrentUserId() ?: ""
                    if (currentUid.isNotBlank()) {
                        UserPresenceManager.setUserOnline(currentUid, checkedItems[0])
                    }

                    Toast.makeText(requireContext(), "Privacy preferences updated", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun setupEncryptionInfo() {
        binding.itemEncryption.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("🔒 End-to-End Encryption")
                .setMessage(
                    "All personal conversations, group discussions, and marketplace inquiries in AroundUS are secured with authenticated 256-bit AES-GCM encryption.\n\n" +
                    "• End-to-End: Messages are encrypted directly on your device before network transmission and can only be decrypted by the intended recipient.\n\n" +
                    "• Authenticated: 128-bit AEAD tags ensure that no message can be altered, intercepted, or tampered with in transit.\n\n" +
                    "• Private: Not even AroundUS or cloud database administrators have access to your private conversations."
                )
                .setPositiveButton("Got It", null)
                .show()
        }
    }

    private fun setupNotificationSettings() {
        binding.itemNotifSettings.setOnClickListener {
            val ann = prefs.getBoolean(KEY_NOTIF_ANNOUNCEMENTS, true)
            val chats = prefs.getBoolean(KEY_NOTIF_CHATS, true)
            val events = prefs.getBoolean(KEY_NOTIF_EVENTS, true)
            val market = prefs.getBoolean(KEY_NOTIF_MARKET, true)

            val options = arrayOf(
                "Community announcements & alerts",
                "Direct messages & inquiry replies",
                "Event reminders & RSVP updates",
                "Marketplace buy & sell updates"
            )
            val checkedItems = booleanArrayOf(ann, chats, events, market)

            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Notification Preferences")
                .setMultiChoiceItems(options, checkedItems) { _, which, isChecked ->
                    checkedItems[which] = isChecked
                }
                .setPositiveButton("Save") { _, _ ->
                    prefs.edit()
                        .putBoolean(KEY_NOTIF_ANNOUNCEMENTS, checkedItems[0])
                        .putBoolean(KEY_NOTIF_CHATS, checkedItems[1])
                        .putBoolean(KEY_NOTIF_EVENTS, checkedItems[2])
                        .putBoolean(KEY_NOTIF_MARKET, checkedItems[3])
                        .apply()

                    Toast.makeText(requireContext(), "Notification settings updated", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun setupHelpCenter() {
        binding.itemHelp.setOnClickListener {
            val helpOptions = arrayOf(
                "Frequently Asked Questions (FAQ)",
                "How to create community events",
                "Neighborhood safety guidelines",
                "Contact AroundUS Support"
            )

            MaterialAlertDialogBuilder(requireContext())
                .setTitle("AroundUS Help & Support")
                .setItems(helpOptions) { _, which ->
                    when (which) {
                        0 -> showFaqDialog()
                        1 -> showEventHelpDialog()
                        2 -> showSafetyGuidelinesDialog()
                        3 -> contactSupportEmail()
                    }
                }
                .setNegativeButton("Close", null)
                .show()
        }
    }

    private fun showFaqDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Frequently Asked Questions")
            .setMessage(
                "• Who can see my posts?\nVerified neighbors in your selected community can view and interact with your posts.\n\n" +
                "• How do I attend local events?\nOpen the Events section and tap 'Attend' on any upcoming community gathering.\n\n" +
                "• How does Buy & Sell work?\nList any item with photos, condition, and price. Neighbors can directly message you to coordinate inspection and pickup."
            )
            .setPositiveButton("Got It", null)
            .show()
    }

    private fun showEventHelpDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Creating Community Events")
            .setMessage(
                "To host an event:\n" +
                "1. Go to the Events tab.\n" +
                "2. Tap the '+ Host Event' button.\n" +
                "3. Set the date, time, location, category, and attach a banner photo.\n" +
                "4. All nearby neighbors will see your event in their feed."
            )
            .setPositiveButton("Understood", null)
            .show()
    }

    private fun showSafetyGuidelinesDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Neighborhood Safety Guidelines")
            .setMessage(
                "• Be courteous, respectful, and neighborly at all times.\n" +
                "• Never share sensitive financial details or passwords.\n" +
                "• Meet in public, well-lit spaces for Marketplace item pickups.\n" +
                "• Report any suspicious activity, spam, or harassment to moderation immediately."
            )
            .setPositiveButton("I Agree", null)
            .show()
    }

    private fun contactSupportEmail() {
        try {
            val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:help@aroundus.app")
                putExtra(Intent.EXTRA_SUBJECT, "AroundUS Support Request")
                putExtra(
                    Intent.EXTRA_TEXT,
                    "User: ${SupabaseClient.getCurrentUserName() ?: "Neighbor"}\n" +
                    "Email: ${SupabaseClient.getCurrentUserEmail() ?: ""}\n" +
                    "App Version: 1.0.0\n\n" +
                    "Describe your issue or question below:\n"
                )
            }
            startActivity(Intent.createChooser(emailIntent, "Send Email via"))
        } catch (_: Exception) {
            Toast.makeText(requireContext(), "Support email: help@aroundus.app", Toast.LENGTH_LONG).show()
        }
    }

    private fun setupFeedback() {
        binding.itemFeedback.setOnClickListener {
            val layout = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(50, 20, 50, 10)
            }

            val tvPrompt = TextView(requireContext()).apply {
                text = "We'd love to hear how we can improve AroundUS for your neighborhood!"
                textSize = 14f
                setTextColor(android.graphics.Color.parseColor("#475569"))
                setPadding(0, 0, 0, 16)
            }

            val etInput = EditText(requireContext()).apply {
                hint = "Share your suggestions, feature ideas, or feedback..."
                minLines = 3
                setBackgroundResource(R.drawable.bg_search_input_rounded)
                setPadding(24, 20, 24, 20)
                textSize = 14f
            }

            layout.addView(tvPrompt)
            layout.addView(etInput)

            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Send Feedback")
                .setView(layout)
                .setPositiveButton("Send") { _, _ ->
                    val feedbackText = etInput.text.toString().trim()
                    if (feedbackText.isNotBlank()) {
                        Toast.makeText(requireContext(), "Thank you for your feedback! It helps build a better community.", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(requireContext(), "Feedback cannot be empty.", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun setupAbout() {
        binding.itemAbout.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("About AroundUS")
                .setMessage(
                    "AroundUS • Version 1.0.0 (Production)\n\n" +
                    "AroundUS is a neighborhood-first community network designed to connect residents, promote local events, support neighborhood commerce, and foster local collaboration.\n\n" +
                    "Developed with ❤️ for local communities."
                )
                .setPositiveButton("Done", null)
                .show()
        }
    }

    private fun setupTerms() {
        binding.itemTerms.setOnClickListener {
            val intent = Intent(requireContext(), TermsAndConditionsActivity::class.java)
            startActivity(intent)
            activity?.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
    }

    private fun setupLogout() {
        binding.itemLogout.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Sign Out")
                .setMessage("Are you sure you want to sign out of your AroundUS account?")
                .setPositiveButton("Sign Out") { _, _ ->
                    val currentUid = SupabaseClient.getCurrentUserId() ?: ""
                    if (currentUid.isNotBlank()) {
                        UserPresenceManager.setUserOnline(currentUid, false)
                    }

                    SupabaseClient.clearSession()
                    Toast.makeText(requireContext(), "Signed out successfully", Toast.LENGTH_SHORT).show()

                    val intent = Intent(requireContext(), LoginActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                    startActivity(intent)
                    requireActivity().finish()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
