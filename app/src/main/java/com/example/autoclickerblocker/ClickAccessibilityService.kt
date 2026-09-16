package com.example.autoclickerblocker

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.*
import android.view.accessibility.AccessibilityEvent
import kotlin.math.abs
import kotlin.math.sqrt

class ClickAccessibilityService : AccessibilityService() {
    companion object { var instance: ClickAccessibilityService? = null }

    private var appTriggerEnabled = false
    private var appTargetPackage = ""
    private var appBlockMs = 60_000L
    private var appBlockRadius = 250f
    private var blockTopPercent = 76f
    private var blockYPercent = 0f
    private var relaunchOnExit = false
    private var resumeClickerAfterBlock = false

    private var timeBlockMs = 60_000L
    private var timeBlockRadius = 250f
    private var timeBlockYPercent = 0f
    private var timeBlockHeightPercent = 0f

    private var colorUnblockEnabled = false
    private var colorX = 0f
    private var colorY = 0f
    private var colorTarget = Color.WHITE
    private var colorTol = 25
    private var watchingColor = false

    private var lastPkg = ""
    private var lastRelaunchAt = 0L

    private enum class BlockMode { NONE, BAND, CIRCLE }
    @Volatile private var blockMode = BlockMode.NONE
    @Volatile private var activeBandStart = 0f
    @Volatile private var activeBandEnd = 0f
    @Volatile private var activeCircleRadius = 0f

    private var blocker: View? = null
    private val handler = Handler(Looper.getMainLooper())
    private val colorTick = object : Runnable {
        override fun run() {
            if (!watchingColor || !colorUnblockEnabled) return
            sampleAndMaybeUnblock()
            if (watchingColor) handler.postDelayed(this, 400L)
        }
    }

    override fun onServiceConnected() {
        instance = this
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        configureColorUnblock(
            prefs.getBoolean("colorUnblock", false),
            prefs.getFloat("colorX", 0f),
            prefs.getFloat("colorY", 0f),
            prefs.getInt("colorTarget", Color.WHITE),
            prefs.getInt("colorTol", 25)
        )
    }

    fun configureAppTrigger(
        enabled: Boolean, target: String, duration: Long, radius: Float,
        topPercent: Float, yPercent: Float, relaunch: Boolean, resumeClicker: Boolean
    ) {
        appTriggerEnabled = enabled
        appTargetPackage = target.trim()
        appBlockMs = duration.coerceAtLeast(100L)
        appBlockRadius = radius.coerceAtLeast(0f)
        blockTopPercent = topPercent.coerceIn(0f, 100f)
        blockYPercent = yPercent.coerceIn(0f, 100f)
        relaunchOnExit = relaunch
        resumeClickerAfterBlock = resumeClicker
    }

    fun configureTimeTrigger(duration: Long, radius: Float, yPercent: Float, heightPercent: Float) {
        timeBlockMs = duration.coerceAtLeast(100L)
        timeBlockRadius = radius.coerceAtLeast(0f)
        timeBlockYPercent = yPercent.coerceIn(0f, 100f)
        timeBlockHeightPercent = heightPercent.coerceIn(0f, 100f)
    }

    fun configureColorUnblock(enabled: Boolean, x: Float, y: Float, color: Int, tol: Int) {
        colorUnblockEnabled = enabled
        colorX = x; colorY = y; colorTarget = color
        colorTol = tol.coerceIn(0, 255)
    }

    fun isBlocked(x: Float, y: Float): Boolean {
        val w = resources.displayMetrics.widthPixels.toFloat()
        val h = resources.displayMetrics.heightPixels.toFloat()
        return when (blockMode) {
            BlockMode.NONE -> false
            BlockMode.BAND -> y >= activeBandStart && y <= activeBandEnd
            BlockMode.CIRCLE -> {
                val dx = x - w / 2f; val dy = y - h / 2f
                sqrt(dx * dx + dy * dy) <= activeCircleRadius
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!appTriggerEnabled || appTargetPackage.isEmpty()) return
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName || pkg.startsWith("com.android.systemui")) return
        val prev = lastPkg; lastPkg = pkg
        if (pkg == appTargetPackage) { startRecoveryBlock(); return }
        if (relaunchOnExit && prev == appTargetPackage && pkg != appTargetPackage) {
            val now = System.currentTimeMillis()
            if (now - lastRelaunchAt > 2500L) {
                lastRelaunchAt = now
                handler.postDelayed({ relaunchTarget() }, 400L)
            }
        }
    }

