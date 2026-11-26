package com.zelvior.salt.core.overlay

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import kotlin.math.roundToInt

/**
 * Detects a downward swipe on the (invisible) top-edge trigger strip to open the shade,
 * and an upward swipe to close it. Each gesture fires at most one callback (debounced via
 * [fired]); callbacks run on the looper thread the detector was created on.
 */
class EdgeGestureDetector(
    context: Context,
    private val onOpen: () -> Unit,
    private val onClose: () -> Unit = {}
) {
    private val thresholdPx =
        (OPEN_THRESHOLD_DP * context.resources.displayMetrics.density).roundToInt()
    private var fired = false

    private val detector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean {
                fired = false
                return true
            }

            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float
            ): Boolean {
                val startY = e1?.y ?: e2.y
                val moved = e2.y - startY
                if (!fired) {
                    when {
                        moved > thresholdPx -> {
                            fired = true
                            onOpen()
                        }
                        moved < -thresholdPx -> {
                            fired = true
                            onClose()
                        }
                    }
                }
                return true
            }
        }
    )

    fun onTouchEvent(event: MotionEvent): Boolean = detector.onTouchEvent(event)

    companion object {
        private const val OPEN_THRESHOLD_DP = 40f
    }
}
