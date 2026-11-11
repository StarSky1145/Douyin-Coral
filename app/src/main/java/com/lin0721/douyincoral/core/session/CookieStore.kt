package com.lin0721.douyincoral.core.session

import android.content.Context
import android.webkit.CookieManager
import com.lin0721.douyincoral.core.Diag
import com.lin0721.douyincoral.core.DouyinEnv
import org.json.JSONObject
import kotlin.random.Random

/**
 * 网页登录态的本地仓库。
 *
 * 登录仍然交给内嵌 WebView（验证码、滑块的活只有真实网页干得了），登录成功后把
 * CookieManager 里 douyin.com 的整组 Cookie 落到 SharedPreferences，之后 OkHttp 直接
 * 头里带上它们。msToken 与 s_v_web_id 网页没给时会生成一份稳定的兜底值。
 */
class CookieStore(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 从 WebView 的 CookieManager 同步一次，返回有变化的条数。 */
    fun syncFromWebView(): Int {
        val raw = runCatching { CookieManager.getInstance().getCookie(DouyinEnv.HOME_URL) }.getOrNull()
        if (raw.isNullOrBlank()) return 0
        val merged = read().toMutableMap()
        var changed = 0
        for (part in raw.split(';')) {
            val item = part.trim()
            val eq = item.indexOf('=')
            if (eq <= 0) continue
            val name = item.substring(0, eq)
            val value = item.substring(eq + 1)
            if (merged[name] != value) {
                merged[name] = value
                changed++
            }
        }
        if (changed > 0) {
            write(merged)
            Diag.log(TAG, "同步 $changed 个 Cookie（共 ${merged.size} 个）", appContext)
        }
        return changed
    }

    /** 读取单个 Cookie。 */
    fun cookie(name: String): String? = read()[name]?.takeIf { it.isNotBlank() }

    /** 补写一个 Cookie（示例：ttwid 兜底注册拿到的值）。 */
    fun put(name: String, value: String) {
        val map = read().toMutableMap()
        map[name] = value
        write(map)
    }

    /** 是否已登录：sessionid 与 sid_tt 都在就算。 */
    val hasLogin: Boolean
        get() = cookie("sessionid") != null && cookie("sid_tt") != null

    /** 请求头里那一整串 Cookie。 */
    fun header(): String {
        val map = read().toMutableMap()
        if (map["s_v_web_id"].isNullOrBlank()) map["s_v_web_id"] = verifyFp()
        return map.entries.joinToString("; ") { "${it.key}=${it.value}" }
    }

    /** 接口必带 msToken：优先用网页给的，没有就用本地兜底值。 */
    fun msToken(): String {
        cookie("msToken")?.takeIf { it.length >= 160 }?.let { return it }
        prefs.getString(KEY_MS, null)?.let { return it }
        return generateMsToken().also { prefs.edit().putString(KEY_MS, it).apply() }
    }

    /** verifyFp 与 s_v_web_id 同值，请求参数和 Cookie 里都要。 */
    fun verifyFp(): String {
        cookie("s_v_web_id")?.let { return it }
        prefs.getString(KEY_FP, null)?.let { return it }
        return generateVerifyFp().also { prefs.edit().putString(KEY_FP, it).apply() }
    }

    /** 退出登录：本地凭据全部清掉。 */
    fun clear() {
        prefs.edit().clear().apply()
    }

    /** 诊断摘要：只列名字和登录态，不泄露值。 */
    fun summary(): String {
        val names = read().keys.sorted()
        return "登录=${if (hasLogin) "是" else "否"} cookie=${names.size} 个 ${names.joinToString(",")}"
    }

    private fun read(): Map<String, String> {
        val text = prefs.getString(KEY_COOKIES, null) ?: return emptyMap()
        val obj = runCatching { JSONObject(text) }.getOrNull() ?: return emptyMap()
        val out = linkedMapOf<String, String>()
        for (name in obj.keys()) out[name] = obj.optString(name, "")
        return out
    }

    private fun write(map: Map<String, String>) {
        val obj = JSONObject()
        for ((name, value) in map) obj.put(name, value)
        prefs.edit().putString(KEY_COOKIES, obj.toString()).apply()
    }

    private fun generateMsToken(): String {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
        val body = buildString { repeat(182) { append(alphabet[Random.nextInt(alphabet.length)]) } }
        return body + "=="
    }

    /** 与网页端同构的 verifyFp：时间戳 base36 + 定长随机串。 */
    private fun generateVerifyFp(): String {
        val alphabet = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
        val stamp = System.currentTimeMillis()
        val base36 = buildString {
            var value = stamp
            while (value > 0) {
                val rest = value % 36
                insert(0, if (rest < 10) '0' + rest.toInt() else 'a' + (rest - 10).toInt())
                value /= 36
            }
        }
        val body = CharArray(36) { '0' }
        for (i in body.indices) {
            body[i] = when (i) {
                8, 13, 18, 23 -> '_'
                14 -> '4'
                else -> {
                    val n = Random.nextInt(alphabet.length)
                    if (i == 19) alphabet[(3 and n) or 8] else alphabet[n]
                }
            }
        }
        return "verify_" + base36 + "_" + String(body)
    }

    private companion object {
        const val TAG = "CookieStore"
        const val PREFS = "douyin_session"
        const val KEY_COOKIES = "cookies"
        const val KEY_MS = "fallback_ms_token"
        const val KEY_FP = "fallback_verify_fp"
    }
}
