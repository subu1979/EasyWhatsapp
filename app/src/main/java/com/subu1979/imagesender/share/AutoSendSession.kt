package com.subu1979.imagesender.share

import android.os.SystemClock

/**
 * Hand-off between the app and [WhatsAppAutoSendService].
 *
 * The service is deliberately inert: it only acts while a send has been armed here, for the number
 * armed here, and only until the window below expires. Anything else on screen is ignored.
 */
object AutoSendSession {

    /** Long enough for WhatsApp to cold start on a slow device, short enough to never linger. */
    private const val WINDOW_MS = 45_000L

    @Volatile
    private var targetDigits: String? = null

    @Volatile
    private var expiresAt: Long = 0L

    @Volatile
    private var recipientPicked: Boolean = false

    fun arm(digits: String) {
        targetDigits = digits
        recipientPicked = false
        expiresAt = SystemClock.elapsedRealtime() + WINDOW_MS
    }

    fun clear() {
        targetDigits = null
        recipientPicked = false
        expiresAt = 0L
    }

    /** The armed recipient, or null when nothing is armed or the window has passed. */
    fun activeTarget(): String? {
        val digits = targetDigits ?: return null
        if (SystemClock.elapsedRealtime() > expiresAt) {
            clear()
            return null
        }
        return digits
    }

    fun markRecipientPicked() {
        recipientPicked = true
    }

    fun isRecipientPicked(): Boolean = recipientPicked
}
