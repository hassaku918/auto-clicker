package com.example.autoclickerblocker

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.*
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView

object RegisterOverlay {
    private var active: View? = null

    fun show(context: Context, onPoint: (Float, Float) -> Unit) {
        dismiss(context)
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val root = FrameLayout(context).apply {
            setBackgroundColor(Color.argb(80, 0, 40, 120))
        }
        val tip = TextView(context).apply {
            text = "画面をタップしてマーカー追加（何回でも）\n右下の完了で終了"
            setTextColor(Color.WHITE)
            textSize = 16f
            setPadding(28, 28, 28, 28)
            setBackgroundColor(Color.argb(180, 0, 0, 0))
        }
        val tipLp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.TOP }
        root.addView(tip, tipLp)

        val done = Button(context).apply {
            text = "完了"
            setOnClickListener { dismiss(context) }
            background = GradientDrawable().apply {
                cornerRadius = 24f
                setColor(Color.rgb(30, 136, 229))
            }
            setTextColor(Color.WHITE)
        }
        val doneLp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            setMargins(0, 0, 32, 48)
        }
        root.addView(done, doneLp)

        root.setOnTouchListener { _, e ->
            if (e.action == MotionEvent.ACTION_UP) {
                onPoint(e.rawX, e.rawY)
                tip.text = "追加した（またタップ可）\n右下の完了で終了"
            }
            true
        }

        val lp = WindowManager.LayoutParams(
            -1, -1,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        runCatching {
            wm.addView(root, lp)
            active = root
        }
    }

    fun dismiss(context: Context) {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        active?.let { runCatching { wm.removeView(it) } }
        active = null
    }
}
