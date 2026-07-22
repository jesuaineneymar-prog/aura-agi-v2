package com.jc.aura

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * AuraAccessibilityService — Helper that provides static utilities
 * for accessibility node interaction used by social media modules.
 * The main accessibility service is AuraVoiceService.
 */
object AuraAccessibilityHelper {

    /**
     * Find and click a node by resource-id.
     */
    fun clickById(root: AccessibilityNodeInfo?, viewId: String): Boolean {
        val nodes = root?.findAccessibilityNodeInfosByViewId(viewId) ?: return false
        return nodes.firstOrNull()?.let {
            it.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        } ?: false
    }

    /**
     * Find and click a node by visible text.
     */
    fun clickByText(root: AccessibilityNodeInfo?, text: String): Boolean {
        val nodes = root?.findAccessibilityNodeInfosByText(text) ?: return false
        return nodes.firstOrNull()?.let {
            it.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        } ?: false
    }

    /**
     * Set text on an input field by resource-id.
     */
    fun setTextById(root: AccessibilityNodeInfo?, viewId: String, text: String): Boolean {
        val nodes = root?.findAccessibilityNodeInfosByViewId(viewId) ?: return false
        val node = nodes.firstOrNull() ?: return false
        val args = android.os.Bundle()
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    /**
     * Scroll forward on a scrollable node.
     */
    fun scrollForward(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false
        if (root.isScrollable) {
            return root.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
        }
        for (i in 0 until root.childCount) {
            if (scrollForward(root.getChild(i))) return true
        }
        return false
    }

    /**
     * Find first node matching a content description.
     */
    fun findByContentDesc(root: AccessibilityNodeInfo?, desc: String): AccessibilityNodeInfo? {
        if (root == null) return null
        if (root.contentDescription?.contains(desc, ignoreCase = true) == true) return root
        for (i in 0 until root.childCount) {
            val found = findByContentDesc(root.getChild(i), desc)
            if (found != null) return found
        }
        return null
    }

    /**
     * Find all clickable buttons in the current window.
     */
    fun findAllClickable(root: AccessibilityNodeInfo?): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        if (root == null) return result
        if (root.isClickable && root.isEnabled) result.add(root)
        for (i in 0 until root.childCount) result.addAll(findAllClickable(root.getChild(i)))
        return result
    }

    /**
     * Perform global back action.
     */
    fun performBack(service: AccessibilityService) {
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
    }

    /**
     * Perform global home action.
     */
    fun performHome(service: AccessibilityService) {
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
    }

    /**
     * Take a screenshot (API 28+).
     */
    fun takeScreenshot(service: AccessibilityService, callback: AccessibilityService.TakeScreenshotCallback) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            service.takeScreenshot(android.view.Display.DEFAULT_DISPLAY, service.mainExecutor, callback)
        }
    }

    /**
     * Dump all visible UI elements for debugging/calibration.
     */
    fun dumpUiTree(root: AccessibilityNodeInfo?, depth: Int = 0): String {
        if (root == null) return ""
        val sb = StringBuilder()
        val indent = "  ".repeat(depth)
        val id = root.viewIdResourceName ?: ""
        val text = root.text ?: ""
        val desc = root.contentDescription ?: ""
        val cls = root.className ?: ""
        if (id.isNotBlank() || text.isNotBlank() || desc.isNotBlank()) {
            sb.append("$indent[$cls] id='$id' text='$text' desc='$desc'\n")
        }
        for (i in 0 until root.childCount) sb.append(dumpUiTree(root.getChild(i), depth + 1))
        return sb.toString()
    }
}
