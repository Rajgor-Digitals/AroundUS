package com.aroundus.app.fragments

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.aroundus.app.R
import com.aroundus.app.databinding.BottomSheetCreateGroupBinding
import com.aroundus.app.models.GroupEntity
import com.aroundus.app.models.resolveGroupCoverRes
import com.aroundus.app.repository.GroupRepository
import com.aroundus.app.repository.NetworkResult
import com.aroundus.app.utils.AppRefreshManager
import com.aroundus.app.utils.RefreshTarget
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch

class CreateGroupBottomSheetFragment : BottomSheetDialogFragment() {

    private var _binding: BottomSheetCreateGroupBinding? = null
    private val binding get() = _binding!!

    private val groupRepository = GroupRepository()
    var onGroupCreated: ((GroupEntity) -> Unit)? = null

    private var selectedImageUri: Uri? = null

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            selectedImageUri = uri
            Glide.with(this)
                .load(uri)
                .centerCrop()
                .into(binding.ivGroupIconPreview)
            binding.tvGroupImageAction.text = "Change Icon / Cover"
            binding.btnRemoveGroupIcon.visibility = View.VISIBLE
            Toast.makeText(requireContext(), "Icon selected", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.Theme_AroundUS_BottomSheetDialog)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetCreateGroupBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        val dialog = dialog as? BottomSheetDialog ?: return
        dialog.behavior.apply {
            state = BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupImagePicker()
        setupCategoryListener()

        binding.btnCloseSheet.setOnClickListener {
            dismiss()
        }

        binding.btnSubmitCreateGroup.setOnClickListener {
            submitCreateGroup()
        }
    }

    private fun setupImagePicker() {
        val launchPicker = {
            pickImageLauncher.launch("image/*")
        }

        binding.containerGroupImage.setOnClickListener { launchPicker() }
        binding.tvGroupImageAction.setOnClickListener { launchPicker() }

        binding.btnRemoveGroupIcon.setOnClickListener {
            selectedImageUri = null
            binding.btnRemoveGroupIcon.visibility = View.GONE
            binding.tvGroupImageAction.text = "+ Add Group Icon"
            updateDefaultIconForCategory(getSelectedCategory())
            Toast.makeText(requireContext(), "Custom icon removed", Toast.LENGTH_SHORT).show()
        }

        updateDefaultIconForCategory(getSelectedCategory())
    }

    private fun setupCategoryListener() {
        binding.chipGroupCategory.setOnCheckedStateChangeListener { _, _ ->
            if (selectedImageUri == null) {
                updateDefaultIconForCategory(getSelectedCategory())
            }
        }
    }

    private fun updateDefaultIconForCategory(category: String) {
        val defaultRes = resolveGroupCoverRes(category, "")
        binding.ivGroupIconPreview.setImageResource(defaultRes)
    }

    private fun getSelectedCategory(): String {
        return when (binding.chipGroupCategory.checkedChipId) {
            R.id.chipLocal -> "Local"
            R.id.chipActivities -> "Activities"
            R.id.chipBuySell -> "Buy / Sell"
            R.id.chipHelpSupport -> "Help & Support"
            R.id.chipGeneral -> "General"
            else -> "Local"
        }
    }

    private fun submitCreateGroup() {
        val name = binding.etGroupName.text.toString().trim()
        val description = binding.etGroupDescription.text.toString().trim()
        val category = getSelectedCategory()

        if (name.isBlank()) {
            binding.etGroupName.error = "Group name is required"
            binding.etGroupName.requestFocus()
            return
        }

        setLoading(true)

        lifecycleScope.launch {
            var uploadedUrl: String? = null
            if (selectedImageUri != null) {
                binding.btnSubmitCreateGroup.text = "Uploading Icon..."
                when (val uploadResult = groupRepository.uploadGroupImage(requireContext(), selectedImageUri!!)) {
                    is NetworkResult.Success -> {
                        uploadedUrl = uploadResult.data
                    }
                    is NetworkResult.Error -> {
                        Toast.makeText(requireContext(), "Icon upload note: ${uploadResult.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            when (val result = groupRepository.createGroup(
                name = name,
                description = description,
                category = category,
                imageUrl = uploadedUrl
            )) {
                is NetworkResult.Success -> {
                    setLoading(false)
                    com.aroundus.app.utils.GroupJoinManager.recordGroupJoined(requireContext(), result.data.groupId, System.currentTimeMillis())
                    AppRefreshManager.notifyDataChanged(RefreshTarget.GROUPS)
                    AppRefreshManager.notifyDataChanged(RefreshTarget.PROFILE)
                    Toast.makeText(requireContext(), "Group created successfully!", Toast.LENGTH_SHORT).show()
                    onGroupCreated?.invoke(result.data)
                    dismiss()
                }
                is NetworkResult.Error -> {
                    setLoading(false)
                    Toast.makeText(requireContext(), result.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun setLoading(isLoading: Boolean) {
        binding.btnSubmitCreateGroup.isEnabled = !isLoading
        binding.btnSubmitCreateGroup.text = if (isLoading) "" else "Create Group"
        binding.pbCreateGroupLoading.visibility = if (isLoading) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "CreateGroupBottomSheet"
        fun newInstance(): CreateGroupBottomSheetFragment = CreateGroupBottomSheetFragment()
    }
}
