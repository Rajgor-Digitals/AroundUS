package com.aroundus.app.activities

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.appcompat.app.AppCompatActivity
import com.aroundus.app.databinding.ActivityFullScreenImageViewerBinding
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import java.io.File

/**
 * Immersive Full-Screen Media Viewer supporting:
 * - Fluid pinch-to-zoom (1x - 5x)
 * - Double-tap to zoom
 * - Pull-down to dismiss
 * - Chrome overlay toggle on tap
 * - Native Share
 */
class FullScreenImageViewerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFullScreenImageViewerBinding
    private var isChromeVisible = true

    companion object {
        const val EXTRA_IMAGE_URL = "extra_image_url"
        const val EXTRA_IMAGE_RES = "extra_image_res"
        const val EXTRA_CAPTION = "extra_caption"
        const val EXTRA_AUTHOR_NAME = "extra_author_name"
        const val EXTRA_SUBTITLE = "extra_subtitle"

        fun start(
            context: Context,
            imageUrl: String? = null,
            imageRes: Int = 0,
            caption: String? = null,
            authorName: String? = null,
            subtitle: String? = null
        ) {
            val intent = Intent(context, FullScreenImageViewerActivity::class.java).apply {
                if (!imageUrl.isNullOrBlank()) putExtra(EXTRA_IMAGE_URL, imageUrl)
                if (imageRes != 0) putExtra(EXTRA_IMAGE_RES, imageRes)
                if (!caption.isNullOrBlank()) putExtra(EXTRA_CAPTION, caption)
                if (!authorName.isNullOrBlank()) putExtra(EXTRA_AUTHOR_NAME, authorName)
                if (!subtitle.isNullOrBlank()) putExtra(EXTRA_SUBTITLE, subtitle)
            }
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupImmersiveWindow()

        binding = ActivityFullScreenImageViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val imageUrl = intent.getStringExtra(EXTRA_IMAGE_URL)
        val imageRes = intent.getIntExtra(EXTRA_IMAGE_RES, 0)
        val caption = intent.getStringExtra(EXTRA_CAPTION)
        val author = intent.getStringExtra(EXTRA_AUTHOR_NAME)
        val subtitle = intent.getStringExtra(EXTRA_SUBTITLE)

        setupUI(author, subtitle, caption, imageUrl)
        loadImage(imageUrl, imageRes)
    }

    private fun setupImmersiveWindow() {
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.BLACK
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            )
        }
    }

    private fun setupUI(
        author: String?,
        subtitle: String?,
        caption: String?,
        imageUrl: String?
    ) {
        binding.tvAuthorName.text = author?.takeIf { it.isNotBlank() } ?: "AroundUS Media"
        binding.tvSubtitle.text = subtitle?.takeIf { it.isNotBlank() } ?: "Pinch to zoom • Double tap"

        if (!caption.isNullOrBlank()) {
            binding.tvCaption.visibility = View.VISIBLE
            binding.tvCaption.text = caption
        } else {
            binding.tvCaption.visibility = View.GONE
        }

        binding.btnClose.setOnClickListener {
            finishWithTransition()
        }

        binding.btnShare.setOnClickListener {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                val shareText = buildString {
                    if (!author.isNullOrBlank()) append("Photo by $author on AroundUS\n")
                    if (!caption.isNullOrBlank()) append("\"$caption\"\n")
                    if (!imageUrl.isNullOrBlank()) append(imageUrl)
                }
                putExtra(Intent.EXTRA_TEXT, shareText)
            }
            startActivity(Intent.createChooser(shareIntent, "Share Media"))
        }

        binding.zoomableImageView.onSingleTapListener = {
            toggleChrome()
        }

        binding.zoomableImageView.onDismissRequested = {
            finishWithTransition()
        }
    }

    private fun loadImage(imageUrl: String?, imageRes: Int) {
        binding.progressBar.visibility = View.VISIBLE

        val glideRequest = when {
            !imageUrl.isNullOrBlank() -> {
                val model: Any = if (imageUrl.startsWith("/") && File(imageUrl).exists()) {
                    File(imageUrl)
                } else {
                    imageUrl
                }
                Glide.with(this).load(model)
            }
            imageRes != 0 -> {
                Glide.with(this).load(imageRes)
            }
            else -> null
        }

        if (glideRequest == null) {
            binding.progressBar.visibility = View.GONE
            return
        }

        glideRequest
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .listener(object : RequestListener<Drawable> {
                override fun onLoadFailed(
                    e: GlideException?,
                    model: Any?,
                    target: Target<Drawable>,
                    isFirstResource: Boolean
                ): Boolean {
                    binding.progressBar.visibility = View.GONE
                    return false
                }

                override fun onResourceReady(
                    resource: Drawable,
                    model: Any,
                    target: Target<Drawable>?,
                    dataSource: DataSource,
                    isFirstResource: Boolean
                ): Boolean {
                    binding.progressBar.visibility = View.GONE
                    binding.zoomableImageView.setImageDrawable(resource)
                    binding.zoomableImageView.resetMatrixToBounds()
                    return true
                }
            })
            .into(binding.zoomableImageView)
    }

    private fun toggleChrome() {
        isChromeVisible = !isChromeVisible
        val targetAlpha = if (isChromeVisible) 1.0f else 0.0f
        val duration = 200L

        binding.topBar.animate().alpha(targetAlpha).setDuration(duration).withEndAction {
            binding.topBar.visibility = if (isChromeVisible) View.VISIBLE else View.GONE
        }.start()

        if (binding.tvCaption.visibility == View.VISIBLE || !binding.tvCaption.text.isNullOrBlank()) {
            binding.bottomBar.animate().alpha(targetAlpha).setDuration(duration).withEndAction {
                binding.bottomBar.visibility = if (isChromeVisible) View.VISIBLE else View.GONE
            }.start()
        }
    }

    private fun finishWithTransition() {
        finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        finishWithTransition()
    }
}
