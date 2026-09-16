package com.example.autoclickerblocker

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    private val prefs by lazy { getSharedPreferences("settings", MODE_PRIVATE) }

    private val captureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val i = Intent(this, ProjectionService::class.java)
            i.putExtra(ProjectionService.EXTRA_CODE, result.resultCode)
            i.putExtra(ProjectionService.EXTRA_DATA, result.data)
            startForegroundService(i)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Screen() }
    }

    private fun parseColorOrWhite(raw: String): Int {
        val t = raw.trim().removePrefix("#")
        return runCatching { Color.parseColor("#" + t.padStart(6, '0').take(6)) }.getOrDefault(Color.WHITE)
    }

    private fun colorToHex(c: Int): String = String.format("#%06X", 0xFFFFFF and c)

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
        var colorEnabled by remember { mutableStateOf(prefs.getBoolean("colorUnblock", false)) }
        var colorX by remember { mutableStateOf(prefs.getFloat("colorX", 0f).toString()) }
        var colorY by remember { mutableStateOf(prefs.getFloat("colorY", 0f).toString()) }
        var colorHex by remember { mutableStateOf(colorToHex(prefs.getInt("colorTarget", Color.WHITE))) }
        var colorTol by remember { mutableStateOf(prefs.getInt("colorTol", 25).toString()) }
        var points by remember { mutableStateOf(PointStore.load(prefs)) }
        var menuOpen by remember { mutableStateOf(false) }
        var slotName by remember { mutableStateOf("save1") }
        var slots by remember { mutableStateOf(SettingSlots.names(this@MainActivity)) }

        fun refreshMarkers() {
            MarkerOverlay.clear(this@MainActivity)
            points.forEachIndexed { i, p ->
                MarkerOverlay.add(this@MainActivity, i, p) { idx, nx, ny ->
                    points = points.toMutableList().also { it[idx] = it[idx].copy(x = nx, y = ny) }
                    PointStore.save(prefs, points)
                }
            }
        }

        fun applyFromPrefs() {
            interval = prefs.getLong("interval", 500).toString()
            randomRadius = prefs.getFloat("randomRadius", 0f).toString()
            appEnabled = prefs.getBoolean("triggerApp", false)
            target = prefs.getString("target", "") ?: ""
            appDuration = prefs.getLong("appBlockDuration", 30000).toString()
            appRadius = prefs.getFloat("appBlockRadius", 0f).toString()
            topPercent = prefs.getFloat("blockTopPercent", 76f).toString()
            yPercent = prefs.getFloat("blockYPercent", 0f).toString()
            relaunchOnExit = prefs.getBoolean("relaunchOnExit", true)
            resumeClicker = prefs.getBoolean("resumeClickerAfterBlock", true)
            timeEnabled = prefs.getBoolean("triggerTime", false)
            hour = prefs.getInt("hour", 18).toString()
            minute = prefs.getInt("minute", 0).toString()
            timeDuration = prefs.getLong("timeBlockDuration", 300000).toString()
            timeRadius = prefs.getFloat("timeBlockRadius", 0f).toString()
            timeYPercent = prefs.getFloat("timeBlockYPercent", 0f).toString()
            timeHeightPercent = prefs.getFloat("timeBlockHeightPercent", 76f).toString()
            colorEnabled = prefs.getBoolean("colorUnblock", false)
            colorX = prefs.getFloat("colorX", 0f).toString()
            colorY = prefs.getFloat("colorY", 0f).toString()
            colorHex = colorToHex(prefs.getInt("colorTarget", Color.WHITE))
            colorTol = prefs.getInt("colorTol", 25).toString()
            points = PointStore.load(prefs)
            refreshMarkers()
        }

        fun save() {
            val parsedColor = parseColorOrWhite(colorHex)
            prefs.edit()
                .putLong("interval", interval.toLongOrNull()?.coerceAtLeast(50) ?: 500)
                .putFloat("randomRadius", randomRadius.toFloatOrNull()?.coerceAtLeast(0f) ?: 0f)
                .putBoolean("triggerApp", appEnabled).putString("target", target)
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
                .putBoolean("colorUnblock", colorEnabled)
                .putFloat("colorX", colorX.toFloatOrNull() ?: 0f)
                .putFloat("colorY", colorY.toFloatOrNull() ?: 0f)
                .putInt("colorTarget", parsedColor)
                .putInt("colorTol", colorTol.toIntOrNull()?.coerceIn(0, 255) ?: 25)
                .apply()
            PointStore.save(prefs, points)
            ClickAccessibilityService.instance?.configureAppTrigger(
                appEnabled, target, appDuration.toLongOrNull() ?: 30000,
                appRadius.toFloatOrNull() ?: 0f, topPercent.toFloatOrNull() ?: 76f,
                yPercent.toFloatOrNull() ?: 0f, relaunchOnExit, resumeClicker
            )
            ClickAccessibilityService.instance?.configureTimeTrigger(
                timeDuration.toLongOrNull() ?: 300000, timeRadius.toFloatOrNull() ?: 0f,
                timeYPercent.toFloatOrNull() ?: 0f, timeHeightPercent.toFloatOrNull() ?: 76f
            )
            ClickAccessibilityService.instance?.configureColorUnblock(
                colorEnabled, colorX.toFloatOrNull() ?: 0f, colorY.toFloatOrNull() ?: 0f,
                parsedColor, colorTol.toIntOrNull() ?: 25
            )
            if (timeEnabled) TimeTrigger.schedule(this@MainActivity) else TimeTrigger.cancel(this@MainActivity)
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
                                    PointStore.save(prefs, points); refreshMarkers()
                                }
                            })
                            DropdownMenuItem(text = { Text("マーカー削除（最後）") }, onClick = {
                                if (points.isNotEmpty()) {
                                    points = points.dropLast(1); PointStore.save(prefs, points); refreshMarkers()
                                }
                                menuOpen = false
                            })
                        }
                        Text("クリッカー", style = MaterialTheme.typography.titleLarge)
                        OutlinedTextField(interval, { interval = it }, label = { Text("クリック間隔 (ms)") })
                        OutlinedTextField(randomRadius, { randomRadius = it }, label = { Text("ランダム範囲 半径(px) / 0=固定") })
                    }
                    itemsIndexed(points) { i, p -> Text("マーカー ${i + 1}: (${p.x.toInt()}, ${p.y.toInt()})") }
                    item {
                        HorizontalDivider()
                        Text("🔄 アプリ復帰", style = MaterialTheme.typography.titleLarge)
                        Row { Checkbox(appEnabled, { appEnabled = it }); Text("有効") }
                        OutlinedTextField(target, { target = it }, label = { Text("対象 package 名") })
                        Row { Checkbox(relaunchOnExit, { relaunchOnExit = it }); Text("終了・落ちを検知したら再起動") }
                        Row { Checkbox(resumeClicker, { resumeClicker = it }); Text("封鎖中も封鎖外をクリック") }
                        OutlinedTextField(yPercent, { yPercent = it }, label = { Text("Y値 (%)") })
                        OutlinedTextField(topPercent, { topPercent = it }, label = { Text("縦の長さ (%)  0=円封鎖") })
                        OutlinedTextField(appDuration, { appDuration = it }, label = { Text("封鎖時間(ms)") })
                        OutlinedTextField(appRadius, { appRadius = it }, label = { Text("円封鎖半径(px)") })
                        HorizontalDivider()
                        Text("⏰ 指定時刻トリガー", style = MaterialTheme.typography.titleLarge)
                        Row { Checkbox(timeEnabled, { timeEnabled = it }); Text("有効") }
                        OutlinedTextField(hour, { hour = it }, label = { Text("時 (0-23)") })
                        OutlinedTextField(minute, { minute = it }, label = { Text("分 (0-59)") })
                        OutlinedTextField(timeYPercent, { timeYPercent = it }, label = { Text("Y値 (%)") })
                        OutlinedTextField(timeHeightPercent, { timeHeightPercent = it }, label = { Text("縦の長さ (%)  0=円封鎖") })
                        OutlinedTextField(timeDuration, { timeDuration = it }, label = { Text("封鎖時間(ms)") })
                        OutlinedTextField(timeRadius, { timeRadius = it }, label = { Text("円封鎖半径(px)") })
                        HorizontalDivider()
                        Text("🎨 色で封鎖解除", style = MaterialTheme.typography.titleLarge)
                        Row { Checkbox(colorEnabled, { colorEnabled = it }); Text("有効") }
                        Button(onClick = {
                            val mpm = getSystemService(MediaProjectionManager::class.java)
                            captureLauncher.launch(mpm.createScreenCaptureIntent())
                        }) { Text("画面取得を許可（Android 9用）") }
                        OutlinedTextField(colorX, { colorX = it }, label = { Text("監視X (px)") })
                        OutlinedTextField(colorY, { colorY = it }, label = { Text("監視Y (px)") })
                        Button(onClick = {
                            RegisterOverlay.show(this@MainActivity) { tx, ty ->
                                colorX = tx.toInt().toString(); colorY = ty.toInt().toString()
                            }
                        }) { Text("画面タップで座標を取る") }
                        OutlinedTextField(colorHex, { colorHex = it }, label = { Text("目標色  #RRGGBB") })
                        OutlinedTextField(colorTol, { colorTol = it }, label = { Text("許容差 0-255") })
                        Button(onClick = { save() }) { Text("設定を保存") }
                        Button(onClick = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) { Text("アクセシビリティ設定") }
                        Button(onClick = { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)) }) { Text("オーバーレイ権限設定") }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            Button(onClick = {
                                startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
                            }) { Text("正確なアラームの権限設定") }
                        }
                        HorizontalDivider()
                        Text("💾 セーブデータ", style = MaterialTheme.typography.titleLarge)
                        Text("マーカー・間隔・封鎖・色解除まで一緒に保存します。", style = MaterialTheme.typography.bodySmall)
                        OutlinedTextField(slotName, { slotName = it }, label = { Text("セーブ名") })
                        Button(onClick = {
                            save()
                            SettingSlots.save(this@MainActivity, slotName)
                            slots = SettingSlots.names(this@MainActivity)
                        }) { Text("この名前でセーブ") }
                    }
                    items(slots) { name ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                SettingSlots.load(this@MainActivity, name)
                                slotName = name
                                applyFromPrefs()
                                save()
                            }) { Text("読込 $name") }
                            Button(onClick = {
                                SettingSlots.delete(this@MainActivity, name)
                                slots = SettingSlots.names(this@MainActivity)
                            }) { Text("削除") }
                        }
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
