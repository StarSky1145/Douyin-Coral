package com.lin0721.douyincoral.core.web

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 抖音数据泵：一个不可见的 WebView 长期挂着 www.douyin.com。
 *
 * 它负责三件事：
 * 1. 过掉风控（真实 Chromium 自己会解 acrawler 挑战、自己维护 msToken/ttwid）；
 * 2. 把页面自带的签名函数借出来，替我们的请求签名；
 * 3. 借页面上下文发请求，Cookie 与同源策略都天然满足。
 */
class DouyinWebEngine(private val appContext: Context) {

    enum class State { IDLE, LOADING, READY, FAILED }

    private val main = Handler(Looper.getMainLooper())
    private val _state = MutableStateFlow(State.IDLE)
    val state: StateFlow<State> = _state
    private val _status = MutableStateFlow("准备中…")
    val status: StateFlow<String> = _status

    private var webView: WebView? = null
    private var injectedFor: String? = null
    private var readyLoopRun: Runnable? = null
    private val pending = ConcurrentHashMap<String, Pending>()

    /** 页面自己发出的接口返回（兜底数据源，也用于诊断） */
    var onCapture: ((String, String) -> Unit)? = null
    var onDebug: ((String) -> Unit)? = null

    @Volatile
    var hasSigner: Boolean = false
        private set

    private class Pending(
        val deferred: CompletableDeferred<JSONObject>,
        val timer: Runnable,
    )

    fun isReady(): Boolean = _state.value == State.READY

