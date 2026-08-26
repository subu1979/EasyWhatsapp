package com.subu1979.imagesender.auto

import android.os.SystemClock

/**
 * The arming handshake between the app and [WhatsAppAutoSendService].
 *
 * Pressing Send on someone's behalf cannot be undone, so the service is built to refuse rather than
 * guess: it acts only while this holds an unexpired arming, only for the number armed here, and only
 * once. Every other situation — a chat the user opened themselves, a stale arming, a second window —
 * falls through to doing nothing.
 */
object AutoSendSession {

    /** Long enough to open WhatsApp, pick or shoot a photo, and look at it before it sends. */
    private const val WINDOW_MS = 120_000L

    /** The preview must sit still this long, so a photo still being edited is not sent. */
    const val SETTLE_MS = 600L

    @Volatile
    private var target: String? = null

    @Volatile
    private var expiresAt: Long = 0L

    @Volatile
    private var consumed: Boolean = false

    /** Recent service activity, newest last. Shown in the app so a failure explains itself. */
    private val events = ArrayDeque<String>()

    fun arm(digits: String) {
        target = digits
        consumed = false
        expiresAt = SystemClock.elapsedRealtime() + WINDOW_MS
        log("ARMED for +$digits")
    }

    fun cancel(reason: String) {
        if (target != null) log("CANCELLED ($reason)")
        target = null
        consumed = false
        expiresAt = 0L
    }

    fun isArmed(): Boolean = activeTarget() != null

    /** The armed recipient, or null once it has expired, been consumed or was never set. */
    fun activeTarget(): String? {
        val digits = target ?: return null
        if (consumed) return null
        if (SystemClock.elapsedRealtime() > expiresAt) {
            log("EXPIRED without sending")
            cancel("timeout")
            return null
        }
        return digits
    }

    /** Marks the single permitted click as spent. */
    fun consume() {
        consumed = true
        target = null
        expiresAt = 0L
    }

    fun log(line: String) {
        synchronized(events) {
            events.addLast(line)
            while (events.size > MAX_EVENTS) events.removeFirst()
        }
    }

    fun snapshot(): List<String> = synchronized(events) { events.toList() }

    fun clearLog() = synchronized(events) { events.clear() }

    private const val MAX_EVENTS = 40
}
