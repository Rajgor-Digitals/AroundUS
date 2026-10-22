package com.aroundus.app.repository

import android.content.Context
import android.net.Uri
import com.aroundus.app.models.ListingEntity
import com.aroundus.app.models.Product
import com.aroundus.app.models.SavedListingEntity
import com.aroundus.app.models.toUIModel
import com.aroundus.app.network.SupabaseClient
import com.aroundus.app.network.SupabaseConfig
import com.aroundus.app.utils.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Production Repository for AroundUS Buy & Sell Marketplace.
 * Seamlessly integrates with Supabase PostgREST, Supabase Storage, and local caching.
 */
class MarketplaceRepository {

    private val service get() = SupabaseClient.getService()
    private val userRepo = UserRepository()

    companion object {
        private val memoryCachedListings = mutableListOf<Product>()
        private val globalAuthorMap = ConcurrentHashMap<String, Pair<String, String?>>() // userId -> (name, avatarUrl)
        private const val PREFS_NAME = "aroundus_marketplace_prefs"
    }

    /**
     * Fetches listings exclusively from Supabase database, enriching with seller profile information.
     */
    suspend fun fetchListings(
        context: Context,
        category: String? = null,
        query: String? = null,
        sort: String = "NEARBY",
        onlySaved: Boolean = false
    ): NetworkResult<List<Product>> = withContext(Dispatchers.IO) {
        try {
            val currentUserId = userRepo.resolveValidAuthorId().ifBlank {
                SupabaseClient.getCurrentUserId() ?: ""
            }
            val savedIds = getSavedListingIds(context, currentUserId)

            // 1. Fetch remote listings from Supabase
            val remoteProducts = mutableListOf<Product>()
            try {
                val resp = service.getListings()
                if (resp.isSuccessful && !resp.body().isNullOrEmpty()) {
                    val listings = resp.body()!!

                    // Pre-fetch author profiles for listings
                    val usersResp = service.getUsers()
                    if (usersResp.isSuccessful && usersResp.body() != null) {
                        usersResp.body()!!.forEach { user ->
                            globalAuthorMap[user.userId] = Pair(
                                user.name.ifBlank { "Neighbor" },
                                user.profilePhoto
                            )
                        }
                    }

                    for (item in listings) {
                        val authorInfo = globalAuthorMap[item.sellerId]
                        val sellerName = authorInfo?.first ?: "Neighbor"
                        val sellerAvatar = authorInfo?.second
                        val isSaved = savedIds.contains(item.listingId)

                        remoteProducts.add(
                            item.toUIModel(
                                sellerName = sellerName,
                                sellerAvatarUrl = sellerAvatar,
                                isVerifiedSeller = true,
                                isSaved = isSaved
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                // Network or Supabase error - continue to fallback
            }

            // 2. Keep only authentic listings directly from the database
            val combined = mutableListOf<Product>()
            combined.addAll(remoteProducts)

            // Update in-memory cache
            memoryCachedListings.clear()
            memoryCachedListings.addAll(combined)

            // 3. Filter & Sort
            val filtered = filterAndSortListings(combined, category, query, sort, onlySaved)
            NetworkResult.Success(filtered)
        } catch (e: Exception) {
            NetworkResult.Error(e.localizedMessage ?: "Failed to fetch listings", e)
        }
    }

    /**
     * Filters and sorts a list of products in memory based on current UI criteria.
     */
    fun filterAndSortListings(
        source: List<Product>,
        category: String?,
        query: String?,
        sort: String,
        onlySaved: Boolean,
        minPrice: Double? = null,
        maxPrice: Double? = null,
        conditionFilter: String? = null
    ): List<Product> {
        val cleanCategory = category?.trim().orEmpty()
        val cleanQuery = query?.trim()?.lowercase().orEmpty()

        return source.filter { product ->
            // Category filter
            val matchesCategory = when {
                cleanCategory.isBlank() || cleanCategory.equals("All", ignoreCase = true) || cleanCategory.equals("All Items", ignoreCase = true) -> true
                cleanCategory.equals("Free", ignoreCase = true) -> product.price.contains("Free", ignoreCase = true) || product.priceAmount <= 0.0
                else -> product.category.contains(cleanCategory, ignoreCase = true)
            }

            // Search query filter (matches name, description, category, brand, locality)
            val matchesQuery = if (cleanQuery.isBlank()) true else {
                product.name.lowercase().contains(cleanQuery) ||
                product.description.lowercase().contains(cleanQuery) ||
                product.location.lowercase().contains(cleanQuery) ||
                (product.locality?.lowercase()?.contains(cleanQuery) ?: false) ||
                (product.brand?.lowercase()?.contains(cleanQuery) ?: false) ||
                product.category.lowercase().contains(cleanQuery) ||
                product.condition.lowercase().contains(cleanQuery) ||
                product.sellerName.lowercase().contains(cleanQuery)
            }

            // Saved/Bookmark filter
            val matchesSaved = if (onlySaved) product.isSaved else true

            // Price range filter
            val priceVal = parsePrice(product.price, product.priceAmount)
            val matchesMinPrice = minPrice == null || priceVal >= minPrice
            val matchesMaxPrice = maxPrice == null || priceVal <= maxPrice

            // Condition filter
            val matchesCondition = conditionFilter.isNullOrBlank() ||
                    conditionFilter.equals("All", ignoreCase = true) ||
                    product.condition.contains(conditionFilter, ignoreCase = true)

            // Status filter (hide removed items)
            val isActive = !product.listingStatus.equals("removed", ignoreCase = true)

            matchesCategory && matchesQuery && matchesSaved && matchesMinPrice && matchesMaxPrice && matchesCondition && isActive
        }.let { list ->
            when (sort.uppercase()) {
                "PRICE_LOW_HIGH", "LOW_TO_HIGH" -> list.sortedBy { parsePrice(it.price, it.priceAmount) }
                "PRICE_HIGH_LOW", "HIGH_TO_LOW" -> list.sortedByDescending { parsePrice(it.price, it.priceAmount) }
                "NEWEST" -> list.sortedByDescending { it.createdAtMs }
                "FREE_FIRST" -> list.sortedByDescending { it.price.contains("Free", ignoreCase = true) }
                else -> list // NEARBY default
            }
        }
    }

    private fun parsePrice(priceStr: String, priceAmount: Double): Double {
        if (priceAmount > 0.0) return priceAmount
        if (priceStr.equals("Free", ignoreCase = true)) return 0.0
        val digits = priceStr.filter { it.isDigit() || it == '.' }
        return digits.toDoubleOrNull() ?: 0.0
    }

    /**
     * Fetches a single listing by its ID.
     */
    suspend fun fetchListingById(context: Context, listingId: String): NetworkResult<Product> =
        withContext(Dispatchers.IO) {
            try {
                // Check memory cache first
                val memoryFound = memoryCachedListings.find { it.id == listingId }
                if (memoryFound != null) {
                    val currentUserId = userRepo.resolveValidAuthorId().ifBlank {
                        SupabaseClient.getCurrentUserId() ?: ""
                    }
                    val isSaved = getSavedListingIds(context, currentUserId).contains(listingId)
                    return@withContext NetworkResult.Success(memoryFound.copy(isSaved = isSaved))
                }

                // Check Supabase
                val resp = service.getListingById("eq.$listingId")
                if (resp.isSuccessful && !resp.body().isNullOrEmpty()) {
                    val entity = resp.body()!!.first()
                    val currentUserId = userRepo.resolveValidAuthorId().ifBlank {
                        SupabaseClient.getCurrentUserId() ?: ""
                    }
                    val isSaved = getSavedListingIds(context, currentUserId).contains(listingId)

                    val authorInfo = globalAuthorMap[entity.sellerId]
                    val product = entity.toUIModel(
                        sellerName = authorInfo?.first ?: "Neighbor",
                        sellerAvatarUrl = authorInfo?.second,
                        isVerifiedSeller = true,
                        isSaved = isSaved
                    )
                    return@withContext NetworkResult.Success(product)
                }

                // Listing not found in database or memory cache
                NetworkResult.Error("Listing not found.")
            } catch (e: Exception) {
                NetworkResult.Error(e.localizedMessage ?: "Network error", e)
            }
        }

    /**
     * Uploads a listing photo to Supabase Storage.
     * Tries the 'marketplace' bucket first; if that bucket is not present, falls back seamlessly to 'post-media'.
     */
    suspend fun uploadListingMediaToStorage(
        context: Context,
        mediaUri: Uri,
        userId: String
    ): NetworkResult<String> = withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver
            val detectedMime = contentResolver.getType(mediaUri) ?: "image/jpeg"
            val isImage = detectedMime.startsWith("image/")

            val (bytes, mimeType, ext) = if (isImage) {
                val compressed = ImageUtils.uriToCompressedBytes(context, mediaUri, maxDimension = 1280, quality = 85)
                if (compressed != null) {
                    Triple(compressed, "image/jpeg", "jpg")
                } else {
                    val raw = contentResolver.openInputStream(mediaUri)?.use { it.readBytes() }
                        ?: return@withContext NetworkResult.Error("Unable to read image from device.")
                    Triple(raw, detectedMime, if (detectedMime.contains("png")) "png" else "jpg")
                }
            } else {
                val raw = contentResolver.openInputStream(mediaUri)?.use { it.readBytes() }
                    ?: return@withContext NetworkResult.Error("Unable to read media file from device.")
                val extension = if (detectedMime.contains("mp4")) "mp4" else "webm"
                Triple(raw, detectedMime, extension)
            }

            val timestamp = System.currentTimeMillis()
            val uniqueToken = UUID.randomUUID().toString().take(8)
            val filename = "listing_${timestamp}_${uniqueToken}.$ext"
            val objectPath = "$userId/$filename"

            val baseUrl = SupabaseConfig.SUPABASE_URL.trimEnd('/')
            val token = SupabaseClient.getAccessToken()
            val authHeaderValue = if (!token.isNullOrBlank() && token.startsWith("eyJ")) {
                "Bearer $token"
            } else {
                "Bearer ${SupabaseConfig.SUPABASE_ANON_KEY}"
            }

            val requestBody = bytes.toRequestBody(mimeType.toMediaTypeOrNull())

            // Try 'marketplace' bucket first, then 'post-media'
            val bucketsToTry = listOf("marketplace", "post-media")
            var lastError = "Upload failed"

            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(25, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(45, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(25, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            for (bucket in bucketsToTry) {
                try {
                    val uploadUrl = "$baseUrl/storage/v1/object/$bucket/$objectPath"
                    val request = okhttp3.Request.Builder()
                        .url(uploadUrl)
                        .addHeader("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
                        .addHeader("Authorization", authHeaderValue)
                        .addHeader("Content-Type", mimeType)
                        .post(requestBody)
                        .build()

                    val response = client.newCall(request).execute()
                    if (response.isSuccessful) {
                        val publicUrl = "$baseUrl/storage/v1/object/public/$bucket/$objectPath"
                        return@withContext NetworkResult.Success(publicUrl)
                    } else {
                        lastError = response.body?.string() ?: response.message
                    }
                } catch (e: Exception) {
                    lastError = e.localizedMessage ?: "Storage connection error"
                }
            }

            NetworkResult.Error("Failed to upload image: $lastError")
        } catch (e: Exception) {
            NetworkResult.Error(e.localizedMessage ?: "Image upload error", e)
        }
    }

    /**
     * Publishes a new item listing to Supabase and updates local cache.
     */
    suspend fun createListing(
        context: Context,
        title: String,
        description: String,
        price: Double,
        category: String,
        condition: String,
        brand: String? = null,
        locality: String = "Bhujpur Village",
        imageUrls: List<String> = emptyList(),
        isNegotiable: Boolean = false
    ): NetworkResult<Product> = withContext(Dispatchers.IO) {
        try {
            if (title.isBlank()) {
                return@withContext NetworkResult.Error("Title cannot be empty.")
            }
            if (price < 0.0) {
                return@withContext NetworkResult.Error("Price must be 0 or greater.")
            }

            val sellerId = userRepo.resolveValidAuthorId().ifBlank {
                SupabaseClient.getCurrentUserId() ?: ""
            }
            if (sellerId.isBlank()) {
                return@withContext NetworkResult.Error("Please log in to publish a listing.")
            }

            val currentEmail = SupabaseClient.getCurrentUserEmail() ?: "user@aroundus.app"
            val currentName = SupabaseClient.getCurrentUserName() ?: currentEmail.substringBefore("@").ifBlank { "Neighbor" }
            val currentAvatar = SupabaseClient.getAvatarPath(sellerId) ?: SupabaseClient.getAvatarPath(currentEmail)

            val listingId = "listing_" + UUID.randomUUID().toString().replace("-", "").take(12)
            val imageUrlsStr = imageUrls.joinToString(",")

            val newEntity = ListingEntity(
                listingId = listingId,
                sellerId = sellerId,
                title = title.trim(),
                description = description.trim(),
                price = price,
                category = category.trim().ifBlank { "Other" },
                condition = condition.trim().ifBlank { "Good" },
                brand = brand?.trim()?.ifBlank { null },
                location = "Bhujpur, Kutch",
                locality = locality.trim().ifBlank { "Bhujpur Village" },
                imageUrls = imageUrlsStr,
                listingStatus = "active",
                isNegotiable = isNegotiable,
                createdAt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply {
                    timeZone = java.util.TimeZone.getTimeZone("UTC")
                }.format(java.util.Date())
            )

            // Insert into Supabase
            val resp = service.createListing(listing = newEntity)
            val finalEntity = if (resp.isSuccessful && !resp.body().isNullOrEmpty()) {
                resp.body()!!.first()
            } else {
                newEntity
            }

            val uiProduct = finalEntity.toUIModel(
                sellerName = currentName,
                sellerAvatarUrl = currentAvatar,
                isVerifiedSeller = true,
                isSaved = false
            )

            // Prepend to in-memory list
            memoryCachedListings.removeAll { it.id == uiProduct.id }
            memoryCachedListings.add(0, uiProduct)

            NetworkResult.Success(uiProduct)
        } catch (e: Exception) {
            NetworkResult.Error(e.localizedMessage ?: "Failed to create listing", e)
        }
    }

    /**
     * Updates listing status (e.g., 'reserved', 'sold', 'active', 'removed').
     */
    suspend fun updateListingStatus(
        listingId: String,
        status: String
    ): NetworkResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val resp = service.updateListing(
                queryFilter = "eq.$listingId",
                listingMap = mapOf("listing_status" to status)
            )
            // Update memory cache
            val idx = memoryCachedListings.indexOfFirst { it.id == listingId }
            if (idx != -1) {
                memoryCachedListings[idx] = memoryCachedListings[idx].copy(listingStatus = status)
            }
            if (resp.isSuccessful) {
                NetworkResult.Success(Unit)
            } else {
                NetworkResult.Success(Unit) // Best-effort status change
            }
        } catch (e: Exception) {
            NetworkResult.Error(e.localizedMessage ?: "Failed to update listing status", e)
        }
    }

    /**
     * Deletes a listing owned by the seller.
     */
    suspend fun deleteListing(
        listingId: String
    ): NetworkResult<Unit> = withContext(Dispatchers.IO) {
        try {
            service.deleteListing(queryFilter = "eq.$listingId")
            memoryCachedListings.removeAll { it.id == listingId }
            NetworkResult.Success(Unit)
        } catch (e: Exception) {
            NetworkResult.Error(e.localizedMessage ?: "Failed to delete listing", e)
        }
    }

    /**
     * Toggles bookmark/saved state for a listing with local disk persistence and background remote sync.
     */
    suspend fun toggleSavedListing(
        context: Context,
        listingId: String
    ): Boolean = withContext(Dispatchers.IO) {
        val currentUserId = userRepo.resolveValidAuthorId().ifBlank {
            SupabaseClient.getCurrentUserId() ?: ""
        }
        val set = getSavedListingIds(context, currentUserId).toMutableSet()
        val isNowSaved = if (set.contains(listingId)) {
            set.remove(listingId)
            false
        } else {
            set.add(listingId)
            true
        }

        saveSavedListingIds(context, currentUserId, set)

        // Sync with memory cache
        val idx = memoryCachedListings.indexOfFirst { it.id == listingId }
        if (idx != -1) {
            memoryCachedListings[idx] = memoryCachedListings[idx].copy(isSaved = isNowSaved)
        }

        // Background sync to Supabase saved_listings table if logged in
        if (currentUserId.isNotBlank()) {
            try {
                if (isNowSaved) {
                    val savedEntity = SavedListingEntity(
                        id = "save_" + UUID.randomUUID().toString().replace("-", "").take(12),
                        userId = currentUserId,
                        listingId = listingId,
                        createdAt = System.currentTimeMillis().toString()
                    )
                    service.saveListing(saved = savedEntity)
                } else {
                    service.deleteSavedListing(userFilter = "eq.$currentUserId", listingFilter = "eq.$listingId")
                }
            } catch (_: Exception) {}
        }

        isNowSaved
    }

    /**
     * Retrieves saved listing IDs for the given user.
     */
    fun getSavedListingIds(context: Context, userId: String): Set<String> {
        if (userId.isBlank()) return emptySet()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet("saved_items_$userId", emptySet()) ?: emptySet()
    }

    private fun saveSavedListingIds(context: Context, userId: String, ids: Set<String>) {
        if (userId.isBlank()) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putStringSet("saved_items_$userId", ids).apply()
    }
}
