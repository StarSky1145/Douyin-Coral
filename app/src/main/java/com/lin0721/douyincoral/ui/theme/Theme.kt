package com.lin0721.douyincoral.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 和音乐播放器（Melodia coral）同一套视觉语言：暖白文字、深炭底色、一个珊瑚橙强调色。
 * 视频场景整体压暗，但留白与圆角口径保持一致。
 */
object Coral {
    val Background = Color(0xFF0D0C0B)
    val Surface = Color(0xFF171615)
    val SurfaceHigh = Color(0xFF232120)
    val Content = Color(0xFFF6F4EF)
    val ContentSecondary = Color(0xFF9A968E)
    val Accent = Color(0xFFD85A30)
    val AccentSoft = Color(0xFFFAECE7)
    val Danger = Color(0xFFE05252)
}

private val scheme = darkColorScheme(
    primary = Coral.Accent,
    onPrimary = Color.White,
    background = Coral.Background,
    onBackground = Coral.Content,
    surface = Coral.Surface,
    onSurface = Coral.Content,
    surfaceVariant = Coral.SurfaceHigh,
    onSurfaceVariant = Coral.ContentSecondary,
    outline = Coral.SurfaceHigh,
)

private val typography = Typography(
    headlineSmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
    labelSmall = TextStyle(fontSize = 11.sp, letterSpacing = 0.4.sp),
)

@Composable
fun DouyinCoralTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}

@Suppress("unused")
private val unusedDark = isSystemInDarkTheme()
