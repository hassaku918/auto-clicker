package com.example.autoclickerblocker

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.*
import android.widget.TextView

object MarkerOverlay {
    private val views = mutableMapOf<Int, View>()

    fun add(context: Context, pointIndex: Int, point: ClickPoint, onMove: (Int, Float, Float) -> Unit) {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val size = 72
        val lp = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.START
        lp.x = (point.x - size / 2f).toInt()
        lp.y = (point.y - size / 2f).toInt()

        val v = TextView(context).apply {
            text = "${pointIndex + 1}"
            textSize = 14f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.rgb(40, 110, 220))
                setStroke(3, Color.WHITE)
            }
            var downRawX = 0f
            var downRawY = 0f
            var downLpX = 0
            var downLpY = 0
            setOnTouchListener { _, e ->
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downRawX = e.rawX
                        downRawY = e.rawY
                        downLpX = lp.x
                        downLpY = lp.y
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        lp.x = (downLpX + (e.rawX - downRawX)).toInt()
                        lp.y = (downLpY + (e.rawY - downRawY)).toInt()
                        runCatching { wm.updateViewLayout(this, lp) }
                        onMove(pointIndex, lp.x + size / 2f, lp.y + size / 2f)
                        true
                    }
                    else -> true
                }
            }
        }
        runCatching {
            wm.addView(v, lp)
            views[pointIndex] = v
        }
    }

    fun clear(context: Context) {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        views.values.forEach { runCatching { wm.removeView(it) } }
        views.clear()
    }
}
