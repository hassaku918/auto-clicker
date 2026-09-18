package com.example.autoclickerblocker

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.*
import android.widget.TextView

object FloatingStopOverlay {
    private var view: View? = null

    fun show(context: Context) {
        if (view != null) return
        val app = context.applicationContext
        val wm = app.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val size = 120
        val lp = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.END
        lp.x = 24
        lp.y = 180

        val tv = TextView(app).apply {
            text = "停止"
            textSize = 16f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.rgb(200, 40, 40))
                setStroke(4, Color.WHITE)
            }
            var downRawX = 0f
            var downRawY = 0f
            var downLpX = 0
            var downLpY = 0
            var moved = false
            setOnTouchListener { _, e ->
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downRawX = e.rawX
                        downRawY = e.rawY
                        downLpX = lp.x
                        downLpY = lp.y
                        moved = false
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (e.rawX - downRawX).toInt()
                        val dy = (e.rawY - downRawY).toInt()
                        if (kotlin.math.abs(dx) + kotlin.math.abs(dy) > 12) moved = true
                        lp.x = (downLpX - dx).coerceAtLeast(0)
                        lp.y = (downLpY + dy).coerceAtLeast(0)
                        runCatching { wm.updateViewLayout(this, lp) }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!moved) {
                            AutomationService.stop(app)
                            hide(app)
                        }
                        true
                    }
                    else -> true
                }
            }
        }
        runCatching {
            wm.addView(tv, lp)
            view = tv
        }
    }

    fun hide(context: Context) {
        val app = context.applicationContext
        val wm = app.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        view?.let { runCatching { wm.removeView(it) } }
        view = null
    }
}