    @SuppressLint("SetJavaScriptEnabled")
    fun createView(context: Context): WebView {
        webView?.let { return it }

        val wv = WebView(context)
        val cm = CookieManager.getInstance()
        cm.setAcceptCookie(true)
        cm.setAcceptThirdPartyCookies(wv, true)

        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            loadsImagesAutomatically = false
            blockNetworkImage = true
            mediaPlaybackRequiresUserGesture = true
            cacheMode = WebSettings.LOAD_DEFAULT
            setSupportMultipleWindows(false)
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            userAgentString = userAgentString.replace("; wv)", ")")
        }
        wv.isHorizontalScrollBarEnabled = false
        wv.isVerticalScrollBarEnabled = false
        wv.addJavascriptInterface(Bridge(), "McBridge")
        wv.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest,
            ): WebResourceResponse? = blockHeavy(request)

            override fun onPageCommitVisible(view: WebView, url: String?) {
                inject()
            }

            override fun onPageFinished(view: WebView, url: String?) {
                inject()
                startReadyLoop()
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: android.webkit.WebResourceError,
            ) {
                if (request.isForMainFrame) {
                    _status.value = "页面加载失败：${error.description}"
                }
            }
        }

        webView = wv
        _state.value = State.LOADING
        _status.value = "正在打开抖音…"
        wv.loadUrl(HOME_URL)
        return wv
    }

    /** 字体和视频资源对拿数据毫无用处，拦掉能省一大半流量，也让页面快很多 */
    private fun blockHeavy(request: WebResourceRequest): WebResourceResponse? {
        val url = request.url?.toString() ?: return null
        val lower = url.lowercase()
        val blocked = lower.endsWith(".woff") || lower.endsWith(".woff2") ||
            lower.endsWith(".ttf") || lower.endsWith(".otf") ||
            lower.endsWith(".mp4") || lower.endsWith(".m4s") || lower.endsWith(".ts")
        if (!blocked) return null
        return WebResourceResponse(
            "text/plain",
            "utf-8",
            204,
            "No Content",
            mapOf("Cache-Control" to "max-age=86400"),
            ByteArrayInputStream(ByteArray(0)),
        )
    }

    private fun inject() {
        val wv = webView ?: return
        if (injectedFor == wv.url) return
        injectedFor = wv.url
        wv.evaluateJavascript(PumpScript.JS, null)
    }

    private fun startReadyLoop() {
        val wv = webView ?: return
        readyLoopRun?.let { main.removeCallbacks(it) }
        var tries = 0
        val probe = object : Runnable {
            override fun run() {
                tries++
                wv.evaluateJavascript(PROBE_JS) { result ->
                    val text = result?.trim()?.trim('"') ?: ""
                    when {
                        text.startsWith("1") -> {
                            hasSigner = true
                            _state.value = State.READY
                            _status.value = "就绪"
                        }
                        text.startsWith("2") -> {
                            hasSigner = false
                            _state.value = State.READY
                            _status.value = "页面已就绪，签名器还在加载"
                        }
                        tries >= 60 -> {
                            _state.value = State.READY
                            _status.value = "页面加载较慢，仍在等签名器"
                        }
                        else -> main.postDelayed(this, 1200)
                    }
                }
            }
        }
        readyLoopRun = probe
        main.postDelayed(probe, 1500)
    }

    /** 把网页当成 RPC 服务调用；返回解析后的 JSON */
    suspend fun request(
        path: String,
        params: Map<String, Any?> = emptyMap(),
        method: String = "GET",
        body: String? = null,
        timeoutMs: Long = 20_000L,
    ): JSONObject {
        val wv = webView ?: throw DouyinException("引擎尚未启动")
        val id = UUID.randomUUID().toString().replace("-", "")
        val paramsJson = JSONObject().apply { params.forEach { (k, v) -> put(k, v) } }.toString()
        val js = "window.__mcRpc(" +
            JSONObject.quote(id) + "," +
            JSONObject.quote(path) + "," +
            JSONObject.quote(paramsJson) + "," +
            JSONObject.quote(method) + "," +
            JSONObject.quote(body ?: "") + ")"

        val deferred = CompletableDeferred<JSONObject>()
        val timer = Runnable {
            pending.remove(id)?.deferred?.completeExceptionally(DouyinException("请求超时：$path"))
        }
        pending[id] = Pending(deferred, timer)
        main.post {
            wv.evaluateJavascript(js, null)
            main.postDelayed(timer, timeoutMs)
        }

        return try {
            withTimeout(timeoutMs + 2_000L) { deferred.await() }
        } catch (e: TimeoutCancellationException) {
            pending.remove(id)
            throw DouyinException("请求超时：$path")
        }
    }

    private fun handlePayload(raw: String) {
        val obj = try {
            JSONObject(raw)
        } catch (e: Exception) {
            return
        }
        when (obj.optString("type")) {
            "rpc" -> {
                val id = obj.optString("id")
                val p = pending.remove(id) ?: return
                main.removeCallbacks(p.timer)
                if (obj.optBoolean("ok")) {
                    val text = obj.optString("body")
                    val json = try {
                        JSONObject(text)
                    } catch (e: Exception) {
                        p.deferred.completeExceptionally(DouyinException("返回不是 JSON"))
                        return
                    }
                    p.deferred.complete(json)
                } else {
                    p.deferred.completeExceptionally(
                        DouyinException(describeFailure(obj.optString("err"))),
                    )
                }
            }
            "capture" -> onCapture?.invoke(obj.optString("url"), obj.optString("body"))
            "diag", "boot" -> onDebug?.invoke(raw)
            else -> Log.d(TAG, "payload $raw")
        }
    }

    private fun describeFailure(err: String): String = when (err) {
        "sign-unavailable" -> "签名组件还没加载好，稍等几秒再试"
        "bad-url" -> "请求地址拼错了"
        else -> err.ifBlank { "未知错误" }
    }

    fun reload() {
        webView?.reload()
    }

    fun flushCookies() {
        CookieManager.getInstance().flush()
    }

    fun hasCookie(name: String): Boolean =
        CookieManager.getInstance().getCookie(HOME_URL)?.contains("$name=") == true

    fun destroy() {
        readyLoopRun?.let { main.removeCallbacks(it) }
        pending.values.forEach { it.deferred.completeExceptionally(DouyinException("引擎已关闭")) }
        pending.clear()
        webView?.let {
            (it.parent as? android.view.ViewGroup)?.removeView(it)
            it.destroy()
        }
        webView = null
        _state.value = State.IDLE
    }

    private inner class Bridge {
        @JavascriptInterface
        fun post(payload: String) {
            main.post { handlePayload(payload) }
        }
    }

    private companion object {
        const val TAG = "DouyinEngine"
        const val HOME_URL = "https://www.douyin.com/"

        // 1 = 页面胶水与签名器都在；2 = 只有胶水；xx = 还没注入
        const val PROBE_JS =
            "(function(){try{" +
                "var glue=typeof window.__mcRpc==='function';" +
                "var ac=!!(window.byted_acrawler||(window.bdms&&window.bdms.acrawler));" +
                "return glue&&ac?'1':(glue?'2':'0');" +
                "}catch(e){return 'xx';}})()"
    }
}

class DouyinException(message: String) : Exception(message)
