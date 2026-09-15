package com.example.autoclickerblocker

import android.app.*
import android.content.*
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
        var appRadius by remember { mutableStateOf(prefs.getFloat("appBlockRadius", 150f).toString()) }

        var timeEnabled by remember { mutableStateOf(prefs.getBoolean("triggerTime", false)) }
        var hour by remember { mutableStateOf(prefs.getInt("hour", 18).toString()) }
        var minute by remember { mutableStateOf(prefs.getInt("minute", 0).toString()) }
        var timeDuration by remember { mutableStateOf(prefs.getLong("timeBlockDuration", 300000).toString()) }
        var timeRadius by remember { mutableStateOf(prefs.getFloat("timeBlockRadius", 300f).toString()) }

        var points by remember { mutableStateOf(PointStore.load(prefs)) }
        var menuOpen by remember { mutableStateOf(false) }

        fun refreshMarkers() {
            MarkerOverlay.clear(this@MainActivity)
            points.forEachIndexed { i, p ->
                MarkerOverlay.add(this@MainActivity, i, p) { idx, nx, ny ->
                    points = points.toMutableList().also {
                        it[idx] = it[idx].copy(x = nx, y = ny)
                    }
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
                .putFloat("appBlockRadius", appRadius.toFloatOrNull()?.coerceAtLeast(1f) ?: 150f)
                .putBoolean("triggerTime", timeEnabled)
                .putInt("hour", hour.toIntOrNull()?.coerceIn(0, 23) ?: 18)
                .putInt("minute", minute.toIntOrNull()?.coerceIn(0, 59) ?: 0)
                .putLong("timeBlockDuration", timeDuration.toLongOrNull()?.coerceAtLeast(100) ?: 300000)
                .putFloat("timeBlockRadius", timeRadius.toFloatOrNull()?.coerceAtLeast(1f) ?: 300f)
                .apply()

            PointStore.save(prefs, points)

            ClickAccessibilityService.instance?.configureAppTrigger(
                appEnabled,
                target,
                appDuration.toLongOrNull() ?: 30000,
                appRadius.toFloatOrNull() ?: 150f
            )
            ClickAccessibilityService.instance?.configureTimeTrigger(
                timeDuration.toLongOrNull() ?: 300000,
                timeRadius.toFloatOrNull() ?: 300f
            )

            if (timeEnabled) TimeTrigger.schedule(this@MainActivity)
            else TimeTrigger.cancel(this@MainActivity)
        }

        Scaffold(
            topBar = { TopAppBar(title = { Text("AutoClickerBlocker v5") }) }
        ) { pad ->
            Box(Modifier.padding(pad)) {
                LazyColumn(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Button(onClick = { menuOpen = true }) { Text("☰ メニュー") }

                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("クリッカー起動") },
                                onClick = {
                                    save()
                                    AutomationService.startClicker(this@MainActivity)
                                    menuOpen = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("クリッカー停止") },
                                onClick = {
                                    AutomationService.stop(this@MainActivity)
                                    menuOpen = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("マーカー追加（画面をタップして配置）") },
                                onClick = {
                                    menuOpen = false
                                    RegisterOverlay.show(this@MainActivity) { tx, ty ->
                                        val p = ClickPoint(
                                            tx, ty,
                                            randomRadius.toFloatOrNull() ?: 0f
                                        )
                                        points = points + p
                                        PointStore.save(prefs, points)
                                        refreshMarkers()
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("マーカー削除（最後）") },
                                onClick = {
                                    if (points.isNotEmpty()) {
                                        points = points.dropLast(1)
                                        PointStore.save(prefs, points)
                                        refreshMarkers()
                                    }
                                    menuOpen = false
                                }
                            )
                        }

                        Text("クリッカー", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "停止するまで動き続けます。メニューの「クリッカー停止」か通知の停止で終了です。",
                            style = MaterialTheme.typography.bodySmall
                        )
                        OutlinedTextField(
                            interval, { interval = it },
                            label = { Text("クリック間隔 (ms)") }
                        )
                        OutlinedTextField(
                            randomRadius, { randomRadius = it },
                            label = { Text("ランダム範囲 半径(px) / 0=固定") }
                        )
                        Text("複数マーカーを登録すると、順番にクリックします。各クリック位置は指定半径内でランダム化されます。")
                    }

                    itemsIndexed(points) { i, p ->
                        Text("マーカー ${i + 1}: (${p.x.toInt()}, ${p.y.toInt()})")
                    }

                    item {
                        HorizontalDivider()
                        Text("📱 アプリ起動トリガー", style = MaterialTheme.typography.titleLarge)
                        Row {
                            Checkbox(appEnabled, { appEnabled = it })
                            Text("有効")
                        }
                        OutlinedTextField(
                            target, { target = it },
                            label = { Text("対象 package 名") }
                        )
                        OutlinedTextField(
                            appRadius, { appRadius = it },
                            label = { Text("無効化範囲 半径(px)") }
                        )
                        OutlinedTextField(
                            appDuration, { appDuration = it },
                            label = { Text("無効化時間(ms)") }
                        )

                        HorizontalDivider()
                        Text("⏰ 指定時刻トリガー", style = MaterialTheme.typography.titleLarge)
                        Row {
                            Checkbox(timeEnabled, { timeEnabled = it })
                            Text("有効")
                        }
                        OutlinedTextField(
                            hour, { hour = it },
                            label = { Text("時 (0-23)") }
                        )
                        OutlinedTextField(
                            minute, { minute = it },
                            label = { Text("分 (0-59)") }
                        )
                        OutlinedTextField(
                            timeRadius, { timeRadius = it },
                            label = { Text("無効化範囲 半径(px)") }
                        )
                        OutlinedTextField(
                            timeDuration, { timeDuration = it },
                            label = { Text("無効化時間(ms)") }
                        )

                        Text(
                            "アプリ起動と指定時刻は、範囲・無効化時間を完全に別々に設定できます。",
                            style = MaterialTheme.typography.bodySmall
                        )

                        Button(onClick = { save() }) { Text("設定を保存") }

                        Button(onClick = {
                            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        }) { Text("アクセシビリティ設定") }

                        Button(onClick = {
                            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
                        }) { Text("オーバーレイ権限設定") }

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            Button(onClick = {
                                startActivity(
                                    Intent(
                                        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                        Uri.parse("package:$packageName")
                                    )
                                )
                            }) { Text("正確なアラームの権限設定（指定時刻トリガーに必要）") }
                        }

                        HorizontalDivider()
                        Text("📦 プリセット", style = MaterialTheme.typography.titleLarge)
                        Button(onClick = {
                            save()
                            PresetStore.save(prefs, "default", points)
                        }) { Text("現在の設定を保存") }
                        Button(onClick = {
                            points = PresetStore.load(prefs, "default")
                            refreshMarkers()
                        }) { Text("保存した設定を読み込む") }
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
