package com.aroundus.app.fragments

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.aroundus.app.R
import com.aroundus.app.activities.ConversationActivity
import com.aroundus.app.activities.MainActivity
import com.aroundus.app.adapters.ProductAdapter
import com.aroundus.app.databinding.FragmentMarketplaceBinding
import com.aroundus.app.models.Product
import com.aroundus.app.network.SupabaseClient
import com.aroundus.app.repository.MarketplaceRepository
import com.aroundus.app.repository.NetworkResult
import com.aroundus.app.utils.AppRefreshManager
import com.aroundus.app.utils.RefreshTarget
import com.aroundus.app.utils.applyAroundUsTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MarketplaceFragment : Fragment() {

    private enum class SortMode {
        NEARBY,
        NEWEST,
        PRICE_LOW_HIGH,
        PRICE_HIGH_LOW
    }

    private var _binding: FragmentMarketplaceBinding? = null
    private val binding get() = _binding!!

    private val marketplaceRepository = MarketplaceRepository()
    private lateinit var productAdapter: ProductAdapter

    private var allFetchedProducts = mutableListOf<Product>()
    private var displayedProducts = mutableListOf<Product>()

    private var currentCategory = "All"
    private var currentQuery = ""
    private var currentSort = SortMode.NEARBY
    private var isSavedFilterOnly = false

    private val searchDebounceHandler = Handler(Looper.getMainLooper())
    private var searchDebounceRunnable: Runnable? = null
    private var loadJob: Job? = null

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
        setupSwipeRefresh()
        observeRefreshEvents()

        loadMarketplaceData(showLoadingIndicator = true)
    }

    override fun onResume() {
        super.onResume()
        if (AppRefreshManager.isStale(RefreshTarget.MARKETPLACE)) {
            loadMarketplaceData(showLoadingIndicator = false)
        }
    }

    private fun observeRefreshEvents() {
        viewLifecycleOwner.lifecycleScope.launch {
            AppRefreshManager.refreshEvents.collect { target ->
                if (target == RefreshTarget.MARKETPLACE || target == RefreshTarget.ALL) {
                    loadMarketplaceData(showLoadingIndicator = false)
                }
            }
        }
    }

    private fun setupRecyclerView() {
        productAdapter = ProductAdapter(
            products = displayedProducts,
            onProductClick = { product ->
                try {
                    val bundle = Bundle().apply {
                        putString("productId", product.id)
                    }
                    findNavController().navigate(R.id.action_marketplace_to_productDetail, bundle)
                } catch (_: Exception) {
                    openChatWithSeller(product)
                }
            },
            onChatClick = { product ->
                openChatWithSeller(product)
            },
            onWishlistClick = { product ->
                lifecycleScope.launch {
                    val isSaved = marketplaceRepository.toggleSavedListing(requireContext(), product.id)
                    val msg = if (isSaved) "Saved to wishlist ❤️" else "Removed from wishlist"
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()

                    if (isSavedFilterOnly) {
                        applyCurrentFilters()
                    }
                }
            }
        )

        binding.rvProducts.apply {
            layoutManager = GridLayoutManager(requireContext(), 2)
            adapter = productAdapter
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefreshLayout.applyAroundUsTheme()
        binding.swipeRefreshLayout.setOnRefreshListener {
            loadMarketplaceData(showLoadingIndicator = false)
        }
    }

    private fun loadMarketplaceData(showLoadingIndicator: Boolean) {
        if (showLoadingIndicator) {
            binding.pbLoading.visibility = View.VISIBLE
        }

        loadJob?.cancel()
        loadJob = lifecycleScope.launch {
            try {
                val result = marketplaceRepository.fetchListings(
                    context = requireContext(),
                    category = currentCategory,
                    query = currentQuery,
                    sort = currentSort.name,
                    onlySaved = isSavedFilterOnly
                )

                binding.pbLoading.visibility = View.GONE
                binding.swipeRefreshLayout.isRefreshing = false

                when (result) {
                    is NetworkResult.Success -> {
                        allFetchedProducts = result.data.toMutableList()
                        applyCurrentFilters()
                        AppRefreshManager.markRefreshed(RefreshTarget.MARKETPLACE)
                    }
                    is NetworkResult.Error -> {
                        // Use local / cached items if available
                        applyCurrentFilters()
                    }
                }
            } catch (e: Exception) {
                binding.pbLoading.visibility = View.GONE
                binding.swipeRefreshLayout.isRefreshing = false
                applyCurrentFilters()
            }
        }
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
            applyCurrentFilters()
        }

        binding.fabSell.setOnClickListener {
            if (!SupabaseClient.isLoggedIn()) {
                Toast.makeText(requireContext(), "Please sign in to list items for sale.", Toast.LENGTH_SHORT).show()
            }
            try {
                findNavController().navigate(R.id.action_marketplace_to_sellItem)
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Unable to open Sell Item: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
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
                val query = s?.toString()?.trim().orEmpty()
                binding.btnClearSearch.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE

                searchDebounceRunnable?.let { searchDebounceHandler.removeCallbacks(it) }
                searchDebounceRunnable = Runnable {
                    currentQuery = query
                    applyCurrentFilters()
                }
                searchDebounceHandler.postDelayed(searchDebounceRunnable!!, 300)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.btnClearSearch.setOnClickListener {
            binding.etSearchProducts.text?.clear()
            currentQuery = ""
            applyCurrentFilters()
        }
    }

    private fun setupCategoryChips() {
        binding.chipAll.setOnClickListener { selectCategory("All", binding.chipAll) }
        binding.chipFurniture.setOnClickListener { selectCategory("Furniture", binding.chipFurniture) }
        binding.chipVehicles.setOnClickListener { selectCategory("Vehicles", binding.chipVehicles) }
        binding.chipElectronics.setOnClickListener { selectCategory("Electronics", binding.chipElectronics) }
        binding.chipMobilePhones.setOnClickListener { selectCategory("Mobile Phones", binding.chipMobilePhones) }
        binding.chipHomeKitchen.setOnClickListener { selectCategory("Home & Kitchen", binding.chipHomeKitchen) }
        binding.chipFashion.setOnClickListener { selectCategory("Fashion", binding.chipFashion) }
        binding.chipBooks.setOnClickListener { selectCategory("Books", binding.chipBooks) }
        binding.chipSports.setOnClickListener { selectCategory("Sports", binding.chipSports) }
        binding.chipOther.setOnClickListener { selectCategory("Other", binding.chipOther) }
    }

    private fun selectCategory(category: String, activeChip: TextView) {
        currentCategory = category
        updateChipStyles(activeChip)
        applyCurrentFilters()
    }

    private fun updateChipStyles(activeChip: TextView) {
        val allChips = listOf(
            binding.chipAll,
            binding.chipFurniture,
            binding.chipVehicles,
            binding.chipElectronics,
            binding.chipMobilePhones,
            binding.chipHomeKitchen,
            binding.chipFashion,
            binding.chipBooks,
            binding.chipSports,
            binding.chipOther
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
        popup.menu.add(0, 2, 1, "🕒 Newest Listings First")
        popup.menu.add(0, 3, 2, "💰 Price: Low to High")
        popup.menu.add(0, 4, 3, "💎 Price: High to Low")

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> {
                    currentSort = SortMode.NEARBY
                    binding.tvSortLabel.text = "Sort: Nearby ▾"
                }
                2 -> {
                    currentSort = SortMode.NEWEST
                    binding.tvSortLabel.text = "Sort: Newest ▾"
                }
                3 -> {
                    currentSort = SortMode.PRICE_LOW_HIGH
                    binding.tvSortLabel.text = "Sort: ₹ Low-High ▾"
                }
                4 -> {
                    currentSort = SortMode.PRICE_HIGH_LOW
                    binding.tvSortLabel.text = "Sort: ₹ High-Low ▾"
                }
            }
            applyCurrentFilters()
            true
        }
        popup.show()
    }

    private fun applyCurrentFilters() {
        val filtered = marketplaceRepository.filterAndSortListings(
            source = allFetchedProducts,
            category = currentCategory,
            query = currentQuery,
            sort = currentSort.name,
            onlySaved = isSavedFilterOnly
        )

        displayedProducts = filtered.toMutableList()
        productAdapter.updateList(displayedProducts)

        val hasItems = displayedProducts.isNotEmpty()
        binding.rvProducts.visibility = if (hasItems) View.VISIBLE else View.GONE
        binding.layoutEmptyState.visibility = if (hasItems) View.GONE else View.VISIBLE

        binding.tvResultsCount.text = when {
            isSavedFilterOnly -> "Saved Wishlist (${displayedProducts.size} items)"
            currentQuery.isNotBlank() || currentCategory != "All" -> "Showing ${displayedProducts.size} listings nearby"
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
        applyCurrentFilters()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        searchDebounceRunnable?.let { searchDebounceHandler.removeCallbacks(it) }
        loadJob?.cancel()
        _binding = null
    }
}
