package com.lin0721.douyincoral.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import com.lin0721.douyincoral.core.Diag
import com.lin0721.douyincoral.core.data.DouyinRepo
import com.lin0721.douyincoral.core.data.VideoDownloader
import com.lin0721.douyincoral.core.model.Aweme
import com.lin0721.douyincoral.core.model.AwemeParser
import com.lin0721.douyincoral.core.model.Profile
import com.lin0721.douyincoral.core.web.DouyinWebEngine
import com.lin0721.douyincoral.ui.theme.Coral
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val UA =
    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

@Composable
fun AppRoot() {
    val context = LocalContext.current
    val engine = remember { DouyinWebEngine(context.applicationContext) }
    val repo = remember { DouyinRepo(engine) }
    val downloader = remember { VideoDownloader(context.applicationContext) }
    val scope = rememberCoroutineScope()

    val engineState by engine.state.collectAsStateWithLifecycle()
    val engineStatus by engine.status.collectAsStateWithLifecycle()

    var items by remember { mutableStateOf<List<Aweme>>(emptyList()) }
    var cursor by remember { mutableStateOf(0L) }
    var hasMore by remember { mutableStateOf(true) }
    var loading by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var me by remember { mutableStateOf<Profile?>(null) }
    var showLogin by remember { mutableStateOf(false) }
    var commentsFor by remember { mutableStateOf<Aweme?>(null) }
    var profileSecUid by remember { mutableStateOf<String?>(null) }
    var profileTitle by remember { mutableStateOf("") }

    fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    suspend fun refreshSelf() {
        me = runCatching { repo.selfProfile() }.getOrNull()
    }

    suspend fun loadMore(attempt: Int = 0) {
        if (loading) return
        loading = true
        loadError = null
        val result = runCatching { repo.feed(cursor = cursor) }
        result.onSuccess { page ->
            items = (items + page.items).distinctBy { it.id }
            cursor = if (page.cursor > 0) page.cursor else cursor + page.items.size
            hasMore = page.hasMore && page.items.isNotEmpty()
        }.onFailure { loadError = it.message ?: "加载失败" }
        loading = false
        // 签名器/网络还没热起来时，别把用户丢在错误页，自己再试几次
        if (result.isFailure && items.isEmpty() && attempt < 3) {
            delay(3000)
            loadMore(attempt + 1)
        }
    }

    LaunchedEffect(engineState) {
        if (engineState == DouyinWebEngine.State.READY && items.isEmpty() && !loading) {
            refreshSelf()
            loadMore()
        }
    }

    // 页面自己拉到的推荐流，直接截下来用：这条路完全不依赖我们的签名
    DisposableEffect(engine) {
        engine.onCapture = { url, body ->
            val interesting = url.contains("/tab/feed/") || url.contains("/aweme/post/")
            if (interesting && body.contains("aweme_list")) {
                scope.launch {
                    val parsed = runCatching {
                        AwemeParser.parseFeed(org.json.JSONObject(body))
                    }.getOrNull()
                    if (parsed != null && parsed.items.isNotEmpty()) {
                        items = (items + parsed.items).distinctBy { it.id }
                        if (parsed.cursor > 0) cursor = parsed.cursor
                        hasMore = parsed.hasMore
                    }
                }
            }
        }
        onDispose { engine.onCapture = null }
    }

    DisposableEffect(Unit) {
        onDispose { engine.destroy() }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // 数据泵：真实渲染、几乎不可见
        AndroidView(
            factory = { ctx -> engine.createView(ctx) },
            modifier = Modifier.fillMaxSize().alpha(0.01f),
        )

        if (items.isEmpty()) {
            LoadingScreen(
                engineState = engineState.name,
                engineStatus = engineStatus,
                error = loadError,
                onRetry = { scope.launch { loadMore() } },
            )
        } else {
            FeedPager(
                items = items,
                hasMore = hasMore,
                loading = loading,
                me = me,
                onLoadMore = { scope.launch { loadMore() } },
                onOpenProfile = { author ->
                    profileSecUid = author.secUid
                    profileTitle = author.nickname
                },
                onOpenComments = { commentsFor = it },
                onToggleLike = { aweme, liked ->
                    items = items.map { if (it.id == aweme.id) it.copy(liked = liked) else it }
                    scope.launch {
                        runCatching { repo.like(aweme.id, liked) }
                            .onFailure {
                                items = items.map { item ->
                                    if (item.id == aweme.id) item.copy(liked = !liked) else item
                                }
                                toast(it.message ?: "点赞失败")
                            }
                    }
                },
                onToggleCollect = { aweme, collected ->
                    items = items.map { if (it.id == aweme.id) it.copy(collected = collected) else it }
                    scope.launch {
                        runCatching { repo.collect(aweme.id, collected) }
                            .onFailure {
                                items = items.map { item ->
                                    if (item.id == aweme.id) item.copy(collected = !collected) else item
                                }
                                toast(it.message ?: "收藏失败")
                            }
                    }
                },
                onDownload = { aweme ->
                    toast("开始下载…")
                    scope.launch {
                        runCatching {
                            downloader.download(aweme.video.playUrl, aweme.desc.ifBlank { aweme.id })
                        }.onSuccess { toast(it) }.onFailure { toast(it.message ?: "下载失败") }
                    }
                },
                onLogin = { showLogin = true },
            )
        }

        if (showLogin) {
            LoginPanel(
                onDismiss = { showLogin = false },
                onFinished = {
                    showLogin = false
                    scope.launch {
                        engine.reload()
                        refreshSelf()
                        items = emptyList()
                        cursor = 0L
                        loadMore()
                    }
                },
            )
        }

        commentsFor?.let { aweme ->
            CommentsSheet(
                repo = repo,
                aweme = aweme,
                onDismiss = { commentsFor = null },
                onToast = { toast(it) },
            )
        }

        profileSecUid?.let { secUid ->
            ProfileSheet(
                repo = repo,
                secUid = secUid,
                nickname = profileTitle,
                onDismiss = { profileSecUid = null },
                onOpenVideo = { profileSecUid = null },
            )
        }
    }
}

