package com.aroundus.app.models

data class Post(
    val id: String = "",
    val userName: String = "Neighbor",
    val userAvatar: Int = 0,
    val neighborhood: String = "Nearby",
    val timeAgo: String = "Just now",
    val category: String = "General",
    val text: String = "",
    val imageRes: Int? = null,
    val likesCount: Int = 0,
    val commentsCount: Int = 0,
    var isLiked: Boolean = false,
    var isSaved: Boolean = false,
    val userAvatarPath: String? = null,
    val authorId: String? = null,
    val mediaUrl: String? = null,
    val isVerified: Boolean = false
) {
    val isVideo: Boolean
        get() = mediaUrl?.let { url ->
            val clean = url.substringBefore("?").lowercase()
            clean.endsWith(".mp4") || clean.endsWith(".webm") || clean.endsWith(".mkv") ||
            clean.endsWith(".mov") || clean.endsWith(".3gp") || clean.contains("/video")
        } ?: false
}

data class Story(
    val id: String,
    val userName: String,
    val avatarRes: Int,
    val isAddStory: Boolean = false
)

data class Product(
    val id: String,
    val name: String,
    val price: String,
    val imageRes: Int,
    val location: String,
    val timeAgo: String,
    val condition: String,
    var isSaved: Boolean = false,
    val category: String = "All",
    val sellerName: String = "Neighbor",
    val sellerAvatarRes: Int = 0,
    val sellerId: String = "",
    val isVerifiedSeller: Boolean = true,
    val description: String = "",
    val imageUrl: String? = null,
    val imageUrls: List<String> = emptyList(),
    val sellerAvatarUrl: String? = null,
    val brand: String? = null,
    val locality: String? = null,
    val priceAmount: Double = 0.0,
    val isNegotiable: Boolean = false,
    val listingStatus: String = "active",
    val createdAtMs: Long = 0L,
    val latitude: Double? = null,
    val longitude: Double? = null
) {
    val isVideo: Boolean
        get() {
            val url = imageUrl ?: imageUrls.firstOrNull() ?: return false
            val clean = url.substringBefore("?").lowercase()
            return clean.endsWith(".mp4") || clean.endsWith(".webm") || clean.endsWith(".mov") || clean.contains("/video")
        }
}

data class Event(
    val id: String,
    val title: String,
    val month: String,
    val day: String,
    val time: String,
    val location: String,
    val attendeesCount: Int,
    val isFree: Boolean,
    val imageRes: Int,
    var isAttending: Boolean = false,
    val creatorId: String? = null,
    val createdAt: String? = null
)

data class ChatItem(
    val id: String,
    val userName: String,
    val avatarRes: Int = 0,
    val lastMessage: String = "",
    val time: String = "",
    val unreadCount: Int = 0,
    val isOnline: Boolean = false,
    val avatarUrl: String? = null
)

data class ProductCardAttachment(
    val id: String = "",
    val title: String = "",
    val price: String = "",
    val imageUrl: String? = null,
    val imageRes: Int = 0
)

data class Message(
    val text: String,
    val isSent: Boolean,
    val time: String,
    val id: String = "",
    var isSeen: Boolean = false,
    var seenTime: String? = null,
    val timestampMs: Long = 0L,
    val productCard: ProductCardAttachment? = null
)

data class Notification(
    val id: String,
    val type: String, // "like", "comment", "follow", "event"
    val text: String,
    val avatarRes: Int,
    val time: String,
    val isUnread: Boolean = true
)

data class Group(
    val id: String,
    val name: String,
    val description: String,
    val coverRes: Int,
    val membersText: String,
    val category: String,
    val isAdmin: Boolean = false,
    val iconBadgeRes: Int = 0,
    var isJoined: Boolean = false,
    var isNotificationOn: Boolean = true
)

data class GroupCategory(
    val id: String,
    val name: String,
    val iconRes: Int,
    var isSelected: Boolean = false
)

data class OnboardingPage(
    val titleFull: String,
    val titleHighlight: String,
    val subtitleFull: String? = null,
    val subtitleHighlight: String? = null,
    val description: String,
    val illustrationRes: Int,
    val headerLogoRes: Int? = null,
    val isFirstPage: Boolean = false
)

data class Comment(
    val id: String,
    val userName: String,
    val avatarRes: Int,
    val text: String,
    val time: String,
    val likeCount: Int,
    val avatarPath: String? = null,
    val userId: String = ""
)
