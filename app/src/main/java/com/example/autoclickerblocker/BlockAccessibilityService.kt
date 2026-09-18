package com.example.autoclickerblocker

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import kotlin.math.abs

class BlockAccessibilityService : AccessibilityService() {
    companion object {
        var instance: BlockAccessibilityService? = null
    }

    private val handler = Handler(Looper.getMainLooper())
    private var blocker: View? = null
    private var watching = false
    private var lastPkg = ""
    @Volatile private var shotPending = false
    private var lastShotAt = 0L

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(0x88, 0x21, 0x96, 0xF3)
        style = Paint.Style.FILL
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(0xCC, 0x15, 0x65, 0xC0)
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    private val colorTick = object : Runnable {
        override fun run() {
            if (!watching) return
            sampleColor()
            // ScreenGrab is cheap; screenshot path is throttled inside sampleColor
            val delay = if (ScreenGrab.ready) 400L else 1200L
            handler.postDelayed(this, delay)
        }
    }

    override fun onServiceConnected() {
        instance = this
    }

    private fun prefs() = getSharedPreferences("settings", MODE_PRIVATE)

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName || pkg.startsWith("com.android.systemui")) return
        val p = prefs()
        if (!p.getBoolean("enabled", false)) return
        val target = p.getString("target", "")?.trim().orEmpty()
        if (target.isEmpty()) return
        val prev = lastPkg
        lastPkg = pkg
        if (pkg == target && prev != target) {
            startBlock()
        }
    }

    fun startBlock() {
        val p = prefs()
        val topPct = p.getFloat("topPercent", 0f).coerceIn(0f, 100f)
        val heightPct = p.getFloat("heightPercent", 20f).coerceIn(1f, 100f)
        val leftPct = p.getFloat("leftPercent", 0f).coerceIn(0f, 100f)
        val widthPct = p.getFloat("widthPercent", 100f).coerceIn(1f, 100f)
        handler.post {
            unblockInternal()
            val dm = resources.displayMetrics
            val screenW = dm.widthPixels
            val screenH = dm.heightPixels
            val boxL = (screenW * leftPct / 100f).toInt().coerceIn(0, screenW - 1)
            val boxT = (screenH * topPct / 100f).toInt().coerceIn(0, screenH - 1)
            val boxW = (screenW * widthPct / 100f).toInt().coerceAtLeast(1)
                .coerceAtMost(screenW - boxL)
            val boxH = (screenH * heightPct / 100f).toInt().coerceAtLeast(1)
                .coerceAtMost(screenH - boxT)

            // Only cover the blocked rectangle so the rest of the screen stays responsive
            val v = object : View(this) {
                init { setWillNotDraw(false) }
                override fun onDraw(canvas: Canvas) {
                    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fillPaint)
                    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), linePaint)
                }
                // Consume all touches inside this small window
                override fun onTouchEvent(event: android.view.MotionEvent?) = true
            }
            val lp = WindowManager.LayoutParams(
                boxW, boxH,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            )
            lp.gravity = Gravity.TOP or Gravity.START
            lp.x = boxL
            lp.y = boxT
            runCatching {
                (getSystemService(WINDOW_SERVICE) as WindowManager).addView(v, lp)
                blocker = v
                watching = true
                shotPending = false
                handler.removeCallbacks(colorTick)
                handler.post(colorTick)
            }
        }
    }

    private fun sampleColor() {
        val p = prefs()
        val x = p.getFloat("colorX", 0f).toInt()
        val y = p.getFloat("colorY", 0f).toInt()
        val target = p.getInt("colorTarget", Color.WHITE)
        val tol = p.getInt("colorTol", 25)

        // Prefer continuous MediaProjection buffer (Android 9 friendly, low cost)
        ScreenGrab.pixel(x, y)?.let {
            if (near(it, target, tol)) unblock()
            return
        }

        // Fallback: takeScreenshot is heavy — at most once per 1.5s, never stack
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val now = SystemClock.elapsedRealtime()
        if (shotPending || now - lastShotAt < 1500L) return
        shotPending = true
        lastShotAt = now
        takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
            override fun onSuccess(screenshot: ScreenshotResult) {
                try {
                    val hb = screenshot.hardwareBuffer
                    val bmp = Bitmap.wrapHardwareBuffer(hb, screenshot.colorSpace) ?: return
                    val sw = if (bmp.config == Bitmap.Config.HARDWARE) {
                        bmp.copy(Bitmap.Config.ARGB_8888, false)
                    } else bmp
                    val px = x.coerceIn(0, (sw.width - 1).coerceAtLeast(0))
                    val py = y.coerceIn(0, (sw.height - 1).coerceAtLeast(0))
                    val pixel = runCatching { sw.getPixel(px, py) }.getOrNull()
                    if (sw !== bmp) sw.recycle()
                    hb.close()
                    if (pixel != null && near(pixel, target, tol)) unblock()
                } finally {
                    shotPending = false
                }
            }
            override fun onFailure(errorCode: Int) {
                shotPending = false
            }
        })
    }

    private fun near(a: Int, b: Int, tol: Int): Boolean {
        return abs(Color.red(a) - Color.red(b)) <= tol &&
            abs(Color.green(a) - Color.green(b)) <= tol &&
            abs(Color.blue(a) - Color.blue(b)) <= tol
    }

    fun unblock() {
        handler.post { unblockInternal() }
    }

    private fun unblockInternal() {
        watching = false
        shotPending = false
        handler.removeCallbacks(colorTick)
        blocker?.let {
            runCatching { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(it) }
            blocker = null
        }
    }

    override fun onInterrupt() {}
    override fun onDestroy() {
        unblockInternal()
        instance = null
        super.onDestroy()
    }
}
