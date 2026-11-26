package com.zelvior.salt.core.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlin.math.roundToInt

/**
 * Owns the WindowManager windows that host the Salt control center.
 *
 * THE GESTURE CONTRACT (why there are TWO windows, not one):
 *
 *   1. `panelWindow` — top ~72% of the screen, hosts the Compose shade. NOT_TOUCH_MODAL,
 *      so the bottom of the screen (app content + gesture nav) keeps working while the
 *      shade is open. While closed it is flagged NOT_TOUCHABLE so it costs nothing and
 *      never blocks anything.
 *   2. `stripWindow` — a thin, always-on touchable zone pinned to the very top edge
 *      (status-bar height + margin), added AFTER the panel so it z-orders above it.
 *      This is what intercepts the pull-down before SystemUI shows the system shade.
 *
 * Both windows are TYPE_APPLICATION_OVERLAY with FLAG_LAYOUT_NO_LIMITS and
 * LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS — without the cutout mode the window's top inset
 * starts BELOW the status bar/cutout and the system shade wins the swipe.
 *
 * The ComposeView is bound to a dedicated [OverlayLifecycleOwner] so it behaves like an
 * in-activity Compose surface.
 */
class OverlayWindowHost(
    private val context: Context,
    private val lifecycleOwner: OverlayLifecycleOwner,
    interactor: OverlayInteractor,
    private val content: @Composable () -> Unit
) {
    private val windowManager =
        context.getSystemService(WindowManager::class.java)

    private var panel: FrameLayout? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private var strip: View? = null

    private val gestureDetector = EdgeGestureDetector(
        context = context,
        onOpen = interactor::open,
        onClose = interactor::close,
        onToggle = interactor::toggle
    )

    fun show() {
        if (panel != null) return

        val screenHeight = context.resources.displayMetrics.heightPixels
        val panelHeight = (screenHeight * PANEL_HEIGHT_FRACTION).roundToInt()

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            panelHeight,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // NOT_TOUCH_MODAL: touches BELOW the panel pass through to the app and nav.
            // NOT_TOUCHABLE: while closed the panel must not steal any touches —
            //                cleared in [onShadeOpenChanged] when the shade opens.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                runCatching { blurBehindRadius = blurRadiusPx() } // real backdrop glass (API 31+)
            }
        }

        val panelView = FrameLayout(context)
        val compose = ComposeView(context).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setContent { content() }
        }
        panelView.addView(
            compose,
            FrameLayout.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT
            )
        )
        windowManager.addView(panelView, params)
        panel = panelView
        panelParams = params

        // Added after the panel so the strip sits on top of it.
        addStrip()
    }

    fun hide() {
        removeStrip()
        panel?.let { runCatching { windowManager.removeView(it) } }
        panel = null
        panelParams = null
    }

    /**
     * Called by [OverlayService] whenever the shade opens/closes: the strip must step
     * aside while open (it would otherwise swallow taps in the panel's header area) and
     * the panel becomes touchable + blurred. Closed: inverse, zero cost.
     */
    fun onShadeOpenChanged(open: Boolean) {
        val params = panelParams ?: return
        if (open) {
            removeStrip()
            params.flags = params.flags and
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                params.blurBehindRadius = blurRadiusPx()
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                params.blurBehindRadius = 0
            }
            params.flags = params.flags or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            addStrip()
        }
        panel?.let { runCatching { windowManager.updateViewLayout(it, params) } }
    }

    // ---------------------------------------------------------------- strip

    private fun addStrip() {
        if (strip != null) return
        val touchView = View(context).apply {
            setOnTouchListener { _, event -> gestureDetector.onTouchEvent(event) }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            stripHeight(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        windowManager.addView(touchView, params)
        strip = touchView
    }

    private fun removeStrip() {
        strip?.let { runCatching { windowManager.removeView(it) } }
        strip = null
    }

    /** Status-bar height + margin so the swipe is captured before SystemUI sees it. */
    private fun stripHeight(): Int {
        val res = context.resources
        val id = res.getIdentifier("status_bar_height", "dimen", "android")
        val statusBar = if (id > 0) res.getDimensionPixelSize(id) else dp(24)
        return statusBar + dp(16)
    }

    private fun blurRadiusPx(): Int = dp(24)

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).roundToInt()

    companion object {
        private const val PANEL_HEIGHT_FRACTION = 0.72f
    }
}
