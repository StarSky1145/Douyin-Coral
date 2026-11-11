package com.lin0721.douyincoral.ui

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.webkit.CookieManager
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

/**
 * 内嵌网页登录：直接把抖音网页版塞进 WebView。
 *
 * 短信验证码、滑块、异地风控全交给真正的网页去处理——我们只等 Cookie 里出现
 * sessionid / sid_tt 就当登录成功，然后把整组 Cookie 交给数据泵复用。
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
            val cookie = cm.getCookie("https://www.douyin.com") ?: ""
            if (cookie.contains("sessionid=") && cookie.contains("sid_tt=")) loggedIn = true
        }
    }

    LaunchedEffect(loggedIn) {
        if (loggedIn) {
            delay(600)
            onFinished()
        }
    }

    Box(Modifier.fillMaxSize().background(Dy.Background)) {
        AndroidView(
            modifier = Modifier.fillMaxSize().padding(top = 52.dp),
            factory = { ctx ->
                WebView(ctx).apply {
                    setBackgroundColor(AndroidColor.BLACK)
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        mediaPlaybackRequiresUserGesture = false
                        cacheMode = WebSettings.LOAD_DEFAULT
                        mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                        userAgentString = userAgentString.replace("; wv)", ")")
                    }
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String?) {
                            loading = false
                        }
                    }
                    loadUrl("https://www.douyin.com/?recommend=1")
                }
            },
        )

        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(52.dp)
                .padding(horizontal = 8.dp),
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
                    if (loggedIn) "登录成功，正在同步…" else "用手机号 + 验证码登录",
                    color = Dy.ContentSecondary,
                    fontSize = 11.sp,
                )
            }
            if (loading && !loggedIn) {
                CircularProgressIndicator(color = Dy.ContentDim, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
            }
        }

        if (loggedIn) {
            Box(
                Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black.copy(alpha = 0.82f))
                    .padding(horizontal = 22.dp, vertical = 16.dp),
            ) {
                Text("已登录，正在载入推荐流", color = Color.White, fontSize = 13.sp)
            }
        }
    }
}
