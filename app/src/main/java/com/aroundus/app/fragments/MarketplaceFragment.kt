package com.aroundus.app.fragments

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.aroundus.app.R
import com.aroundus.app.activities.ConversationActivity
import com.aroundus.app.activities.MainActivity
import com.aroundus.app.adapters.ProductAdapter
import com.aroundus.app.databinding.FragmentMarketplaceBinding
import com.aroundus.app.models.Product
import com.aroundus.app.utils.DummyData

class MarketplaceFragment : Fragment() {

    private enum class SortMode {
        NEARBY,
        PRICE_LOW_HIGH,
        PRICE_HIGH_LOW,
        FREE_FIRST
    }

    private var _binding: FragmentMarketplaceBinding? = null
    private val binding get() = _binding!!

    private lateinit var productAdapter: ProductAdapter
    private val allProducts = DummyData.products
    private var filteredProducts = mutableListOf<Product>()

    private var currentCategory = "All"
    private var currentQuery = ""
    private var currentSort = SortMode.NEARBY
    private var isSavedFilterOnly = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMarketplaceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupTopBarActions()
        setupSearch()
        setupCategoryChips()
        applyFilters()
    }

    private fun setupRecyclerView() {
        productAdapter = ProductAdapter(
            products = filteredProducts,
            onProductClick = { product ->
                try {
                    val bundle = Bundle().apply {
                        putString("productId", product.id)
                    }
                    findNavController().navigate(R.id.action_marketplace_to_productDetail, bundle)
                } catch (_: Exception) {
                    // Fallback to conversation with seller
                    openChatWithSeller(product)
                }
            },
            onChatClick = { product ->
                openChatWithSeller(product)
            }
        )

        binding.rvProducts.apply {
            layoutManager = GridLayoutManager(requireContext(), 2)
            adapter = productAdapter
        }
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

    private fun setupTopBarActions() {
        binding.btnMenu.setOnClickListener {
            (activity as? MainActivity)?.openDrawer()
        }

        binding.btnFilter.setOnClickListener { v ->
            showSortMenu(v)
        }

        binding.tvSortLabel.setOnClickListener { v ->
            showSortMenu(v)
        }

        binding.btnSavedItems.setOnClickListener {
            isSavedFilterOnly = !isSavedFilterOnly
            if (isSavedFilterOnly) {
                binding.btnSavedItems.setImageResource(R.drawable.ic_bookmark)
                binding.btnSavedItems.imageTintList = ColorStateList.valueOf(Color.parseColor("#0D8A3F"))
                Toast.makeText(requireContext(), "Showing saved items ❤️", Toast.LENGTH_SHORT).show()
            } else {
                binding.btnSavedItems.setImageResource(R.drawable.ic_bookmark_outline)
                binding.btnSavedItems.imageTintList = ColorStateList.valueOf(Color.parseColor("#0F172A"))
            }
            applyFilters()
        }

        binding.fabSell.setOnClickListener {
            try {
                findNavController().navigate(R.id.action_marketplace_to_sellItem)
            } catch (_: Exception) {
                Toast.makeText(requireContext(), "Sell an item in Bhujpur neighborhood", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnResetFilters.setOnClickListener {
            resetAllFilters()
        }
    }

    private fun setupSearch() {
        binding.etSearchProducts.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                currentQuery = s?.toString()?.trim().orEmpty()
                binding.btnClearSearch.visibility = if (currentQuery.isNotEmpty()) View.VISIBLE else View.GONE
                applyFilters()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.btnClearSearch.setOnClickListener {
            binding.etSearchProducts.text?.clear()
        }
    }

    private fun setupCategoryChips() {
        binding.chipAll.setOnClickListener { selectCategory("All", binding.chipAll) }
        binding.chipFurniture.setOnClickListener { selectCategory("Furniture", binding.chipFurniture) }
        binding.chipVehicles.setOnClickListener { selectCategory("Vehicles", binding.chipVehicles) }
        binding.chipElectronics.setOnClickListener { selectCategory("Electronics", binding.chipElectronics) }
        binding.chipFree.setOnClickListener { selectCategory("Free", binding.chipFree) }
        binding.chipGarden.setOnClickListener { selectCategory("Home & Garden", binding.chipGarden) }
    }

    private fun selectCategory(category: String, activeChip: TextView) {
        currentCategory = category
        updateChipStyles(activeChip)
        applyFilters()
    }

    private fun updateChipStyles(activeChip: TextView) {
        val allChips = listOf(
            binding.chipAll,
            binding.chipFurniture,
            binding.chipVehicles,
            binding.chipElectronics,
            binding.chipFree,
            binding.chipGarden
        )

        allChips.forEach { chip ->
            if (chip == activeChip) {
                chip.setBackgroundResource(R.drawable.bg_category_chip_active)
                chip.setTextColor(Color.WHITE)
            } else {
                chip.setBackgroundResource(R.drawable.bg_category_chip_inactive)
                chip.setTextColor(Color.parseColor("#475569"))
            }
        }
    }

    private fun showSortMenu(anchor: View) {
        val popup = PopupMenu(requireContext(), anchor)
        popup.menu.add(0, 1, 0, "⚡ Nearby Listings (Default)")
        popup.menu.add(0, 2, 1, "💰 Price: Low to High")
        popup.menu.add(0, 3, 2, "💎 Price: High to Low")
        popup.menu.add(0, 4, 3, "🎁 Free Items First")

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> {
                    currentSort = SortMode.NEARBY
                    binding.tvSortLabel.text = "Sort: Nearby ▾"
                }
                2 -> {
                    currentSort = SortMode.PRICE_LOW_HIGH
                    binding.tvSortLabel.text = "Sort: ₹ Low-High ▾"
                }
                3 -> {
                    currentSort = SortMode.PRICE_HIGH_LOW
                    binding.tvSortLabel.text = "Sort: ₹ High-Low ▾"
                }
                4 -> {
                    currentSort = SortMode.FREE_FIRST
                    binding.tvSortLabel.text = "Sort: Free First ▾"
                }
            }
            applyFilters()
            true
        }
        popup.show()
    }

    private fun parsePriceAmount(priceStr: String): Int {
        if (priceStr.equals("Free", ignoreCase = true)) return 0
        val digits = priceStr.filter { it.isDigit() }
        return digits.toIntOrNull() ?: 0
    }

    private fun applyFilters() {
        val list = allProducts.filter { product ->
            val matchesCategory = when (currentCategory) {
                "All" -> true
                "Free" -> product.price.equals("Free", ignoreCase = true) || product.category.equals("Free", ignoreCase = true)
                else -> product.category.contains(currentCategory, ignoreCase = true)
            }

            val matchesQuery = if (currentQuery.isBlank()) true else {
                product.name.contains(currentQuery, ignoreCase = true) ||
                product.location.contains(currentQuery, ignoreCase = true) ||
                product.condition.contains(currentQuery, ignoreCase = true) ||
                product.category.contains(currentQuery, ignoreCase = true) ||
                product.sellerName.contains(currentQuery, ignoreCase = true)
            }

            val matchesSaved = if (isSavedFilterOnly) product.isSaved else true

            matchesCategory && matchesQuery && matchesSaved
        }.toMutableList()

        // Apply selected sort
        when (currentSort) {
            SortMode.NEARBY -> {}
            SortMode.PRICE_LOW_HIGH -> list.sortBy { parsePriceAmount(it.price) }
            SortMode.PRICE_HIGH_LOW -> list.sortByDescending { parsePriceAmount(it.price) }
            SortMode.FREE_FIRST -> list.sortByDescending { it.price.equals("Free", ignoreCase = true) }
        }

        filteredProducts = list
        productAdapter.updateList(filteredProducts)

        val hasItems = filteredProducts.isNotEmpty()
        binding.rvProducts.visibility = if (hasItems) View.VISIBLE else View.GONE
        binding.layoutEmptyState.visibility = if (hasItems) View.GONE else View.VISIBLE

        binding.tvResultsCount.text = when {
            isSavedFilterOnly -> "Saved Wishlist (${filteredProducts.size} items)"
            currentQuery.isNotBlank() || currentCategory != "All" -> "Showing ${filteredProducts.size} listings nearby"
            else -> "Verified Local Neighbors · Zero Commission"
        }
    }

    private fun resetAllFilters() {
        currentCategory = "All"
        currentQuery = ""
        isSavedFilterOnly = false
        currentSort = SortMode.NEARBY
        binding.etSearchProducts.text?.clear()
        binding.btnSavedItems.setImageResource(R.drawable.ic_bookmark_outline)
        binding.btnSavedItems.imageTintList = ColorStateList.valueOf(Color.parseColor("#0F172A"))
        binding.tvSortLabel.text = "Sort: Nearby ▾"
        updateChipStyles(binding.chipAll)
        applyFilters()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
