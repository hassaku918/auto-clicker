package com.example.autoclickerblocker

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Screen() }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun Screen() {
        Scaffold(topBar = { TopAppBar(title = { Text("ScreenBlocker") }) }) { pad ->
            Column(
                Modifier.padding(pad).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("特定アプリを開くと長方形で画面封鎖し、指定時間後に自動解除します。")
                Text("1. ユーザー補助をオン\n2. オーバーレイを許可\n3. フローティング設定で package・範囲・時間を保存")
                Button(onClick = {
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }) { Text("ユーザー補助を開く") }
                Button(onClick = {
                    startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$packageName")
                        )
                    )
                }) { Text("オーバーレイ権限") }
                Button(onClick = {
                    if (!Settings.canDrawOverlays(this@MainActivity)) {
                        startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:$packageName")
                            )
                        )
                    } else {
                        FloatingPanel.show(this@MainActivity)
                        moveTaskToBack(true)
                    }
                }) { Text("フローティング設定を開く") }
                Button(onClick = {
                    BlockAccessibilityService.instance?.startBlock()
                }) { Text("今すぐ封鎖テスト") }
                Button(
                    onClick = { BlockAccessibilityService.instance?.unblock() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("封鎖を解除") }
            }
        }
    }
}
