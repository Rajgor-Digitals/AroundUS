package com.aroundus.app.adapters

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.aroundus.app.R
import com.aroundus.app.databinding.ItemProductBinding
import com.aroundus.app.models.Product

class ProductAdapter(
    private var products: List<Product>,
    private val onProductClick: (Product) -> Unit,
    private val onChatClick: ((Product) -> Unit)? = null
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
            if (product.price.equals("Free", ignoreCase = true)) {
                binding.tvPrice.text = "🎁 Free"
                binding.tvPrice.setBackgroundResource(R.drawable.bg_free_badge)
            } else {
                binding.tvPrice.text = product.price
                binding.tvPrice.setBackgroundResource(R.drawable.bg_price_pill)
            }

            binding.tvLocation.text = product.location
            binding.tvTime.text = product.timeAgo
            binding.tvCondition.text = product.condition
            binding.ivProduct.setImageResource(product.imageRes)

            // Seller details
            binding.tvSellerName.text = product.sellerName
            if (product.sellerAvatarRes != 0) {
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

                val msg = if (product.isSaved) "Saved to wishlist ❤️" else "Removed from wishlist"
                Toast.makeText(v.context, msg, Toast.LENGTH_SHORT).show()
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
