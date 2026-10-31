package com.lin0721.douyincoral.core.data

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * 抖音的视频直链要带 Referer，否则 CDN 会 403。
 * 下载完成写进系统「下载」目录（Q 以上走 MediaStore，免权限）。
 */
class VideoDownloader(private val context: Context) {

    suspend fun download(url: String, fileName: String): String = withContext(Dispatchers.IO) {
        require(url.startsWith("http")) { "视频地址无效" }
        val safeName = sanitize(fileName) + ".mp4"
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", CdnProbe.UA)
            setRequestProperty("Referer", "https://www.douyin.com/")
            setRequestProperty("Accept", "*/*")
            val cookie = CdnProbe.cookie()
            if (cookie.isNotEmpty()) setRequestProperty("Cookie", cookie)
        }
        try {
            if (conn.responseCode !in 200..299) {
                throw IllegalStateException("下载失败：HTTP ${conn.responseCode}")
            }
            conn.inputStream.use { input ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.Video.Media.DISPLAY_NAME, safeName)
                        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                        put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                        put(MediaStore.Video.Media.IS_PENDING, 1)
                    }
                    val resolver = context.contentResolver
                    val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                        ?: throw IllegalStateException("无法创建下载文件")
                    resolver.openOutputStream(uri)?.use { output -> input.copyTo(output) }
                        ?: throw IllegalStateException("无法写入下载文件")
                    values.clear()
                    values.put(MediaStore.Video.Media.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                    "已保存到「下载」：$safeName"
                } else {
                    val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "")
                    if (!dir.exists()) dir.mkdirs()
                    val file = File(dir, safeName)
                    file.outputStream().use { output -> input.copyTo(output) }
                    "已保存：${file.absolutePath}"
                }
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun sanitize(name: String): String {
        val cleaned = name.replace(Regex("[\\\\/:*?\"<>|\\n\\r]"), " ").trim()
        return cleaned.take(60).ifBlank { "douyin_${System.currentTimeMillis()}" }
    }

}
