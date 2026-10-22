package com.aroundus.app.models

import com.aroundus.app.R
import com.google.gson.annotations.SerializedName

/**
 * Supabase Database Entity Models
 * Core entities corresponding to Supabase Postgres database tables.
 */

data class UserEntity(
    @SerializedName(value = "user_id", alternate = ["userId"])
    val userId: String = "",
    @SerializedName("name")
    val name: String = "",
    @SerializedName("email")
    val email: String = "",
    @SerializedName("phone")
    val phone: String? = null,
    @SerializedName("location")
    val location: String? = null,
    @SerializedName("bio")
    val bio: String? = null,
    @SerializedName(value = "profile_photo", alternate = ["profilePhoto", "avatar_url", "profile_image", "avatar", "profile", "image"])
    val profilePhoto: String? = null,
    @SerializedName(value = "isVerified", alternate = ["is_verified"])
    val isVerified: Boolean? = null,
    @SerializedName(value = "created_at", alternate = ["createdAt"])
    val createdAt: String? = null,
    @SerializedName(value = "username_last_changed", alternate = ["usernameLastChanged"])
    val usernameLastChanged: Long? = null
) {
    val profile: String? get() = profilePhoto
}

data class PostEntity(
    @SerializedName(value = "post_id", alternate = ["postId"])
    val postId: String = "",
    @SerializedName(value = "author_id", alternate = ["authorId"])
    val authorId: String = "",
    @SerializedName("type")
    val type: String = "General",
    @SerializedName("text")
    val text: String = "",
    @SerializedName("media")
    val media: String? = null,
    @SerializedName("timestamp")
    val timestamp: String = "",
    @SerializedName("location")
    val location: String? = null
)

data class CommentEntity(
    @SerializedName(value = "comment_id", alternate = ["commentId"])
    val commentId: String = "",
    @SerializedName(value = "post_id", alternate = ["postId"])
    val postId: String = "",
    @SerializedName(value = "user_id", alternate = ["userId"])
    val userId: String = "",
    @SerializedName("text")
    val text: String = "",
    @SerializedName("timestamp")
    val timestamp: String = ""
)

data class PostLikeEntity(
    @SerializedName(value = "like_id", alternate = ["likeId"])
    val likeId: String = "",
    @SerializedName(value = "post_id", alternate = ["postId"])
    val postId: String = "",
    @SerializedName(value = "user_id", alternate = ["userId"])
    val userId: String = "",
    @SerializedName(value = "created_at", alternate = ["createdAt"])
    val createdAt: String? = null
)

data class GroupEntity(
    @SerializedName(value = "group_id", alternate = ["groupId"])
    val groupId: String = "",
    @SerializedName("name")
    val name: String = "",
    @SerializedName("description")
    val description: String? = null,
    @SerializedName("category")
    val category: String? = null,
    @SerializedName("members")
    val members: String? = null, // Stores comma-separated user IDs or legacy count
    @SerializedName(value = "created_at", alternate = ["createdAt"])
    val createdAt: String? = null
) {
    /**
     * Extracts individual member IDs.
     */
    fun getMemberList(): List<String> {
        val raw = members?.trim().orEmpty()
        if (raw.isBlank()) return emptyList()

        // JSON array format: ["usr_1", "usr_2"]
        if (raw.startsWith("[") && raw.endsWith("]")) {
            val inner = raw.substring(1, raw.length - 1).trim()
            if (inner.isBlank()) return emptyList()
            return inner.split(",")
                .map { it.trim().removeSurrounding("\"").removeSurrounding("'") }
                .filter { it.isNotBlank() }
        }

        // Comma-separated format: usr_1,usr_2
        if (raw.contains(",")) {
            return raw.split(",")
                .map { it.trim() }
                .filter { it.isNotBlank() }
        }

        // Single ID or legacy text check (e.g. "1 Member" is not a user ID)
        if (raw.matches(Regex("^\\d+\\s+Members?$", RegexOption.IGNORE_CASE))) {
            return emptyList()
        }

        return listOf(raw)
    }

    /**
     * Checks if a user is a member of this group.
     */
    fun isMember(userId: String?): Boolean {
        if (userId.isNullOrBlank()) return false
        val memberList = getMemberList()
        if (memberList.isNotEmpty()) {
            return memberList.contains(userId)
        }
        val raw = members.orEmpty()
        return raw.contains(userId)
    }

    /**
     * Returns the integer count of members.
     */
    fun getMemberCount(): Int {
        val list = getMemberList()
        if (list.isNotEmpty()) return list.size

        val raw = members?.trim().orEmpty()
        val digits = raw.filter { it.isDigit() }
        val parsed = digits.toIntOrNull()
        return if (parsed != null && parsed > 0) parsed else 1
    }

    /**
     * Formats the member text for UI display.
     */
    fun formatMembersDisplay(): String {
        val count = getMemberCount()
        return if (count == 1) "1 Member" else "$count Members"
    }
}

