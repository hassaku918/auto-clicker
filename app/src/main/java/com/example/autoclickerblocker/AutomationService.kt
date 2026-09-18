package com.example.autoclickerblocker

import android.app.*
import android.content.*
import android.os.*
import androidx.core.app.NotificationCompat
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class AutomationService : Service() {
    @Volatile private var running = false
    private var worker: Thread? = null

    override fun onCreate() {
        super.onCreate()
        val ch = NotificationChannel("clicker", "AutoClicker", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        val stop = PendingIntent.getService(
            this, 99,
            Intent(this, AutomationService::class.java).setAction("STOP"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        startForeground(
            10,
            NotificationCompat.Builder(this, "clicker")
                .setContentTitle("AutoClickerBlocker")
                .setContentText("実行中 — 通知の停止 or 画面の赤い停止ボタン")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .addAction(android.R.drawable.ic_media_pause, "停止", stop)
                .setOngoing(true)
                .build()
        )
        FloatingStopOverlay.show(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!running) {
            running = true
            val prefs = getSharedPreferences("settings", MODE_PRIVATE)
            worker = Thread {
                var i = 0
                while (running) {
                    val started = SystemClock.elapsedRealtime()
                    val interval = prefs.getLong("interval", 500L).coerceAtLeast(20L)
                    val points = PointStore.load(prefs)
                    if (points.isNotEmpty()) {
                        val p = points[i % points.size]
                        val angle = Random.nextDouble(0.0, Math.PI * 2)
                        val rad = if (p.randomRadius > 0) Math.sqrt(Random.nextDouble()) * p.randomRadius else 0.0
                        val x = (p.x + cos(angle) * rad).toFloat()
                        val y = (p.y + sin(angle) * rad).toFloat()
                        val svc = ClickAccessibilityService.instance
                        if (svc != null && !svc.isBlocked(x, y)) {
                            svc.click(x, y)
                        }
                        i++
                    }
                    val elapsed = SystemClock.elapsedRealtime() - started
                    val sleepFor = interval - elapsed
                    if (sleepFor > 0) {
                        try {
                            Thread.sleep(sleepFor)
                        } catch (_: InterruptedException) {
                            break
                        }
                    }
                }
            }.also { it.start() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        worker?.interrupt()
        worker = null
        FloatingStopOverlay.hide(this)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null

    companion object {
        fun startClicker(c: Context) {
            c.startForegroundService(Intent(c, AutomationService::class.java))
        }
        fun stop(c: Context) {
            c.startService(Intent(c, AutomationService::class.java).setAction("STOP"))
            c.stopService(Intent(c, AutomationService::class.java))
            FloatingStopOverlay.hide(c)
        }
    }
}
