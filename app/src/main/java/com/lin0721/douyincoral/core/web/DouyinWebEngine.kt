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
import com.lin0721.douyincoral.core.Diag
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

            override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                injectedFor = null
                Diag.log(TAG, "start $url", appContext)
            }

            override fun onPageCommitVisible(view: WebView, url: String?) {
                inject(force = true)
            }

            override fun onPageFinished(view: WebView, url: String?) {
                Diag.log(TAG, "finish $url", appContext)
                inject(force = true)
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

    private fun inject(force: Boolean = false) {
        val wv = webView ?: return
        if (!force && injectedFor == wv.url) return
        injectedFor = wv.url
        wv.evaluateJavascript(PumpScript.JS) { result ->
            Diag.log(TAG, "inject -> $result", appContext)
        }
    }

    /**
     * 页面自带的签名函数是异步加载的，有时候要几十秒才出现，期间还得防着页面把文档整个换掉
     * （风控挑战页常常如此），所以这里一直探、并且隔一会儿补注入一次。
     */
    private fun startReadyLoop() {
        val wv = webView ?: return
        readyLoopRun?.let { main.removeCallbacks(it) }
        var tries = 0
        var announced = false
        val probe = object : Runnable {
            override fun run() {
                tries++
                if (tries % 8 == 0) inject(force = true)
                wv.evaluateJavascript(PROBE_JS) { result ->
                    val info = runCatching { JSONObject(result ?: "{}") }.getOrNull()
                    val glue = info?.optInt("r") == 1
                    val ac = info?.optBoolean("ac") == true
                    val patched = info?.optBoolean("patched") == true
                    Diag.log(
                        TAG,
                        "probe#$tries glue=$glue ac=$ac patched=$patched ready=${info?.optString("ready")} " +
                            "href=${info?.optString("href")} js=${info?.optJSONArray("js")?.length() ?: 0} " +
                            "env=${info?.optBoolean("envUrl")} ttwid=${info?.optBoolean("ttwid")} " +
                            "nonce=${info?.optBoolean("nonce")} body=${info?.optString("body")}",
                        appContext,
                    )
                    when {
                        !glue && tries < 120 -> main.postDelayed(this, if (tries < 15) 400 else 1000)
                        glue && (ac || patched) -> {
                            hasSigner = ac
                            _state.value = State.READY
                            _status.value = if (ac) "就绪" else "就绪（借页面签名）"
                            Diag.log(TAG, "ready ac=$ac patched=$patched", appContext)
                            Diag.flush(appContext)
                        }
                        glue && !announced -> {
                            announced = true
                            _state.value = State.READY
                            _status.value = "页面已就绪，正在等签名器"
                            if (tries < 120) main.postDelayed(this, 1000)
                        }
                        tries >= 120 -> {
                            _state.value = State.READY
                            _status.value = "页面打开了，但签名器一直没出现"
                            Diag.log(TAG, "give up waiting signer", appContext)
                            Diag.flush(appContext)
                        }
                        else -> {
                            // 页面自己也不动，轻推一下逼它发请求
                            if (tries % 3 == 0) wv.evaluateJavascript(NUDGE_JS, null)
                            main.postDelayed(this, if (tries < 15) 400 else 1000)
                        }
                    }
                }
            }
        }
        readyLoopRun = probe
        main.postDelayed(probe, 250)
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
        Diag.log(TAG, "rpc $method $path", appContext)
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
                Diag.log(
                    TAG,
                    "rpc-done ok=${obj.optBoolean("ok")} signed=${obj.optBoolean("signed")} " +
                        "status=${obj.optInt("status")} err=${obj.optString("err")} " +
                        "len=${obj.optString("body").length} head=${obj.optString("body").take(90)}",
                    appContext,
                )
                if (obj.optBoolean("ok")) {
                    val text = obj.optString("body")
                    val json = if (text.isBlank()) {
                        JSONObject()  // 风控会回 200 + 空 body，交给上层决定是否重试
                    } else {
                        try {
                            JSONObject(text)
                        } catch (e: Exception) {
                            p.deferred.completeExceptionally(
                                DouyinException("接口返回了网页而不是数据（多半被风控）：${text.take(60)}"),
                            )
                            return
                        }
                    }
                    p.deferred.complete(json)
                } else {
                    p.deferred.completeExceptionally(
                        DouyinException(describeFailure(obj.optString("err"))),
                    )
                }
            }
            "capture" -> {
                val u = obj.optString("url")
                val b = obj.optString("body")
                Diag.log(TAG, "capture ${u.take(140)} len=${b.length}", appContext)
                onCapture?.invoke(u, b)
            }
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
                "if(typeof window.__mcInfo==='function'){return window.__mcInfo();}" +
                "return {r:0,ac:false,patched:false};" +
                "}catch(e){return {r:-1,e:String(e)};}})()"

        const val NUDGE_JS = "(function(){try{return window.__mcNudge?window.__mcNudge():false;}catch(e){return false;}})()"
    }
}

class DouyinException(message: String) : Exception(message)
