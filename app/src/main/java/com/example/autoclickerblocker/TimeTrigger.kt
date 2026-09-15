package com.example.autoclickerblocker

import android.app.*
import android.content.*
import java.util.*

object TimeTrigger {
    private const val REQUEST_CODE = 777

    fun schedule(context: Context) {
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("triggerTime", false)) {
            cancel(context)
            return
        }

        val h = prefs.getInt("hour", 18)
        val m = prefs.getInt("minute", 0)

        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, h)
            set(Calendar.MINUTE, m)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        val pi = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, TimeTriggerReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val am = context.getSystemService(AlarmManager::class.java)
        runCatching {
            am.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                cal.timeInMillis,
                pi
            )
        }
    }

    fun cancel(context: Context) {
        val pi = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, TimeTriggerReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        context.getSystemService(AlarmManager::class.java).cancel(pi)
    }
}

class TimeTriggerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("triggerTime", false)) return

        val duration = prefs.getLong("timeBlockDuration", 300_000L)
        val radius = prefs.getFloat("timeBlockRadius", 300f)

        ClickAccessibilityService.instance?.configureTimeTrigger(duration, radius)
        ClickAccessibilityService.instance?.triggerTimeBlock()

        // Schedule the next occurrence independently of the app-trigger settings.
        TimeTrigger.schedule(context)
    }
}
