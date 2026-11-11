package com.lin0721.douyincoral.core.sign

import android.content.Context
import app.cash.quickjs.QuickJs
import com.lin0721.douyincoral.core.Diag
import com.lin0721.douyincoral.core.DouyinEnv
import com.lin0721.douyincoral.core.DouyinException
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.concurrent.Executors

/**
 * a_bogus 签名器。
 *
 * 算法在 `assets/sign/abogus.js` 里（纯 JS，不依赖浏览器或 Node），这里只负责用 QuickJS
 * 起一个常驻解释器、把参数递进去、把 a_bogus 取回来。QuickJS 的上下文不是线程安全的，
 * 所以所有调用都串到同一个后台线程上执行。
 */
class SignEngine(context: Context) {

    /** 签名档位：盐值与 pageId 随抖音版本变化，两份都留着，谁通就用谁。 */
    enum class Profile(val salt: String, val pageId: Int) {
        /** 公开实现里长期在用的默认值 */
        CLASSIC("cus", 0),

        /** 1.0.1.19 版分析报告给出的取值 */
        V1019("dhzx", 6241),
    }

    private val appContext = context.applicationContext
    private val executor = Executors.newSingleThreadExecutor { task -> Thread(task, "abogus-sign") }
    private val dispatcher = executor.asCoroutineDispatcher()
    private var engine: QuickJs? = null

    /** 对 `query` 和 `body` 签出 a_bogus；调用方负责把它拼回 URL。 */
    suspend fun sign(
        query: String,
        body: String,
        profile: Profile,
        userAgent: String = DouyinEnv.USER_AGENT,
        fingerprint: String = DouyinEnv.FINGERPRINT,
        now: Long = System.currentTimeMillis(),
        rand: IntArray? = null,
    ): String = withContext(dispatcher) {
        val js = ensureEngine()
        val cfg = JSONObject()
            .put("params", query)
            .put("body", body)
            .put("ua", userAgent)
            .put("fp", fingerprint)
            .put("salt", profile.salt)
            .put("pageId", profile.pageId)
            .put("now", now)
        rand?.let { cfg.put("rand", it.toList()) }
        val result = try {
            js.evaluate("ABogusSign.sign($cfg)")
        } catch (e: Exception) {
            throw DouyinException("签名失败：${e.message}", e)
        }
        (result as? String)?.takeIf { it.isNotBlank() }
            ?: throw DouyinException("签名失败：脚本没有输出")
    }

    /**
     * 自检：用固定时间/随机数跑两组基准用例，结果必须和开发机 `tools/abogus-check.mjs`
     * 一致。真机上 QuickJS 版本差异会在这里暴露出来。
     */
    suspend fun selfCheck(): Boolean = withContext(dispatcher) {
        try {
            val js = ensureEngine()
            var passed = 0
            for (vector in VECTORS) {
                val cfg = JSONObject()
                    .put("params", vector.query)
                    .put("body", vector.body)
                    .put("ua", DouyinEnv.USER_AGENT)
                    .put("fp", DouyinEnv.FINGERPRINT)
                    .put("salt", vector.profile.salt)
                    .put("pageId", vector.profile.pageId)
                    .put("now", FIXED_NOW)
                    .put("rand", FIXED_RAND.toList())
                val got = js.evaluate("ABogusSign.sign($cfg)") as? String
                if (got == vector.expected) {
                    passed++
                } else {
                    Diag.log(TAG, "selfcheck 不一致 ${vector.name}", appContext)
                }
            }
            Diag.log(TAG, "selfcheck $passed/${VECTORS.size}", appContext)
            passed == VECTORS.size
        } catch (e: Exception) {
            Diag.log(TAG, "selfcheck 异常：${e.message}", appContext)
            false
        }
    }

    /** 关闭解释器；离开主界面时调用。关掉后台线程但放掉队列里已排的活。 */
    fun close() {
        runCatching { executor.execute { runCatching { engine?.close() }; engine = null } }
        executor.shutdown()
    }

    private fun ensureEngine(): QuickJs {
        engine?.let { return it }
        val source = appContext.assets.open(ASSET).bufferedReader().use { it.readText() }
        val js = QuickJs.create()
        val ready = js.evaluate("$source\n'ready';")
        if (ready != "ready") throw DouyinException("签名脚本加载失败")
        Diag.log(TAG, "quickjs 就绪（${source.length} 字节）", appContext)
        engine = js
        return js
    }

    private class Vector(
        val name: String,
        val query: String,
        val body: String,
        val profile: Profile,
        val expected: String,
    )

    private companion object {
        const val TAG = "SignEngine"
        const val ASSET = "sign/abogus.js"
        const val FIXED_NOW = 1_762_866_245_123L
        val FIXED_RAND = intArrayOf(1299, 4, 9871)

        val VECTORS = listOf(
            Vector(
                name = "feed-get",
                query = "device_platform=webapp&aid=6383&channel=channel_pc_web&count=8&max_cursor=0",
                body = "",
                profile = Profile.CLASSIC,
                expected = "DJ8hBfg2kVxBknWu5lALfY3q6Ur3YDTH0SVkMD2fLV3G9g39HMYp9exoGhtvgmDjiT/QIeLjy4hbTNO" +
                    "prQCj01wf9uso/2AMmDSkKl5Q5xSSs1Xce6UgrUkP-hsACFrQsv1liOfkqw2aFuRmA9Fe-wHvPjojx2f39gcV",
            ),
            Vector(
                name = "like-post",
                query = "device_platform=webapp&aid=6383&channel=channel_pc_web&aweme_id=7380308675841297704&type=1",
                body = "aweme_id=7380308675841297704&type=1",
                profile = Profile.CLASSIC,
                expected = "DJ8hBfg2kVxBknWu5lALfY3q6f1tYDTH0SVkMD2fB7HG9g39HMYp9exoGhtvgmDjiT/QIeLjy4hbTNO" +
                    "prQCj01wf9uso/2AMmDSkKl5Q5xSSs1Xce6UgrUkP-hsACFrQsv1liOfkqw2aFuRmA9Fe-wHvPjojx2f39gcx",
            ),
        )
    }
}
