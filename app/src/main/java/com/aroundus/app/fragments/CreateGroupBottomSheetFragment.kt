package com.aroundus.app.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.aroundus.app.R
import com.aroundus.app.databinding.BottomSheetCreateGroupBinding
import com.aroundus.app.models.GroupEntity
import com.aroundus.app.repository.GroupRepository
import com.aroundus.app.repository.NetworkResult
import com.aroundus.app.utils.AppRefreshManager
import com.aroundus.app.utils.RefreshTarget
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch

class CreateGroupBottomSheetFragment : BottomSheetDialogFragment() {

    private var _binding: BottomSheetCreateGroupBinding? = null
    private val binding get() = _binding!!

    private val groupRepository = GroupRepository()
    var onGroupCreated: ((GroupEntity) -> Unit)? = null

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

        binding.btnCloseSheet.setOnClickListener {
            dismiss()
        }

        binding.btnSubmitCreateGroup.setOnClickListener {
            submitCreateGroup()
        }
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
            when (val result = groupRepository.createGroup(name = name, description = description, category = category)) {
                is NetworkResult.Success -> {
                    setLoading(false)
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
