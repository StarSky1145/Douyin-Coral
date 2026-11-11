package com.lin0721.douyincoral.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import coil3.compose.AsyncImage
import com.lin0721.douyincoral.core.Diag
import com.lin0721.douyincoral.core.data.CdnProbe
import com.lin0721.douyincoral.core.model.Aweme
import com.lin0721.douyincoral.core.model.Author
import com.lin0721.douyincoral.core.model.Profile
import kotlinx.coroutines.launch

/** 首页：顶部「直播 / 推荐 / 关注」+ 全屏竖向翻页。 */
@Composable
fun HomeTab(
    items: List<Aweme>,
    active: Boolean,
    hasMore: Boolean,
    loading: Boolean,
    me: Profile?,
    followedIds: Set<String>,
    onLoadMore: () -> Unit,
    onOpenProfile: (Author) -> Unit,
    onOpenComments: (Aweme) -> Unit,
    onToggleLike: (Aweme, Boolean) -> Unit,
    onToggleCollect: (Aweme, Boolean) -> Unit,
    onFollow: (Aweme) -> Unit,
    onShare: (Aweme) -> Unit,
    onLogin: () -> Unit,
    onSearch: () -> Unit,
) {
    var topTab by remember { mutableStateOf(TopTab.Recommend) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (topTab == TopTab.Recommend) {
            FeedPager(
                items = items,
                active = active,
                hasMore = hasMore,
                loading = loading,
                followedIds = followedIds,
                onLoadMore = onLoadMore,
                onOpenProfile = onOpenProfile,
                onOpenComments = onOpenComments,
                onToggleLike = onToggleLike,
                onToggleCollect = onToggleCollect,
                onFollow = onFollow,
                onShare = onShare,
            )
        } else {
            val (title, hint) = when (topTab) {
                TopTab.Follow -> "还没有关注的作者" to "在网页端登录并关注后，这里会出现关注流"
                else -> "直播" to "直播场次还没接，先把推荐流打磨好"
            }
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(8.dp))
                Text(
                    hint,
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }

        TopBar(
            selected = topTab,
            me = me,
            onSelect = { topTab = it },
            onLogin = onLogin,
            onSearch = onSearch,
        )
    }
}

private enum class TopTab { Live, Recommend, Follow }

@Composable
private fun BoxScope.TopBar(
    selected: TopTab,
    me: Profile?,
    onSelect: (TopTab) -> Unit,
    onLogin: () -> Unit,
    onSearch: () -> Unit,
) {
    Row(
        Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 14.dp)
            .height(40.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable { onSelect(TopTab.Live) },
        ) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF32D74B)))
            Spacer(Modifier.width(5.dp))
            Text("直播", color = if (selected == TopTab.Live) Color.White else Color.White.copy(alpha = 0.6f), fontSize = 15.sp)
        }

        Spacer(Modifier.weight(1f))

        TabTitle("推荐", selected == TopTab.Recommend) { onSelect(TopTab.Recommend) }
        Spacer(Modifier.width(20.dp))
        TabTitle("关注", selected == TopTab.Follow) { onSelect(TopTab.Follow) }

        Spacer(Modifier.weight(1f))

        if (me == null) {
            Text(
                "登录",
                color = Color.White,
                fontSize = 13.sp,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.18f))
                    .clickable { onLogin() }
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            )
        } else {
            AsyncImage(
                model = me.avatar,
                contentDescription = null,
                modifier = Modifier.size(26.dp).clip(CircleShape),
            )
        }
        Spacer(Modifier.width(14.dp))
        Icon(
            Icons.Rounded.Search,
            contentDescription = "搜索",
            tint = Color.White,
            modifier = Modifier.size(22.dp).clickable { onSearch() },
        )
    }
}

@Composable
private fun TabTitle(text: String, selected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onClick() }) {
        Text(
            text,
            color = if (selected) Color.White else Color.White.copy(alpha = 0.6f),
            fontSize = 17.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .width(18.dp)
                .height(2.5.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (selected) Color.White else Color.Transparent),
        )
    }
}

@Composable
private fun FeedPager(
    items: List<Aweme>,
    active: Boolean,
    hasMore: Boolean,
    loading: Boolean,
    followedIds: Set<String>,
    onLoadMore: () -> Unit,
    onOpenProfile: (Author) -> Unit,
    onOpenComments: (Aweme) -> Unit,
    onToggleLike: (Aweme, Boolean) -> Unit,
    onToggleCollect: (Aweme, Boolean) -> Unit,
    onFollow: (Aweme) -> Unit,
    onShare: (Aweme) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { items.size })
    var preparedId by remember { mutableStateOf<String?>(null) }
    var playing by remember { mutableStateOf(false) }

    val player = remember {
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
            .apply { repeatMode = Player.REPEAT_MODE_OFF }
    }
    DisposableEffect(Unit) { onDispose { player.release() } }

    // 页签不可见就停，切回来再接着放
    LaunchedEffect(active, preparedId) {
        if (!active) player.pause()
        else if (preparedId != null) player.play()
    }

    LaunchedEffect(pagerState.currentPage, items.size) {
        val page = pagerState.currentPage
        val item = items.getOrNull(page) ?: return@LaunchedEffect
        player.stop()
        player.clearMediaItems()
        preparedId = null
        playing = false
        if (item.video.playUrl.isNotEmpty() || item.video.playUrls.isNotEmpty()) {
            val playable = CdnProbe.resolve(item.id, item.video)
            if (playable.isNotEmpty() && pagerState.currentPage == page) {
                Diag.log("Play", "play ${item.id} ${playable.take(90)}")
                player.setMediaItem(MediaItem.fromUri(playable))
                player.prepare()
                player.playWhenReady = true
            }
        }
        if (items.size - page <= 2 && hasMore && !loading) onLoadMore()
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    preparedId = items.getOrNull(pagerState.currentPage)?.id
                }
                if (playbackState == Player.STATE_ENDED) {
                    scope.launch {
                        val next = pagerState.currentPage + 1
                        if (next < items.size) pagerState.animateScrollToPage(next)
                    }
                }
            }

            override fun onIsPlayingChanged(isPlayingNow: Boolean) {
                playing = isPlayingNow
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
            VideoPage(
                aweme = aweme,
                isCurrent = page == pagerState.currentPage,
                player = player,
                preparedId = preparedId,
                playing = playing,
                followed = followedIds.contains(aweme.author.uid),
                onTogglePlay = { if (player.isPlaying) player.pause() else player.play() },
                onDoubleTap = { if (!aweme.liked) onToggleLike(aweme, true) },
                onOpenProfile = { onOpenProfile(aweme.author) },
                onOpenComments = { onOpenComments(aweme) },
                onToggleLike = { onToggleLike(aweme, !aweme.liked) },
                onToggleCollect = { onToggleCollect(aweme, !aweme.collected) },
                onShare = { onShare(aweme) },
                onFollow = { onFollow(aweme) },
                onSeekTo = { fraction ->
                    val dur = player.duration
                    if (dur > 0) player.seekTo((dur * fraction).toLong())
                },
            )
        }
    }
}
