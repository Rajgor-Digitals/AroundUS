package com.aroundus.app.models

data class Post(
    val id: String = "",
    val userName: String = "Neighbor",
    val userAvatar: Int = 0,
    val neighborhood: String = "Bhujpur, Kutch",
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
    val mediaUrl: String? = null
)

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
    var isSaved: Boolean = false
)

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
    var isAttending: Boolean = false
)

data class ChatItem(
    val id: String,
    val userName: String,
    val avatarRes: Int,
    val lastMessage: String,
    val time: String,
    val unreadCount: Int,
    val isOnline: Boolean
)

data class Message(
    val text: String,
    val isSent: Boolean,
    val time: String
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
