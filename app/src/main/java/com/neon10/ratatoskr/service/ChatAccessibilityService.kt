package com.neon10.ratatoskr.service

import android.accessibilityservice.AccessibilityService
import android.content.pm.PackageManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Native Android AccessibilityService for collecting chat messages.
 * Enhanced version with better WeChat 8.0.69 compatibility and debug logging.
 */
class ChatAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "ChatA11yService"

        @Volatile
        var instance: ChatAccessibilityService? = null
            private set

        // WeChat package names (try all possible variants)
        val WECHAT_PACKAGES = listOf(
            "com.tencent.mm",
            "com.tencent.mm.debug",
            "com.tencent.mm.release",
            "com.tencent.mm.stub"
        )

        // WeChat 8.0.69 message list item IDs (multiple patterns)
        val MESSAGE_ITEM_IDS = listOf(
            "com.tencent.mm:id conversation list item",
            "com.tencent.mm:id/listitem_chatting_ui",
            "com.tencent.mm:id/item",
            "com.tencent.mm:id/b5e",
            "com.tencent.mm:id/b5m",
            "com.tencent.mm:id/b5o",
            "com.tencent.mm:id/b5n",
            "com.tencent.mm:id/b5l",
            "list_item",
            "chatting_ui_item"
        )

        // WeChat 8.0.69 chat bubble IDs
        val BUBBLE_IDS = listOf(
            "com.tencent.mm:id/chat_bubble",
            "com.tencent.mm:id/b9j",
            "com.tencent.mm:id/b9k",
            "com.tencent.mm:id/b9l",
            "chat_bubble",
            "bubble"
        )

        // Message content IDs
        val CONTENT_IDS = listOf(
            "com.tencent.mm:id/content",
            "com.tencent.mm:id/b5t",
            "com.tencent.mm:id/b5s",
            "com.tencent.mm:id/b5r",
            "content",
            "msg_content"
        )

        // Debug mode - set to true to see all UI elements
        var debugMode = true
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "=== ChatAccessibilityService Connected ===")
        Log.d(TAG, "Debug mode: $debugMode")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (debugMode && event != null) {
            val packageName = event.packageName?.toString() ?: "unknown"
            if (WECHAT_PACKAGES.any { packageName.contains(it.substringAfter('.')) }) {
                Log.d(TAG, "=== WeChat Event ===")
                Log.d(TAG, "EventType: ${event.eventType} (${getEventTypeName(event.eventType)})")
                Log.d(TAG, "ClassName: ${event.className}")
                Log.d(TAG, "Text: ${event.text}")
                Log.d(TAG, "ContentDescription: ${event.contentDescription}")
                Log.d(TAG, "Source: ${event.source}")
                dumpNodeTree(event.source, 0)
            }
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "Service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        Log.d(TAG, "Service destroyed")
    }

    /**
     * Get the root node of the current active window.
     */
    fun getRootNode(): AccessibilityNodeInfo? {
        return try {
            rootInActiveWindow
        } catch (e: Exception) {
            Log.e(TAG, "Error getting root node: ${e.message}")
            null
        }
    }

    /**
     * Get all windows
     */
    fun getAllWindows() = windows

    /**
     * Check if current app is WeChat
     */
    fun isWeChatCurrentApp(): Boolean {
        val root = getRootNode() ?: return false
        val packageName = root.packageName?.toString() ?: ""
        root.recycle()
        return WECHAT_PACKAGES.any { packageName == it }
    }

    /**
     * Dump node tree for debugging
     */
    fun dumpNodeTree(node: AccessibilityNodeInfo?, depth: Int) {
        if (node == null || depth > 5) return

        val indent = "  ".repeat(depth)
        val id = node.viewIdResourceName ?: "no-id"
        val text = node.text?.toString() ?: ""
        val contentDesc = node.contentDescription?.toString() ?: ""
        val className = node.className?.toString() ?: ""

        Log.d(TAG, "$indent[${className.substringAfterLast('.')}] id=$id text=$text desc=$contentDesc")

        for (i in 0 until node.childCount) {
            dumpNodeTree(node.getChild(i), depth + 1)
        }
    }

    /**
     * Find node by text (with fallback search)
     */
    fun findNodeByText(root: AccessibilityNodeInfo?, text: String): AccessibilityNodeInfo? {
        if (root == null) return null

        if (root.text?.toString()?.contains(text) == true ||
            root.contentDescription?.toString()?.contains(text) == true) {
            return root
        }

        for (i in 0 until root.childCount) {
            val found = findNodeByText(root.getChild(i), text)
            if (found != null) return found
        }

        return null
    }

    /**
     * Find all nodes matching any of the given IDs
     */
    fun findNodesByIds(root: AccessibilityNodeInfo?, ids: List<String>): List<AccessibilityNodeInfo> {
        val results = mutableListOf<AccessibilityNodeInfo>()
        if (root == null) return results

        val nodeId = root.viewIdResourceName ?: ""

        // Try exact match or partial match
        for (id in ids) {
            if (nodeId.contains(id) || id.contains(nodeId.substringAfterLast('.'))) {
                results.add(root)
                break
            }
        }

        for (i in 0 until root.childCount) {
            results.addAll(findNodesByIds(root.getChild(i), ids))
        }

        return results
    }

    /**
     * Deep search for text in all children
     */
    fun findAllTextNodes(root: AccessibilityNodeInfo?, depth: Int = 0): List<Pair<String, AccessibilityNodeInfo>> {
        val results = mutableListOf<Pair<String, AccessibilityNodeInfo>>()
        if (root == null || depth > 10) return results

        val text = root.text?.toString()
        val desc = root.contentDescription?.toString()

        if (!text.isNullOrEmpty()) {
            results.add(text to root)
        }
        if (!desc.isNullOrEmpty() && desc != text) {
            results.add(desc to root)
        }

        for (i in 0 until root.childCount) {
            results.addAll(findAllTextNodes(root.getChild(i), depth + 1))
        }

        return results
    }

    private fun getEventTypeName(type: Int): String {
        return when (type) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> "WINDOW_STATE_CHANGED"
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> "WINDOW_CONTENT_CHANGED"
            AccessibilityEvent.TYPE_VIEW_CLICKED -> "VIEW_CLICKED"
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> "VIEW_TEXT_CHANGED"
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> "VIEW_SCROLLED"
            else -> "TYPE_$type"
        }
    }
}
