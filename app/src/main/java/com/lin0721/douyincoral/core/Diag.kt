package com.lin0721.douyincoral.core

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 现场记录。手机上没有 adb，出了事只能靠它：日志同时留在内存和
 * /sdcard/Download/chaodian-diag.txt，随时能捞出来看。
 */
object Diag {

    private const val FILE_NAME = "chaodian-diag.txt"
    private val buffer = StringBuilder()
    private val stamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private var counter = 0

    @Synchronized
    fun log(tag: String, message: String, context: Context? = null) {
        buffer.append(stamp.format(Date()))
            .append(' ')
            .append(tag)
            .append(" | ")
            .append(message)
            .append('\n')
        if (buffer.length > 200_000) buffer.delete(0, 60_000)
        counter++
        if (context != null && counter % 3 == 0) flush(context)
    }

    @Synchronized
    fun dump(): String = buffer.toString()

    @Synchronized
    fun flush(context: Context) {
        val body = "潮见诊断 · " + stamp.format(Date()) + "\n" + buffer
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                writeViaMediaStore(context, body)
            } else {
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                File(dir, FILE_NAME).writeText(body)
            }
        }
    }

    private fun writeViaMediaStore(context: Context, body: String) {
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        runCatching {
            resolver.query(
                collection,
                arrayOf(MediaStore.Downloads._ID),
                "${MediaStore.Downloads.DISPLAY_NAME}=?",
                arrayOf(FILE_NAME),
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(0)
                    resolver.delete(
                        collection.buildUpon().appendPath(id.toString()).build(),
                        null,
                        null,
                    )
                }
            }
        }
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, FILE_NAME)
            put(MediaStore.Downloads.MIME_TYPE, "text/plain")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        val uri = resolver.insert(collection, values) ?: return
        resolver.openOutputStream(uri)?.use { it.write(body.toByteArray()) }
    }
}
