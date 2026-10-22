package com.aroundus.app.adapters

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.aroundus.app.R
import com.aroundus.app.databinding.ItemProductBinding
import com.aroundus.app.models.Product
import com.aroundus.app.utils.loadUserAvatar
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy

class ProductAdapter(
    private var products: List<Product>,
    private val onProductClick: (Product) -> Unit,
    private val onChatClick: ((Product) -> Unit)? = null,
    private val onWishlistClick: ((Product) -> Unit)? = null
) : RecyclerView.Adapter<ProductAdapter.ProductViewHolder>() {

    fun updateList(newProducts: List<Product>) {
        products = newProducts
        notifyDataSetChanged()
    }

    inner class ProductViewHolder(private val binding: ItemProductBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(product: Product) {
            binding.tvProductName.text = product.name

            // Price badge styling
            if (product.price.equals("Free", ignoreCase = true) || product.priceAmount <= 0.0) {
                binding.tvPrice.text = "🎁 Free"
                binding.tvPrice.setBackgroundResource(R.drawable.bg_free_badge)
            } else {
                binding.tvPrice.text = product.price
                binding.tvPrice.setBackgroundResource(R.drawable.bg_price_pill)
            }

            binding.tvLocation.text = if (!product.locality.isNullOrBlank()) product.locality else product.location
            binding.tvTime.text = product.timeAgo
            binding.tvCondition.text = product.condition

            // Video indicator badge
            binding.badgeVideo.visibility = if (product.isVideo) View.VISIBLE else View.GONE

            // Product image loading (Supabase remote URL / local resource)
            if (!product.imageUrl.isNullOrBlank()) {
                Glide.with(binding.root.context)
                    .load(product.imageUrl)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .placeholder(R.drawable.bg_image_placeholder)
                    .error(R.drawable.bg_image_placeholder)
                    .centerCrop()
                    .into(binding.ivProduct)
            } else if (product.imageRes != 0) {
                binding.ivProduct.setImageResource(product.imageRes)
            } else {
                binding.ivProduct.setImageResource(R.drawable.bg_image_placeholder)
            }

            // Seller details
            binding.tvSellerName.text = product.sellerName
            if (!product.sellerAvatarUrl.isNullOrBlank()) {
                binding.ivSellerAvatar.loadUserAvatar(product.sellerAvatarUrl)
            } else if (product.sellerAvatarRes != 0) {
                binding.ivSellerAvatar.setImageResource(product.sellerAvatarRes)
            } else {
                binding.ivSellerAvatar.setImageResource(R.drawable.shape_avatar_placeholder)
            }

            binding.ivVerifiedBadge.visibility = if (product.isVerifiedSeller) View.VISIBLE else View.GONE

            // Wishlist heart button
            updateWishlistIcon(product.isSaved)
            binding.btnWishlist.setOnClickListener { v ->
                product.isSaved = !product.isSaved
                updateWishlistIcon(product.isSaved)

                // Micro-bounce animation
                v.animate().scaleX(1.3f).scaleY(1.3f).setDuration(120).withEndAction {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                }.start()

                onWishlistClick?.invoke(product)
            }

            // Quick chat pill
            binding.btnQuickChat.setOnClickListener {
                onChatClick?.invoke(product)
            }

            // Entire card click
            binding.root.setOnClickListener { onProductClick(product) }
        }

        private fun updateWishlistIcon(isSaved: Boolean) {
            if (isSaved) {
                binding.btnWishlist.setImageResource(R.drawable.ic_favorite)
                binding.btnWishlist.imageTintList = ColorStateList.valueOf(Color.parseColor("#EF4444"))
            } else {
                binding.btnWishlist.setImageResource(R.drawable.ic_favorite_border)
                binding.btnWishlist.imageTintList = ColorStateList.valueOf(Color.parseColor("#64748B"))
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductViewHolder {
        val binding = ItemProductBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ProductViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ProductViewHolder, position: Int) {
        holder.bind(products[position])
    }

    override fun getItemCount() = products.size
}
