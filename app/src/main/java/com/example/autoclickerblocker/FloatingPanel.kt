package com.example.autoclickerblocker

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.text.InputType
import android.view.*
import android.widget.*

object FloatingPanel {
    private var root: View? = null

    fun show(context: Context) {
        if (root != null) return
        val app = context.applicationContext
        val wm = app.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)

        val scroll = ScrollView(app)
        val box = LinearLayout(app).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 16, 20, 16)
            setBackgroundColor(Color.argb(240, 30, 30, 35))
        }

        fun label(t: String) = TextView(app).apply {
            text = t
            setTextColor(Color.WHITE)
            textSize = 12f
            setPadding(0, 8, 0, 2)
        }

        fun field(def: String, number: Boolean = false): EditText {
            return EditText(app).apply {
                setText(def)
                setTextColor(Color.WHITE)
                setHintTextColor(Color.GRAY)
                setBackgroundColor(Color.argb(80, 255, 255, 255))
                if (number) inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            }
        }

        val enabled = CheckBox(app).apply {
            text = "監視オン"
            setTextColor(Color.WHITE)
            isChecked = prefs.getBoolean("enabled", false)
        }
        val target = field(prefs.getString("target", "") ?: "")
        val top = field(prefs.getFloat("topPercent", 0f).toString(), true)
        val height = field(prefs.getFloat("heightPercent", 20f).toString(), true)
        val left = field(prefs.getFloat("leftPercent", 0f).toString(), true)
        val width = field(prefs.getFloat("widthPercent", 100f).toString(), true)
        val duration = field(prefs.getLong("blockDurationMs", 30_000L).toString(), true)

        fun save() {
            prefs.edit()
                .putBoolean("enabled", enabled.isChecked)
                .putString("target", target.text.toString().trim())
                .putFloat("topPercent", top.text.toString().toFloatOrNull() ?: 0f)
                .putFloat("heightPercent", height.text.toString().toFloatOrNull() ?: 20f)
                .putFloat("leftPercent", left.text.toString().toFloatOrNull() ?: 0f)
                .putFloat("widthPercent", width.text.toString().toFloatOrNull() ?: 100f)
                .putLong("blockDurationMs", duration.text.toString().toLongOrNull()?.coerceAtLeast(100L) ?: 30_000L)
                .commit()
            Toast.makeText(app, "保存した", Toast.LENGTH_SHORT).show()
        }

        box.addView(TextView(app).apply {
            text = "ScreenBlocker 設定"
            setTextColor(Color.WHITE)
            textSize = 16f
        })
        box.addView(enabled)
        box.addView(label("対象 package 名"))
        box.addView(target)
        box.addView(label("封鎖 上端 %"))
        box.addView(top)
        box.addView(label("封鎖 高さ %"))
        box.addView(height)
        box.addView(label("封鎖 左端 %"))
        box.addView(left)
        box.addView(label("封鎖 幅 %"))
        box.addView(width)
        box.addView(label("封鎖時間 (ms)  経過で自動解除"))
        box.addView(duration)

        val row = LinearLayout(app).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(Button(app).apply {
            text = "保存"
            setOnClickListener { save() }
        })
        row.addView(Button(app).apply {
            text = "今すぐ封鎖"
            setOnClickListener {
                save()
                BlockAccessibilityService.instance?.startBlock()
                    ?: Toast.makeText(app, "ユーザー補助をオンにして", Toast.LENGTH_SHORT).show()
            }
        })
        row.addView(Button(app).apply {
            text = "解除"
            setOnClickListener { BlockAccessibilityService.instance?.unblock() }
        })
        row.addView(Button(app).apply {
            text = "閉じる"
            setOnClickListener { hide(app) }
        })
        box.addView(row)
        scroll.addView(box)

        val lp = WindowManager.LayoutParams(
            (app.resources.displayMetrics.widthPixels * 0.9f).toInt(),
            (app.resources.displayMetrics.heightPixels * 0.55f).toInt(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.CENTER
        runCatching {
            wm.addView(scroll, lp)
            root = scroll
        }
    }

    fun hide(context: Context) {
        val app = context.applicationContext
        val wm = app.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        root?.let { runCatching { wm.removeView(it) } }
        root = null
    }
}
