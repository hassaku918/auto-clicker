package com.example.autoclickerblocker

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
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
    private var blockTopPercent = 76f
    private var relaunchOnExit = false
    private var resumeClickerAfterBlock = false

    private var timeBlockMs = 60_000L
    private var timeBlockRadius = 250f

    private var lastPkg = ""
    private var lastRelaunchAt = 0L

    private enum class BlockMode { NONE, BAND, CIRCLE }
    @Volatile private var blockMode = BlockMode.NONE
    @Volatile private var activeBandPercent = 0f
    @Volatile private var activeCircleRadius = 0f

    private var blocker: View? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() { instance = this }

    fun configureAppTrigger(
        enabled: Boolean,
        target: String,
        duration: Long,
        radius: Float,
        topPercent: Float,
        relaunch: Boolean,
        resumeClicker: Boolean
    ) {
        appTriggerEnabled = enabled
        appTargetPackage = target.trim()
        appBlockMs = duration.coerceAtLeast(100L)
        appBlockRadius = radius.coerceAtLeast(0f)
        blockTopPercent = topPercent.coerceIn(0f, 100f)
        relaunchOnExit = relaunch
        resumeClickerAfterBlock = resumeClicker
    }

    fun configureTimeTrigger(duration: Long, radius: Float) {
        timeBlockMs = duration.coerceAtLeast(100L)
        timeBlockRadius = radius.coerceAtLeast(1f)
    }

    fun isBlocked(x: Float, y: Float): Boolean {
        val w = resources.displayMetrics.widthPixels.toFloat()
        val h = resources.displayMetrics.heightPixels.toFloat()
        return when (blockMode) {
            BlockMode.NONE -> false
            BlockMode.BAND -> y <= h * (activeBandPercent / 100f)
            BlockMode.CIRCLE -> {
                val dx = x - w / 2f
                val dy = y - h / 2f
                sqrt(dx * dx + dy * dy) <= activeCircleRadius
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!appTriggerEnabled || appTargetPackage.isEmpty()) return
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName || pkg.startsWith("com.android.systemui")) return

        val prev = lastPkg
        lastPkg = pkg

        if (pkg == appTargetPackage) {
            startRecoveryBlock()
            return
        }

        if (relaunchOnExit && prev == appTargetPackage && pkg != appTargetPackage) {
            val now = System.currentTimeMillis()
            if (now - lastRelaunchAt > 2500L) {
                lastRelaunchAt = now
                handler.postDelayed({ relaunchTarget() }, 400L)
            }
        }
    }

    private fun startRecoveryBlock() {
        if (blockTopPercent > 0f) {
            blockTopBand(appBlockMs, blockTopPercent)
        } else {
            blockCircle(appBlockMs, appBlockRadius.coerceAtLeast(1f))
        }
        if (resumeClickerAfterBlock) {
            AutomationService.startClicker(this)
        }
    }

    private fun relaunchTarget() {
        runCatching {
            val launch = packageManager.getLaunchIntentForPackage(appTargetPackage) ?: return
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            startActivity(launch)
        }
    }

    fun triggerTimeBlock() {
        blockCircle(timeBlockMs, timeBlockRadius)
        if (resumeClickerAfterBlock) {
            AutomationService.startClicker(this)
        }
    }

    fun click(x: Float, y: Float) {
        if (isBlocked(x, y)) return
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 30)
        dispatchGesture(
            GestureDescription.Builder().addStroke(stroke).build(),
            null,
            null
        )
    }

    private fun blockTopBand(ms: Long, percent: Float) {
        handler.post {
            unblock()
            activeBandPercent = percent
            blockMode = BlockMode.BAND
            val v = object : View(this) {
                override fun onTouchEvent(event: MotionEvent?): Boolean {
                    if (event == null) return true
                    val limit = height * (percent / 100f)
                    return event.y <= limit
                }
            }
            addBlocker(v, ms)
        }
    }

    private fun blockCircle(ms: Long, radius: Float) {
        handler.post {
            unblock()
            activeCircleRadius = radius
            blockMode = BlockMode.CIRCLE
            val v = object : View(this) {
                override fun onTouchEvent(event: MotionEvent?): Boolean {
                    if (event == null) return true
                    val cx = width / 2f
                    val cy = height / 2f
                    val dx = event.x - cx
                    val dy = event.y - cy
                    return sqrt(dx * dx + dy * dy) <= radius
                }
            }
            addBlocker(v, ms)
        }
    }

    private fun addBlocker(v: View, ms: Long) {
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

    fun unblock() {
        handler.post {
            blockMode = BlockMode.NONE
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
