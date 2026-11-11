package com.lin0721.douyincoral.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import com.lin0721.douyincoral.core.data.CdnProbe
import com.lin0721.douyincoral.core.data.DouyinRepo
import com.lin0721.douyincoral.core.model.Aweme
import com.lin0721.douyincoral.ui.formatCount
import com.lin0721.douyincoral.ui.theme.Dy
import kotlinx.coroutines.launch

/** 搜索页：顶部搜索框 + 双列结果，点卡片直接进播放。 */
@Composable
fun SearchScreen(
    repo: DouyinRepo,
    onClose: () -> Unit,
    onOpenComments: (Aweme) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Aweme>>(emptyList()) }
    var offset by remember { mutableStateOf(0L) }
    var hasMore by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<Aweme?>(null) }

    fun runSearch(keyword: String, append: Boolean = false) {
        val text = keyword.trim()
        if (text.isEmpty() || loading) return
        scope.launch {
            loading = true
            error = null
            runCatching { repo.search(text, if (append) offset else 0L) }
                .onSuccess { page ->
                    results = if (append) (results + page.items).distinctBy { it.id } else page.items
                    offset = if (page.cursor > 0) page.cursor else results.size.toLong()
                    hasMore = page.hasMore && page.items.isNotEmpty()
                }
                .onFailure { error = it.message ?: "搜索失败" }
            loading = false
        }
    }

    Box(Modifier.fillMaxSize().background(Dy.Background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Rounded.ArrowBack,
                    contentDescription = "返回",
                    tint = Dy.Content,
                    modifier = Modifier.size(24.dp).clickable { onClose() },
                )
                Spacer(Modifier.width(8.dp))
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.weight(1f).height(40.dp),
                    placeholder = { Text("搜索你感兴趣的内容", color = Dy.ContentSecondary, fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(Icons.Rounded.Search, contentDescription = null, tint = Dy.ContentSecondary, modifier = Modifier.size(18.dp))
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Dy.SurfaceHigh,
                        unfocusedContainerColor = Dy.SurfaceHigh,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = Dy.Content,
                        unfocusedTextColor = Dy.Content,
                        cursorColor = Dy.Accent,
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { runSearch(query) }),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "搜索",
                    color = Dy.Content,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable { runSearch(query) },
                )
            }

            when {
                loading && results.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Dy.ContentDim, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                }

                error != null -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(error!!, color = Dy.ContentSecondary, fontSize = 12.sp)
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "重试",
                            color = Dy.Content,
                            fontSize = 13.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Dy.SurfaceHigh)
                                .clickable { runSearch(query) }
                                .padding(horizontal = 18.dp, vertical = 7.dp),
                        )
                    }
                }

                results.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("搜点东西看看", color = Dy.ContentSecondary, fontSize = 12.sp)
                }

                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(results, key = { it.id }) { aweme ->
                        ResultCard(aweme) { preview = aweme }
                    }
                }
            }
        }

        preview?.let { aweme ->
            VideoPreview(
                aweme = aweme,
                onClose = { preview = null },
                onOpenComments = {
                    onOpenComments(aweme)
                },
            )
        }

        // 滑到底自动续页
        LaunchedEffect(results.size, hasMore) {
            if (results.isNotEmpty() && hasMore && !loading) {
                // 简单阈值：结果不足两屏时续一页
                if (results.size < 20) runSearch(query, append = true)
            }
        }
    }
}

@Composable
private fun ResultCard(aweme: Aweme, onClick: () -> Unit) {
    Column(Modifier.clickable { onClick() }) {
        Box {
            AsyncImage(
                model = aweme.video.cover,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(0.75f).background(Dy.SurfaceHigh),
            )
            Row(
                Modifier.align(Alignment.BottomStart).padding(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Favorite, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(3.dp))
                Text(formatCount(aweme.diggCount), color = Color.White, fontSize = 11.sp)
            }
        }
        Text(
            aweme.desc.ifBlank { "无描述" },
            color = Dy.Content,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 0.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = aweme.author.avatar,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(16.dp).clip(CircleShape),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                aweme.author.nickname,
                color = Dy.ContentSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(10.dp))
    }
}

/** 点搜索结果后的单条播放：自带播放器，退出即释放。 */
@Composable
private fun VideoPreview(
    aweme: Aweme,
    onClose: () -> Unit,
    onOpenComments: () -> Unit,
) {
    val context = LocalContext.current
    var playing by remember { mutableStateOf(true) }

    val player = remember(aweme.id) {
        val headers = buildMap {
            put("Referer", "https://www.douyin.com/")
            put("User-Agent", CdnProbe.UA)
            val cookie = CdnProbe.cookie()
            if (cookie.isNotEmpty()) put("Cookie", cookie)
        }
        val http = DefaultHttpDataSource.Factory().setDefaultRequestProperties(headers)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(http))
            .build()
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlayingNow: Boolean) {
                playing = isPlayingNow
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener); player.release() }
    }

    LaunchedEffect(aweme.id) {
        val url = CdnProbe.resolve(aweme.id, aweme.video)
        if (url.isNotEmpty()) {
            player.setMediaItem(MediaItem.fromUri(url))
            player.prepare()
            player.playWhenReady = true
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    this.player = player
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.ArrowBack,
                contentDescription = "返回",
                tint = Color.White,
                modifier = Modifier.size(26.dp).clickable { onClose() },
            )
            Spacer(Modifier.weight(1f))
            Icon(
                Icons.Rounded.ChatBubble,
                contentDescription = "评论",
                tint = Color.White,
                modifier = Modifier.size(24.dp).clickable { onOpenComments() },
            )
        }
        Column(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(16.dp),
        ) {
            Text("@${aweme.author.nickname}", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            if (aweme.desc.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(aweme.desc, color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
