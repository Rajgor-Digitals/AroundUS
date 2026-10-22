package com.aroundus.app.utils

import android.graphics.Color
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

/**
 * Applies the unified AroundUS aesthetic to any SwipeRefreshLayout instance:
 * Brand Primary Green (#0D8A3F) on clean white card background.
 */
fun SwipeRefreshLayout.applyAroundUsTheme() {
    setColorSchemeColors(Color.parseColor("#0D8A3F"))
    setProgressBackgroundColorSchemeColor(Color.WHITE)
}
