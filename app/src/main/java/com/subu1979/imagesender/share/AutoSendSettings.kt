package com.subu1979.imagesender.share

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.TextUtils

/** Reads and opens the system switch that controls [WhatsAppAutoSendService]. */
object AutoSendSettings {

    fun isEnabled(context: Context): Boolean {
        val expected = ComponentName(context, WhatsAppAutoSendService::class.java)
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabled)
        for (entry in splitter) {
            val component = ComponentName.unflattenFromString(entry) ?: continue
            if (component == expected) return true
        }
        return false
    }

    /** Opens the system Accessibility screen; the user flips the switch there. */
    fun openSettings(context: Context): Boolean = runCatching {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    }.getOrDefault(false)
}
