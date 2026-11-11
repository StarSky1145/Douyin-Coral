package com.lin0721.douyincoral.ui

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.lin0721.douyincoral.ui.theme.Dy
import kotlinx.coroutines.delay
import java.io.ByteArrayInputStream

/** 小屏适配：把登录弹窗放大，输入框拉成数字键盘友好的尺寸。 */
private const val SHRINK_JS = """
(function(){
  var css = '@media (max-width:520px){' +
    'div[class*="login"] input{font-size:18px !important;height:52px !important;}' +
    'button[class*="login"],div[class*="login"] button{font-size:18px !important;min-height:52px !important;}' +
    'input[type="tel"],input[inputmode="numeric"]{font-size:20px !important;}' +
    '}';
  var s = document.createElement('style'); s.textContent = css;
  document.head && document.head.appendChild(s);
  document.querySelectorAll('input').forEach(function(i){
    if (/手机|phone|tel/i.test(i.placeholder||'')) { i.setAttribute('inputmode','numeric'); i.setAttribute('type','tel'); }
  });
  return 'ok';
})()
"""

/**
 * 内嵌网页登录（小屏版）。
 *
 * 页面本身过重，这里做了三件事：拦掉图片/字体/视频请求、缓存优先、
 * 登录出来后注入 CSS 把控件放大。登录成功靠 Cookie 里出现 sessionid 判定。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LoginSheet(
    onDismiss: () -> Unit,
    onFinished: () -> Unit,
) {
    var loggedIn by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val cm = CookieManager.getInstance()
        cm.setAcceptCookie(true)
        while (!loggedIn) {
            delay(600)
            val c = cm.getCookie("https://www.douyin.com") ?: ""
            if (c.contains("sessionid=") && c.contains("sid_tt=")) loggedIn = true
        }
    }
    LaunchedEffect(loggedIn) { if (loggedIn) { delay(500); onFinished() } }

    Box(Modifier.fillMaxSize().background(Dy.Background)) {
        AndroidView(
            modifier = Modifier.fillMaxSize().padding(top = 48.dp),
            factory = { ctx ->
                WebView(ctx).apply {
                    setBackgroundColor(AndroidColor.BLACK)
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
                        loadsImagesAutomatically = false
                        blockNetworkImage = true
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        setSupportZoom(true)
                        builtInZoomControls = true
                        displayZoomControls = false
                        mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                    }
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(
                            view: WebView,
                            request: WebResourceRequest,
                        ): WebResourceResponse? {
                            val u = request.url?.toString()?.lowercase() ?: return null
                            val heavy = u.endsWith(".woff") || u.endsWith(".woff2") || u.endsWith(".ttf") ||
                                u.endsWith(".otf") || u.endsWith(".mp4") || u.endsWith(".m4s")
                            if (!heavy) return null
                            return WebResourceResponse(
                                "text/plain", "utf-8", 204, "No Content",
                                mapOf("Cache-Control" to "max-age=86400"),
                                ByteArrayInputStream(ByteArray(0)),
                            )
                        }

                        override fun onPageFinished(view: WebView, url: String?) {
                            loading = false
                            view.evaluateJavascript(SHRINK_JS, null)
                        }
                    }
                    loadUrl("https://www.douyin.com/")
                }
            },
        )

        Row(
            Modifier.fillMaxWidth().statusBarsPadding().height(48.dp).padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.Close,
                contentDescription = "关闭",
                tint = Dy.Content,
                modifier = Modifier.size(24.dp).clickable { onDismiss() },
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("登录抖音", color = Dy.Content, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(
                    if (loggedIn) "登录成功，正在同步…" else "点右上角「登录」→ 手机号验证码",
                    color = Dy.ContentSecondary, fontSize = 11.sp,
                )
            }
            if (loading && !loggedIn) {
                CircularProgressIndicator(color = Dy.ContentDim, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
            }
        }

        if (loggedIn) {
            Box(
                Modifier.align(Alignment.Center)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black.copy(alpha = 0.85f))
                    .padding(horizontal = 20.dp, vertical = 14.dp),
            ) { Text("已登录，正在载入推荐流", color = Color.White, fontSize = 13.sp) }
        }
    }
}
