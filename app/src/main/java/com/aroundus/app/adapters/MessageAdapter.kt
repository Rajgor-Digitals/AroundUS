package com.aroundus.app.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.aroundus.app.R
import com.aroundus.app.databinding.ItemMessageReceivedBinding
import com.aroundus.app.databinding.ItemMessageSentBinding
import com.aroundus.app.models.Message
import com.aroundus.app.models.ProductCardAttachment
import com.bumptech.glide.Glide

class MessageAdapter(
    private val messages: List<Message>,
    private val onProductClick: ((ProductCardAttachment) -> Unit)? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_SENT = 1
        private const val VIEW_TYPE_RECEIVED = 2
    }

    inner class SentViewHolder(private val binding: ItemMessageSentBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(message: Message) {
            val product = message.productCard
            if (product != null) {
                binding.cardProductAttachment.visibility = View.VISIBLE
                binding.tvProductTitle.text = product.title
                binding.tvProductPrice.text = product.price

                if (!product.imageUrl.isNullOrBlank()) {
                    Glide.with(binding.root.context)
                        .load(product.imageUrl)
                        .placeholder(R.drawable.bg_image_placeholder)
                        .error(R.drawable.bg_image_placeholder)
                        .centerCrop()
                        .into(binding.ivProductThumb)
                } else if (product.imageRes != 0) {
                    binding.ivProductThumb.setImageResource(product.imageRes)
                } else {
                    binding.ivProductThumb.setImageResource(R.drawable.bg_image_placeholder)
                }

                binding.cardProductAttachment.setOnClickListener {
                    onProductClick?.invoke(product)
                }
            } else {
                binding.cardProductAttachment.visibility = View.GONE
            }

            binding.tvMessage.text = message.text
            binding.tvMessage.visibility = if (message.text.isNotBlank()) View.VISIBLE else View.GONE
            binding.tvTime.text = message.time

            if (message.isSeen) {
                binding.ivReceipt.setImageResource(R.drawable.ic_done_all_green)
                binding.ivReceipt.imageTintList = android.content.res.ColorStateList.valueOf(
                    android.graphics.Color.parseColor("#0D8A3F")
                )
                binding.ivReceipt.contentDescription = "Seen"
            } else {
                binding.ivReceipt.setImageResource(R.drawable.ic_done_single)
                binding.ivReceipt.imageTintList = android.content.res.ColorStateList.valueOf(
                    android.graphics.Color.parseColor("#94A3B8")
                )
                binding.ivReceipt.contentDescription = "Sent"
            }
        }
    }

    inner class ReceivedViewHolder(private val binding: ItemMessageReceivedBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(message: Message) {
            val product = message.productCard
            if (product != null) {
                binding.cardProductAttachment.visibility = View.VISIBLE
                binding.tvProductTitle.text = product.title
                binding.tvProductPrice.text = product.price

                if (!product.imageUrl.isNullOrBlank()) {
                    Glide.with(binding.root.context)
                        .load(product.imageUrl)
                        .placeholder(R.drawable.bg_image_placeholder)
                        .error(R.drawable.bg_image_placeholder)
                        .centerCrop()
                        .into(binding.ivProductThumb)
                } else if (product.imageRes != 0) {
                    binding.ivProductThumb.setImageResource(product.imageRes)
                } else {
                    binding.ivProductThumb.setImageResource(R.drawable.bg_image_placeholder)
                }

                binding.cardProductAttachment.setOnClickListener {
                    onProductClick?.invoke(product)
                }
            } else {
                binding.cardProductAttachment.visibility = View.GONE
            }

            binding.tvMessage.text = message.text
            binding.tvMessage.visibility = if (message.text.isNotBlank()) View.VISIBLE else View.GONE
            binding.tvTime.text = message.time
        }
    }

    override fun getItemViewType(position: Int) =
        if (messages[position].isSent) VIEW_TYPE_SENT else VIEW_TYPE_RECEIVED

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_SENT) {
            SentViewHolder(ItemMessageSentBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        } else {
            ReceivedViewHolder(ItemMessageReceivedBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is SentViewHolder -> holder.bind(messages[position])
            is ReceivedViewHolder -> holder.bind(messages[position])
        }
    }

    override fun getItemCount() = messages.size
}
