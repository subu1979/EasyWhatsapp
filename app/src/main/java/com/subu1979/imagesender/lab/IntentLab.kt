package com.subu1979.imagesender.lab

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.subu1979.imagesender.share.WhatsAppApp

/** What is handed to WhatsApp alongside the recipient. */
enum class Payload(val label: String) {
    TEXT("Text only"),
    IMAGE("Image only"),
    TEXT_AND_IMAGE("Text + image")
}

/**
 * One intent permutation to try against WhatsApp.
 *
 * The point of the lab is that nothing here is assumed: each variant is built, resolved against the
 * package manager and launched, and the device decides what happens. Two of them name a private
 * WhatsApp activity, which PRD section 7 excludes from the product — they exist here only to answer
 * the question, not to ship.
 */
enum class Variant(
    val id: String,
    val label: String,
    val note: String,
    val supportsImage: Boolean
) {
    CLICK_TO_CHAT(
        id = "A_CLICK_TO_CHAT",
        label = "A · Click-to-chat (wa.me)",
        note = "Documented. Opens a chat with an unsaved number; text can be pre-filled, never sent.",
        supportsImage = false
    ),
    SEND_PLAIN(
        id = "B_ACTION_SEND",
        label = "B · ACTION_SEND",
        note = "Documented. WhatsApp asks the user to choose the recipient.",
        supportsImage = true
    ),
    SEND_JID(
        id = "C_ACTION_SEND_JID",
        label = "C · ACTION_SEND + jid",
        note = "Undocumented extra. Shipped in v1.1.0 and ignored by this WhatsApp build.",
        supportsImage = true
    ),
    SEND_JID_COMPONENT(
        id = "D_JID_CONTACT_PICKER",
        label = "D · jid + ContactPicker component",
        note = "Undocumented private activity. The permutation never tried before.",
        supportsImage = true
    ),
    SEND_COMPONENT_ONLY(
        id = "E_CONTACT_PICKER_ONLY",
        label = "E · ContactPicker component, no jid",
        note = "Control for D: shows what the component does on its own.",
        supportsImage = true
    ),
    SENDTO_SCHEME(
        id = "F_SENDTO_WHATSAPP",
        label = "F · ACTION_SENDTO whatsapp://send",
        note = "Scheme form of click-to-chat. Text only.",
        supportsImage = false
    ),
    VIEW_SCHEME(
        id = "G_VIEW_WHATSAPP",
        label = "G · ACTION_VIEW whatsapp://send",
        note = "Same scheme through ACTION_VIEW; some builds route it differently.",
        supportsImage = false
    );

    fun accepts(payload: Payload): Boolean =
        supportsImage || payload == Payload.TEXT
}

/** What the device did, recorded verbatim so the report is evidence rather than recollection. */
data class LabResult(
    val variant: Variant,
    val target: WhatsAppApp,
    val payload: Payload,
    val action: String,
    val mimeType: String?,
    val component: String?,
    val extras: List<String>,
    val resolvedActivity: String,
    val launch: String
)

object IntentLab {

    private const val CONTACT_PICKER = "com.whatsapp.contact.picker.ContactPicker"
    private const val JID_SUFFIX = "@s.whatsapp.net"
    private const val MIME_IMAGE = "image/*"
    private const val MIME_TEXT = "text/plain"

    fun run(
        context: Context,
        variant: Variant,
        target: WhatsAppApp,
        payload: Payload,
        digits: String,
        text: String,
        image: Uri?
    ): LabResult {
        val intent = build(context, variant, target, payload, digits, text, image)
        val resolved = resolve(context, intent)
        val launch = try {
            context.startActivity(intent)
            "OK"
        } catch (e: ActivityNotFoundException) {
            "ActivityNotFoundException: ${e.message}"
        } catch (e: SecurityException) {
            "SecurityException: ${e.message}"
        } catch (e: Exception) {
            "${e.javaClass.simpleName}: ${e.message}"
        }

        return LabResult(
            variant = variant,
            target = target,
            payload = payload,
            action = intent.action.orEmpty(),
            mimeType = intent.type,
            component = intent.component?.flattenToShortString(),
            extras = describeExtras(intent, payload, digits, text, image),
            resolvedActivity = resolved,
            launch = launch
        )
    }

    private fun build(
        context: Context,
        variant: Variant,
        target: WhatsAppApp,
        payload: Payload,
        digits: String,
        text: String,
        image: Uri?
    ): Intent = when (variant) {
        Variant.CLICK_TO_CHAT -> Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://wa.me/$digits?text=${Uri.encode(text)}")
        ).applyCommon(target)

        Variant.SENDTO_SCHEME -> Intent(
            Intent.ACTION_SENDTO,
            Uri.parse("whatsapp://send?phone=$digits&text=${Uri.encode(text)}")
        ).applyCommon(target)

        Variant.VIEW_SCHEME -> Intent(
            Intent.ACTION_VIEW,
            Uri.parse("whatsapp://send?phone=$digits&text=${Uri.encode(text)}")
        ).applyCommon(target)

        Variant.SEND_PLAIN -> sendIntent(context, target, payload, text, image)

        Variant.SEND_JID -> sendIntent(context, target, payload, text, image).apply {
            putExtra("jid", "$digits$JID_SUFFIX")
        }

        Variant.SEND_JID_COMPONENT -> sendIntent(context, target, payload, text, image).apply {
            putExtra("jid", "$digits$JID_SUFFIX")
            component = ComponentName(target.packageName, CONTACT_PICKER)
        }

        Variant.SEND_COMPONENT_ONLY -> sendIntent(context, target, payload, text, image).apply {
            component = ComponentName(target.packageName, CONTACT_PICKER)
        }
    }

    private fun sendIntent(
        context: Context,
        target: WhatsAppApp,
        payload: Payload,
        text: String,
        image: Uri?
    ): Intent = Intent(Intent.ACTION_SEND).apply {
        if (payload == Payload.TEXT || image == null) {
            type = MIME_TEXT
            putExtra(Intent.EXTRA_TEXT, text)
        } else {
            type = MIME_IMAGE
            putExtra(Intent.EXTRA_STREAM, image)
            clipData = ClipData.newUri(context.contentResolver, "image", image)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (payload == Payload.TEXT_AND_IMAGE) putExtra(Intent.EXTRA_TEXT, text)
        }
        applyCommon(target)
    }

    private fun Intent.applyCommon(target: WhatsAppApp): Intent = apply {
        setPackage(target.packageName)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /** What the package manager says will handle the intent, before it is launched. */
    private fun resolve(context: Context, intent: Intent): String {
        val info = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            ?: return "none — no activity matched"
        return "${info.activityInfo.packageName}/${info.activityInfo.name}"
    }

    private fun describeExtras(
        intent: Intent,
        payload: Payload,
        digits: String,
        text: String,
        image: Uri?
    ): List<String> = buildList {
        intent.getStringExtra("jid")?.let { add("jid=$it") }
        if (intent.hasExtra(Intent.EXTRA_STREAM)) add("EXTRA_STREAM=${image ?: "null"}")
        if (intent.hasExtra(Intent.EXTRA_TEXT)) add("EXTRA_TEXT=\"$text\"")
        intent.data?.let { add("data=$it") }
        add("payload=${payload.name}")
        add("recipient=$digits")
    }
}
