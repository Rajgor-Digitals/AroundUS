package com.aroundus.app.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.aroundus.app.R
import com.aroundus.app.models.Post
import com.aroundus.app.network.SupabaseClient
import com.aroundus.app.repository.NetworkResult
import com.aroundus.app.repository.PostRepository
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.hdodenhof.circleimageview.CircleImageView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Production utility for handling post interactions matching AroundUS Design System:
 * - Rounded Material Bottom Sheet Options Menu
 * - Native System Share (Intent.ACTION_SEND)
 * - Copy post text to clipboard
 * - Styled Delete Post Bottom Sheet for authors
 * - Report post dialog for neighbors
 */
object PostActionUtils {

    private val postRepository = PostRepository()

    /**
     * Checks if the currently logged-in user is the author of this post.
     */
    fun isPostAuthor(post: Post): Boolean {
        val currentUserId = SupabaseClient.getCurrentUserId() ?: ""
        val currentEmail = SupabaseClient.getCurrentUserEmail() ?: ""
        val currentName = SupabaseClient.getCurrentUserName() ?: ""

        if (currentUserId.isNotBlank() && !post.authorId.isNullOrBlank() && post.authorId == currentUserId) {
            return true
        }
        if (currentEmail.isNotBlank() && !post.authorId.isNullOrBlank() && post.authorId.equals(currentEmail, ignoreCase = true)) {
            return true
        }
        if (currentName.isNotBlank() && !post.userName.isNullOrBlank() && post.userName.equals(currentName, ignoreCase = true)) {
            return true
        }
        return false
    }

    /**
     * Triggers the native Android system share sheet with comprehensive post context.
     */
    fun sharePost(context: Context, post: Post) {
        val author = post.userName?.takeIf { it.isNotBlank() } ?: "Neighbor"
        val location = post.neighborhood?.takeIf { it.isNotBlank() } ?: "AroundUS"
        val postText = post.text?.takeIf { it.isNotBlank() } ?: "Shared a photo on AroundUS"

        val shareMessage = buildString {
            append("📢 Post by $author in $location\n\n")
            append("\"$postText\"\n\n")
            if (!post.mediaUrl.isNullOrBlank()) {
                append("Media: ${post.mediaUrl}\n\n")
            }
            append("Join AroundUS — Your Neighborhood Community Network.")
        }

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Post by $author on AroundUS")
            putExtra(Intent.EXTRA_TEXT, shareMessage)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share Post via"))
    }

    /**
     * Copies the post text to the device clipboard.
     */
    fun copyPostText(context: Context, post: Post) {
        val text = post.text
        if (text.isNullOrBlank()) {
            Toast.makeText(context, "No text content to copy.", Toast.LENGTH_SHORT).show()
            return
        }
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = ClipData.newPlainText("AroundUS Post", text)
        clipboard?.setPrimaryClip(clip)
        Toast.makeText(context, "Post text copied to clipboard!", Toast.LENGTH_SHORT).show()
    }

