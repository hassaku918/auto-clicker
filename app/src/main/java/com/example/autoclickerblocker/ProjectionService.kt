package com.example.autoclickerblocker

import android.app.*
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class ProjectionService : Service() {
    override fun onCreate() {
        super.onCreate()
        val ch = NotificationChannel("grab", "ScreenGrab", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        val n = NotificationCompat.Builder(this, "grab")
            .setContentTitle("AutoClickerBlocker")
            .setContentText("色監視のため画面を読んでいます")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(21, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(21, n)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val code = intent?.getIntExtra(EXTRA_CODE, 0) ?: 0
        val data = intent?.getParcelableExtra<Intent>(EXTRA_DATA)
        if (code != 0 && data != null) {
            runCatching {
                val mp = getSystemService(MediaProjectionManager::class.java)
                    .getMediaProjection(code, data)
                if (mp != null) ScreenGrab.start(this, mp)
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        ScreenGrab.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val EXTRA_CODE = "code"
        const val EXTRA_DATA = "data"
    }
}
