package com.phonebot.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Path
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors

/**
 * با فعال‌سازی این سرویس در تنظیمات Accessibility، لمس/سوایپ/تایپ خودکار و
 * خواندن محتوای صفحه و گرفتن اسکرین‌شات ممکن می‌شود.
 */
class ControlAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance === this) instance = null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    fun tap(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 80))
            .build()
        dispatchGesture(gesture, null, null)
    }

    fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long = 300) {
        val path = Path().apply { moveTo(x1, y1); lineTo(x2, y2) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
            .build()
        dispatchGesture(gesture, null, null)
    }

    fun pressBack() = performGlobalAction(GLOBAL_ACTION_BACK)
    fun pressHome() = performGlobalAction(GLOBAL_ACTION_HOME)
    fun pressRecents() = performGlobalAction(GLOBAL_ACTION_RECENTS)

    fun typeInFocused(text: String): Boolean {
        val node = findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
        val args = Bundle()
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    fun clickNodeWithText(query: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val target = findNode(root, query.lowercase()) ?: return false
        return clickNodeOrParent(target)
    }

    private fun clickNodeOrParent(node: AccessibilityNodeInfo): Boolean {
        var n: AccessibilityNodeInfo? = node
        while (n != null) {
            if (n.isClickable) return n.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            n = n.parent
        }
        return false
    }

    private fun findNode(node: AccessibilityNodeInfo, query: String): AccessibilityNodeInfo? {
        val text = ((node.text?.toString() ?: "") + " " + (node.contentDescription?.toString() ?: "")).lowercase()
        if (text.contains(query)) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findNode(child, query)
            if (found != null) return found
        }
        return null
    }

    fun dumpScreenText(): String {
        val root = rootInActiveWindow ?: return "(صفحه‌ای در دسترس نیست)"
        val sb = StringBuilder()
        collectText(root, sb)
        return if (sb.isEmpty()) "(متنی پیدا نشد)" else sb.toString()
    }

    private fun collectText(node: AccessibilityNodeInfo, sb: StringBuilder) {
        val t = node.text?.toString()?.trim()
        val d = node.contentDescription?.toString()?.trim()
        if (!t.isNullOrBlank()) sb.append(t).append('\n')
        if (!d.isNullOrBlank() && d != t) sb.append(d).append('\n')
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { collectText(it, sb) }
        }
    }

    @Suppress("DEPRECATION")
    fun takeScreenshotToFile(ctx: Context, onDone: (File?) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            onDone(null)
            return
        }
        takeScreenshot(
            Display.DEFAULT_DISPLAY,
            Executors.newSingleThreadExecutor(),
            object : TakeScreenshotCallback {
                override fun onSuccess(result: ScreenshotResult) {
                    try {
                        val bitmap = Bitmap.wrapHardwareBuffer(result.hardwareBuffer, result.colorSpace)
                        result.hardwareBuffer.close()
                        val file = File(ctx.getExternalFilesDir(null), "shot_${System.currentTimeMillis()}.png")
                        FileOutputStream(file).use { out ->
                            bitmap?.copy(Bitmap.Config.ARGB_8888, false)
                                ?.compress(Bitmap.CompressFormat.PNG, 100, out)
                        }
                        Handler(Looper.getMainLooper()).post { onDone(file) }
                    } catch (e: Exception) {
                        Log.e("ControlAccessibilityService", "screenshot failed", e)
                        Handler(Looper.getMainLooper()).post { onDone(null) }
                    }
                }

                override fun onFailure(errorCode: Int) {
                    Handler(Looper.getMainLooper()).post { onDone(null) }
                }
            }
        )
    }

    companion object {
        var instance: ControlAccessibilityService? = null
            private set
    }
}