data class MessageEntity(
    @SerializedName(value = "message_id", alternate = ["messageId"])
    val messageId: String = "",
    @SerializedName(value = "sender_id", alternate = ["senderId"])
    val senderId: String = "",
    @SerializedName(value = "receiver_id", alternate = ["receiverId"])
    val receiverId: String? = null,
    @SerializedName(value = "group_id", alternate = ["groupId"])
    val groupId: String? = null,
    @SerializedName("text")
    val text: String = "",
    @SerializedName("timestamp")
    val timestamp: String = ""
)

data class EventEntity(
    @SerializedName(value = "event_id", alternate = ["eventId"])
    val eventId: String = "",
    @SerializedName(value = "creator_id", alternate = ["creatorId"])
    val creatorId: String = "",
    @SerializedName("title")
    val title: String = "",
    @SerializedName("date")
    val date: String = "",
    @SerializedName("location")
    val location: String? = null,
    @SerializedName("attendees")
    val attendees: String? = null // Can store attendee count or list of user IDs
)

/**
 * Supabase Auth AuthResponse Model
 */
data class AuthResponse(
    @SerializedName("access_token")
    val accessToken: String? = null,
    @SerializedName("token_type")
    val tokenType: String? = null,
    @SerializedName("user")
    val user: AuthUser? = null,
    @SerializedName("error")
    val error: String? = null,
    @SerializedName("error_description")
    val errorDescription: String? = null
)

data class AuthUser(
    @SerializedName("id")
    val id: String = "",
    @SerializedName("email")
    val email: String? = null
)

data class SignUpRequest(
    @SerializedName("email")
    val email: String,
    @SerializedName("password")
    val password: String,
    @SerializedName("data")
    val data: Map<String, String>? = null
)

data class LoginRequest(
    @SerializedName("email")
    val email: String,
    @SerializedName("password")
    val password: String
)

data class OtpRequest(
    @SerializedName("email")
    val email: String,
    @SerializedName("create_user")
    val createUser: Boolean = true
)

data class FollowEntity(
    @SerializedName(value = "follower_id", alternate = ["followerId"])
    val followerId: String = "",
    @SerializedName(value = "following_id", alternate = ["followingId"])
    val followingId: String = ""
)

