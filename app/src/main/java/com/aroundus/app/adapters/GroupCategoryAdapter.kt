package com.aroundus.app.adapters
 
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.aroundus.app.R
import com.aroundus.app.databinding.ItemGroupCategoryBinding
import com.aroundus.app.models.GroupCategory

class GroupCategoryAdapter(
    private val categories: List<GroupCategory>,
    private val onCategoryClick: (GroupCategory) -> Unit
) : RecyclerView.Adapter<GroupCategoryAdapter.CategoryViewHolder>() {

    private var selectedIndex = 0

    inner class CategoryViewHolder(val binding: ItemGroupCategoryBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val binding = ItemGroupCategoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CategoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        val category = categories[position]
        val isSelected = position == selectedIndex
        val context = holder.itemView.context

        holder.binding.apply {
            tvCategoryName.text = category.name
            ivCategoryIcon.setImageResource(category.iconRes)

            if (isSelected) {
                layoutCategory.setBackgroundResource(R.drawable.bg_group_category_selected)
                ivCategoryIcon.setColorFilter(ContextCompat.getColor(context, R.color.app_green))
                tvCategoryName.setTextColor(ContextCompat.getColor(context, R.color.app_green))
            } else {
                layoutCategory.setBackgroundResource(R.drawable.bg_group_category_unselected)
                val unselectedColor = Color.parseColor("#334155")
                ivCategoryIcon.setColorFilter(unselectedColor)
                tvCategoryName.setTextColor(unselectedColor)
            }

            root.setOnClickListener {
                val prev = selectedIndex
                selectedIndex = holder.bindingAdapterPosition
                notifyItemChanged(prev)
                notifyItemChanged(selectedIndex)
                onCategoryClick(category)
            }
        }
    }

    override fun getItemCount(): Int = categories.size

    fun setSelectedCategory(categoryName: String) {
        val index = categories.indexOfFirst { it.name.equals(categoryName, ignoreCase = true) }
        if (index != -1 && index != selectedIndex) {
            val prev = selectedIndex
            selectedIndex = index
            notifyItemChanged(prev)
            notifyItemChanged(selectedIndex)
        }
    }
}
