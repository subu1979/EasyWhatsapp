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

    /** ACTION_SEND with an image MIME type, a content URI and temporary read permission. */
    fun shareImage(context: Context, image: Uri, app: WhatsAppApp): ShareResult {
        if (!isInstalled(context, app.packageName)) return ShareResult.NotInstalled

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = MIME_IMAGE
            putExtra(Intent.EXTRA_STREAM, image)
            // ClipData carries the grant to targets that read the URI from the clip instead.
            clipData = ClipData.newUri(context.contentResolver, "image", image)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            setPackage(app.packageName)
        }
        return start(context, intent)
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
}
