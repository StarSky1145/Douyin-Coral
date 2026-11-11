package com.lin0721.douyincoral.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 抖音的视觉口径：纯黑底、纯白字、一个玫红强调色。
 * 视频页为了不抢画面，文字只保留白 + 半透明白两档。
 */
object Dy {
    val Background = Color(0xFF000000)
    val Surface = Color(0xFF121212)
    val SurfaceHigh = Color(0xFF1E1E1E)
    val Outline = Color(0xFF2A2A2A)

    val Content = Color(0xFFFFFFFF)
    val ContentDim = Color(0xB3FFFFFF)
    val ContentSecondary = Color(0xFF9E9E9E)

    val Accent = Color(0xFFFE2C55)
    val AccentSoft = Color(0x33FE2C55)
    val Danger = Color(0xFFFF3B30)
}

private val scheme = darkColorScheme(
    primary = Dy.Accent,
    onPrimary = Color.White,
    background = Dy.Background,
    onBackground = Dy.Content,
    surface = Dy.Surface,
    onSurface = Dy.Content,
    surfaceVariant = Dy.SurfaceHigh,
    onSurfaceVariant = Dy.ContentSecondary,
    outline = Dy.Outline,
)

private val typography = Typography(
    headlineSmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
    labelSmall = TextStyle(fontSize = 11.sp, letterSpacing = 0.4.sp),
)

@Composable
fun DyTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}
