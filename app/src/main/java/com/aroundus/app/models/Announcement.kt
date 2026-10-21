package com.aroundus.app.models

data class Announcement(
    val id: String = "",
    val authorName: String = "",
    val authorAvatarRes: Int? = null,
    val authorAvatarUrl: String? = null,
    val isVerified: Boolean = false,
    val location: String = "Bhujpur, Kutch",
    val timeAgo: String = "Just now",
    val title: String = "",
    val category: String = "Community", // "Community", "Alerts", "Events"
    val content: String = "",
    val eventDate: String? = null,
    val eventTime: String? = null,
    val eventVenue: String? = null,
    val eventAddress: String? = null,
    val imageRes: Int? = null,
    val imageUrl: String? = null,
    var likesCount: Int = 0,
    var commentsCount: Int = 0,
    var isLiked: Boolean = false,
    var isSaved: Boolean = false,
    val authorId: String? = null
)
