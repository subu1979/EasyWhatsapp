package com.subu1979.imagesender.auto

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
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

    private val settleHandler = Handler(Looper.getMainLooper())
    private var settlePending = false

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Manual mode should leave nothing of ours running: an enabled accessibility service keeps
        // the process alive for as long as it is switched on, so it switches itself off instead.
        if (AutoSendSettings.mode(this) == SendMode.MANUAL) {
            trace("manual mode — disabling self")
            AutoSendSession.log(this, "SERVICE off (manual mode)")
            disableSelf()
            return
        }

        val packageName = event?.packageName?.toString() ?: return
        val target = AutoSendSession.activeTarget(this)
        if (target == null) {
            trace("no arming (event from $packageName)")
            return
        }
        if (packageName != WhatsAppApp.STANDARD.packageName &&
            packageName != WhatsAppApp.BUSINESS.packageName
        ) {
            return
        }

        val root = rootInActiveWindow
        if (root == null) {
            trace("armed +$target but rootInActiveWindow is null")
            return
        }

        // Confirm the recipient whenever WhatsApp shows it, which is on the chat screen rather
        // than on the composer that follows.
        when (digitsMatch(root, target)) {
            Match.ARMED_NUMBER -> {
                if (!AutoSendSession.chatVerified(this)) {
                    trace("chat verified for +$target")
                    AutoSendSession.log(this, "CHAT verified for +$target")
                }
                AutoSendSession.markChatVerified(this)
            }
            Match.OTHER_CONVERSATION -> {
                settleHandler.removeCallbacksAndMessages(null)
                settlePending = false
                if (AutoSendSession.chatVerified(this)) {
                    trace("different chat on screen, verification dropped")
                    AutoSendSession.log(this, "HOLDING — a different chat is open")
                }
                AutoSendSession.clearChatVerified(this)
            }
            Match.UNKNOWN -> Unit
        }

        val send = findSendControl(root, packageName)
        if (send == null) return

        if (!AutoSendSession.chatVerified(this)) {
            trace("send control present but chat never confirmed for +$target")
            return
        }

        // Let the preview settle so a photo still being cropped or captioned is not sent early.
        //
        // The wait is scheduled rather than driven by the next event: a composer opened from the
        // camera is static and emits nothing further, so waiting for another event left the send
        // hanging forever. Observed on a CPH2637 — the gallery composer kept emitting and worked,
        // the camera one did not.
        if (settlePending) return
        settlePending = true
        AutoSendSession.log(this, "PREVIEW ready for +$target, settling")
        trace("preview ready for +$target, clicking in ${AutoSendSession.SETTLE_MS}ms")
        settleHandler.postDelayed({ sendIfStillValid(target) }, AutoSendSession.SETTLE_MS)
    }

    /** Re-reads the screen after the settle delay: nothing is clicked on stale information. */
    private fun sendIfStillValid(target: String) {
        settlePending = false
        if (AutoSendSession.activeTarget(this) != target) {
            trace("arming changed during settle, not clicking")
            return
        }
        if (!AutoSendSession.chatVerified(this)) {
            trace("verification lost during settle, not clicking")
            return
        }

        val root = rootInActiveWindow ?: return
        val packageName = root.packageName?.toString() ?: return
        val send = findSendControl(root, packageName)
        if (send == null) {
            trace("send control gone after settle")
            return
        }

        trace("clicking send for +$target")
        val clicked = clickSelfOrAncestor(send)
        AutoSendSession.log(this, if (clicked) "SENT to +$target" else "CLICK REFUSED by WhatsApp")
        if (clicked) {
            AutoSendSession.consume(this)
            ArmingService.stop(this)
            toast(getString(R.string.auto_sent_toast))
        }
    }

    override fun onInterrupt() = Unit

    override fun onServiceConnected() {
        super.onServiceConnected()
        if (AutoSendSettings.mode(this) == SendMode.MANUAL) {
            disableSelf()
            return
        }
        // OEM builds unbind and re-bind this service freely; a rebind mid-flow must not look like a
        // fresh start, so the arming is left alone and only its own expiry ends it.
        AutoSendSession.log(this, "SERVICE connected")
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

    private enum class Match { ARMED_NUMBER, OTHER_CONVERSATION, UNKNOWN }

    /**
     * Whether this screen names the armed number, names a different chat, or says nothing about the
     * recipient. Only a chat screen is treated as evidence of a different conversation: pickers and
     * galleries are full of dates and file names that are not phone numbers.
     */
    private fun digitsMatch(root: AccessibilityNodeInfo, target: String): Match {
        val tail = target.takeLast(MIN_MATCH_DIGITS)
        val matched = findNode(root) { node ->
            val text = node.text?.toString() ?: node.contentDescription?.toString()
            val digits = text?.filter { it.isDigit() } ?: return@findNode false
            digits.length >= MIN_MATCH_DIGITS && digits.endsWith(tail)
        }
        if (matched != null) return Match.ARMED_NUMBER

        val onConversation = root.className?.toString()?.contains("Conversation") == true ||
            findNode(root) { it.viewIdResourceName?.endsWith(":id/conversation_contact_name") == true } != null
        return if (onConversation) Match.OTHER_CONVERSATION else Match.UNKNOWN
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

    /** Everything the service decides, in logcat, so a failure is diagnosable over adb. */
    private fun trace(message: String) {
        Log.i(TAG, message)
    }

    /** Digit runs currently on screen, to show why a match failed. */
    private fun visibleDigits(root: AccessibilityNodeInfo): String {
        val found = mutableListOf<String>()
        fun walk(node: AccessibilityNodeInfo) {
            val text = node.text?.toString() ?: node.contentDescription?.toString()
            val digits = text?.filter { it.isDigit() }.orEmpty()
            if (digits.length >= 6) found += digits
            for (i in 0 until node.childCount) node.getChild(i)?.let(::walk)
        }
        walk(root)
        return if (found.isEmpty()) "no digit runs" else found.joinToString(", ")
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
        const val TAG = "AutoSend"
    }
}
