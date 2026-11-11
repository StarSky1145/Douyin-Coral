package com.lin0721.douyincoral.ui

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.douyincoral.core.session.CookieStore
import com.lin0721.douyincoral.ui.theme.Dy

/**
 * Cookie 导入登录。
 *
 * 小屏设备上网页登录页渲染不出来（全黑、组件点不中），所以改成：
 * 电脑浏览器登录 www.douyin.com → 复制整串 Cookie → 粘进来。
 * 效果和网页登录一样，只是把"哪来的 Cookie"交给外面。
 */
@Composable
fun LoginSheet(
    onDismiss: () -> Unit,
    onFinished: () -> Unit,
) {
    val context = LocalContext.current
    val store = remember { CookieStore(context) }
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Dy.Background)
            .statusBarsPadding()
            .padding(20.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("登录", color = Dy.Content, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text("取消", color = Dy.ContentSecondary, fontSize = 13.sp, modifier = Modifier.clickable { onDismiss() })
        }

        Spacer(Modifier.height(18.dp))
        Text(
            "在电脑浏览器打开 www.douyin.com 并登录，按 F12 → Application → Cookies，\n把整串 Cookie 复制下来粘到下面。",
            color = Dy.ContentSecondary,
            fontSize = 12.sp,
            lineHeight = 18.sp,
        )

        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { text = it; error = null },
            modifier = Modifier.fillMaxWidth().weight(1f),
            placeholder = { Text("sid_tt=…; sessionid=…; ttwid=…", color = Dy.ContentSecondary, fontSize = 12.sp) },
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = Dy.Content),
            shape = RoundedCornerShape(10.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Dy.SurfaceHigh,
                unfocusedContainerColor = Dy.SurfaceHigh,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = Dy.Accent,
            ),
        )

        error?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, color = Color(0xFFFF6B6B), fontSize = 12.sp)
        }

        Spacer(Modifier.height(16.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(46.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (text.isBlank()) Dy.SurfaceHigh else Dy.Accent)
                .clickable {
                    if (text.isBlank()) return@clickable
                    val n = store.importFrom(text)
                    if (n <= 0 || !store.hasLogin) {
                        error = "没解析到登录态，确认粘贴的是完整 Cookie（要有 sessionid 和 sid_tt）"
                    } else {
                        onFinished()
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Text("导入并登录", color = if (text.isBlank()) Dy.ContentSecondary else Color.White, fontSize = 15.sp)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "提示：Cookie 等于账号密码，别发给别人。",
            color = Dy.ContentSecondary,
            fontSize = 11.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}
