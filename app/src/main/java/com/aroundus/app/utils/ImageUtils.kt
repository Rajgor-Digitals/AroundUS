package com.aroundus.app.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream

object ImageUtils {

    /**
     * Resizes and compresses an image from a content/file Uri into a compact JPEG Base64 Data URI.
     * Dimensions are capped at [maxDimension]px (default 320px) to produce a lightweight payload
     * (~15-30 KB) that saves directly into the Supabase database and syncs instantly to all users worldwide.
     */
    fun uriToCompressedBase64(
        context: Context,
        uri: Uri,
        maxDimension: Int = 320,
        quality: Int = 80
    ): String? {
        return try {
            val contentResolver = context.contentResolver

            // 1. Decode bounds only to compute sub-sampling factor
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOptions)
            } ?: return null

            val originalWidth = boundsOptions.outWidth
            val originalHeight = boundsOptions.outHeight
            if (originalWidth <= 0 || originalHeight <= 0) return null

            var inSampleSize = 1
            if (originalHeight > maxDimension || originalWidth > maxDimension) {
                val halfHeight = originalHeight / 2
                val halfWidth = originalWidth / 2
                while ((halfHeight / inSampleSize) >= maxDimension && (halfWidth / inSampleSize) >= maxDimension) {
                    inSampleSize *= 2
                }
            }

            // 2. Decode sampled bitmap into memory
            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
            }
            val sampledBitmap = contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            } ?: return null

            // 3. Scale down accurately to maxDimension maintaining aspect ratio
            val width = sampledBitmap.width
            val height = sampledBitmap.height
            val scale = (maxDimension.toFloat() / maxOf(width, height)).coerceAtMost(1.0f)
            val finalBitmap = if (scale < 1.0f) {
                val scaled = Bitmap.createScaledBitmap(
                    sampledBitmap,
                    (width * scale).toInt().coerceAtLeast(1),
                    (height * scale).toInt().coerceAtLeast(1),
                    true
                )
                if (scaled != sampledBitmap) {
                    sampledBitmap.recycle()
                }
                scaled
            } else {
                sampledBitmap
            }

            // 4. Compress to JPEG
            val baos = ByteArrayOutputStream()
            finalBitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos)
            val imageBytes = baos.toByteArray()
            finalBitmap.recycle()

            // 5. Format as Base64 Data URI
            "data:image/jpeg;base64," + Base64.encodeToString(imageBytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Reads an image from Uri, sub-samples it to prevent OOM, scales it to [maxDimension]px (default 1280px),
     * and compresses it into a high-quality JPEG ByteArray (~100-250 KB) ready for Supabase Storage upload.
     */
    fun uriToCompressedBytes(
        context: Context,
        uri: Uri,
        maxDimension: Int = 1280,
        quality: Int = 85
    ): ByteArray? {
        return try {
            val contentResolver = context.contentResolver

            // 1. Decode bounds only to compute sub-sampling factor
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOptions)
            } ?: return null

            val originalWidth = boundsOptions.outWidth
            val originalHeight = boundsOptions.outHeight
            if (originalWidth <= 0 || originalHeight <= 0) return null

            var inSampleSize = 1
            if (originalHeight > maxDimension || originalWidth > maxDimension) {
                val halfHeight = originalHeight / 2
                val halfWidth = originalWidth / 2
                while ((halfHeight / inSampleSize) >= maxDimension && (halfWidth / inSampleSize) >= maxDimension) {
                    inSampleSize *= 2
                }
            }

            // 2. Decode sampled bitmap into memory
            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
            }
            val sampledBitmap = contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            } ?: return null

            // 3. Scale down accurately to maxDimension maintaining aspect ratio
            val width = sampledBitmap.width
            val height = sampledBitmap.height
            val scale = (maxDimension.toFloat() / maxOf(width, height)).coerceAtMost(1.0f)
            val finalBitmap = if (scale < 1.0f) {
                val scaled = Bitmap.createScaledBitmap(
                    sampledBitmap,
                    (width * scale).toInt().coerceAtLeast(1),
                    (height * scale).toInt().coerceAtLeast(1),
                    true
                )
                if (scaled != sampledBitmap) {
                    sampledBitmap.recycle()
                }
                scaled
            } else {
                sampledBitmap
            }

            // 4. Compress to JPEG
            val baos = ByteArrayOutputStream()
            finalBitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos)
            val bytes = baos.toByteArray()
            finalBitmap.recycle()
            bytes
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

