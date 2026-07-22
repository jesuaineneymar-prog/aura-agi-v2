package com.jc.aura

import android.view.accessibility.AccessibilityNodeInfo

/**
 * Utility functions for accessibility node tree traversal.
 * These replace deprecated/removed APIs from Android API 33+.
 */
object AuraAccessibilityUtils {

    /**
     * Replacement for the removed findAccessibilityNodeInfosByContentDescription.
     * Traverses the node tree to find nodes whose contentDescription contains the given text.
     */
    fun findByContentDescription(
        root: AccessibilityNodeInfo?,
        description: String
    ): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        root?.let { findNodesByContentDescription(it, description, result) }
        return result
    }

    private fun findNodesByContentDescription(
        node: AccessibilityNodeInfo,
        description: String,
        result: MutableList<AccessibilityNodeInfo>
    ) {
        val cd = node.contentDescription?.toString() ?: ""
        if (cd.contains(description, ignoreCase = true)) {
            result.add(node)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            child?.let { findNodesByContentDescription(it, description, result) }
        }
    }
}
