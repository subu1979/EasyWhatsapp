package com.subu1979.imagesender.share

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Presses the two buttons the user would otherwise press inside WhatsApp: the armed recipient in
 * the "Send to…" list, then Send on the attachment preview.
 *
 * WhatsApp offers no way for another app to send on its behalf, so the only route to a hands-free
 * flow is driving its UI. The service is scoped as tightly as possible:
 *
 * - it receives events from WhatsApp and WhatsApp Business only (see accessibility_service_config),
 * - it does nothing at all unless [AutoSendSession] has an armed, unexpired recipient,
 * - it clicks a row only when that row's digits match the armed number.
 *
 * WhatsApp's layout is not a contract, so every step degrades to "do nothing" and the user simply
 * finishes by hand.
 */
class WhatsAppAutoSendService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val target = AutoSendSession.activeTarget() ?: return
        val packageName = event?.packageName?.toString() ?: return
        if (packageName != WhatsAppApp.STANDARD.packageName &&
            packageName != WhatsAppApp.BUSINESS.packageName
        ) {
            return
        }

        val root = rootInActiveWindow ?: return
        if (!AutoSendSession.isRecipientPicked()) {
            if (clickRecipient(root, target)) {
                AutoSendSession.markRecipientPicked()
                return
            }
        }
        if (clickSend(root, packageName)) {
            AutoSendSession.clear()
        }
    }

    override fun onInterrupt() = Unit

    /** Finds the row whose visible digits match the armed number and clicks it. */
    private fun clickRecipient(root: AccessibilityNodeInfo, target: String): Boolean {
        val match = findNode(root) { node ->
            val text = node.text?.toString() ?: return@findNode false
            val digits = text.filter { it.isDigit() }
            digits.isNotEmpty() && matchesTarget(digits, target)
        } ?: return false

        return clickSelfOrAncestor(match)
    }

    /**
     * WhatsApp shows the number in national or international form depending on the screen, so the
     * comparison works from the end of the string inwards.
     */
    private fun matchesTarget(digits: String, target: String): Boolean {
        if (digits == target) return true
        val shorter = minOf(digits.length, target.length)
        if (shorter < MIN_MATCH_DIGITS) return false
        return digits.takeLast(shorter) == target.takeLast(shorter)
    }

    /** Clicks the send control on the attachment preview. */
    private fun clickSend(root: AccessibilityNodeInfo, packageName: String): Boolean {
        root.findAccessibilityNodeInfosByViewId("$packageName:id/send")
            ?.firstOrNull { it.isVisibleToUser }
            ?.let { return clickSelfOrAncestor(it) }

        val byDescription = findNode(root) { node ->
            node.isVisibleToUser &&
                node.contentDescription?.toString().equals(SEND_DESCRIPTION, ignoreCase = true)
        } ?: return false

        return clickSelfOrAncestor(byDescription)
    }

    private fun clickSelfOrAncestor(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        var depth = 0
        while (current != null && depth < MAX_ANCESTOR_DEPTH) {
            if (current.isClickable) {
                return current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            current = current.parent
            depth++
        }
        return false
    }

    private fun findNode(
        root: AccessibilityNodeInfo,
        predicate: (AccessibilityNodeInfo) -> Boolean
    ): AccessibilityNodeInfo? {
        if (predicate(root)) return root
        for (index in 0 until root.childCount) {
            val child = root.getChild(index) ?: continue
            findNode(child, predicate)?.let { return it }
        }
        return null
    }

    private companion object {
        /** Below this, a coincidental digit run could match the wrong row. */
        const val MIN_MATCH_DIGITS = 8
        const val MAX_ANCESTOR_DEPTH = 6
        const val SEND_DESCRIPTION = "Send"
    }
}
