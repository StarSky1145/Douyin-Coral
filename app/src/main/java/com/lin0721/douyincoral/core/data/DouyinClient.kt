package com.lin0721.douyincoral.core.data

import android.content.Context
import com.lin0721.douyincoral.core.Diag
import com.lin0721.douyincoral.core.DouyinEnv
import com.lin0721.douyincoral.core.DouyinException
import com.lin0721.douyincoral.core.RiskControlException
import com.lin0721.douyincoral.core.session.CookieStore
import com.lin0721.douyincoral.core.sign.SignEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 抖音 web 接口客户端：OkHttp 直连 `www.douyin.com`，自己带 Cookie、Referer、UA、
 * msToken，并按当前请求的查询串签出 a_bogus。
 *
 * 签名档位有两份（见 [SignEngine.Profile]），默认用公开实现里长期在用的那份；如果服务器
 * 回了空 body（典型的风控静默拒绝），会换另一份再试一次，之后记住能用的那份。
 */
class DouyinClient(
    private val appContext: Context,
    private val cookies: CookieStore,
    private val signer: SignEngine,
) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private var profile = SignEngine.Profile.CLASSIC
    private var ttwidTried = false

    /** 发一次接口请求并返回解析后的 JSON；空 body 返回空对象交给上层判断。 */
    suspend fun request(
        path: String,
        params: Map<String, Any?> = emptyMap(),
        method: String = "GET",
        body: String? = null,
        timeoutMs: Long = 20_000L,
    ): JSONObject {
        ensureTtwid()
        val query = buildQuery(params)
        val verb = method.uppercase(Locale.US)
        var text = fetch(path, query, verb, body, profile, timeoutMs)

        if (text.isBlank()) {
            val backup = if (profile == SignEngine.Profile.CLASSIC) {
                SignEngine.Profile.V1019
            } else {
                SignEngine.Profile.CLASSIC
            }
            val retry = runCatching { fetch(path, query, verb, body, backup, timeoutMs) }.getOrNull()
            if (!retry.isNullOrBlank()) {
                profile = backup
                Diag.log(TAG, "签名档位切到 ${backup.name}", appContext)
                text = retry
            }
        }
        if (text.isBlank()) return JSONObject()

        val head = text.trimStart()
        if (head.startsWith("<!DOCTYPE", ignoreCase = true) || head.startsWith("<html", ignoreCase = true)) {
            throw RiskControlException("被风控要求验证：抖音返回了网页而不是数据，请先登录或稍后再试")
        }
        return runCatching { JSONObject(text) }.getOrElse {
            throw DouyinException("接口返回的不是 JSON：${text.take(60)}", it)
        }
    }

    private suspend fun fetch(
        path: String,
        query: String,
        verb: String,
        body: String?,
        profile: SignEngine.Profile,
        timeoutMs: Long,
    ): String {
        val signature = signer.sign(query = query, body = body.orEmpty(), profile = profile)
        val builder = Request.Builder()
            .url("${DouyinEnv.HOME_URL.trimEnd('/')}$path?$query&a_bogus=${encode(signature)}")
            .header("User-Agent", DouyinEnv.USER_AGENT)
            .header("Referer", DouyinEnv.REFERER)
            .header("Accept", "application/json, text/plain, */*")
            .header("Accept-Language", "zh-CN,zh;q=0.9")
            .header("Cookie", cookies.header())

        if (verb == "POST") {
            builder.header("Origin", DouyinEnv.HOME_URL.trimEnd('/'))
            builder.post(body.orEmpty().toRequestBody(FORM))
        } else {
            builder.get()
        }

        return withContext(Dispatchers.IO) {
            val response = try {
                withTimeout(timeoutMs) { http.newCall(builder.build()).await() }
            } catch (e: TimeoutCancellationException) {
                throw DouyinException("请求超时：$path")
            }
            response.use {
                val text = it.body?.string().orEmpty()
                Diag.log(TAG, "$verb $path → ${it.code}，${text.length} 字节，档位 ${profile.name}", appContext)
                if (!it.isSuccessful) throw DouyinException("接口返回 HTTP ${it.code}")
                text
            }
        }
    }

    /** 没有 ttwid 时去官方注册口拿一个：免登录，之后匿名请求才有机会通过。 */
    private suspend fun ensureTtwid() {
        if (cookies.cookie("ttwid") != null || ttwidTried) return
        ttwidTried = true
        val payload = """{"region":"cn","aid":1768,"needFid":false,""" +
            """"service":"www.douyin.com","migrate_info":{"ticket":"","source":"node"},""" +
            """"cbUrlProtocol":"https","union":true}"""
        val request = Request.Builder()
            .url(DouyinEnv.TTWID_URL)
            .header("User-Agent", DouyinEnv.USER_AGENT)
            .post(payload.toRequestBody(JSON))
            .build()
        runCatching {
            withTimeout(8_000L) { http.newCall(request).await() }.use { response ->
                for (line in response.headers("Set-Cookie")) {
                    val value = Regex("ttwid=([^;]+)").find(line)?.groupValues?.get(1) ?: continue
                    cookies.put("ttwid", value)
                    Diag.log(TAG, "ttwid 兜底注册成功", appContext)
                    return
                }
            }
        }.onFailure {
            Diag.log(TAG, "ttwid 兜底注册失败：${it.message}", appContext)
        }
    }

    private fun buildQuery(params: Map<String, Any?>): String {
        val all = LinkedHashMap<String, Any?>()
        all.putAll(commonParams())
        all.putAll(params)
        return all.entries
            .filter { it.value != null }
            .joinToString("&") { (key, value) -> "${encode(key)}=${encode(value.toString())}" }
    }

    /** 网页端每次请求都会带的浏览器环境参数，少了它们签名再对也会被判成机器。 */
    private fun commonParams(): Map<String, Any?> {
        val verifyFp = cookies.verifyFp()
        return linkedMapOf(
            "device_platform" to "webapp",
            "aid" to 6383,
            "channel" to "channel_pc_web",
            "pc_client_type" to 1,
            "pc_libra_divert" to "Windows",
            "update_version_code" to 170400,
            "support_h265" to 1,
            "support_dash" to 0,
            "version_code" to "190500",
            "version_name" to "19.5.0",
            "cookie_enabled" to "true",
            "screen_width" to 1920,
            "screen_height" to 1080,
            "browser_language" to "zh-CN",
            "browser_platform" to "Win32",
            "browser_name" to "Chrome",
            "browser_version" to "131.0.0.0",
            "browser_online" to "true",
            "engine_name" to "Blink",
            "engine_version" to "131.0.0.0",
            "os_name" to "Windows",
            "os_version" to "10",
            "cpu_core_num" to 12,
            "device_memory" to 8,
            "platform" to "PC",
            "downlink" to 10,
            "effective_type" to "4g",
            "round_trip_time" to 50,
            "msToken" to cookies.msToken(),
            "verifyFp" to verifyFp,
            "fp" to verifyFp,
        )
    }

    /** 只保留 unreserved 字符的百分号编码，保证「签什么就发什么」。 */
    private fun encode(value: String): String {
        val out = StringBuilder(value.length + 16)
        for (byte in value.toByteArray(Charsets.UTF_8)) {
            val code = byte.toInt() and 0xFF
            val keep = code in 'a'.code..'z'.code || code in 'A'.code..'Z'.code ||
                code in '0'.code..'9'.code || code == '-'.code || code == '_'.code ||
                code == '.'.code || code == '~'.code
            if (keep) {
                out.append(code.toChar())
            } else {
                out.append('%').append(HEX[code shr 4]).append(HEX[code and 0x0F])
            }
        }
        return out.toString()
    }

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!continuation.isCancelled) continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response)
            }
        })
        continuation.invokeOnCancellation { runCatching { cancel() } }
    }

    private companion object {
        const val TAG = "DouyinClient"
        const val HEX = "0123456789ABCDEF"
        val FORM = "application/x-www-form-urlencoded".toMediaType()
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
