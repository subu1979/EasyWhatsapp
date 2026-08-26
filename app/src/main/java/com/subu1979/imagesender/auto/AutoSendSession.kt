package com.subu1979.imagesender.auto

import android.content.Context
import android.os.SystemClock
import androidx.core.content.edit

/**
 * The arming handshake between the app and [WhatsAppAutoSendService].
 *
 * Kept on disk rather than in memory: the app goes to the background the moment WhatsApp opens, and
 * OEM builds — ColorOS and HyperOS in particular — kill the process straight away. The accessibility
 * service is then re-bound into a fresh process, so anything held in a field is gone exactly when it
 * is needed. Observed on a CPH2637 (Android 16): the first on-device run did nothing because the
 * arming had evaporated between tapping Initiate and attaching the photo.
 *
 * Expiry uses [SystemClock.elapsedRealtime], which is monotonic across process death and resets only
 * on reboot — a reboot mid-send leaves the arming expired, which is the safe direction.
 *
 * Pressing Send on someone's behalf cannot be undone, so the service is built to refuse rather than
 * guess: it acts only while this holds an unexpired arming, only for the number armed here, and only
 * once.
 */
object AutoSendSession {

    /**
     * Long enough to open WhatsApp, shoot a photo and look at it before it sends.
     *
     * Two minutes was not: on a CPH2637 the camera path — open camera, frame, shoot, review — ran
     * past it, the arming expired mid-flow, and with it went the foreground service holding the
     * process unfrozen, so the service stopped receiving events and said nothing. The window is
     * generous because it is not the safety mechanism: the arming is single-shot, tied to one
     * verified chat, and cancellable from its notification.
     */
    private const val WINDOW_MS = 600_000L

    /** The preview must sit still this long, so a photo still being edited is not sent. */
    const val SETTLE_MS = 600L

    private const val PREFS = "auto_send_session"
    private const val KEY_TARGET = "target"
    private const val KEY_EXPIRES_AT = "expires_at"
    private const val KEY_LOG = "log"
    private const val KEY_VERIFIED_AT = "verified_at"
    private const val KEY_ARMED_AT = "armed_at"

    /**
     * How long after the launch a WhatsApp conversation is taken to be the one this app opened.
     *
     * WhatsApp titles a chat with the contact's name, or an unsaved number's profile name, and only
     * falls back to digits when it has neither. Requiring digits therefore worked for the user's own
     * chat and for nobody else.
     */
    private const val LAUNCH_GRACE_MS = 60_000L
    private const val MAX_EVENTS = 40
    private const val LOG_SEPARATOR = "\n"

    fun arm(context: Context, digits: String) {
        context.prefs().edit {
            putString(KEY_TARGET, digits)
            putLong(KEY_EXPIRES_AT, SystemClock.elapsedRealtime() + WINDOW_MS)
            putLong(KEY_ARMED_AT, SystemClock.elapsedRealtime())
            remove(KEY_VERIFIED_AT)
        }
        log(context, "ARMED for +$digits")
    }

    fun cancel(context: Context, reason: String) {
        if (context.prefs().getString(KEY_TARGET, null) != null) log(context, "CANCELLED ($reason)")
        clearArming(context)
    }

    /** The armed recipient, or null once it has expired, been consumed or was never set. */
    fun activeTarget(context: Context): String? {
        val prefs = context.prefs()
        val digits = prefs.getString(KEY_TARGET, null) ?: return null
        if (SystemClock.elapsedRealtime() > prefs.getLong(KEY_EXPIRES_AT, 0L)) {
            log(context, "EXPIRED without sending")
            clearArming(context)
            return null
        }
        return digits
    }

    /**
     * Records that the armed number was seen on a WhatsApp chat screen.
     *
     * The screen that carries the Send control is not always the one that names the recipient — the
     * gallery picker shows photo timestamps and nothing else — so the recipient is confirmed once,
     * when WhatsApp opens the chat, and that confirmation authorises the composer that follows.
     */
    fun markChatVerified(context: Context) {
        context.prefs().edit { putLong(KEY_VERIFIED_AT, SystemClock.elapsedRealtime()) }
    }

    /** True while the chat WhatsApp just opened can still be attributed to this app's launch. */
    fun withinLaunchGrace(context: Context): Boolean {
        val armedAt = context.prefs().getLong(KEY_ARMED_AT, 0L)
        return armedAt != 0L && SystemClock.elapsedRealtime() - armedAt < LAUNCH_GRACE_MS
    }

    /** True while a chat confirmation from this arming is still recent enough to act on. */
    fun chatVerified(context: Context): Boolean {
        val at = context.prefs().getLong(KEY_VERIFIED_AT, 0L)
        return at != 0L && SystemClock.elapsedRealtime() - at < WINDOW_MS
    }

    /** Called when a different chat is on screen: the confirmation no longer applies. */
    fun clearChatVerified(context: Context) {
        context.prefs().edit { remove(KEY_VERIFIED_AT) }
    }

    /** Marks the single permitted click as spent. */
    fun consume(context: Context) = clearArming(context)

    fun log(context: Context, line: String) {
        val prefs = context.prefs()
        val lines = prefs.getString(KEY_LOG, "").orEmpty()
            .split(LOG_SEPARATOR)
            .filter { it.isNotBlank() }
            .plus(line)
            .takeLast(MAX_EVENTS)
        prefs.edit { putString(KEY_LOG, lines.joinToString(LOG_SEPARATOR)) }
    }

    fun snapshot(context: Context): List<String> =
        context.prefs().getString(KEY_LOG, "").orEmpty()
            .split(LOG_SEPARATOR)
            .filter { it.isNotBlank() }

    fun clearLog(context: Context) {
        context.prefs().edit { remove(KEY_LOG) }
    }

    private fun clearArming(context: Context) {
        context.prefs().edit {
            remove(KEY_TARGET)
            remove(KEY_EXPIRES_AT)
            remove(KEY_VERIFIED_AT)
            remove(KEY_ARMED_AT)
        }
    }

    private fun Context.prefs() = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
