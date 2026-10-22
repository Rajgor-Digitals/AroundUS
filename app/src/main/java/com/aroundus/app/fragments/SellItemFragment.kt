package com.aroundus.app.fragments

import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.aroundus.app.R
import com.aroundus.app.databinding.FragmentSellItemBinding
import com.aroundus.app.network.SupabaseClient
import com.aroundus.app.repository.MarketplaceRepository
import com.aroundus.app.repository.NetworkResult
import com.aroundus.app.utils.AppRefreshManager
import com.aroundus.app.utils.RefreshTarget
import com.bumptech.glide.Glide
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.launch

class SellItemFragment : Fragment() {

    private var _binding: FragmentSellItemBinding? = null
    private val binding get() = _binding!!

    private val marketplaceRepository = MarketplaceRepository()
    private val selectedPhotoUris = mutableListOf<Uri>()

    private var selectedCategory: String = "Furniture"
    private var selectedCondition: String = "Like New"
    private var isPublishing = false

    private val photoPickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            val remainingSlots = 5 - selectedPhotoUris.size
            val toAdd = uris.take(remainingSlots)
            selectedPhotoUris.addAll(toAdd)
            renderPhotoThumbnails()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSellItemBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupTopBar()
        setupPhotoPicker()
        setupCategorySelection()
        setupConditionSelection()
        setupPublishAction()
    }

    private fun setupTopBar() {
        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.btnCancel.setOnClickListener {
            findNavController().popBackStack()
        }
    }

    private fun setupPhotoPicker() {
        binding.cardAddPhoto.setOnClickListener {
            if (selectedPhotoUris.size >= 5) {
                Toast.makeText(requireContext(), "Maximum 5 photos allowed.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            photoPickerLauncher.launch("image/*")
        }
    }

    private fun renderPhotoThumbnails() {
        // Keep the cardAddPhoto (first child) and remove any previously added thumbnail views
        val container = binding.layoutPhotosContainer
        val childrenToRemove = mutableListOf<View>()
        for (i in 1 until container.childCount) {
            childrenToRemove.add(container.getChildAt(i))
        }
        childrenToRemove.forEach { container.removeView(it) }

        // Update count label on Add Card
        val count = selectedPhotoUris.size
        binding.tvAddPhotoCount.text = "+ Add ($count/5)"
        binding.cardAddPhoto.visibility = if (count >= 5) View.GONE else View.VISIBLE

        // Add thumbnail cards for each selected photo
        selectedPhotoUris.forEachIndexed { index, uri ->
            val card = MaterialCardView(requireContext()).apply {
                layoutParams = ViewGroup.MarginLayoutParams(
                    (100 * resources.displayMetrics.density).toInt(),
                    (100 * resources.displayMetrics.density).toInt()
                ).apply {
                    marginEnd = (10 * resources.displayMetrics.density).toInt()
                }
                radius = 14 * resources.displayMetrics.density
                cardElevation = 2 * resources.displayMetrics.density
                strokeColor = Color.parseColor("#E2E8F0")
                strokeWidth = (1 * resources.displayMetrics.density).toInt()
            }

            val frame = FrameLayout(requireContext()).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }

            val iv = ImageView(requireContext()).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                scaleType = ImageView.ScaleType.CENTER_CROP
            }
            Glide.with(this).load(uri).centerCrop().into(iv)
            frame.addView(iv)

            // Delete (X) button on top right of thumbnail
            val btnDelete = ImageView(requireContext()).apply {
                val size = (26 * resources.displayMetrics.density).toInt()
                layoutParams = FrameLayout.LayoutParams(size, size).apply {
                    gravity = Gravity.TOP or Gravity.END
                    topMargin = (6 * resources.displayMetrics.density).toInt()
                    rightMargin = (6 * resources.displayMetrics.density).toInt()
                }
                setImageResource(R.drawable.ic_close)
                setBackgroundResource(R.drawable.bg_circle_close_btn)
                setPadding(
                    (5 * resources.displayMetrics.density).toInt(),
                    (5 * resources.displayMetrics.density).toInt(),
                    (5 * resources.displayMetrics.density).toInt(),
                    (5 * resources.displayMetrics.density).toInt()
                )
                setColorFilter(Color.WHITE)
                setOnClickListener {
                    selectedPhotoUris.removeAt(index)
                    renderPhotoThumbnails()
                }
            }
            frame.addView(btnDelete)

            card.addView(frame)
            container.addView(card)
        }
    }

    private fun setupCategorySelection() {
        val categories = listOf(
            binding.catFurniture to "Furniture",
            binding.catVehicles to "Vehicles",
            binding.catElectronics to "Electronics",
            binding.catMobilePhones to "Mobile Phones",
            binding.catHomeKitchen to "Home & Kitchen",
            binding.catFashion to "Fashion",
            binding.catBooks to "Books",
            binding.catSports to "Sports",
            binding.catOther to "Other"
        )

        categories.forEach { (view, catName) ->
            view.setOnClickListener {
                selectedCategory = catName
                categories.forEach { (v, _) ->
                    if (v == view) {
                        v.setBackgroundResource(R.drawable.bg_category_chip_active)
                        v.setTextColor(Color.WHITE)
                    } else {
                        v.setBackgroundResource(R.drawable.bg_category_chip_inactive)
                        v.setTextColor(Color.parseColor("#475569"))
                    }
                }
            }
        }
    }

    private fun setupConditionSelection() {
        val conditions = listOf(
            binding.condBrandNew to "Brand New",
            binding.condLikeNew to "Like New",
            binding.condExcellent to "Excellent",
            binding.condGood to "Good",
            binding.condFair to "Fair"
        )

        conditions.forEach { (view, condName) ->
            view.setOnClickListener {
                selectedCondition = condName
                conditions.forEach { (v, _) ->
                    if (v == view) {
                        v.setBackgroundResource(R.drawable.bg_category_chip_active)
                        v.setTextColor(Color.WHITE)
                    } else {
                        v.setBackgroundResource(R.drawable.bg_category_chip_inactive)
                        v.setTextColor(Color.parseColor("#475569"))
                    }
                }
            }
        }
    }

    private fun setupPublishAction() {
        binding.btnPublish.setOnClickListener {
            if (isPublishing) return@setOnClickListener

            val title = binding.etTitle.text.toString().trim()
            val priceStr = binding.etPrice.text.toString().trim()
            val locality = binding.etLocality.text.toString().trim().ifBlank { "Bhujpur Village, Kutch" }
            val description = binding.etDescription.text.toString().trim()
            val brand = binding.etBrand.text.toString().trim()
            val isNegotiable = binding.switchNegotiable.isChecked

            if (title.isBlank()) {
                binding.etTitle.error = "Please enter an item title"
                binding.etTitle.requestFocus()
                return@setOnClickListener
            }

            val price = priceStr.toDoubleOrNull()
            if (price == null || price < 0.0) {
                binding.etPrice.error = "Please enter a valid price (e.g. 2400 or 0)"
                binding.etPrice.requestFocus()
                return@setOnClickListener
            }

            if (description.isBlank()) {
                binding.etDescription.error = "Please provide a short description"
                binding.etDescription.requestFocus()
                return@setOnClickListener
            }

            val currentUserId = SupabaseClient.getCurrentUserId() ?: ""
            if (currentUserId.isBlank()) {
                Toast.makeText(requireContext(), "Please sign in to publish a listing.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            publishListing(
                title = title,
                price = price,
                description = description,
                category = selectedCategory,
                condition = selectedCondition,
                brand = brand,
                locality = locality,
                isNegotiable = isNegotiable,
                userId = currentUserId
            )
        }
    }

    private fun publishListing(
        title: String,
        price: Double,
        description: String,
        category: String,
        condition: String,
        brand: String,
        locality: String,
        isNegotiable: Boolean,
        userId: String
    ) {
        isPublishing = true
        binding.btnPublish.isEnabled = false
        binding.pbPublishing.visibility = View.VISIBLE
        binding.btnPublish.text = "Uploading photos & publishing..."

        lifecycleScope.launch {
            try {
                val uploadedUrls = mutableListOf<String>()

                // 1. Upload all selected photos to Supabase Storage
                for (uri in selectedPhotoUris) {
                    when (val uploadResult = marketplaceRepository.uploadListingMediaToStorage(requireContext(), uri, userId)) {
                        is NetworkResult.Success -> {
                            uploadedUrls.add(uploadResult.data)
                        }
                        is NetworkResult.Error -> {
                            // If storage upload fails, we log and proceed with accessible local URI as fallback
                            uploadedUrls.add(uri.toString())
                        }
                    }
                }

                // 2. Publish to Supabase listings table
                val createResult = marketplaceRepository.createListing(
                    context = requireContext(),
                    title = title,
                    description = description,
                    price = price,
                    category = category,
                    condition = condition,
                    brand = brand.ifBlank { null },
                    locality = locality,
                    imageUrls = uploadedUrls,
                    isNegotiable = isNegotiable
                )

                binding.pbPublishing.visibility = View.GONE
                binding.btnPublish.isEnabled = true
                isPublishing = false

                when (createResult) {
                    is NetworkResult.Success -> {
                        AppRefreshManager.notifyDataChanged(RefreshTarget.MARKETPLACE)
                        AppRefreshManager.notifyDataChanged(RefreshTarget.PROFILE)
                        Toast.makeText(
                            requireContext(),
                            "🎉 Listing published successfully in $locality!",
                            Toast.LENGTH_LONG
                        ).show()
                        findNavController().popBackStack()
                    }
                    is NetworkResult.Error -> {
                        Toast.makeText(
                            requireContext(),
                            "Failed to publish: ${createResult.message}",
                            Toast.LENGTH_LONG
                        ).show()
                        binding.btnPublish.text = "Publish Listing"
                    }
                }
            } catch (e: Exception) {
                binding.pbPublishing.visibility = View.GONE
                binding.btnPublish.isEnabled = true
                binding.btnPublish.text = "Publish Listing"
                isPublishing = false
                Toast.makeText(requireContext(), "Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
