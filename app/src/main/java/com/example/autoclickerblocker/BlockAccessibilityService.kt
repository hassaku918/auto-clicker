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
import android.view.Display
import android.view.MotionEvent
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
            if (colorMatched()) {
                unblock()
                return
            }
            handler.postDelayed(this, 250L)
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
            val v = object : View(this) {
                init { setWillNotDraw(false) }
                override fun onDraw(canvas: Canvas) {
                    val vh = height.toFloat()
                    val vw = width.toFloat()
                    val t = vh * (topPct / 100f)
                    val b = (t + vh * (heightPct / 100f)).coerceAtMost(vh)
                    val l = vw * (leftPct / 100f)
                    val r = (l + vw * (widthPct / 100f)).coerceAtMost(vw)
                    canvas.drawRect(l, t, r, b, fillPaint)
                    canvas.drawRect(l, t, r, b, linePaint)
                }
                override fun onTouchEvent(event: MotionEvent?): Boolean {
                    if (event == null) return true
                    val vh = height.toFloat()
                    val vw = width.toFloat()
                    val t = vh * (topPct / 100f)
                    val b = (t + vh * (heightPct / 100f)).coerceAtMost(vh)
                    val l = vw * (leftPct / 100f)
                    val r = (l + vw * (widthPct / 100f)).coerceAtMost(vw)
                    return event.x in l..r && event.y in t..b
                }
            }
            val lp = WindowManager.LayoutParams(
                -1, -1,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            )
            runCatching {
                (getSystemService(WINDOW_SERVICE) as WindowManager).addView(v, lp)
                blocker = v
                watching = true
                handler.removeCallbacks(colorTick)
                handler.post(colorTick)
            }
        }
    }

    private fun colorMatched(): Boolean {
        val p = prefs()
        val x = p.getFloat("colorX", 0f).toInt()
        val y = p.getFloat("colorY", 0f).toInt()
        val target = p.getInt("colorTarget", Color.WHITE)
        val tol = p.getInt("colorTol", 25)
        ScreenGrab.pixel(x, y)?.let { return near(it, target, tol) }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
            override fun onSuccess(screenshot: ScreenshotResult) {
                val hb = screenshot.hardwareBuffer
                val bmp = Bitmap.wrapHardwareBuffer(hb, screenshot.colorSpace) ?: return
                val sw = if (bmp.config == Bitmap.Config.HARDWARE) bmp.copy(Bitmap.Config.ARGB_8888, false) else bmp
                val px = x.coerceIn(0, (sw.width - 1).coerceAtLeast(0))
                val py = y.coerceIn(0, (sw.height - 1).coerceAtLeast(0))
                val pixel = runCatching { sw.getPixel(px, py) }.getOrNull()
                if (sw !== bmp) sw.recycle()
                hb.close()
                if (pixel != null && near(pixel, target, tol)) unblock()
            }
            override fun onFailure(errorCode: Int) {}
        })
        return false
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
