package com.subu1979.imagesender

import android.Manifest
import android.provider.ContactsContract
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.subu1979.imagesender.share.ContactBridge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The temporary-contact bridge is what makes an unsaved number visible to WhatsApp, so it is worth
 * proving on a real device that the contact appears and, more importantly, disappears again.
 */
@RunWith(AndroidJUnit4::class)
class ContactBridgeTest {

    @get:Rule
    val permissions: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.WRITE_CONTACTS
    )

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val number = "+919551563593"

    @Test
    fun contactIsCreatedAndFullyRemoved() {
        val rawContactId = ContactBridge.createTemporary(context, number, "+91 95515 63593")
        assertNotNull("contact insert failed", rawContactId)
        assertEquals(1, countRowsFor(number))

        ContactBridge.delete(context, rawContactId!!)
        assertEquals("contact survived deletion", 0, countRowsFor(number))
        assertEquals("raw contact row survived deletion", 0, countRawContacts(rawContactId))
    }

    @Test
    fun cleanUpRemovesAContactLeftBehind() {
        val rawContactId = ContactBridge.createTemporary(context, number, "+91 95515 63593")
        assertNotNull(rawContactId)

        // Simulates the app being killed mid-send: nothing deleted it, so the next launch must.
        ContactBridge.cleanUpLeftovers(context)
        assertEquals(0, countRowsFor(number))
    }

    private fun countRowsFor(phoneNumber: String): Int {
        val cursor = context.contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(ContactsContract.Data._ID),
            "${ContactsContract.Data.MIMETYPE} = ? AND " +
                "${ContactsContract.CommonDataKinds.Phone.NUMBER} = ?",
            arrayOf(ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE, phoneNumber),
            null
        )
        return cursor?.use { it.count } ?: 0
    }

    private fun countRawContacts(rawContactId: Long): Int {
        val cursor = context.contentResolver.query(
            ContactsContract.RawContacts.CONTENT_URI,
            arrayOf(ContactsContract.RawContacts._ID),
            "${ContactsContract.RawContacts._ID} = ?",
            arrayOf(rawContactId.toString()),
            null
        )
        return cursor?.use { it.count } ?: 0
    }
}
