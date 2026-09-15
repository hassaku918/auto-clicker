package com.example.autoclickerblocker

import android.content.SharedPreferences

object PresetStore {
    fun save(prefs: SharedPreferences, name: String, points: List<ClickPoint>) {
        prefs.edit().putString(
            "preset_$name",
            points.joinToString(";") { "${it.x},${it.y}" }
        ).apply()
    }

    fun load(prefs: SharedPreferences, name: String): List<ClickPoint> =
        (prefs.getString("preset_$name", "") ?: "").split(";")
            .mapNotNull {
                val a = it.split(",")
                if (a.size == 2) {
                    val x=a[0].toFloatOrNull(); val y=a[1].toFloatOrNull()
                    if (x != null && y != null) ClickPoint(x,y) else null
                } else null
            }
}
