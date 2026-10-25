package com.prayerwallpaper.app

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Debug builds only. A screen reader builds what it says from the accessibility tree: a control's label, its role and its
 * state. This prints that tree for the window in front, one line per node a screen reader would stop on, with the phrase it
 * would say, so a script can check the words without a voice.
 *
 *   adb shell settings put secure enabled_accessibility_services com.prayerwallpaper.app/.DebugA11yDump
 *   adb shell settings put secure accessibility_enabled 1
 *   adb shell am broadcast -a com.prayerwallpaper.app.DUMP_A11Y -p com.prayerwallpaper.app
 *   adb logcat -d -s A11yDump
 */
class DebugA11yDump : AccessibilityService() {
    private val dump = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val root = rootInActiveWindow?.also { it.refresh() }
            if (root == null) {
                Log.i(TAG, "no window")
                return
            }
            // --es click "Move list down" does what a screen reader's double tap does: the accessibility action on that control.
            intent.getStringExtra("click")?.let { wanted ->
                val target = find(root, wanted)
                Log.i(TAG, "CLICK \"$wanted\" -> " + (target?.performAction(AccessibilityNodeInfo.ACTION_CLICK) ?: "no such control"))
                return
            }
            Log.i(TAG, "---- window ${root.packageName}")
            walk(root, 0)
        }
    }

    override fun onServiceConnected() {
        registerReceiver(dump, IntentFilter(ACTION), Context.RECEIVER_EXPORTED)
        Log.i(TAG, "ready")
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(dump) }
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    private fun walk(node: AccessibilityNodeInfo, depth: Int) {
        if (!node.isVisibleToUser) return
        // A screen reader stops on something it can act on, or that has words of its own.
        val stop = node.isClickable || node.isCheckable || node.isFocusable || node.isHeading || node.liveRegion != 0 ||
            !node.text.isNullOrBlank() && node.childCount == 0 || !node.contentDescription.isNullOrBlank()
        if (stop && node.isImportantForAccessibility) {
            val label = label(node)
            val role = role(node)
            val state = state(node)
            val words = listOf(label, role, state).filter { it.isNotBlank() }.joinToString(", ")
            val flags = buildList {
                if (node.isHeading) add("heading")
                if (node.liveRegion != 0) add("live")
                if (!node.isEnabled) add("disabled")
                if (node.isClickable) add("clickable")
            }.joinToString(" ")
            val box = android.graphics.Rect().also { node.getBoundsInScreen(it) }
            val extras = node.extras.keySet().joinToString(",") { it.removePrefix("AccessibilityNodeInfo.") + "=" + node.extras.get(it) }
            Log.i(TAG, "SAYS  $words    [$flags ${node.className?.toString()?.substringAfterLast('.')}${if (extras.isNotBlank()) " extras($extras)" else ""}] ${(box.height() / resources.displayMetrics.density).toInt()}dp")
            // A control is one stop: what is inside it is part of what it says, not something to stop on again. Unless what is inside is
            // a control too (a home screen widget's rows): TalkBack gives that its own stop.
            if ((node.isClickable || node.isCheckable) && !hasActionableDescendant(node)) return
        }
        for (i in 0 until node.childCount) node.getChild(i)?.let { walk(it, depth + 1) }
    }

    /** The first control whose spoken label starts with [wanted]. */
    private fun find(node: AccessibilityNodeInfo, wanted: String): AccessibilityNodeInfo? {
        if (node.isVisibleToUser && (node.isClickable || node.isCheckable) && label(node).startsWith(wanted)) return node
        for (i in 0 until node.childCount) node.getChild(i)?.let { child -> find(child, wanted)?.let { return it } }
        return null
    }

    private fun hasActionableDescendant(node: AccessibilityNodeInfo): Boolean =
        (0 until node.childCount).any { i -> node.getChild(i)?.let { it.isVisibleToUser && (it.isClickable || it.isCheckable || hasActionableDescendant(it)) } == true }

    /** What the control is called: its own words, or those of what is inside it, as a screen reader merges them (not what is its own stop). */
    private fun label(node: AccessibilityNodeInfo): String {
        val own = node.contentDescription?.toString().orEmpty().ifBlank { node.text?.toString().orEmpty() }
        if (own.isNotBlank()) return own
        return buildList {
            for (i in 0 until node.childCount) node.getChild(i)?.let { child -> if (!(child.isClickable || child.isCheckable)) add(label(child)) }
        }.filter { it.isNotBlank() }.joinToString(", ")
    }

    /** The role the node itself reports: its role description, or a specific class. Nothing is guessed from clickability. */
    private fun role(node: AccessibilityNodeInfo): String = node.extras.getCharSequence("AccessibilityNodeInfo.roleDescription")?.toString() ?: when (node.className?.toString()) {
        "android.widget.Switch" -> "switch"
        "android.widget.RadioButton" -> "radio button"
        "android.widget.Button" -> "button"
        "android.widget.CheckBox" -> "checkbox"
        "android.widget.SeekBar" -> "slider"
        "android.widget.EditText" -> "edit box"
        "android.widget.ImageView" -> "image"
        else -> ""
    }

    private fun state(node: AccessibilityNodeInfo): String = when {
        !node.stateDescription.isNullOrBlank() -> node.stateDescription.toString()
        node.isCheckable -> if (node.isChecked) "on" else "off"
        node.isSelected -> "selected"
        else -> ""
    }

    private companion object {
        const val TAG = "A11yDump"
        const val ACTION = "com.prayerwallpaper.app.DUMP_A11Y"
    }
}
