package com.subu1979.imagesender.share

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

/**
 * Launches WhatsApp / WhatsApp Business through public Android mechanisms only (FR-05, FR-06).
 *
 * Deliberately avoids undocumented WhatsApp extras and private activities (PRD section 7), so the
 * recipient is confirmed inside WhatsApp itself rather than pre-selected by a reverse-engineered
 * intent. [openChat] opens the chat for an unsaved number via the public wa.me deep link.
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

    /**
     * ACTION_SEND with an image MIME type, a content URI and temporary read permission.
     *
     * When [recipientDigits] is given, the chat JID is attached so WhatsApp opens straight into
     * that conversation with the image already staged and only Send left to press. That extra is
     * not part of any documented contract, so it is treated as an optimisation: if WhatsApp
     * rejects the intent, the same share is retried without it and WhatsApp asks for the recipient
     * as before.
     */
    fun shareImage(
        context: Context,
        image: Uri,
        app: WhatsAppApp,
        recipientDigits: String? = null
    ): ShareResult {
        if (!isInstalled(context, app.packageName)) return ShareResult.NotInstalled

        if (recipientDigits != null) {
            val direct = start(context, sendIntent(context, image, app, recipientDigits))
            if (direct != ShareResult.LaunchFailed) return direct
        }
        return start(context, sendIntent(context, image, app, recipient = null))
    }

    private fun sendIntent(
        context: Context,
        image: Uri,
        app: WhatsAppApp,
        recipient: String?
    ): Intent = Intent(Intent.ACTION_SEND).apply {
        type = MIME_IMAGE
        putExtra(Intent.EXTRA_STREAM, image)
        // ClipData carries the grant to targets that read the URI from the clip instead.
        clipData = ClipData.newUri(context.contentResolver, "image", image)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        setPackage(app.packageName)
        if (recipient != null) putExtra(EXTRA_JID, "$recipient$JID_SUFFIX")
    }

    /**
     * Opens the chat with an unsaved number using the public wa.me link, without attaching anything
     * and without creating a contact.
     */
    fun openChat(context: Context, digits: String, app: WhatsAppApp): ShareResult {
        if (!isInstalled(context, app.packageName)) return ShareResult.NotInstalled

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$digits")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            setPackage(app.packageName)
        }
        return start(context, intent)
    }

    private fun start(context: Context, intent: Intent): ShareResult = try {
        context.startActivity(intent)
        ShareResult.Success
    } catch (_: ActivityNotFoundException) {
        ShareResult.LaunchFailed
    } catch (_: SecurityException) {
        ShareResult.UriNotGrantable
    }

    private const val MIME_IMAGE = "image/*"

    /** WhatsApp's chat-target extra: "<country code><number>@s.whatsapp.net". */
    private const val EXTRA_JID = "jid"
    private const val JID_SUFFIX = "@s.whatsapp.net"
}
