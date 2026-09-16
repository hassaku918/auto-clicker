package com.example.autoclickerblocker

import android.content.Context

object SettingSlots {
    private const val LIST = "slotNames"
    private const val SRC = "settings"

    private val KEYS = listOf(
        "interval", "randomRadius", "points",
        "triggerApp", "target", "appBlockDuration", "appBlockRadius",
        "blockTopPercent", "blockYPercent", "relaunchOnExit", "resumeClickerAfterBlock",
        "triggerTime", "hour", "minute", "timeBlockDuration", "timeBlockRadius",
        "timeBlockYPercent", "timeBlockHeightPercent",
        "colorUnblock", "colorX", "colorY", "colorTarget", "colorTol"
    )

    fun names(context: Context): List<String> {
        val raw = context.getSharedPreferences(SRC, Context.MODE_PRIVATE).getString(LIST, "") ?: ""
        return raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    fun save(context: Context, name: String) {
        val n = sanitize(name) ?: return
        val src = context.getSharedPreferences(SRC, Context.MODE_PRIVATE)
        val dst = context.getSharedPreferences("slot_$n", Context.MODE_PRIVATE).edit().clear()
        val all = src.all
        KEYS.forEach { k ->
            when (val v = all[k]) {
                is String -> dst.putString(k, v)
                is Int -> dst.putInt(k, v)
                is Long -> dst.putLong(k, v)
                is Float -> dst.putFloat(k, v)
                is Boolean -> dst.putBoolean(k, v)
            }
        }
        dst.apply()
        val list = (names(context) + n).distinct()
        src.edit().putString(LIST, list.joinToString(",")).apply()
    }

    fun load(context: Context, name: String) {
        val n = sanitize(name) ?: return
        val src = context.getSharedPreferences("slot_$n", Context.MODE_PRIVATE)
        val dst = context.getSharedPreferences(SRC, Context.MODE_PRIVATE).edit()
        KEYS.forEach { k ->
            if (!src.contains(k)) return@forEach
            when (val v = src.all[k]) {
                is String -> dst.putString(k, v)
                is Int -> dst.putInt(k, v)
                is Long -> dst.putLong(k, v)
                is Float -> dst.putFloat(k, v)
                is Boolean -> dst.putBoolean(k, v)
            }
        }
        dst.apply()
    }

    fun delete(context: Context, name: String) {
        val n = sanitize(name) ?: return
        context.getSharedPreferences("slot_$n", Context.MODE_PRIVATE).edit().clear().apply()
        val left = names(context).filter { it != n }
        context.getSharedPreferences(SRC, Context.MODE_PRIVATE)
            .edit().putString(LIST, left.joinToString(",")).apply()
    }

    private fun sanitize(name: String): String? {
        val n = name.trim().replace(Regex("[^0-9A-Za-z_\u3040-\u30ff\u4e00-\u9faf-]+"), "_")
        return n.take(24).ifEmpty { null }
    }
}
