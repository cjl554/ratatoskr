package com.neon10.ratatoskr.data

import android.util.Log
import com.neon10.ratatoskr.service.ChatAccessibilityService
import com.neon10.ratatoskr.service.ChatAccessibilityService.Companion.WECHAT_PACKAGES
import com.neon10.ratatoskr.service.ChatAccessibilityService.Companion.debugMode

/**
 * Collects chat messages from WeChat using AccessibilityService.
 * Enhanced version with better WeChat 8.0.69 compatibility.
 */
class ChatMessageCollector {

    companion object {
        private const val TAG = "ChatCollector"

        // For debug output
        var lastCollectedMessages = emptyList<String>()
        var lastDebugInfo = ""
        var lastError: String? = null
    }

    /**
     * Collect messages from the current WeChat chat screen.
     * Returns a list of message texts.
     */
    fun collectMessages(): List<String> {
        lastError = null
        lastDebugInfo = ""
        lastCollectedMessages = emptyList()

        val service = ChatAccessibilityService.instance

        if (service == null) {
            val error = "AccessibilityService not connected"
            Log.e(TAG, error)
            lastError = error
            return emptyList()
        }

        val rootNode = service.getRootNode()
        if (rootNode == null) {
            val error = "Cannot get root node"
            Log.e(TAG, error)
            lastError = error
            return emptyList()
        }

        // Check if we're in WeChat
        val packageName = rootNode.packageName?.toString() ?: ""
        if (!WECHAT_PACKAGES.contains(packageName)) {
            val error = "Not in WeChat. Current app: $packageName"
            Log.e(TAG, error)
            lastError = error
            rootNode.recycle()
            return emptyList()
        }

        Log.d(TAG, "=== Collecting messages from $packageName ===")

        // Collect all text content
        val messages = mutableListOf<String>()
        val allTexts = service.findAllTextNodes(rootNode)

        if (debugMode) {
            Log.d(TAG, "=== All text nodes found (${allTexts.size}) ===")
            allTexts.take(50).forEachIndexed { index, (text, _) ->
                if (text.length > 0 && text.length < 500) {
                    Log.d(TAG, "  [$index]: $text")
                }
            }
        }

        lastDebugInfo = buildString {
            appendLine("=== Debug Info ===")
            appendLine("Package: $packageName")
            appendLine("Total text nodes: ${allTexts.size}")
            appendLine("")
            appendLine("=== All Texts ===")
            allTexts.take(30).forEachIndexed { index, (text, _) ->
                if (text.length in 1..200) {
                    appendLine("[$index] $text")
                }
            }
        }

        // Filter and clean messages
        messages.addAll(
            allTexts
                .map { it.first }
                .filter { text ->
                    text.isNotBlank() &&
                    text.length >= 2 &&
                    text.length <= 1000 &&
                    !isSystemMessage(text) &&
                    !isTimeStamp(text)
                }
                .distinct()
        )

        // Deduplicate and limit
        lastCollectedMessages = messages.take(50).distinct()

        Log.d(TAG, "=== Collected ${lastCollectedMessages.size} messages ===")

        if (debugMode && messages.isEmpty()) {
            Log.d(TAG, "=== No messages collected ===")
            Log.d(TAG, "Raw texts (first 20):")
            allTexts.take(20).forEach { (text, _) ->
                Log.d(TAG, "  - '$text' (len=${text.length})")
            }
        }

        rootNode.recycle()
        return lastCollectedMessages
    }

    /**
     * Check if text is a system message (time, date, etc.)
     */
    private fun isSystemMessage(text: String): Boolean {
        val lower = text.lowercase()

        // Time patterns
        if (text.matches(Regex("^\\d{1,2}:\\d{2}$"))) return true
        if (text.matches(Regex("^\\d{4}-\\d{2}-\\d{2}$"))) return true
        if (text.matches(Regex("^\\d{4}/\\d{2}/\\d{2}$"))) return true

        // Common system texts
        val systemTexts = listOf(
            "已撤回一条消息",
            "你已添加",
            "以上是打招呼内容",
            "查看详情",
            "复制",
            "撤回",
            "删除",
            "引用",
            "翻译",
            "复制成功",
            "小程序",
            "链接",
            "视频号",
            "朋友圈",
            "[图片]",
            "[表情]",
            "[语音]",
            "[视频]",
            "[文件]",
            "[红包]",
            "[转账]"
        )

        return systemTexts.any { lower.contains(it.lowercase()) }
    }

    /**
     * Check if text is a timestamp
     */
    private fun isTimeStamp(text: String): Boolean {
        return text.matches(Regex("^\\d{1,2}:\\d{2}$")) ||
               text.matches(Regex("^\\d{4}年\\d{1,2}月\\d{1,2}日$")) ||
               text.matches(Regex("^昨天 \\d{1,2}:\\d{2}$")) ||
               text.matches(Regex("^\\d+分钟前$"))
    }

    /**
     * Get the last collected messages (for debugging)
     */
    fun getLastMessages(): List<String> = lastCollectedMessages

    /**
     * Get debug info from last collection
     */
    fun getDebugInfo(): String = lastDebugInfo

    /**
     * Get last error
     */
    fun getLastError(): String? = lastError
}
