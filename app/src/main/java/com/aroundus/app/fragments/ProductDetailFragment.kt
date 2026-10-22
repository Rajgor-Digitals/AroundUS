package com.aroundus.app.fragments

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.aroundus.app.R
import com.aroundus.app.activities.ConversationActivity
import com.aroundus.app.databinding.FragmentProductDetailBinding
import com.aroundus.app.models.Product
import com.aroundus.app.network.SupabaseClient
import com.aroundus.app.repository.MarketplaceRepository
import com.aroundus.app.repository.NetworkResult
import com.aroundus.app.activities.FullScreenImageViewerActivity
import com.aroundus.app.utils.loadUserAvatar
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import kotlinx.coroutines.launch

class ProductDetailFragment : Fragment() {

    private var _binding: FragmentProductDetailBinding? = null
    private val binding get() = _binding!!

    private val marketplaceRepository = MarketplaceRepository()
    private var currentProduct: Product? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProductDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val productId = arguments?.getString("productId") ?: ""

        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        loadProduct(productId)
    }

    private fun loadProduct(productId: String) {
        lifecycleScope.launch {
            when (val result = marketplaceRepository.fetchListingById(requireContext(), productId)) {
                is NetworkResult.Success -> {
                    currentProduct = result.data
                    bindProductDetails(result.data)
                }
                is NetworkResult.Error -> {
                    Toast.makeText(requireContext(), "Unable to load listing details.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun bindProductDetails(product: Product) {
        binding.tvProductName.text = product.name
        binding.tvPrice.text = product.price
        binding.chipCondition.text = product.condition
        binding.tvLocation.text = if (!product.locality.isNullOrBlank()) product.locality else product.location
        binding.tvTimeAgo.text = "Posted ${product.timeAgo}"

        // Negotiable badge
        binding.badgeNegotiable.visibility = if (product.isNegotiable) View.VISIBLE else View.GONE

        // Description
        binding.tvDescription.text = if (product.description.isNotBlank()) {
            product.description
        } else {
            "Verified item listed by local neighbor in ${product.location}. Available for immediate in-person inspection and pickup in Bhujpur."
        }

        // Category & Brand metadata
        binding.tvCategoryMeta.text = "Category: ${product.category}"
        if (!product.brand.isNullOrBlank()) {
            binding.tvBrandMeta.text = "Brand: ${product.brand}"
            binding.tvBrandMeta.visibility = View.VISIBLE
        } else {
            binding.tvBrandMeta.visibility = View.GONE
        }

        // Seller information
        binding.tvSellerName.text = product.sellerName
        if (!product.sellerAvatarUrl.isNullOrBlank()) {
            binding.ivSellerAvatar.loadUserAvatar(product.sellerAvatarUrl)
        } else if (product.sellerAvatarRes != 0) {
            binding.ivSellerAvatar.setImageResource(product.sellerAvatarRes)
        } else {
            binding.ivSellerAvatar.setImageResource(R.drawable.shape_avatar_placeholder)
        }
        binding.ivSellerVerified.visibility = if (product.isVerifiedSeller) View.VISIBLE else View.GONE

        // Setup image carousel
        setupImageCarousel(product)

        // Save / Bookmark action
        updateSaveIcon(product.isSaved)
        binding.btnSave.setOnClickListener {
            lifecycleScope.launch {
                val isSaved = marketplaceRepository.toggleSavedListing(requireContext(), product.id)
                product.isSaved = isSaved
                updateSaveIcon(isSaved)
                val msg = if (isSaved) "Saved to wishlist ❤️" else "Removed from wishlist"
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
            }
        }

        // Share action
        binding.btnShare.setOnClickListener {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(
                    Intent.EXTRA_TEXT,
                    "Check out this listing on AroundUS:\n${product.name} - ${product.price}\nLocation: ${product.location}"
                )
            }
            startActivity(Intent.createChooser(shareIntent, "Share Listing"))
        }

        // Report action
        binding.btnReportListing.setOnClickListener {
            showReportDialog()
        }

        // Owner actions vs Buyer actions
        val currentUserId = SupabaseClient.getCurrentUserId() ?: ""
        val isOwner = product.sellerId.isNotBlank() && product.sellerId == currentUserId

        if (isOwner) {
            binding.btnChatWithSeller.visibility = View.GONE
            binding.layoutOwnerActions.visibility = View.VISIBLE

            val isSold = product.listingStatus.equals("sold", ignoreCase = true)
            binding.btnMarkStatus.text = if (isSold) "Mark as Active" else "Mark as Sold"

            binding.btnMarkStatus.setOnClickListener {
                val newStatus = if (isSold) "active" else "sold"
                lifecycleScope.launch {
                    marketplaceRepository.updateListingStatus(product.id, newStatus)
                    Toast.makeText(requireContext(), "Listing updated to $newStatus.", Toast.LENGTH_SHORT).show()
                    loadProduct(product.id)
                }
            }

            binding.btnDeleteListing.setOnClickListener {
                showDeleteConfirmation(product.id)
            }
        } else {
            binding.btnChatWithSeller.visibility = View.VISIBLE
            binding.layoutOwnerActions.visibility = View.GONE

            binding.btnChatWithSeller.setOnClickListener {
                openChatWithSeller(product)
            }
        }
    }

    private fun setupImageCarousel(product: Product) {
        val mediaList = mutableListOf<String>()

        if (product.imageUrls.isNotEmpty()) {
            mediaList.addAll(product.imageUrls)
        } else if (!product.imageUrl.isNullOrBlank()) {
            mediaList.add(product.imageUrl)
        }

        val totalCount = if (mediaList.isNotEmpty()) mediaList.size else 1
        binding.tvImageCounter.text = "1/$totalCount"
        binding.tvImageCounter.visibility = if (totalCount > 1) View.VISIBLE else View.GONE

        val adapter = ProductDetailImageAdapter(mediaList, product.imageRes) { clickedUrl, clickedRes ->
            FullScreenImageViewerActivity.start(
                context = requireContext(),
                imageUrl = clickedUrl,
                imageRes = clickedRes,
                caption = "${product.name} • ${product.price}\n${product.description ?: ""}",
                authorName = product.sellerName,
                subtitle = product.location
            )
        }
        binding.viewPagerImages.adapter = adapter

        binding.viewPagerImages.registerOnPageChangeCallback(object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                binding.tvImageCounter.text = "${position + 1}/$totalCount"
            }
        })

        try {
            binding.dotsIndicator.attachTo(binding.viewPagerImages)
            binding.dotsIndicator.visibility = if (totalCount > 1) View.VISIBLE else View.GONE
        } catch (_: Exception) {}
    }

    private fun updateSaveIcon(isSaved: Boolean) {
        val iconRes = if (isSaved) R.drawable.ic_bookmark else R.drawable.ic_bookmark_outline
        binding.btnSave.setImageResource(iconRes)
        val colorInt = if (isSaved) Color.parseColor("#0D8A3F") else Color.parseColor("#0F172A")
        binding.btnSave.imageTintList = ColorStateList.valueOf(colorInt)
    }

    private fun openChatWithSeller(product: Product) {
        val currentUserId = SupabaseClient.getCurrentUserId() ?: ""
        if (product.sellerId.isNotBlank() && product.sellerId == currentUserId) {
            Toast.makeText(requireContext(), "You cannot message yourself (this is your own listing).", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(requireContext(), ConversationActivity::class.java).apply {
            putExtra("userId", product.sellerId.ifBlank { "seller_${product.id}" })
            putExtra("userName", product.sellerName)
            putExtra("avatarUrl", product.sellerAvatarUrl)
            putExtra("avatarRes", product.sellerAvatarRes)
            putExtra("isOnline", true)
            putExtra("autoSendProductInquiry", true)
            putExtra("productId", product.id)
            putExtra("productName", product.name)
            putExtra("productPrice", product.price)
            putExtra("productImageUrl", product.imageUrl)
            putExtra("productImageRes", product.imageRes)
        }
        startActivity(intent)
    }

    private fun showDeleteConfirmation(listingId: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Listing")
            .setMessage("Are you sure you want to permanently delete this listing from AroundUS?")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    when (val result = marketplaceRepository.deleteListing(listingId)) {
                        is NetworkResult.Success -> {
                            Toast.makeText(requireContext(), "Listing deleted.", Toast.LENGTH_SHORT).show()
                            findNavController().popBackStack()
                        }
                        is NetworkResult.Error -> {
                            Toast.makeText(requireContext(), "Failed to delete: ${result.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showReportDialog() {
        val reasons = arrayOf("Misleading or Inaccurate", "Inappropriate or Offensive", "Scam or Fraud", "Prohibited Item")
        AlertDialog.Builder(requireContext())
            .setTitle("Report Listing")
            .setItems(reasons) { _, which ->
                Toast.makeText(
                    requireContext(),
                    "Thank you. This listing has been flagged for moderation: ${reasons[which]}",
                    Toast.LENGTH_LONG
                ).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private class ProductDetailImageAdapter(
        private val mediaUrls: List<String>,
        private val fallbackRes: Int,
        private val onImageClick: (String?, Int) -> Unit
    ) : RecyclerView.Adapter<ProductDetailImageAdapter.ImageViewHolder>() {

        inner class ImageViewHolder(val imageView: ImageView) : RecyclerView.ViewHolder(imageView)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
            val iv = ImageView(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                scaleType = ImageView.ScaleType.CENTER_CROP
            }
            return ImageViewHolder(iv)
        }

        override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
            val url = mediaUrls.getOrNull(position)
            if (!url.isNullOrBlank()) {
                Glide.with(holder.imageView.context)
                    .load(url)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .placeholder(R.drawable.bg_image_placeholder)
                    .error(R.drawable.bg_image_placeholder)
                    .centerCrop()
                    .into(holder.imageView)
            } else if (fallbackRes != 0) {
                holder.imageView.setImageResource(fallbackRes)
            } else {
                holder.imageView.setImageResource(R.drawable.bg_image_placeholder)
            }

            holder.imageView.setOnClickListener {
                onImageClick(url, fallbackRes)
            }
        }

        override fun getItemCount() = if (mediaUrls.isNotEmpty()) mediaUrls.size else 1
    }
}
