package com.aroundus.app.models

data class MyCommentItem(
    val commentId: String = "",
    val postId: String = "",
    val commentText: String = "",
    val timestamp: String = "",
    val timeAgo: String = "Just now",
    val postTitle: String = "Post",
    val postMediaUrl: String? = null,
    val postCategory: String? = null,
    val postAuthorName: String? = null,
    val postAuthorAvatarUrl: String? = null
)
