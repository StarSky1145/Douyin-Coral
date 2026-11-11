package com.lin0721.douyincoral.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.douyincoral.ui.theme.Dy

/** 抖音的计数习惯：1.2万 */
fun formatCount(value: Long): String {
    if (value < 10_000) return value.toString()
    val wan = value / 10_000.0
    return if (wan >= 100.0) {
        "${wan.toInt()}万"
    } else {
        val rounded = kotlin.math.round(wan * 10.0) / 10.0
        if (rounded % 1.0 == 0.0) "${rounded.toInt()}万" else "${rounded}万"
    }
}

@Composable
fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    background: Color = Dy.SurfaceHigh,
    color: Color = Dy.Content,
) {
    Text(
        text = text,
        color = color,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(background)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

/** 抖音式相对时间：刚刚 / x分钟前 / x小时前 / x天前 / 日期。 */
fun relativeTime(epochSeconds: Long): String {
    if (epochSeconds <= 0L) return ""
    val now = System.currentTimeMillis() / 1000
    val diff = now - epochSeconds
    return when {
        diff < 60 -> "刚刚"
        diff < 3600 -> "${diff / 60}分钟前"
        diff < 86400 -> "${diff / 3600}小时前"
        diff < 86400 * 30 -> "${diff / 86400}天前"
        else -> java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            .format(java.util.Date(epochSeconds * 1000))
    }
}
