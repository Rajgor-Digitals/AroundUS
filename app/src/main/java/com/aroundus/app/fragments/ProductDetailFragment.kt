package com.aroundus.app.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import com.aroundus.app.R
import com.aroundus.app.activities.ConversationActivity
import com.aroundus.app.databinding.FragmentProductDetailBinding
import com.aroundus.app.models.Product
import com.aroundus.app.utils.DummyData

class ProductDetailFragment : Fragment() {

    private var _binding: FragmentProductDetailBinding? = null
    private val binding get() = _binding!!

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

        val productId = arguments?.getString("productId") ?: "1"
        val product = DummyData.products.find { it.id == productId } ?: DummyData.products.first()

        bindProductDetails(product)
    }

    private fun bindProductDetails(product: Product) {
        binding.tvProductName.text = product.name
        binding.tvPrice.text = product.price
        binding.chipCondition.text = product.condition
        binding.tvDescription.text = if (product.description.isNotBlank()) {
            product.description
        } else {
            "Verified item listed by local neighbor in ${product.location}. Available for immediate in-person inspection and pickup."
        }

        binding.tvSellerName.text = product.sellerName
        if (product.sellerAvatarRes != 0) {
            binding.ivSellerAvatar.setImageResource(product.sellerAvatarRes)
        } else {
            binding.ivSellerAvatar.setImageResource(R.drawable.shape_avatar_placeholder)
        }

        // Setup image carousel
        val imageList = listOf(product.imageRes)
        binding.viewPagerImages.adapter = ProductImageAdapter(imageList)
        try {
            binding.dotsIndicator.attachTo(binding.viewPagerImages)
        } catch (_: Exception) {}

        binding.btnBack.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        // Save / Wishlist
        updateSaveIcon(product.isSaved)
        binding.btnSave.setOnClickListener {
            product.isSaved = !product.isSaved
            updateSaveIcon(product.isSaved)
            val msg = if (product.isSaved) "Saved to wishlist ❤️" else "Removed from wishlist"
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
        }

        // Chat with seller
        binding.btnViewProfile.text = "Chat"
        binding.btnViewProfile.setOnClickListener {
            openChatWithSeller(product)
        }
    }

    private fun updateSaveIcon(isSaved: Boolean) {
        val iconRes = if (isSaved) R.drawable.ic_bookmark else R.drawable.ic_bookmark_outline
        binding.btnSave.setImageResource(iconRes)
    }

    private fun openChatWithSeller(product: Product) {
        val intent = Intent(requireContext(), ConversationActivity::class.java).apply {
            putExtra("userId", product.sellerId.ifBlank { "seller_${product.id}" })
            putExtra("userName", product.sellerName)
            putExtra("avatarRes", product.sellerAvatarRes)
            putExtra("isOnline", true)
        }
        startActivity(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private class ProductImageAdapter(private val images: List<Int>) :
        RecyclerView.Adapter<ProductImageAdapter.ImageViewHolder>() {

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
            holder.imageView.setImageResource(images[position])
        }

        override fun getItemCount() = images.size
    }
}
