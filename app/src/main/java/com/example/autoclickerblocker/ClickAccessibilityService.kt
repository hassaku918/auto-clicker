package com.example.autoclickerblocker

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.*
import android.view.accessibility.AccessibilityEvent
import kotlin.math.sqrt

class ClickAccessibilityService : AccessibilityService() {
    companion object { var instance: ClickAccessibilityService? = null }

    private var appTriggerEnabled = false
    private var appTargetPackage = ""
    private var appBlockMs = 60_000L
    private var appBlockRadius = 250f

    private var timeBlockMs = 60_000L
    private var timeBlockRadius = 250f

    private var blocker: View? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() { instance = this }

    fun configureAppTrigger(enabled: Boolean, target: String, duration: Long, radius: Float) {
        appTriggerEnabled = enabled
        appTargetPackage = target
        appBlockMs = duration.coerceAtLeast(100L)
        appBlockRadius = radius.coerceAtLeast(1f)
    }

    fun configureTimeTrigger(duration: Long, radius: Float) {
        timeBlockMs = duration.coerceAtLeast(100L)
        timeBlockRadius = radius.coerceAtLeast(1f)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!appTriggerEnabled ||
            event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val pkg = event.packageName?.toString() ?: return
        if (pkg == appTargetPackage) blockFor(appBlockMs, appBlockRadius)
    }

    fun triggerTimeBlock() {
        blockFor(timeBlockMs, timeBlockRadius)
    }

    fun click(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 30)
        dispatchGesture(
            GestureDescription.Builder().addStroke(stroke).build(),
            null,
            null
        )
    }

    /**
     * Blocks touch inside a circular region.
     * The overlay itself is transparent and only consumes events inside the configured circle.
     */
    fun blockFor(ms: Long, radius: Float) {
        handler.post {
            unblock()

            val v = object : View(this) {
                override fun onTouchEvent(event: MotionEvent?): Boolean {
                    if (event == null) return true
                    val cx = width / 2f
                    val cy = height / 2f
                    val dx = event.x - cx
                    val dy = event.y - cy
                    val inside = sqrt(dx * dx + dy * dy) <= radius
                    return inside
                }
            }

            val lp = WindowManager.LayoutParams(
                -1,
                -1,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            )

            runCatching {
                (getSystemService(WINDOW_SERVICE) as WindowManager).addView(v, lp)
                blocker = v
                handler.postDelayed({ unblock() }, ms)
            }
        }
    }

    fun unblock() {
        handler.post {
            blocker?.let {
                runCatching {
                    (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(it)
                }
                blocker = null
            }
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        unblock()
        instance = null
        super.onDestroy()
    }
}