@Composable
private fun LoadingScreen(
    engineState: String,
    engineStatus: String,
    error: String?,
    onRetry: () -> Unit,
) {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            tick++
        }
    }
    val tail = remember(tick) {
        Diag.dump().lines().filter { it.isNotBlank() }.takeLast(7).joinToString("\n")
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Coral.Background)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(color = Coral.Accent, strokeWidth = 2.dp, modifier = Modifier.size(26.dp))
        Spacer(Modifier.height(18.dp))
        Text("正在接入抖音", color = Coral.Content, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(6.dp))
        Text("$engineState · $engineStatus", color = Coral.ContentSecondary, fontSize = 12.sp)
        error?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = Coral.Danger, fontSize = 12.sp)
        }
        Spacer(Modifier.height(16.dp))
        if (tail.isNotEmpty()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 190.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Coral.Surface)
                    .padding(12.dp),
            ) {
                Text(tail, color = Coral.ContentSecondary, fontSize = 10.sp, lineHeight = 14.sp)
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Pill(text = "重试", modifier = Modifier.clickable { onRetry() })
            Pill(
                text = "复制诊断",
                modifier = Modifier.clickable {
                    val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("diag", Diag.dump()))
                    Diag.flush(context)
                    Toast.makeText(context, "诊断已复制", Toast.LENGTH_SHORT).show()
                },
            )
        }
    }
}

