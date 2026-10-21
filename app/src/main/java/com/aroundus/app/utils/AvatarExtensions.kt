package com.aroundus.app.utils

import android.net.Uri
import android.util.Base64
import android.widget.ImageView
import com.aroundus.app.R
import com.bumptech.glide.Glide
import com.bumptech.glide.signature.ObjectKey
import java.io.File

/**
 * Extension function to load user profile photos from Supabase (HTTP/HTTPS, Base64 Data URI),
 * local cache file, content URI, or fallback to placeholder.
 */
fun ImageView.loadUserAvatar(
    avatarPath: String?,
    placeholderRes: Int = R.drawable.shape_avatar_placeholder
) {
    if (avatarPath.isNullOrBlank()) {
        setImageResource(placeholderRes)
        return
    }

    try {
        when {
            avatarPath.startsWith("http://") || avatarPath.startsWith("https://") -> {
                Glide.with(context)
                    .load(avatarPath)
                    .placeholder(placeholderRes)
                    .error(placeholderRes)
                    .circleCrop()
                    .into(this)
            }
            avatarPath.startsWith("data:image/") || (avatarPath.length > 200 && !avatarPath.startsWith("/") && !avatarPath.startsWith("content:")) -> {
                val base64Data = if (avatarPath.contains(",")) {
                    avatarPath.substringAfter(",")
                } else {
                    avatarPath
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
            avatarPath.startsWith("content://") || avatarPath.startsWith("file://") -> {
                Glide.with(context)
                    .load(Uri.parse(avatarPath))
                    .placeholder(placeholderRes)
                    .error(placeholderRes)
                    .circleCrop()
                    .into(this)
            }
            avatarPath.startsWith("/") -> {
                val file = File(avatarPath)
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
                val localFile = File(context.filesDir, avatarPath)
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

