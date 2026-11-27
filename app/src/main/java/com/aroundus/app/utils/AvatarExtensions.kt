package com.aroundus.app.utils

import android.net.Uri
import android.util.Base64
import android.widget.ImageView
import com.aroundus.app.R
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.signature.ObjectKey
import java.io.File

/**
 * Resolves the best avatar path/URL to render for a user across devices:
 * - Always prefers valid remote HTTP/HTTPS URLs or Base64 data URIs.
 * - For other users, ignores foreign device-local file/content paths so a cached remote URL or placeholder is used.
 */
fun resolveBestAvatarPath(
    primary: String?,
    cached: String?,
    isCurrentUser: Boolean,
    localFallbackPath: String? = null
): String? {
    val p = primary?.trim()?.takeIf { it.isNotBlank() }
    val c = cached?.trim()?.takeIf { it.isNotBlank() }

    fun isRemoteOrInline(path: String?): Boolean {
        if (path.isNullOrBlank()) return false
        return path.startsWith("http://") ||
            path.startsWith("https://") ||
            path.startsWith("data:image/") ||
            path.startsWith("res:")
    }

    if (isRemoteOrInline(p)) return p
    if (isRemoteOrInline(c)) return c
    if (isCurrentUser) {
        return p ?: c ?: localFallbackPath?.trim()?.takeIf { it.isNotBlank() }
    }
    return null
}

/**
 * Extension function to load user profile photos from Supabase (HTTP/HTTPS, Base64 Data URI),
 * local cache file, content URI, or fallback to placeholder.
 */
fun ImageView.loadUserAvatar(
    avatarPath: String?,
    placeholderRes: Int = R.drawable.shape_avatar_placeholder
) {
    val cleanPath = avatarPath?.trim()
    if (cleanPath.isNullOrBlank()) {
        setImageResource(placeholderRes)
        return
    }

    try {
        when {
            cleanPath.startsWith("http://") || cleanPath.startsWith("https://") -> {
                Glide.with(context)
                    .load(cleanPath)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .placeholder(placeholderRes)
                    .error(placeholderRes)
                    .circleCrop()
                    .into(this)
            }
            cleanPath.startsWith("res:") -> {
                val resId = cleanPath.removePrefix("res:").toIntOrNull()
                if (resId != null && resId != 0) {
                    Glide.with(context)
                        .load(resId)
                        .placeholder(placeholderRes)
                        .error(placeholderRes)
                        .circleCrop()
                        .into(this)
                } else {
                    setImageResource(placeholderRes)
                }
            }
            cleanPath.startsWith("data:image/") || (cleanPath.length > 200 && !cleanPath.startsWith("/") && !cleanPath.startsWith("content:")) -> {
                val base64Data = if (cleanPath.contains(",")) {
                    cleanPath.substringAfter(",")
                } else {
                    cleanPath
                }
                val imageBytes = Base64.decode(base64Data.trim(), Base64.DEFAULT)
                Glide.with(context)
                    .asBitmap()
                    .load(imageBytes)
                    .placeholder(placeholderRes)
                    .error(placeholderRes)
                    .circleCrop()
                    .into(this)
            }
            cleanPath.startsWith("content://") || cleanPath.startsWith("file://") -> {
                Glide.with(context)
                    .load(Uri.parse(cleanPath))
                    .placeholder(placeholderRes)
                    .error(placeholderRes)
                    .circleCrop()
                    .into(this)
            }
            cleanPath.startsWith("/") -> {
                val file = File(cleanPath)
                if (file.exists() && file.length() > 0L) {
                    Glide.with(context)
                        .load(file)
                        .signature(ObjectKey(file.lastModified()))
                        .placeholder(placeholderRes)
                        .error(placeholderRes)
                        .circleCrop()
                        .into(this)
                } else {
                    setImageResource(placeholderRes)
                }
            }
            else -> {
                val localFile = File(context.filesDir, cleanPath)
                if (localFile.exists() && localFile.length() > 0L) {
                    Glide.with(context)
                        .load(localFile)
                        .signature(ObjectKey(localFile.lastModified()))
                        .placeholder(placeholderRes)
                        .error(placeholderRes)
                        .circleCrop()
                        .into(this)
                } else {
                    setImageResource(placeholderRes)
                }
            }
        }
    } catch (_: Exception) {
        setImageResource(placeholderRes)
    }
}

