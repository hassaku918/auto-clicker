package com.example.autoclickerblocker

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.*
import android.widget.TextView

object MarkerOverlay {
    private val views=mutableMapOf<Int,View>()

    fun add(context:Context, pointIndex:Int, point:ClickPoint, onMove:(Int,Float,Float)->Unit){
        val wm=context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val v=TextView(context).apply{
            text="${pointIndex+1}"
            textSize=14f
            setTextColor(Color.WHITE)
            gravity=Gravity.CENTER
            background=GradientDrawable().apply{
                shape=GradientDrawable.OVAL
                setColor(Color.rgb(40,110,220))
                setStroke(3,Color.WHITE)
            }
            var dx=0f;var dy=0f
            setOnTouchListener{_,e->
                when(e.action){
                    MotionEvent.ACTION_DOWN->{dx=e.rawX-x;dy=e.rawY-y;true}
                    MotionEvent.ACTION_MOVE->{
                        x=e.rawX-dx;y=e.rawY-dy
                        onMove(pointIndex,x+width/2f,y+height/2f)
                        true
                    }
                    else->true
                }
            }
        }
        val lp=WindowManager.LayoutParams(72,72,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            android.graphics.PixelFormat.TRANSLUCENT)
        lp.gravity=Gravity.TOP or Gravity.START
        lp.x=(point.x-36).toInt();lp.y=(point.y-36).toInt()
        runCatching{wm.addView(v,lp);views[pointIndex]=v}
    }

    fun clear(context:Context){
        val wm=context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        views.values.forEach{runCatching{wm.removeView(it)}}
        views.clear()
    }
}
