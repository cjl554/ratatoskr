package com.neon10.ratatoskr.ui.pages

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.neon10.ratatoskr.data.ChatMessageCollector
import com.neon10.ratatoskr.service.ChatAccessibilityService
import com.neon10.ratatoskr.ui.components.ChatAssistOverlay

@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var overlayEnabled by remember { mutableStateOf(false) }
    var debugMode by remember { mutableStateOf(true) }
    var debugInfo by remember { mutableStateOf("") }
    var lastMessages by remember { mutableStateOf<List<String>>(emptyList()) }
    var lastError by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Ratatoskr", style = MaterialTheme.typography.headlineSmall)

        // === 悬浮窗设置 ===
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("悬浮窗", style = MaterialTheme.typography.titleMedium)
                Button(onClick = {
                    val canOverlay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(context) else true
                    if (!canOverlay) {
                        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + context.packageName))
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    } else {
                        ChatAssistOverlay.install(context.applicationContext as android.app.Application)
                        if (overlayEnabled) {
                            ChatAssistOverlay.disable()
                            overlayEnabled = false
                        } else {
                            ChatAssistOverlay.enable()
                            overlayEnabled = true
                        }
                    }
                }) { Text(if (overlayEnabled) "关闭悬浮窗" else "开启悬浮窗") }

                Button(onClick = {
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                }) { Text("开启无障碍服务") }
            }
        }

        // === 调试面板 (新增) ===
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("🔧 开发者调试", style = MaterialTheme.typography.titleMedium)
                    Switch(
                        checked = debugMode,
                        onCheckedChange = {
                            debugMode = it
                            ChatAccessibilityService.debugMode = it
                        }
                    )
                }

                Text(
                    text = "开启后可在 Logcat 中查看详细日志",
                    style = MaterialTheme.typography.bodySmall
                )

                // 测试收集按钮
                Button(
                    onClick = {
                        try {
                            val collector = ChatMessageCollector()
                            lastMessages = collector.collectMessages()
                            debugInfo = collector.getDebugInfo()
                            lastError = collector.getLastError()
                        } catch (e: Exception) {
                            lastError = e.message ?: "Unknown error"
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("🧪 测试消息收集")
                }

                // 错误信息
                if (lastError != null) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                "❌ 错误: $lastError",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                // 收集到的消息数量
                if (lastMessages.isNotEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                "✅ 收集到 ${lastMessages.size} 条消息:",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Spacer(Modifier.height(8.dp))
                            lastMessages.take(10).forEach { msg ->
                                Text(
                                    "• ${msg.take(50)}${if (msg.length > 50) "..." else ""}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            if (lastMessages.size > 10) {
                                Text(
                                    "... 还有 ${lastMessages.size - 10} 条",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                } else if (lastError == null && debugInfo.isNotEmpty()) {
                    Text(
                        "⚠️ 没有收集到消息，请确认已在微信聊天界面",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                // 详细日志
                if (debugInfo.isNotEmpty()) {
                    OutlinedButton(
                        onClick = { /* Logcat 查看 */ },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("📋 查看详细日志 (在 Logcat 中过滤 'ChatCollector')")
                    }
                }
            }
        }

        // === 风格偏好 ===
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("风格偏好", style = MaterialTheme.typography.titleMedium)
                Text("稳妥 / 推进 / 幽默：可在后续版本设置权重")
            }
        }

        Spacer(Modifier.height(8.dp))
        Text("提示：点击悬浮窗由 AI 生成候选，点击候选可复制。", style = MaterialTheme.typography.bodySmall)

        // 版本信息
        Text(
            "Ratatoskr v0.0.1 + 微信8.0.69兼容补丁",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )
    }
}