data class ListingEntity(
    @SerializedName(value = "listing_id", alternate = ["listingId", "id"])
    val listingId: String = "",
    @SerializedName(value = "seller_id", alternate = ["sellerId"])
    val sellerId: String = "",
    @SerializedName("title")
    val title: String = "",
    @SerializedName("description")
    val description: String? = null,
    @SerializedName("price")
    val price: Double = 0.0,
    @SerializedName("category")
    val category: String = "Other",
    @SerializedName("condition")
    val condition: String = "Good",
    @SerializedName("brand")
    val brand: String? = null,
    @SerializedName("location")
    val location: String? = "Bhujpur, Kutch",
    @SerializedName("locality")
    val locality: String? = "Bhujpur Village",
    @SerializedName("latitude")
    val latitude: Double? = null,
    @SerializedName("longitude")
    val longitude: Double? = null,
    @SerializedName(value = "image_urls", alternate = ["imageUrls", "media", "images"])
    val imageUrls: String? = null,
    @SerializedName(value = "listing_status", alternate = ["listingStatus", "status"])
    val listingStatus: String = "active",
    @SerializedName(value = "is_negotiable", alternate = ["isNegotiable", "negotiable"])
    val isNegotiable: Boolean = false,
    @SerializedName(value = "created_at", alternate = ["createdAt"])
    val createdAt: String? = null,
    @SerializedName(value = "updated_at", alternate = ["updatedAt"])
    val updatedAt: String? = null
) {
    fun getImageList(): List<String> {
        val raw = imageUrls?.trim().orEmpty()
        if (raw.isBlank()) return emptyList()
        if (raw.startsWith("[") && raw.endsWith("]")) {
            val inner = raw.substring(1, raw.length - 1).trim()
            if (inner.isBlank()) return emptyList()
            return inner.split(",")
                .map { it.trim().removeSurrounding("\"").removeSurrounding("'") }
                .filter { it.isNotBlank() }
        }
        if (raw.contains(",")) {
            return raw.split(",").map { it.trim() }.filter { it.isNotBlank() }
        }
        return listOf(raw)
    }

    val isVideo: Boolean
        get() {
            val first = getImageList().firstOrNull() ?: imageUrls ?: return false
            val clean = first.substringBefore("?").lowercase()
            return clean.endsWith(".mp4") || clean.endsWith(".webm") || clean.endsWith(".mov") || clean.contains("/video")
        }
}

data class SavedListingEntity(
    @SerializedName("id")
    val id: String = "",
    @SerializedName(value = "user_id", alternate = ["userId"])
    val userId: String = "",
    @SerializedName(value = "listing_id", alternate = ["listingId"])
    val listingId: String = "",
    @SerializedName(value = "created_at", alternate = ["createdAt"])
    val createdAt: String? = null
)

// Extension functions to convert Database Entities to UI Models

fun formatTimeAgo(timestampStr: String?): String {
    if (timestampStr.isNullOrBlank()) return "Just now"
    try {
        val timeMs = timestampStr.toLongOrNull() ?: return "Just now"
        val nowMs = System.currentTimeMillis()
        val diffMs = nowMs - timeMs

        if (diffMs < 0) return "Just now"

        val diffSec = diffMs / 1000
        val diffMin = diffSec / 60
        val diffHours = diffMin / 60
        val diffDays = diffHours / 24

        return when {
            diffMin < 1 -> "Just now"
            diffMin < 60 -> "${diffMin}m ago"
            diffHours < 24 -> "${diffHours}h ago"
            diffDays < 7 -> "${diffDays}d ago"
            else -> "${diffDays / 7}w ago"
        }
    } catch (_: Exception) {
        return "Just now"
    }
}

fun PostEntity.toUIModel(
    userName: String = "Neighbor",
    userAvatarPath: String? = null,
    isVerified: Boolean = false,
    likesCount: Int = 0,
    commentsCount: Int = 0,
    isLiked: Boolean = false
): Post {
    return Post(
        id = postId ?: "",
        userName = if (userName.isNullOrBlank()) "Neighbor" else userName,
        userAvatar = R.drawable.shape_avatar_placeholder,
        neighborhood = if (location.isNullOrBlank()) "Nearby" else location,
        timeAgo = formatTimeAgo(timestamp),
        category = if (type.isNullOrBlank()) "General" else type,
        text = text ?: "",
        imageRes = if (!media.isNullOrBlank()) R.drawable.bg_image_placeholder else null,
        likesCount = likesCount,
        commentsCount = commentsCount,
        isLiked = isLiked,
        userAvatarPath = userAvatarPath,
        authorId = authorId ?: "",
        mediaUrl = media,
        isVerified = isVerified
    )
}

