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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lin0721.douyincoral.core.Diag
import com.lin0721.douyincoral.ui.theme.Coral
import kotlinx.coroutines.delay

private const val DESKTOP_UA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/124.0.0.0 Safari/537.36"

/**
 * 抖音登录的自动化脚本。
 *
 * 界面是我们自己的原生表单；真正跟服务器打交道的是网页里那套官方 JS ——
 * 我们在看不见的 WebView 里把「登录 → 验证码登录 → 填手机号 → 提交」按顺序按一遍，
 * 这样既不用逆向 passport 接口，也不会把那个丑陋的网页糊到用户脸上。
 */
private const val AUTOMATION_JS = """
(function () {
  if (window.__mcL) { return 'ready'; }
  var L = {
    pick: function (re) {
      var els = [].slice.call(document.querySelectorAll('button, div, span, a, p'));
      for (var i = 0; i < els.length; i++) {
        var t = (els[i].innerText || '').trim();
        if (re.test(t)) {
          var r = els[i].getBoundingClientRect();
          if (r.width > 2 && r.height > 2) { els[i].click(); return t; }
        }
      }
      return '';
    },
    input: function (re) {
      var ins = [].slice.call(document.querySelectorAll('input'));
      for (var i = 0; i < ins.length; i++) {
        var el = ins[i];
        var s = (el.type || '') + ' ' + (el.placeholder || '') + ' ' + (el.name || '') + ' ' + (el.getAttribute('aria-label') || '');
        if (re.test(s)) { return el; }
      }
      return null;
    },
    fill: function (el, v) {
      if (!el) { return false; }
      try {
        var d = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value');
        d.set.call(el, v);
        el.dispatchEvent(new Event('input', { bubbles: true }));
        el.dispatchEvent(new Event('change', { bubbles: true }));
        return true;
      } catch (e) { return false; }
    },
    open: function () { return this.pick(/^(登录|立即登录|登录\/注册)$/); },
    smsMode: function () { return this.pick(/^(验证码登录|密码登录|验证码)$/); },
    phoneFill: function (v) { return this.fill(this.input(/tel|手机|phone|mobile/i), v); },
    codeFill: function (v) { return this.fill(this.input(/验证码|code|captcha/i), v); },
    send: function () { return this.pick(/获取验证码|发送验证码|重新获取/); },
    submit: function () { return this.pick(/^(登录|立即登录)$/); },
    state: function () {
      var t = document.body ? (document.body.innerText || '') : '';
      return JSON.stringify({
        inputs: document.querySelectorAll('input').length,
        text: t.replace(/\s+/g, ' ').slice(0, 100)
      });
    }
  };
  window.__mcL = L;
  return 'ready';
})();
"""

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LoginSheet(onDismiss: () -> Unit, onFinished: () -> Unit) {
    var phone by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("正在准备…") }
    var busy by remember { mutableStateOf(false) }
    val holder = remember { WebHolder() }

    fun run(js: String, label: String) {
        holder.view?.post {
            holder.view?.evaluateJavascript(js) { result ->
                Diag.log("Login", "$label -> $result")
            }
        }
    }

    // 登录成功由 Cookie 判定，不用页面自己说
    LaunchedEffect(Unit) {
        while (true) {
            val cookies = CookieManager.getInstance().getCookie("https://www.douyin.com").orEmpty()
            if (cookies.contains("sessionid")) {
                CookieManager.getInstance().flush()
                Diag.log("Login", "拿到 sessionid，登录完成")
                onFinished()
                return@LaunchedEffect
            }
            delay(800)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(Modifier.fillMaxSize()) {
            // 不可见的执行器：真干活的是它
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.databaseEnabled = true
                        settings.userAgentString = DESKTOP_UA
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView, url: String?) {
                                Diag.log("Login", "hidden page ${url?.take(70)}")
                                view.postDelayed({
                                    view.evaluateJavascript(AUTOMATION_JS) { r ->
                                        Diag.log("Login", "inject -> $r")
                                        view.evaluateJavascript("window.__mcL.open()") { o ->
                                            Diag.log("Login", "open -> $o")
                                        }
                                    }
                                }, 800)
                                view.postDelayed({
                                    view.evaluateJavascript("window.__mcL && window.__mcL.smsMode()") { m ->
                                        Diag.log("Login", "smsMode -> $m")
                                        status = "输入手机号，点「获取验证码」"
                                    }
                                }, 2600)
                            }
                        }
                        loadUrl("https://www.douyin.com/home")
                        holder.view = this
                    }
                },
                modifier = Modifier.size(2.dp).alpha(0f),
            )

            Column(
                Modifier
                    .fillMaxSize()
                    .background(Coral.Background)
                    .padding(horizontal = 26.dp),
            ) {
                Spacer(Modifier.height(48.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("登录抖音", color = Coral.Content, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    Text(
                        "关闭",
                        color = Coral.ContentSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .clickable { onDismiss() }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "手机号验证码登录，登录态存在本机",
                    color = Coral.ContentSecondary,
                    fontSize = 12.sp,
                )

                Spacer(Modifier.height(34.dp))
                Field(value = phone, placeholder = "手机号", keyboard = KeyboardType.Phone) { v ->
                    phone = v.filter { it.isDigit() }.take(11)
                }
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        Field(value = code, placeholder = "短信验证码", keyboard = KeyboardType.Number) { v ->
                            code = v.filter { it.isDigit() }.take(6)
                        }
                    }
                    Spacer(Modifier.size(10.dp))
                    Text(
                        if (busy) "发送中…" else "获取验证码",
                        color = Coral.Accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(Coral.AccentSoft.copy(alpha = 0.16f))
                            .clickable(enabled = !busy) {
                                if (phone.length < 11) {
                                    status = "手机号要 11 位"
                                    return@clickable
                                }
                                busy = true
                                run("window.__mcL && window.__mcL.phoneFill('$phone')", "phoneFill")
                                holder.view?.postDelayed({
                                    holder.view?.evaluateJavascript("window.__mcL && window.__mcL.send()") { r ->
                                        Diag.log("Login", "send -> $r")
                                        status = "已点「获取验证码」，等短信"
                                        busy = false
                                    }
                                }, 500)
                            }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }

                Spacer(Modifier.weight(1f))

                Text(status, color = Coral.ContentSecondary, fontSize = 11.sp)
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (code.length >= 4) Coral.Accent else Coral.SurfaceHigh)
                        .clickable(enabled = code.length >= 4) {
                            busy = true
                            status = "正在登录…"
                            run("window.__mcL && window.__mcL.phoneFill('$phone')", "phoneFill")
                            holder.view?.postDelayed({
                                holder.view?.evaluateJavascript("window.__mcL && window.__mcL.codeFill('$code')") { c ->
                                    Diag.log("Login", "codeFill -> $c")
                                    holder.view?.evaluateJavascript("window.__mcL && window.__mcL.submit()") { s ->
                                        Diag.log("Login", "submit -> $s")
                                        status = "已提交，等着拿登录态（成了会自动关）"
                                        busy = false
                                    }
                                }
                            }, 500)
                        }
                        .padding(vertical = 15.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "登录",
                        color = if (code.length >= 4) Coral.Content else Coral.ContentSecondary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "登录信息只保存在本机，用来读取你自己的点赞、收藏和评论",
                    color = Coral.ContentSecondary.copy(alpha = 0.75f),
                    fontSize = 11.sp,
                )
                Spacer(Modifier.height(28.dp))
            }
        }
    }
}

private class WebHolder {
    var view: WebView? = null
}

@Composable
private fun Field(
    value: String,
    placeholder: String,
    keyboard: KeyboardType,
    onValueChange: (String) -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Coral.Surface)
            .padding(horizontal = 16.dp, vertical = 15.dp),
    ) {
        if (value.isEmpty()) {
            Text(placeholder, color = Coral.ContentSecondary.copy(alpha = 0.7f), fontSize = 14.sp)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboard),
            textStyle = TextStyle(color = Coral.Content, fontSize = 14.sp),
            cursorBrush = SolidColor(Coral.Accent),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