@Composable
private fun FeedPager(
    items: List<Aweme>,
    hasMore: Boolean,
    loading: Boolean,
    me: Profile?,
    onLoadMore: () -> Unit,
    onOpenProfile: (com.lin0721.douyincoral.core.model.Author) -> Unit,
    onOpenComments: (Aweme) -> Unit,
    onToggleLike: (Aweme, Boolean) -> Unit,
    onToggleCollect: (Aweme, Boolean) -> Unit,
    onDownload: (Aweme) -> Unit,
    onLogin: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { items.size })

    val player = remember {
        val http = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(
                mapOf("Referer" to "https://www.douyin.com/", "User-Agent" to UA),
            )
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(http))
            .build()
            .apply { repeatMode = Player.REPEAT_MODE_OFF }
    }
    DisposableEffect(Unit) { onDispose { player.release() } }

    LaunchedEffect(pagerState.currentPage, items.size) {
        val item = items.getOrNull(pagerState.currentPage) ?: return@LaunchedEffect
        if (item.video.playUrl.isNotEmpty()) {
            player.setMediaItem(MediaItem.fromUri(item.video.playUrl))
            player.prepare()
            player.playWhenReady = true
        }
        if (items.size - pagerState.currentPage <= 2 && hasMore && !loading) onLoadMore()
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    scope.launch {
                        val next = pagerState.currentPage + 1
                        if (next < items.size) pagerState.animateScrollToPage(next)
                    }
                }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    Box(Modifier.fillMaxSize()) {
        VerticalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1,
        ) { page ->
            val aweme = items.getOrNull(page) ?: return@VerticalPager
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(aweme.id) {
                        detectTapGestures(
                            onTap = {
                                if (player.isPlaying) player.pause() else player.play()
                            },
                            onDoubleTap = { onToggleLike(aweme, !aweme.liked) },
                        )
                    },
            ) {
                if (page == pagerState.currentPage) {
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
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    )
                }

                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Black.copy(alpha = 0.35f),
                                0.28f to Color.Transparent,
                                0.62f to Color.Transparent,
                                1f to Color.Black.copy(alpha = 0.72f),
                            ),
                        ),
                )

                Row(
                    Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("推荐", color = Coral.Content, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    if (me == null) {
                        Text(
                            "登录",
                            color = Coral.Content,
                            fontSize = 13.sp,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Coral.Accent)
                                .clickable { onLogin() }
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                        )
                    } else {
                        AsyncImage(
                            model = me.avatar,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp).clip(CircleShape),
                        )
                    }
                }

                Column(
                    Modifier.align(Alignment.BottomStart).padding(start = 16.dp, end = 88.dp, bottom = 26.dp),
                ) {
                    Text(
                        "@${aweme.author.nickname}",
                        color = Coral.Content,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable { onOpenProfile(aweme.author) },
                    )
                    Spacer(Modifier.height(8.dp))
                    if (aweme.desc.isNotBlank()) {
                        Text(
                            aweme.desc,
                            color = Coral.Content.copy(alpha = 0.92f),
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = Coral.Content.copy(alpha = 0.85f),
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            listOf(aweme.musicTitle, aweme.musicAuthor).filter { it.isNotBlank() }
                                .joinToString(" · ").ifBlank { "原声" },
                            color = Coral.Content.copy(alpha = 0.85f),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Column(
                    Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 26.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    RailAction(
                        icon = if (aweme.liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        tint = if (aweme.liked) Coral.Accent else Coral.Content,
                        label = formatCount(aweme.diggCount + if (aweme.liked) 1 else 0),
                        onClick = { onToggleLike(aweme, !aweme.liked) },
                    )
                    RailAction(
                        icon = Icons.Rounded.Send,
                        label = formatCount(aweme.commentCount),
                        onClick = { onOpenComments(aweme) },
                    )
                    RailAction(
                        icon = if (aweme.collected) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                        tint = if (aweme.collected) Coral.Accent else Coral.Content,
                        label = formatCount(aweme.collectCount + if (aweme.collected) 1 else 0),
                        onClick = { onToggleCollect(aweme, !aweme.collected) },
                    )
                    RailAction(
                        icon = Icons.Rounded.Download,
                        label = "保存",
                        onClick = { onDownload(aweme) },
                    )
                }

                if (loading && pagerState.currentPage >= items.size - 1) {
                    Box(Modifier.align(Alignment.Center), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Coral.Content, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun RailAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color = Coral.Content,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(30.dp).clickable { onClick() },
        )
        Spacer(Modifier.height(4.dp))
        Text(label, color = Coral.Content.copy(alpha = 0.9f), fontSize = 11.sp)
    }
}
