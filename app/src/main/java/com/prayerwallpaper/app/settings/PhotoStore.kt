package com.prayerwallpaper.app.settings

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import com.prayerwallpaper.app.logic.scaledPhotoSize
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * The chosen background photo. It arrives from the system photo picker as a content URI, and a
 * downscaled JPEG copy is kept in app-private storage. No storage permission is involved.
 */
class PhotoStore(private val context: Context) {
    private val file = File(context.filesDir, "background.jpg")
    private var cached: Pair<Long, Bitmap>? = null

    /** Decodes [uri], scales it down to screen size, stores it, and returns the new version stamp. */
    @Throws(IOException::class)
    fun import(uri: Uri): Long {
        val bitmap = try {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                val (w, h) = scaledPhotoSize(info.size.width, info.size.height)
                decoder.setTargetSize(w, h)
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } catch (e: Exception) {
            throw IOException("Could not read that image", e)
        }
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it)) { "JPEG encode failed" } }
        Files.move(temp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        bitmap.recycle()
        return System.currentTimeMillis().coerceAtLeast(1)
    }

    /** The stored photo for [version], or null if none. Decoded once and kept until the version changes. */
    @Synchronized
    fun bitmap(version: Long): Bitmap? {
        if (version == 0L || !file.exists()) return null
        cached?.let { (v, bitmap) -> if (v == version) return bitmap }
        val decoded = BitmapFactory.decodeFile(file.path) ?: return null
        cached = version to decoded
        return decoded
    }
}
