package com.example.autoclickerblocker

import android.app.*
import android.content.*
import android.graphics.PixelFormat
import android.view.*

object RegisterOverlay {
    fun show(context: Context, onPoint: (Float,Float)->Unit) {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val view = object : View(context) {
            override fun onTouchEvent(e: MotionEvent): Boolean {
                if (e.action == MotionEvent.ACTION_UP) {
                    onPoint(e.rawX, e.rawY)
                    wm.removeView(this)
                }
                return true
            }
        }
        val lp = WindowManager.LayoutParams(
            -1,-1,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        runCatching { wm.addView(view,lp) }
    }
}
