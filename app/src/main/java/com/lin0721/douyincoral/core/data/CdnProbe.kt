package com.lin0721.douyincoral.core.data

import android.webkit.CookieManager
import com.lin0721.douyincoral.core.Diag
import com.lin0721.douyincoral.core.model.VideoInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * 视频直链的探测与自愈。
 *
 * 抖音一次下发好几条 CDN 线路，哪条能通取决于会话和地区；而且没有 ttwid / cookie 的
 * 请求会被直接 403。所以这里做两件事：把网页会话的 Cookie 带上，再挨个试到通为止。
 */
object CdnProbe {

    const val UA =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/120.0.0.0 Mobile Safari/537.36"

    fun cookie(): String =
        runCatching { CookieManager.getInstance().getCookie("https://www.douyin.com") ?: "" }.getOrDefault("")

    fun candidates(video: VideoInfo): List<String> = buildList {
        addAll(video.playUrls)
        if (video.playUrl.isNotEmpty()) add(video.playUrl)
        if (video.uri.isNotEmpty()) {
            add("https://aweme.snssdk.com/aweme/v1/play/?video_id=${video.uri}&ratio=1080p&line=0")
        }
    }.distinct()

    private val cache = java.util.Collections.synchronizedMap(HashMap<String, String>())

    fun cached(key: String): String? = cache[key]

    fun remember(key: String, url: String) {
        cache[key] = url
    }

    fun forget(key: String) {
        cache.remove(key)
    }

    /** 依次探测候选地址，返回第一个能读的；全挂就把第一条兜底交出去 */
    suspend fun resolve(key: String, video: VideoInfo): String = withContext(Dispatchers.IO) {
        cached(key)?.let { return@withContext it }
        val list = candidates(video)
        if (list.isEmpty()) return@withContext ""
        for (url in list.take(5)) {
            if (probe(url)) {
                remember(key, url)
                Diag.log("CdnProbe", "hit $url".take(160))
                return@withContext url
            }
        }
        Diag.log("CdnProbe", "all failed, fallback ${list.first().take(120)}")
        list.first()
    }

    private fun probe(url: String): Boolean = runCatching {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 4000
            readTimeout = 4000
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("User-Agent", UA)
            setRequestProperty("Referer", "https://www.douyin.com/")
            setRequestProperty("Accept", "*/*")
            setRequestProperty("Range", "bytes=0-1024")
            val c = cookie()
            if (c.isNotEmpty()) setRequestProperty("Cookie", c)
        }
        try {
            val code = conn.responseCode
            Diag.log("CdnProbe", "probe $code ${url.take(90)}")
            code in 200..299 || code == 206
        } finally {
            conn.disconnect()
        }
    }.getOrDefault(false)
}
