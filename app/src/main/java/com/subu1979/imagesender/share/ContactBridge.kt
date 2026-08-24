package com.subu1979.imagesender.share

import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.Context
import android.provider.ContactsContract
import androidx.core.content.edit

/**
 * Creates the throwaway contact that makes a number visible to WhatsApp, and removes it again.
 *
 * WhatsApp only lists numbers it can see in the address book, so an unsaved number never appears in
 * its share picker. The contact therefore exists for the duration of one send and is deleted as
 * soon as the user comes back. The id is also persisted, so a contact survives a crash or a force
 * stop only until the next launch, when [cleanUpLeftovers] removes it.
 */
object ContactBridge {

    private const val PREFS = "contact_bridge"
    private const val KEY_PENDING_RAW_CONTACT_ID = "pending_raw_contact_id"
    private const val NO_ID = -1L

    /** Inserts a local contact holding [e164] and returns its raw contact id. */
    fun createTemporary(context: Context, e164: String, displayName: String): Long? {
        val operations = arrayListOf(
            ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                .build(),
            ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(
                    ContactsContract.Data.MIMETYPE,
                    ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE
                )
                .withValue(
                    ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME,
                    displayName
                )
                .build(),
            ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(
                    ContactsContract.Data.MIMETYPE,
                    ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE
                )
                .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, e164)
                .withValue(
                    ContactsContract.CommonDataKinds.Phone.TYPE,
                    ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE
                )
                .build()
        )

        return runCatching {
            val results = context.contentResolver.applyBatch(ContactsContract.AUTHORITY, operations)
            val uri = results.firstOrNull()?.uri ?: return null
            val rawContactId = ContentUris.parseId(uri)
            rememberPending(context, rawContactId)
            rawContactId
        }.getOrNull()
    }

    fun delete(context: Context, rawContactId: Long) {
        // CALLER_IS_SYNC_ADAPTER removes the row outright instead of tombstoning it.
        val uri = ContactsContract.RawContacts.CONTENT_URI.buildUpon()
            .appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true")
            .build()
        runCatching {
            context.contentResolver.delete(
                uri,
                "${ContactsContract.RawContacts._ID} = ?",
                arrayOf(rawContactId.toString())
            )
        }
        forgetPending(context, rawContactId)
    }

    /** Deletes a contact left behind by an interrupted send. Safe to call on every launch. */
    fun cleanUpLeftovers(context: Context) {
        val pending = context.prefs().getLong(KEY_PENDING_RAW_CONTACT_ID, NO_ID)
        if (pending != NO_ID) delete(context, pending)
    }

    private fun rememberPending(context: Context, rawContactId: Long) {
        context.prefs().edit { putLong(KEY_PENDING_RAW_CONTACT_ID, rawContactId) }
    }

    private fun forgetPending(context: Context, rawContactId: Long) {
        val prefs = context.prefs()
        if (prefs.getLong(KEY_PENDING_RAW_CONTACT_ID, NO_ID) == rawContactId) {
            prefs.edit { remove(KEY_PENDING_RAW_CONTACT_ID) }
        }
    }

    private fun Context.prefs() = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
