package com.example.autoclickerblocker

import android.accessibilityservice.AccessibilityService
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent

class BlockAccessibilityService : AccessibilityService() {
    companion object {
        var instance: BlockAccessibilityService? = null
    }

    private val handler = Handler(Looper.getMainLooper())
    private var blocker: View? = null
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

    private val autoUnblock = Runnable { unblock() }

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
        val durationMs = p.getLong("blockDurationMs", 30_000L).coerceAtLeast(100L)

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

            val v = object : View(this) {
                init { setWillNotDraw(false) }
                override fun onDraw(canvas: Canvas) {
                    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fillPaint)
                    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), linePaint)
                }
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
                handler.removeCallbacks(autoUnblock)
                handler.postDelayed(autoUnblock, durationMs)
            }
        }
    }

    fun unblock() {
        handler.post { unblockInternal() }
    }

    private fun unblockInternal() {
        handler.removeCallbacks(autoUnblock)
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
