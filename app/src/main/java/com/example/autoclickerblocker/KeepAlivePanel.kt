package com.example.autoclickerblocker

import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*

@Composable
fun KeepAliveButtons(activity: ComponentActivity) {
    val pm = activity.getSystemService(PowerManager::class.java)
    var ignoring by remember {
        mutableStateOf(pm.isIgnoringBatteryOptimizations(activity.packageName))
    }
    fun refresh() {
        ignoring = pm.isIgnoringBatteryOptimizations(activity.packageName)
    }
    HorizontalDivider()
    Text("⚡ 長時間停止しにくくする", style = MaterialTheme.typography.titleLarge)
    Text(
        if (ignoring) "電池最適化: 除外済み" else "電池最適化: まだかかっている",
        style = MaterialTheme.typography.bodyMedium
    )
    Text(
        "手順\n" +
            "1. 「電池最適化を外す」を押して許可する\n" +
            "2. ユーザー補助でこのアプリをオンにする\n" +
            "3. 実行中の通知を消さない\n" +
            "4. 最近使ったアプリからこのアプリをスワイプしない\n" +
            "5. 端末再起動後は、色解除を使うなら画面取得をやり直す\n" +
            "Xiaomi / Huawei / OPPO / vivo は設定の自動起動とバックグラウンド制限も許可する",
        style = MaterialTheme.typography.bodySmall
    )
    Button(onClick = {
        runCatching {
            activity.startActivity(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:${activity.packageName}")
                )
            )
        }
        refresh()
    }) { Text("電池最適化を外す") }
    Button(onClick = {
        runCatching {
            activity.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }) { Text("電池最適化の一覧を開く") }
    Button(onClick = {
        runCatching {
            activity.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${activity.packageName}")
                )
            )
        }
    }) { Text("アプリ情報（通知・電池）") }
    Button(onClick = { refresh() }) { Text("状態を再読込") }
}
