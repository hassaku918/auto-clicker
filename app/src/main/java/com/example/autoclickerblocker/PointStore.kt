package com.example.autoclickerblocker

import android.content.SharedPreferences

object PointStore {
    fun save(prefs: SharedPreferences, points: List<ClickPoint>) {
        prefs.edit().putString("points",
            points.joinToString(";") { "${it.x},${it.y},${it.randomRadius}" }).apply()
    }
    fun load(prefs: SharedPreferences): List<ClickPoint> =
        (prefs.getString("points","") ?: "").split(";").mapNotNull {
            val a=it.split(",")
            if(a.size>=2) {
                val x=a[0].toFloatOrNull(); val y=a[1].toFloatOrNull()
                val r=if(a.size>=3) a[2].toFloatOrNull() ?: 0f else 0f
                if(x!=null && y!=null) ClickPoint(x,y,r) else null
            } else null
        }
}
