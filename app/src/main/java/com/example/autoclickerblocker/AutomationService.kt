package com.example.autoclickerblocker

import android.app.*
import android.content.*
import android.os.*
import androidx.core.app.NotificationCompat
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class AutomationService:Service(){
    private var running=false
    private var worker:Thread?=null

    override fun onCreate(){
        super.onCreate()
        val ch=NotificationChannel("clicker","AutoClicker",NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        val stop=PendingIntent.getService(this,99,
            Intent(this,AutomationService::class.java).setAction("STOP"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        startForeground(10,NotificationCompat.Builder(this,"clicker")
            .setContentTitle("AutoClickerBlocker")
            .setContentText("自動クリック実行中（停止するまで）")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .addAction(android.R.drawable.ic_media_pause,"停止",stop).build())
    }

    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int{
        if(intent?.action=="STOP"){stopSelf();return START_NOT_STICKY}
        if(!running){
            running=true
            val prefs=getSharedPreferences("settings",MODE_PRIVATE)
            val interval=prefs.getLong("interval",500L).coerceAtLeast(50)
            val points=PointStore.load(prefs)
            worker=Thread{
                var i=0
                while(running){
                    if(points.isNotEmpty()){
                        val p=points[i%points.size]
                        val angle=Random.nextDouble(0.0,Math.PI*2)
                        val rad=if(p.randomRadius>0) Math.sqrt(Random.nextDouble())*p.randomRadius else 0.0
                        val x=(p.x+cos(angle)*rad).toFloat()
                        val y=(p.y+sin(angle)*rad).toFloat()
                        val svc=ClickAccessibilityService.instance
                        if(svc!=null && !svc.isBlocked(x,y)){
                            svc.click(x,y)
                        }
                        i++
                    }
                    try{Thread.sleep(interval)}catch(_:InterruptedException){break}
                }
                if(running) stopSelf()
            }.also{it.start()}
        }
        return START_NOT_STICKY
    }
    override fun onDestroy(){running=false;worker?.interrupt();worker=null;super.onDestroy()}
    override fun onBind(intent:Intent?)=null

    companion object{
        fun startClicker(c:Context)=c.startForegroundService(Intent(c,AutomationService::class.java))
        fun stop(c:Context)=c.stopService(Intent(c,AutomationService::class.java))
    }
}
