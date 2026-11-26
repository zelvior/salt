package com.zelvior.salt.core.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewTreeLifecycleOwner
import androidx.compose.ui.platform.ViewTreeSavedStateRegistryOwner
import androidx.compose.ui.platform.ViewTreeViewModelStoreOwner
import kotlin.math.roundToInt

/**
 * Owns the [WindowManager] overlay window that hosts the Jetpack Compose control center.
 *
 * The window is a single `TYPE_APPLICATION_OVERLAY` with `FLAG_NOT_TOUCH_MODAL`, so touches
 * outside the trigger strip / open panel fall through to the app underneath:
 *
 *   root FrameLayout (transparent, not touchable)
 *     ├── triggerStrip   : thin, always-on touch zone at the very top edge
 *     └── panelContainer : top portion of the screen, holds the ComposeView
 *
 * The ComposeView is bound to a dedicated [OverlayLifecycleOwner] so it behaves like an
 * in-activity Compose surface.
 */
class OverlayWindowHost(
    private val context: Context,
    private val lifecycleOwner: OverlayLifecycleOwner,
    private val interactor: OverlayInteractor,
    private val content: @Composable () -> Unit
) {
    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var root: FrameLayout? = null
    private var composeView: ComposeView? = null

    private val gestureDetector = EdgeGestureDetector(
        context = context,
        onOpen = interactor::open,
        onClose = interactor::close
    )

    fun show() {
        if (root != null) return

        val dm = context.resources.displayMetrics
        val screenHeight = dm.heightPixels
        val panelHeight = (screenHeight * PANEL_HEIGHT_FRACTION).roundToInt()

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        val rootView = FrameLayout(context).apply {
            isClickable = false
            isFocusable = false
        }

        // Trigger strip — the only always-on interception zone.
        val strip = View(context).apply {
            isClickable = true
            setOnTouchListener { _, event -> gestureDetector.onTouchEvent(event) }
        }
        rootView.addView(
            strip,
            FrameLayout.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                TRIGGER_HEIGHT_DP.dp(context),
                Gravity.TOP
            )
        )

        // Panel container — top portion of the screen; the Compose content animates within.
        val panelContainer = FrameLayout(context)
        val compose = ComposeView(context).apply {
            // Bind the overlay's lifecycle / VM / SavedState owners so Compose works as if
            // hosted by an Activity.
            ViewTreeLifecycleOwner.set(this, lifecycleOwner)
            ViewTreeViewModelStoreOwner.set(this, lifecycleOwner)
            ViewTreeSavedStateRegistryOwner.set(this, lifecycleOwner)
            setContent { content() }
        }
        panelContainer.addView(
            compose,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        rootView.addView(
            panelContainer,
            FrameLayout.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                panelHeight,
                Gravity.TOP
            )
        )

        windowManager.addView(rootView, params)
        root = rootView
        composeView = compose
    }

    fun hide() {
        root?.let { windowManager.removeView(it) }
        root = null
        composeView = null
    }

    private fun Int.dp(context: Context): Int =
        (this * context.resources.displayMetrics.density).roundToInt()

    companion object {
        private const val TRIGGER_HEIGHT_DP = 32
        private const val PANEL_HEIGHT_FRACTION = 0.72f
    }
}
