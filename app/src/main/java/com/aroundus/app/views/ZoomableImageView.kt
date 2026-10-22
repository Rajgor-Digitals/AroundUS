package com.aroundus.app.views

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Matrix
import android.graphics.RectF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.abs

/**
 * High-performance, zero-dependency ZoomableImageView supporting:
 * - Fluid pinch-to-zoom (1.0x to 5.0x)
 * - Animated double-tap to zoom (toggle 1.0x <-> 2.5x)
 * - Smooth panning with boundary-aware clamping
 * - Single-tap listener for toggling UI chrome/toolbars
 * - Pull-down to dismiss callback when not zoomed
 */
class ZoomableImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr),
    ScaleGestureDetector.OnScaleGestureListener,
    GestureDetector.OnGestureListener,
    GestureDetector.OnDoubleTapListener {

    private val imageMatrixState = Matrix()
    private val matrixValues = FloatArray(9)

    private val scaleDetector = ScaleGestureDetector(context, this)
    private val gestureDetector = GestureDetector(context, this)

    private var minScale = 1.0f
    private var maxScale = 5.0f
    private var doubleTapScale = 2.5f

    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var isDragging = false

    var onSingleTapListener: (() -> Unit)? = null
    var onDismissRequested: (() -> Unit)? = null

    private var swipeStartY = 0f
    private var isDismissSwipe = false

    init {
        scaleType = ScaleType.MATRIX
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (changed || imageMatrixState.isIdentity) {
            resetMatrixToBounds()
        }
    }

    fun resetMatrixToBounds() {
        val d = drawable ?: return
        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()
        if (viewWidth <= 0 || viewHeight <= 0) return

        val drawableWidth = d.intrinsicWidth.toFloat()
        val drawableHeight = d.intrinsicHeight.toFloat()
        if (drawableWidth <= 0 || drawableHeight <= 0) return

        val scaleX = viewWidth / drawableWidth
        val scaleY = viewHeight / drawableHeight
        val scale = minOf(scaleX, scaleY)

        val dx = (viewWidth - drawableWidth * scale) / 2f
        val dy = (viewHeight - drawableHeight * scale) / 2f

        imageMatrixState.reset()
        imageMatrixState.postScale(scale, scale)
        imageMatrixState.postTranslate(dx, dy)
        imageMatrix = imageMatrixState
    }

    private fun getCurrentScale(): Float {
        imageMatrixState.getValues(matrixValues)
        return matrixValues[Matrix.MSCALE_X]
    }

    private fun getTransformedRect(): RectF {
        val d = drawable ?: return RectF(0f, 0f, 0f, 0f)
        val rect = RectF(0f, 0f, d.intrinsicWidth.toFloat(), d.intrinsicHeight.toFloat())
        imageMatrixState.mapRect(rect)
        return rect
    }

    private fun checkBoundsAndCenter() {
        val rect = getTransformedRect()
        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()

        var deltaX = 0f
        var deltaY = 0f

        if (rect.width() <= viewWidth) {
            deltaX = (viewWidth - rect.width()) / 2f - rect.left
        } else if (rect.left > 0) {
            deltaX = -rect.left
        } else if (rect.right < viewWidth) {
            deltaX = viewWidth - rect.right
        }

        if (rect.height() <= viewHeight) {
            deltaY = (viewHeight - rect.height()) / 2f - rect.top
        } else if (rect.top > 0) {
            deltaY = -rect.top
        } else if (rect.bottom < viewHeight) {
            deltaY = viewHeight - rect.bottom
        }

        imageMatrixState.postTranslate(deltaX, deltaY)
        imageMatrix = imageMatrixState
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y
                swipeStartY = event.y
                isDismissSwipe = false
                isDragging = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - lastTouchX
                val dy = event.y - lastTouchY
                val currentScale = getCurrentScale()

                val baseScale = getBaseScale()
                val isAtBaseScale = currentScale <= baseScale * 1.05f

                if (isAtBaseScale) {
                    // Check for pull-down to dismiss
                    val totalDy = event.y - swipeStartY
                    if (totalDy > 80f && abs(event.x - lastTouchX) < abs(dy) * 0.8f) {
                        isDismissSwipe = true
                    }
                } else {
                    // Panning when zoomed
                    imageMatrixState.postTranslate(dx, dy)
                    checkBoundsAndCenter()
                    isDragging = true
                }

                lastTouchX = event.x
                lastTouchY = event.y
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDismissSwipe) {
                    onDismissRequested?.invoke()
                } else {
                    checkBoundsAndCenter()
                }
                isDragging = false
                isDismissSwipe = false
            }
        }
        return true
    }

    private fun getBaseScale(): Float {
        val d = drawable ?: return 1.0f
        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()
        if (viewWidth <= 0 || viewHeight <= 0) return 1.0f
        return minOf(viewWidth / d.intrinsicWidth, viewHeight / d.intrinsicHeight)
    }

    // --- ScaleGestureDetector.OnScaleGestureListener ---

    override fun onScale(detector: ScaleGestureDetector): Boolean {
        val factor = detector.scaleFactor
        val currentScale = getCurrentScale()
        val base = getBaseScale()

        val targetScale = currentScale * factor
        if (targetScale in (base * 0.8f)..(base * maxScale)) {
            imageMatrixState.postScale(factor, factor, detector.focusX, detector.focusY)
            checkBoundsAndCenter()
        }
        return true
    }

    override fun onScaleBegin(detector: ScaleGestureDetector): Boolean = true

    override fun onScaleEnd(detector: ScaleGestureDetector) {
        val currentScale = getCurrentScale()
        val base = getBaseScale()
        if (currentScale < base) {
            animateZoom(currentScale, base, width / 2f, height / 2f)
        }
    }

    // --- GestureDetector.OnDoubleTapListener ---

    override fun onDoubleTap(e: MotionEvent): Boolean {
        val currentScale = getCurrentScale()
        val base = getBaseScale()
        val targetScale = if (currentScale > base * 1.2f) base else base * doubleTapScale
        animateZoom(currentScale, targetScale, e.x, e.y)
        return true
    }

    override fun onDoubleTapEvent(e: MotionEvent): Boolean = false

    override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
        onSingleTapListener?.invoke()
        return true
    }

    private fun animateZoom(fromScale: Float, toScale: Float, focusX: Float, focusY: Float) {
        val animator = ValueAnimator.ofFloat(fromScale, toScale).apply {
            duration = 240
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { va ->
                val current = va.animatedValue as Float
                val scaleFactor = current / getCurrentScale()
                imageMatrixState.postScale(scaleFactor, scaleFactor, focusX, focusY)
                checkBoundsAndCenter()
            }
        }
        animator.start()
    }

    // --- GestureDetector.OnGestureListener unused stubs ---
    override fun onDown(e: MotionEvent): Boolean = true
    override fun onShowPress(e: MotionEvent) {}
    override fun onSingleTapUp(e: MotionEvent): Boolean = false
    override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean = false
    override fun onLongPress(e: MotionEvent) {}
    override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean = false
}
