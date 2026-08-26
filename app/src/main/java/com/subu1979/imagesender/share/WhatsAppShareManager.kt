package com.subu1979.imagesender.share

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

/**
 * Opens a WhatsApp chat for a number the user typed, through the documented click-to-chat link.
 *
 * This is the only mechanism that reaches a number which is not in the address book: WhatsApp's
 * share picker lists chats and contacts only, so an attachment sent from here could never be aimed
 * at an unsaved recipient. Attachments are therefore left to WhatsApp itself, which can add them to
 * the chat this opens.
 */
object WhatsAppShareManager {

    fun installedApps(context: Context): List<WhatsAppApp> =
        WhatsAppApp.entries.filter { isInstalled(context, it.packageName) }

    fun isInstalled(context: Context, packageName: String): Boolean = try {
        context.packageManager.getPackageInfo(packageName, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    fun openChat(context: Context, digits: String, app: WhatsAppApp): ShareResult {
        if (!isInstalled(context, app.packageName)) return ShareResult.NotInstalled

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$digits")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            setPackage(app.packageName)
        }
        return try {
            context.startActivity(intent)
            ShareResult.Success
        } catch (_: ActivityNotFoundException) {
            ShareResult.LaunchFailed
        }
    }
}
