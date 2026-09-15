package com.example.autoclickerblocker

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    private val prefs by lazy { getSharedPreferences("settings", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Screen() }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun Screen() {
        var interval by remember { mutableStateOf(prefs.getLong("interval", 500).toString()) }
        var randomRadius by remember { mutableStateOf(prefs.getFloat("randomRadius", 0f).toString()) }

        var appEnabled by remember { mutableStateOf(prefs.getBoolean("triggerApp", false)) }
        var target by remember { mutableStateOf(prefs.getString("target", "") ?: "") }
        var appDuration by remember { mutableStateOf(prefs.getLong("appBlockDuration", 30000).toString()) }
        var appRadius by remember { mutableStateOf(prefs.getFloat("appBlockRadius", 0f).toString()) }
        var topPercent by remember { mutableStateOf(prefs.getFloat("blockTopPercent", 76f).toString()) }
        var yPercent by remember { mutableStateOf(prefs.getFloat("blockYPercent", 0f).toString()) }
        var relaunchOnExit by remember { mutableStateOf(prefs.getBoolean("relaunchOnExit", true)) }
        var resumeClicker by remember { mutableStateOf(prefs.getBoolean("resumeClickerAfterBlock", true)) }

        var timeEnabled by remember { mutableStateOf(prefs.getBoolean("triggerTime", false)) }
        var hour by remember { mutableStateOf(prefs.getInt("hour", 18).toString()) }
        var minute by remember { mutableStateOf(prefs.getInt("minute", 0).toString()) }
        var timeDuration by remember { mutableStateOf(prefs.getLong("timeBlockDuration", 300000).toString()) }
        var timeRadius by remember { mutableStateOf(prefs.getFloat("timeBlockRadius", 0f).toString()) }
        var timeYPercent by remember { mutableStateOf(prefs.getFloat("timeBlockYPercent", 0f).toString()) }
        var timeHeightPercent by remember { mutableStateOf(prefs.getFloat("timeBlockHeightPercent", 76f).toString()) }

        var points by remember { mutableStateOf(PointStore.load(prefs)) }
        var menuOpen by remember { mutableStateOf(false) }

        fun refreshMarkers() {
            MarkerOverlay.clear(this@MainActivity)
            points.forEachIndexed { i, p ->
                MarkerOverlay.add(this@MainActivity, i, p) { idx, nx, ny ->
                    points = points.toMutableList().also { it[idx] = it[idx].copy(x = nx, y = ny) }
                    PointStore.save(prefs, points)
                }
            }
        }

        fun save() {
            prefs.edit()
                .putLong("interval", interval.toLongOrNull()?.coerceAtLeast(50) ?: 500)
                .putFloat("randomRadius", randomRadius.toFloatOrNull()?.coerceAtLeast(0f) ?: 0f)
                .putBoolean("triggerApp", appEnabled)
                .putString("target", target)
                .putLong("appBlockDuration", appDuration.toLongOrNull()?.coerceAtLeast(100) ?: 30000)
                .putFloat("appBlockRadius", appRadius.toFloatOrNull()?.coerceAtLeast(0f) ?: 0f)
                .putFloat("blockTopPercent", topPercent.toFloatOrNull()?.coerceIn(0f, 100f) ?: 76f)
                .putFloat("blockYPercent", yPercent.toFloatOrNull()?.coerceIn(0f, 100f) ?: 0f)
                .putBoolean("relaunchOnExit", relaunchOnExit)
                .putBoolean("resumeClickerAfterBlock", resumeClicker)
                .putBoolean("triggerTime", timeEnabled)
                .putInt("hour", hour.toIntOrNull()?.coerceIn(0, 23) ?: 18)
                .putInt("minute", minute.toIntOrNull()?.coerceIn(0, 59) ?: 0)
                .putLong("timeBlockDuration", timeDuration.toLongOrNull()?.coerceAtLeast(100) ?: 300000)
                .putFloat("timeBlockRadius", timeRadius.toFloatOrNull()?.coerceAtLeast(0f) ?: 0f)
                .putFloat("timeBlockYPercent", timeYPercent.toFloatOrNull()?.coerceIn(0f, 100f) ?: 0f)
                .putFloat("timeBlockHeightPercent", timeHeightPercent.toFloatOrNull()?.coerceIn(0f, 100f) ?: 76f)
                .apply()

            PointStore.save(prefs, points)
            ClickAccessibilityService.instance?.configureAppTrigger(
                appEnabled, target,
                appDuration.toLongOrNull() ?: 30000,
                appRadius.toFloatOrNull() ?: 0f,
                topPercent.toFloatOrNull() ?: 76f,
                yPercent.toFloatOrNull() ?: 0f,
                relaunchOnExit, resumeClicker
            )
            ClickAccessibilityService.instance?.configureTimeTrigger(
                timeDuration.toLongOrNull() ?: 300000,
                timeRadius.toFloatOrNull() ?: 0f,
                timeYPercent.toFloatOrNull() ?: 0f,
                timeHeightPercent.toFloatOrNull() ?: 76f
            )
            if (timeEnabled) TimeTrigger.schedule(this@MainActivity)
            else TimeTrigger.cancel(this@MainActivity)
        }

        Scaffold(topBar = { TopAppBar(title = { Text("AutoClickerBlocker v6") }) }) { pad ->
            Box(Modifier.padding(pad)) {
                LazyColumn(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        Button(onClick = { menuOpen = true }) { Text("☰ メニュー") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(text = { Text("クリッカー起動") }, onClick = {
                                save(); AutomationService.startClicker(this@MainActivity); menuOpen = false
                            })
                            DropdownMenuItem(text = { Text("クリッカー停止") }, onClick = {
                                AutomationService.stop(this@MainActivity); menuOpen = false
                            })
                            DropdownMenuItem(text = { Text("マーカー追加（画面をタップして配置）") }, onClick = {
                                menuOpen = false
                                RegisterOverlay.show(this@MainActivity) { tx, ty ->
                                    points = points + ClickPoint(tx, ty, randomRadius.toFloatOrNull() ?: 0f)
                                    PointStore.save(prefs, points)
                                    refreshMarkers()
                                }
                            })
                            DropdownMenuItem(text = { Text("マーカー削除（最後）") }, onClick = {
                                if (points.isNotEmpty()) {
                                    points = points.dropLast(1)
                                    PointStore.save(prefs, points)
                                    refreshMarkers()
                                }
                                menuOpen = false
                            })
                        }
                        Text("クリッカー", style = MaterialTheme.typography.titleLarge)
                        OutlinedTextField(interval, { interval = it }, label = { Text("クリック間隔 (ms)") })
                        OutlinedTextField(randomRadius, { randomRadius = it }, label = { Text("ランダム範囲 半径(px) / 0=固定") })
                    }
                    itemsIndexed(points) { i, p ->
                        Text("マーカー ${i + 1}: (${p.x.toInt()}, ${p.y.toInt()})")
                    }
                    item {
                        HorizontalDivider()
                        Text("🔄 アプリ復帰", style = MaterialTheme.typography.titleLarge)
                        Row { Checkbox(appEnabled, { appEnabled = it }); Text("有効") }
                        OutlinedTextField(target, { target = it }, label = { Text("対象 package 名") })
                        Row { Checkbox(relaunchOnExit, { relaunchOnExit = it }); Text("終了・落ちを検知したら再起動") }
                        Row { Checkbox(resumeClicker, { resumeClicker = it }); Text("封鎖中も封鎖外をクリック") }
                        OutlinedTextField(yPercent, { yPercent = it }, label = { Text("Y値 (%)  封鎖の始点。上端=0") })
                        OutlinedTextField(topPercent, { topPercent = it }, label = { Text("縦の長さ (%)  0=円封鎖") })
                        OutlinedTextField(appDuration, { appDuration = it }, label = { Text("封鎖時間(ms)") })
                        OutlinedTextField(appRadius, { appRadius = it }, label = { Text("円封鎖半径(px)") })

                        HorizontalDivider()
                        Text("⏰ 指定時刻トリガー", style = MaterialTheme.typography.titleLarge)
                        Row { Checkbox(timeEnabled, { timeEnabled = it }); Text("有効") }
                        OutlinedTextField(hour, { hour = it }, label = { Text("時 (0-23)") })
                        OutlinedTextField(minute, { minute = it }, label = { Text("分 (0-59)") })
                        OutlinedTextField(timeYPercent, { timeYPercent = it }, label = { Text("Y値 (%)  封鎖の始点。上端=0") })
                        OutlinedTextField(timeHeightPercent, { timeHeightPercent = it }, label = { Text("縦の長さ (%)  0=円封鎖") })
                        OutlinedTextField(timeDuration, { timeDuration = it }, label = { Text("封鎖時間(ms)") })
                        OutlinedTextField(timeRadius, { timeRadius = it }, label = { Text("円封鎖半径(px)") })

                        Button(onClick = { save() }) { Text("設定を保存") }
                        Button(onClick = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) { Text("アクセシビリティ設定") }
                        Button(onClick = { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)) }) { Text("オーバーレイ権限設定") }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            Button(onClick = {
                                startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
                            }) { Text("正確なアラームの権限設定") }
                        }
                        HorizontalDivider()
                        Text("📦 プリセット", style = MaterialTheme.typography.titleLarge)
                        Button(onClick = { save(); PresetStore.save(prefs, "default", points) }) { Text("現在の設定を保存") }
                        Button(onClick = { points = PresetStore.load(prefs, "default"); refreshMarkers() }) { Text("保存した設定を読み込む") }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        MarkerOverlay.clear(this)
        super.onDestroy()
    }
}