    private fun startRecoveryBlock() {
        applyBlock(appBlockMs, blockYPercent, blockTopPercent, appBlockRadius)
        if (resumeClickerAfterBlock) AutomationService.startClicker(this)
    }

    private fun relaunchTarget() {
        runCatching {
            val launch = packageManager.getLaunchIntentForPackage(appTargetPackage) ?: return
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            startActivity(launch)
        }
    }

    fun triggerTimeBlock() {
        applyBlock(timeBlockMs, timeBlockYPercent, timeBlockHeightPercent, timeBlockRadius)
        if (resumeClickerAfterBlock) AutomationService.startClicker(this)
    }

    private fun applyBlock(ms: Long, yPercent: Float, heightPercent: Float, radius: Float) {
        if (heightPercent > 0f) blockBand(ms, yPercent, heightPercent)
        else blockCircle(ms, radius.coerceAtLeast(1f))
    }

    fun click(x: Float, y: Float) {
        if (isBlocked(x, y)) return
        val path = Path().apply { moveTo(x, y) }
        dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, 30)).build(), null, null)
    }

    private fun blockBand(ms: Long, yPercent: Float, heightPercent: Float) {
        handler.post {
            unblock()
            val v = object : View(this) {
                override fun onTouchEvent(event: MotionEvent?): Boolean {
                    if (event == null) return true
                    val start = height * (yPercent / 100f)
                    val end = (start + height * (heightPercent / 100f)).coerceAtMost(height.toFloat())
                    return event.y >= start && event.y <= end
                }
            }
            val h = resources.displayMetrics.heightPixels.toFloat()
            activeBandStart = h * (yPercent / 100f)
            activeBandEnd = (activeBandStart + h * (heightPercent / 100f)).coerceAtMost(h)
            blockMode = BlockMode.BAND
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
                    val cx = width / 2f; val cy = height / 2f
                    val dx = event.x - cx; val dy = event.y - cy
                    return sqrt(dx * dx + dy * dy) <= radius
                }
            }
            addBlocker(v, ms)
        }
    }

    private fun addBlocker(v: View, ms: Long) {
        val lp = WindowManager.LayoutParams(
            -1, -1, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        runCatching {
            (getSystemService(WINDOW_SERVICE) as WindowManager).addView(v, lp)
            blocker = v
            handler.postDelayed({ unblock() }, ms)
            startColorWatch()
        }
    }

    private fun startColorWatch() {
        if (!colorUnblockEnabled) return
        watchingColor = true
        handler.removeCallbacks(colorTick)
        handler.post(colorTick)
    }

    private fun sampleAndMaybeUnblock() {
        val grabbed = ScreenGrab.pixel(colorX.toInt(), colorY.toInt())
        if (grabbed != null) {
            if (nearColor(grabbed, colorTarget, colorTol)) unblock()
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
            override fun onSuccess(screenshot: ScreenshotResult) {
                val hb = screenshot.hardwareBuffer
                val bmp = Bitmap.wrapHardwareBuffer(hb, screenshot.colorSpace) ?: return
                val sw = if (bmp.config == Bitmap.Config.HARDWARE) bmp.copy(Bitmap.Config.ARGB_8888, false) else bmp
                val x = colorX.toInt().coerceIn(0, (sw.width - 1).coerceAtLeast(0))
                val y = colorY.toInt().coerceIn(0, (sw.height - 1).coerceAtLeast(0))
                val pixel = runCatching { sw.getPixel(x, y) }.getOrNull()
                if (sw !== bmp) sw.recycle()
                hb.close()
                if (pixel != null && nearColor(pixel, colorTarget, colorTol)) unblock()
            }
            override fun onFailure(errorCode: Int) {}
        })
    }

    private fun nearColor(a: Int, b: Int, tol: Int): Boolean {
        return abs(Color.red(a) - Color.red(b)) <= tol &&
            abs(Color.green(a) - Color.green(b)) <= tol &&
            abs(Color.blue(a) - Color.blue(b)) <= tol
    }

    fun unblock() {
        handler.post {
            watchingColor = false
            handler.removeCallbacks(colorTick)
            blockMode = BlockMode.NONE
            blocker?.let {
                runCatching { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(it) }
                blocker = null
            }
        }
    }

    override fun onInterrupt() {}
    override fun onDestroy() { unblock(); instance = null; super.onDestroy() }
}
