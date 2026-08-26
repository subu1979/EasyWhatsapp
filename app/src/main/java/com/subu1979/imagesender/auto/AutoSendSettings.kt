package com.subu1979.imagesender.auto

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.TextUtils
import androidx.core.content.edit

enum class SendMode { MANUAL, AUTO }

/**
 * The user's choice of mode, and the state of the system switch that Auto mode depends on.
 *
 * Auto mode is only ever *requested* here; whether it can act depends on the Accessibility service
 * being enabled, which only the user can do in system settings.
 */
object AutoSendSettings {

    private const val PREFS = "auto_send"
    private const val KEY_MODE = "mode"

    fun mode(context: Context): SendMode =
        if (context.prefs().getString(KEY_MODE, SendMode.MANUAL.name) == SendMode.AUTO.name) {
            SendMode.AUTO
        } else {
            SendMode.MANUAL
        }

    fun setMode(context: Context, mode: SendMode) {
        context.prefs().edit { putString(KEY_MODE, mode.name) }
        if (mode == SendMode.MANUAL) AutoSendSession.cancel(context, "switched to manual")
    }

    /** Whether the user has granted the service in system settings. */
    fun serviceEnabled(context: Context): Boolean {
        val expected = ComponentName(context, WhatsAppAutoSendService::class.java)
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabled)
        for (entry in splitter) {
            if (ComponentName.unflattenFromString(entry) == expected) return true
        }
        return false
    }

    fun openAccessibilitySettings(context: Context): Boolean = runCatching {
        context.startActivity(
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        true
    }.getOrDefault(false)

    /** App info, where Android 13+ hides "Allow restricted settings" for sideloaded installs. */
    fun openAppInfo(context: Context): Boolean = runCatching {
        context.startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                android.net.Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        true
    }.getOrDefault(false)

    private fun Context.prefs() = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
