package com.lin0721.douyincoral.ui

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lin0721.douyincoral.core.Diag
import com.lin0721.douyincoral.ui.theme.Coral
import kotlinx.coroutines.delay

private const val LOGIN_UA =
    "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/120.0.0.0 Safari/537.36"

/** 自动把网页上的「登录」按下去，省得用户在密密麻麻的页面里找 */
private const val CLICK_LOGIN_JS = """
(function () {
  function findAndClick() {
    var nodes = document.querySelectorAll('button, div, span, a, p');
    for (var i = 0; i < nodes.length; i++) {
      var t = (nodes[i].innerText || '').trim();
      if (t === '登录' || t === '立即登录' || t === '登录/注册') {
        var r = nodes[i].getBoundingClientRect();
        if (r.width > 2 && r.height > 2) {
          nodes[i].click();
          return true;
        }
      }
    }
    return false;
  }
  var tries = 0;
  function loop() {
    tries++;
    if (findAndClick()) { return 'clicked'; }
    if (tries > 25) { return 'not-found'; }
    setTimeout(loop, 800);
  }
  setTimeout(loop, 1000);
  return 'armed';
})();
"""

/**
 * 登录面板：给一个能点的抖音网页（和后台数据泵共用同一份 Cookie 仓库），
 * 手机号验证码登录即可，登完 Cookie 立刻对引擎生效。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LoginPanel(onDismiss: () -> Unit, onFinished: () -> Unit) {
    var loggedIn by remember { mutableStateOf(false) }
    var cookieCount by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            val cookies = CookieManager.getInstance().getCookie("https://www.douyin.com").orEmpty()
            val ok = cookies.contains("sessionid=") || cookies.contains("sid_tt=") ||
                cookies.contains("passport_csrf_token")
            cookieCount = cookies.split(';').size
            if (ok && cookies.contains("sessionid")) loggedIn = true
            if (loggedIn) {
                CookieManager.getInstance().flush()
                Diag.log("Login", "session id 已到手，cookie 段数=$cookieCount")
                delay(600)
                onFinished()
                return@LaunchedEffect
            }
            delay(1200)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(Coral.Background),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("登录抖音", color = Coral.Content, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text(
                    if (loggedIn) "已登录" else "登录后点我",
                    color = if (loggedIn) Coral.Accent else Coral.ContentSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Coral.SurfaceHigh)
                        .clickable {
                            CookieManager.getInstance().flush()
                            Diag.log("Login", "手动完成，cookie 段数=$cookieCount")
                            onFinished()
                        }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                )
                Spacer(Modifier.weight(0.06f))
                Text(
                    "关闭",
                    color = Coral.ContentSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .clickable {
                            CookieManager.getInstance().flush()
                            onDismiss()
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.databaseEnabled = true
                            settings.loadsImagesAutomatically = true
                            settings.useWideViewPort = true
                            settings.loadWithOverviewMode = true
                            settings.setSupportZoom(false)
                            settings.mediaPlaybackRequiresUserGesture = false
                            settings.userAgentString = LOGIN_UA
                            CookieManager.getInstance().setAcceptCookie(true)
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView, url: String?) {
                                    Diag.log("Login", "page finished ${url?.take(80)}")
                                    view.evaluateJavascript(CLICK_LOGIN_JS) { r ->
                                        Diag.log("Login", "auto-click $r")
                                    }
                                }
                            }
                            loadUrl("https://www.douyin.com/home")
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }

            Text(
                "在网页里点「登录」→ 选验证码登录；成功后这里会自动关掉",
                color = Coral.ContentSecondary,
                fontSize = 11.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
}