    /**
     * Displays a customized AroundUS Bottom Sheet with modern rounded cards and curated icons.
     */
    fun showPostOptionsMenu(
        context: Context,
        anchorView: View? = null,
        post: Post,
        onDeleted: (() -> Unit)? = null
    ) {
        val bottomSheet = BottomSheetDialog(context)
        val view = LayoutInflater.from(context).inflate(R.layout.bottom_sheet_post_options, null)
        bottomSheet.setContentView(view)

        (view.parent as? View)?.setBackgroundColor(Color.TRANSPARENT)

        val ivAuthorAvatar = view.findViewById<CircleImageView>(R.id.ivSheetAuthorAvatar)
        val tvAuthorName = view.findViewById<TextView>(R.id.tvSheetAuthorName)
        val tvSubtitle = view.findViewById<TextView>(R.id.tvSheetSubtitle)
        val btnClose = view.findViewById<ImageButton>(R.id.btnCloseSheet)

        val itemShare = view.findViewById<LinearLayout>(R.id.itemSharePost)
        val itemCopy = view.findViewById<LinearLayout>(R.id.itemCopyText)
        val itemSave = view.findViewById<LinearLayout>(R.id.itemSavePost)
        val tvSaveTitle = view.findViewById<TextView>(R.id.tvSaveTitle)
        val itemDelete = view.findViewById<LinearLayout>(R.id.itemDeletePost)
        val itemReport = view.findViewById<LinearLayout>(R.id.itemReportPost)

        // Bind Post Info
        val author = post.userName?.takeIf { it.isNotBlank() } ?: "Neighbor"
        val location = post.neighborhood?.takeIf { it.isNotBlank() } ?: "Bhujpur, Kutch"
        tvAuthorName.text = author
        tvSubtitle.text = "$location • Post Options"

        ivAuthorAvatar.loadUserAvatar(post.userAvatarPath, post.userAvatar)

        val isAuthor = isPostAuthor(post)
        if (isAuthor) {
            itemDelete.visibility = View.VISIBLE
            itemReport.visibility = View.GONE
        } else {
            itemDelete.visibility = View.GONE
            itemReport.visibility = View.VISIBLE
        }

        // Bookmark status
        val currentUserId = SupabaseClient.getCurrentUserId() ?: ""
        var isSaved = if (currentUserId.isNotBlank()) {
            SupabaseClient.getSavedPostIds(currentUserId).contains(post.id)
        } else false

        tvSaveTitle.text = if (isSaved) "Remove from Bookmarks" else "Save Post"

        btnClose.setOnClickListener {
            bottomSheet.dismiss()
        }

        itemShare.setOnClickListener {
            bottomSheet.dismiss()
            sharePost(context, post)
        }

        itemCopy.setOnClickListener {
            bottomSheet.dismiss()
            copyPostText(context, post)
        }

        itemSave.setOnClickListener {
            if (currentUserId.isNotBlank()) {
                isSaved = SupabaseClient.toggleSavedPost(currentUserId, post.id)
                tvSaveTitle.text = if (isSaved) "Remove from Bookmarks" else "Save Post"
                val toastMsg = if (isSaved) "Post saved to your bookmarks" else "Post removed from bookmarks"
                Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show()
                AppRefreshManager.notifyDataChanged(RefreshTarget.PROFILE)
            }
            bottomSheet.dismiss()
        }

        itemDelete.setOnClickListener {
            bottomSheet.dismiss()
            confirmAndDeletePost(context, post, onDeleted)
        }

        itemReport.setOnClickListener {
            bottomSheet.dismiss()
            showReportDialog(context, post)
        }

        bottomSheet.show()
    }

    /**
     * Shows an AroundUS-themed confirmation bottom sheet before deleting.
     */
    fun confirmAndDeletePost(
        context: Context,
        post: Post,
        onDeleted: (() -> Unit)? = null
    ) {
        val confirmSheet = BottomSheetDialog(context)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_delete_post_confirm, null)
        confirmSheet.setContentView(view)

        (view.parent as? View)?.setBackgroundColor(Color.TRANSPARENT)

        val btnCancel = view.findViewById<MaterialButton>(R.id.btnCancelDelete)
        val btnConfirm = view.findViewById<MaterialButton>(R.id.btnConfirmDelete)

        btnCancel.setOnClickListener {
            confirmSheet.dismiss()
        }

        btnConfirm.setOnClickListener {
            confirmSheet.dismiss()
            val progressToast = Toast.makeText(context, "Deleting post...", Toast.LENGTH_SHORT)
            progressToast.show()

            CoroutineScope(Dispatchers.Main).launch {
                when (val result = postRepository.deletePost(post.id, context)) {
                    is NetworkResult.Success -> {
                        progressToast.cancel()
                        Toast.makeText(context, "Post deleted successfully.", Toast.LENGTH_SHORT).show()

                        // Notify all feeds to refresh immediately
                        AppRefreshManager.notifyDataChanged(RefreshTarget.HOME_FEED)
                        AppRefreshManager.notifyDataChanged(RefreshTarget.PROFILE)
                        AppRefreshManager.notifyDataChanged(RefreshTarget.ANNOUNCEMENTS)

                        onDeleted?.invoke()
                    }
                    is NetworkResult.Error -> {
                        progressToast.cancel()
                        Toast.makeText(context, "Failed to delete: ${result.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        confirmSheet.show()
    }

    private fun showReportDialog(context: Context, post: Post) {
        val reasons = arrayOf("Spam or misleading", "Offensive or inappropriate", "Harassment", "False information")
        MaterialAlertDialogBuilder(context)
            .setTitle("Report Post")
            .setItems(reasons) { _, which ->
                Toast.makeText(
                    context,
                    "Thank you. This post has been reported for: ${reasons[which]}",
                    Toast.LENGTH_LONG
                ).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
