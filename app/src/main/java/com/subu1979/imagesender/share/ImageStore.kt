package com.subu1979.imagesender.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * Image handling for the share flow.
 *
 * The picked photo-picker URI is forwarded as-is wherever possible (PRD section 8: do not persist
 * images unnecessarily). [copyToCache] is the fallback for the case where the receiving app cannot
 * be granted the original URI; only then does the app own the file and need FileProvider.
 */
object ImageStore {

    private const val CACHE_DIR = "shared_images"
    private const val PREVIEW_MAX_PX = 1440

    /** Decodes a down-sampled preview so large photos do not blow up the memory budget. */
    fun loadPreview(context: Context, uri: Uri): Bitmap? = runCatching {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val longestEdge = maxOf(info.size.width, info.size.height)
            if (longestEdge > PREVIEW_MAX_PX) {
                decoder.setTargetSampleSize(sampleSizeFor(longestEdge))
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = false
        }
    }.getOrNull()

    fun canRead(context: Context, uri: Uri): Boolean = runCatching {
        context.contentResolver.openInputStream(uri)?.use { true } ?: false
    }.getOrDefault(false)

    /** Copies the picked image into the app's cache and returns a grantable FileProvider URI. */
    fun copyToCache(context: Context, source: Uri): Uri? = runCatching {
        val dir = File(context.cacheDir, CACHE_DIR).apply { mkdirs() }
        val target = File(dir, "share_${System.currentTimeMillis()}")
        context.contentResolver.openInputStream(source)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: return null
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", target)
    }.getOrNull()

    /**
     * Creates the destination for ACTION_IMAGE_CAPTURE. The camera app writes into this app-owned
     * cache file, which is why FileProvider is needed here (PRD section 8).
     */
    fun createCaptureUri(context: Context): Uri? = runCatching {
        val dir = File(context.cacheDir, CACHE_DIR).apply { mkdirs() }
        val target = File(dir, "capture_${System.currentTimeMillis()}.jpg")
        target.createNewFile()
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", target)
    }.getOrNull()

    /** Removes copies left behind by earlier sessions. Called once at startup. */
    fun clearCache(context: Context) {
        runCatching { File(context.cacheDir, CACHE_DIR).listFiles()?.forEach { it.delete() } }
    }

    private fun sampleSizeFor(longestEdge: Int): Int {
        var sample = 1
        while (longestEdge / (sample * 2) >= PREVIEW_MAX_PX) sample *= 2
        return sample
    }
}
