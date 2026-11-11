package com.lin0721.douyincoral.ui.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import com.lin0721.douyincoral.core.model.Aweme
import com.lin0721.douyincoral.ui.formatCount
import com.lin0721.douyincoral.ui.theme.Dy
import kotlinx.coroutines.delay

/** 单个视频页：播放器 + 右栏操作 + 左下文案 + 底部进度条。 */
@Composable
fun VideoPage(
    aweme: Aweme,
    isCurrent: Boolean,
    player: ExoPlayer,
    preparedId: String?,
    playing: Boolean,
    followed: Boolean,
    onTogglePlay: () -> Unit,
    onDoubleTap: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenComments: () -> Unit,
    onToggleLike: () -> Unit,
    onToggleCollect: () -> Unit,
    onShare: () -> Unit,
    onFollow: () -> Unit,
    onSeekTo: (Float) -> Unit,
) {
    var progress by remember(aweme.id) { mutableFloatStateOf(0f) }
    val ready = isCurrent && preparedId == aweme.id

    LaunchedEffect(ready, playing) {
        if (!ready) return@LaunchedEffect
        while (true) {
            val dur = player.duration
            if (dur > 0) progress = (player.currentPosition.toFloat() / dur).coerceIn(0f, 1f)
            delay(200)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(aweme.id) {
                detectTapGestures(onTap = { onTogglePlay() }, onDoubleTap = { onDoubleTap() })
            },
    ) {
        if (ready) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        this.player = player
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            AsyncImage(
                model = aweme.video.cover,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (isCurrent && !ready) {
            CircularProgressIndicator(
                color = Color.White.copy(alpha = 0.75f),
                strokeWidth = 2.dp,
                modifier = Modifier.align(Alignment.Center).size(28.dp),
            )
        }
        if (ready && !playing) {
            Icon(
                Icons.Rounded.PlayArrow,
                contentDescription = "播放",
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.align(Alignment.Center).size(72.dp),
            )
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.28f),
                        0.3f to Color.Transparent,
                        0.6f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.82f),
                    ),
                ),
        )

        ActionRail(
            aweme = aweme,
            followed = followed,
            playing = playing,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 10.dp, bottom = 34.dp),
            onOpenProfile = onOpenProfile,
            onFollow = onFollow,
            onToggleLike = onToggleLike,
            onOpenComments = onOpenComments,
            onToggleCollect = onToggleCollect,
            onShare = onShare,
        )

        Column(
            Modifier.align(Alignment.BottomStart).padding(start = 14.dp, end = 92.dp, bottom = 34.dp),
        ) {
            Text(
                "@${aweme.author.nickname}",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.5f), Offset(0f, 1f), 3f)),
                modifier = Modifier.clickable { onOpenProfile() },
            )
            if (aweme.desc.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    aweme.desc,
                    color = Color.White.copy(alpha = 0.95f),
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.size(13.dp),
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    listOf(aweme.musicTitle, aweme.musicAuthor).filter { it.isNotBlank() }
                        .joinToString(" · ").ifBlank { "原声" },
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // 底部进度条：细线 + 可点跳转
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(2.dp)
                .background(Color.White.copy(alpha = 0.22f))
                .pointerInput(aweme.id) {
                    detectTapGestures { offset ->
                        onSeekTo((offset.x / size.width).coerceIn(0f, 1f))
                    }
                },
        ) {
            Box(
                Modifier
                    .fillMaxWidth(if (ready) progress else 0f)
                    .height(2.dp)
                    .background(Color.White.copy(alpha = 0.75f)),
            )
        }
    }
}

@Composable
private fun ActionRail(
    aweme: Aweme,
    followed: Boolean,
    playing: Boolean,
    modifier: Modifier,
    onOpenProfile: () -> Unit,
    onFollow: () -> Unit,
    onToggleLike: () -> Unit,
    onOpenComments: () -> Unit,
    onToggleCollect: () -> Unit,
    onShare: () -> Unit,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(contentAlignment = Alignment.BottomCenter) {
            AsyncImage(
                model = aweme.author.avatar,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(Dy.SurfaceHigh)
                    .clickable { onOpenProfile() },
            )
            if (!followed) {
                Box(
                    Modifier
                        .offset(y = 9.dp)
                        .size(19.dp)
                        .clip(CircleShape)
                        .background(Dy.Accent)
                        .clickable { onFollow() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = "关注", tint = Color.White, modifier = Modifier.size(14.dp))
                }
            }
        }

        Spacer(Modifier.height(if (followed) 0.dp else 8.dp))

        RailItem(
            icon = Icons.Rounded.Favorite,
            tint = if (aweme.liked) Dy.Accent else Color.White,
            label = formatCount(aweme.diggCount + if (aweme.liked) 1 else 0),
            onClick = onToggleLike,
        )
        RailItem(
            icon = Icons.Rounded.ChatBubble,
            label = formatCount(aweme.commentCount),
            onClick = onOpenComments,
        )
        RailItem(
            icon = if (aweme.collected) Icons.Rounded.Star else Icons.Rounded.StarBorder,
            tint = if (aweme.collected) Color(0xFFFFC107) else Color.White,
            label = formatCount(aweme.collectCount + if (aweme.collected) 1 else 0),
            onClick = onToggleCollect,
        )
        RailItem(
            icon = Icons.Rounded.Send,
            label = formatCount(aweme.shareCount),
            onClick = onShare,
        )

        MusicDisc(cover = aweme.video.cover, spinning = playing)
    }
}

@Composable
private fun RailItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color = Color.White,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(31.dp).clickable { onClick() },
        )
        Spacer(Modifier.height(3.dp))
        Text(
            label,
            color = Color.White,
            fontSize = 11.sp,
            style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.4f), Offset(0f, 1f), 2f)),
        )
    }
}

/** 右下角旋转唱片。 */
@Composable
private fun MusicDisc(cover: String, spinning: Boolean) {
    val transition = rememberInfiniteTransition(label = "disc")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "angle",
    )
    Box(
        Modifier
            .size(42.dp)
            .rotate(if (spinning) angle else 0f)
            .clip(CircleShape)
            .background(Color(0xFF2A2A2A)),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = cover,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().clip(CircleShape),
        )
        Box(
            Modifier
                .size(13.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.85f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = Color.White, modifier = Modifier.size(8.dp))
        }
    }
}