fun CommentEntity.toUIModel(userName: String = "Neighbor", userAvatarPath: String? = null): Comment {
    return Comment(
        id = commentId ?: "",
        userName = if (userName.isNullOrBlank()) "Neighbor" else userName,
        avatarRes = R.drawable.shape_avatar_placeholder,
        text = text ?: "",
        time = formatTimeAgo(timestamp),
        likeCount = 0,
        avatarPath = userAvatarPath,
        userId = userId ?: ""
    )
}


fun resolveGroupCoverRes(category: String?, name: String?): Int {
    val cat = category?.lowercase() ?: ""
    val n = name?.lowercase() ?: ""
    return when {
        cat.contains("buy") || cat.contains("sell") || n.contains("buy") || n.contains("market") ->
            R.drawable.img_group_bhujpur_buysell
        cat.contains("help") || cat.contains("support") || n.contains("help") ->
            R.drawable.img_group_help_each_other
        cat.contains("activity") || cat.contains("activities") || cat.contains("sport") || n.contains("yoga") ->
            R.drawable.img_group_yoga_wellness
        n.contains("pet") || n.contains("animal") ->
            R.drawable.img_group_pet_lovers
        n.contains("student") || n.contains("study") || cat.contains("education") ->
            R.drawable.img_group_students_hub
        n.contains("green") || n.contains("clean") ->
            R.drawable.img_group_clean_green
        else ->
            R.drawable.img_group_bhujpur_residents
    }
}

fun GroupEntity.toUIModel(currentUserId: String? = null): Group {
    val isJoined = if (currentUserId.isNullOrBlank()) false else isMember(currentUserId)
    val displayMembers = if (getMemberList().isNotEmpty()) formatMembersDisplay() else (members ?: "1 Member")
    return Group(
        id = groupId,
        name = name,
        description = description ?: "",
        coverRes = resolveGroupCoverRes(category, name),
        membersText = displayMembers,
        category = category ?: "Local",
        isJoined = isJoined
    )
}

fun EventEntity.toUIModel(): Event {
    return Event(
        id = eventId,
        title = title,
        month = "TODAY",
        day = "1",
        time = date,
        location = location ?: "Bhujpur, Kutch",
        attendeesCount = 1,
        isFree = true,
        imageRes = R.drawable.bg_gradient_onboarding
    )
}

fun MessageEntity.toUIModel(currentUserId: String?): Message {
    return Message(
        text = text,
        isSent = senderId == currentUserId,
        time = "Now"
    )
}

fun ListingEntity.toUIModel(
    sellerName: String = "Neighbor",
    sellerAvatarUrl: String? = null,
    isVerifiedSeller: Boolean = true,
    isSaved: Boolean = false
): Product {
    val imgList = getImageList()
    val firstImg = imgList.firstOrNull()
    val formattedPrice = if (price <= 0.0) "Free" else "₹${String.format(java.util.Locale.US, "%,.0f", price)}"
    val locText = if (!locality.isNullOrBlank()) locality else (location ?: "Bhujpur Village")

    return Product(
        id = listingId,
        name = title,
        price = formattedPrice,
        imageRes = R.drawable.bg_image_placeholder,
        location = locText,
        timeAgo = formatTimeAgo(createdAt),
        condition = if (condition.isBlank()) "Good" else condition,
        isSaved = isSaved,
        category = category,
        sellerName = sellerName,
        sellerAvatarRes = R.drawable.shape_avatar_placeholder,
        sellerId = sellerId,
        isVerifiedSeller = isVerifiedSeller,
        description = description ?: "",
        imageUrl = firstImg,
        imageUrls = imgList,
        sellerAvatarUrl = sellerAvatarUrl,
        brand = brand,
        locality = locText,
        priceAmount = price,
        isNegotiable = isNegotiable,
        listingStatus = listingStatus,
        createdAtMs = try {
            if (createdAt?.toLongOrNull() != null) createdAt.toLong()
            else {
                val parser = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
                parser.timeZone = java.util.TimeZone.getTimeZone("UTC")
                parser.parse(createdAt?.substringBefore(".") ?: "")?.time ?: System.currentTimeMillis()
            }
        } catch (_: Exception) { System.currentTimeMillis() },
        latitude = latitude,
        longitude = longitude
    )
}
