package com.subu1979.imagesender.share

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/** The pair of permissions needed to create and remove the temporary recipient contact. */
object ContactPermission {

    val REQUIRED = arrayOf(
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.WRITE_CONTACTS
    )

    fun isGranted(context: Context): Boolean = REQUIRED.all { permission ->
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
}
