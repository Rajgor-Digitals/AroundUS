package com.aroundus.app.utils

import android.net.Uri
import android.util.Base64
import android.view.View
import android.widget.ImageView
import com.aroundus.app.R
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import java.io.File

/**
 * Universal image loading extension for posts across all devices.
 * Seamlessly handles:
 * 1. Remote Supabase Storage public HTTP/HTTPS URLs with disk caching.
 * 2. Base64 Data URIs (legacy fallback).
 * 3. Local content/file URIs (on the device that created them).
 * 4. Graceful handling of inaccessible remote or foreign device sandbox paths (displays placeholder instead of crashing).
 */
fun ImageView.loadPostImage(
    mediaUrl: String?,
    placeholderRes: Int = R.drawable.bg_image_placeholder
) {
    if (mediaUrl.isNullOrBlank()) {
        visibility = View.GONE
        return
    }

    val trimmed = mediaUrl.trim()
    visibility = View.VISIBLE

    when {
        // 1. Remote Supabase Storage HTTP / HTTPS URL
        trimmed.startsWith("http://") || trimmed.startsWith("https://") -> {
            Glide.with(context)
                .load(trimmed)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .placeholder(placeholderRes)
                .error(placeholderRes)
                .into(this)
        }

        // 2. Base64 Data URI or raw Base64 data (legacy fallback)
        trimmed.startsWith("data:image/") || (trimmed.length > 200 && !trimmed.startsWith("/") && !trimmed.startsWith("content:")) -> {
            try {
                val base64Data = if (trimmed.contains(",")) trimmed.substringAfter(",") else trimmed
                val imageBytes = Base64.decode(base64Data.trim(), Base64.DEFAULT)
                Glide.with(context)
                    .asBitmap()
                    .load(imageBytes)
                    .placeholder(placeholderRes)
                    .error(placeholderRes)
                    .into(this)
            } catch (_: Exception) {
                setImageResource(placeholderRes)
            }
        }

        // 3. Local Content URI (if valid on this physical device)
        trimmed.startsWith("content://") -> {
            try {
                val uri = Uri.parse(trimmed)
                Glide.with(context)
                    .load(uri)
                    .placeholder(placeholderRes)
                    .error(placeholderRes)
                    .into(this)
            } catch (_: Exception) {
                setImageResource(placeholderRes)
            }
        }

        // 4. Local File path (if it exists on this physical device)
        trimmed.startsWith("/") || trimmed.startsWith("file://") -> {
            val filePath = trimmed.removePrefix("file://")
            val localFile = File(filePath)
            if (localFile.exists() && localFile.length() > 0L) {
                Glide.with(context)
                    .load(localFile)
                    .placeholder(placeholderRes)
                    .error(placeholderRes)
                    .into(this)
            } else {
                // If it belongs to another device's sandbox, display placeholder gracefully without crashing
                setImageResource(placeholderRes)
            }
        }

        else -> {
            val resId = try {
                context.resources.getIdentifier(trimmed, "drawable", context.packageName)
            } catch (_: Exception) {
                0
            }
            if (resId != 0) {
                Glide.with(context)
                    .load(resId)
                    .placeholder(placeholderRes)
                    .error(placeholderRes)
                    .into(this)
            } else {
                setImageResource(placeholderRes)
            }
        }
    }
}
