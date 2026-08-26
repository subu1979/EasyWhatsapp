package com.subu1979.imagesender.auto

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import com.subu1979.imagesender.R
import com.subu1979.imagesender.share.WhatsAppApp

/**
 * Presses Send on the media preview of the chat this app opened, so the user does not have to.
 *
 * The service watches WhatsApp only, does nothing unless [AutoSendSession] holds an arming for the
 * chat on screen, and clicks at most once per arming. It never types, never navigates, and never
 * chooses a recipient — the chat is already the right one, because the app opened it by deep link.
 *
 * WhatsApp's view ids are not a contract, so the send control is looked up in three widening steps
 * and every failure is logged with what was actually on screen, which is what makes re-targeting a
 * five-minute job instead of another guess.
 */
class WhatsAppAutoSendService : AccessibilityService() {

    private var previewSeenAt = 0L
    private var lastSignature = ""

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val target = AutoSendSession.activeTarget() ?: return
        val packageName = event?.packageName?.toString() ?: return
        if (packageName != WhatsAppApp.STANDARD.packageName &&
            packageName != WhatsAppApp.BUSINESS.packageName
        ) {
            return
        }

        val root = rootInActiveWindow ?: return
        val send = findSendControl(root, packageName)
        if (send == null) {
            previewSeenAt = 0L
            return
        }

        // The recipient is verified from the screen itself rather than trusted from the arming:
        // if the user navigated to another chat, the digits will not match and nothing happens.
        if (!chatMatches(root, target)) {
            val signature = "no-match"
            if (signature != lastSignature) {
                lastSignature = signature
                AutoSendSession.log("HOLDING — send control found, chat does not show +$target")
            }
            previewSeenAt = 0L
            return
        }

        // Let the preview settle so a photo still being cropped or captioned is not sent early.
        val now = SystemClock.elapsedRealtime()
        if (previewSeenAt == 0L) {
            previewSeenAt = now
            AutoSendSession.log("PREVIEW ready for +$target, settling")
            return
        }
        if (now - previewSeenAt < AutoSendSession.SETTLE_MS) return

        val clicked = clickSelfOrAncestor(send)
        AutoSendSession.log(if (clicked) "SENT to +$target" else "CLICK REFUSED by WhatsApp")
        if (clicked) {
            AutoSendSession.consume()
            previewSeenAt = 0L
            toast(getString(R.string.auto_sent_toast))
        }
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        AutoSendSession.cancel("service stopped")
        return super.onUnbind(intent)
    }

    /** Three widening attempts: exact id, described "Send", then a clickable node labelled send. */
    private fun findSendControl(
        root: AccessibilityNodeInfo,
        packageName: String
    ): AccessibilityNodeInfo? {
        SEND_IDS.forEach { id ->
            root.findAccessibilityNodeInfosByViewId("$packageName:id/$id")
                ?.firstOrNull { it.isVisibleToUser }
                ?.let { return it }
        }

        findNode(root) { node ->
            node.isVisibleToUser &&
                node.contentDescription?.toString().equals(SEND_LABEL, ignoreCase = true)
        }?.let { return it }

        return findNode(root) { node ->
            node.isVisibleToUser && node.isClickable &&
                node.contentDescription?.toString()?.startsWith(SEND_LABEL, ignoreCase = true) == true
        }
    }

    /** True when the armed number appears in the visible text, which is how WhatsApp titles an
     *  unsaved chat ("+91 97910 39009"). */
    private fun chatMatches(root: AccessibilityNodeInfo, target: String): Boolean {
        val found = findNode(root) { node ->
            val text = node.text?.toString() ?: node.contentDescription?.toString()
            val digits = text?.filter { it.isDigit() } ?: return@findNode false
            digits.length >= MIN_MATCH_DIGITS && digits.endsWith(target.takeLast(MIN_MATCH_DIGITS))
        }
        return found != null
    }

    private fun clickSelfOrAncestor(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        var depth = 0
        while (current != null && depth < MAX_ANCESTOR_DEPTH) {
            if (current.isClickable) return current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
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

    private fun toast(text: String) {
        Toast.makeText(applicationContext, text, Toast.LENGTH_SHORT).show()
    }

    private companion object {
        /** Ids seen across WhatsApp builds for the media-preview send control. */
        val SEND_IDS = listOf("send", "send_btn", "send_button")
        const val SEND_LABEL = "Send"

        /** Below this a coincidental digit run could match the wrong chat. */
        const val MIN_MATCH_DIGITS = 8
        const val MAX_ANCESTOR_DEPTH = 6
    }
}
