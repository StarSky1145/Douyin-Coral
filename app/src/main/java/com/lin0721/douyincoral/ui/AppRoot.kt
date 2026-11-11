package com.lin0721.douyincoral.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.douyincoral.core.Diag
import com.lin0721.douyincoral.core.data.CdnProbe
import com.lin0721.douyincoral.core.data.DouyinClient
import com.lin0721.douyincoral.core.data.DouyinRepo
import com.lin0721.douyincoral.core.data.VideoDownloader
import com.lin0721.douyincoral.core.model.Aweme
import com.lin0721.douyincoral.core.model.Profile
import com.lin0721.douyincoral.core.session.CookieStore
import com.lin0721.douyincoral.core.sign.SignEngine
import com.lin0721.douyincoral.ui.home.HomeTab
import com.lin0721.douyincoral.ui.home.ShareSheet
import com.lin0721.douyincoral.ui.me.MeTab
import com.lin0721.douyincoral.ui.search.SearchScreen
import com.lin0721.douyincoral.ui.shell.AppTab
import com.lin0721.douyincoral.ui.shell.DouyinBottomBar
import com.lin0721.douyincoral.ui.shell.PlaceholderTab
import com.lin0721.douyincoral.ui.theme.Dy
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun AppRoot() {
    val context = LocalContext.current
    val cookies = remember { CookieStore(context.applicationContext) }
    val signer = remember { SignEngine(context.applicationContext) }
    val client = remember { DouyinClient(context.applicationContext, cookies, signer) }
    val repo = remember { DouyinRepo(client) }
    val downloader = remember { VideoDownloader(context.applicationContext) }
    val scope = rememberCoroutineScope()

    var tab by remember { mutableStateOf(AppTab.Home) }
    var items by remember { mutableStateOf<List<Aweme>>(emptyList()) }
    var cursor by remember { mutableLongStateOf(0L) }
    var hasMore by remember { mutableStateOf(true) }
    var loading by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var me by remember { mutableStateOf<Profile?>(null) }
    var followedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showLogin by remember { mutableStateOf(false) }
    var commentsFor by remember { mutableStateOf<Aweme?>(null) }
    var shareFor by remember { mutableStateOf<Aweme?>(null) }
    var showSearch by remember { mutableStateOf(false) }
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
            if (items.isEmpty()) loadError = "接口没有返回推荐数据，先登录再试一次"
        }.onFailure { loadError = it.message ?: "加载失败" }
        loading = false
        if (result.isFailure && items.isEmpty() && attempt < 3) {
            delay(3000)
            loadMore(attempt + 1)
        }
    }

    // 启动链路：把网页登录态搬下来 → 自检签名器 → 拉首页数据
    LaunchedEffect(Unit) {
        cookies.syncFromWebView()
        Diag.log("AppRoot", cookies.summary(), context)
        signer.selfCheck()
        refreshSelf()
        loadMore()
    }

    DisposableEffect(Unit) {
        onDispose { signer.close() }
    }

    Column(Modifier.fillMaxSize().background(Dy.Background)) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                AppTab.Home -> {
                    if (items.isEmpty()) {
                        LoadingScreen(
                            status = if (loading) "正在加载推荐…" else "正在连接抖音接口…",
                            error = loadError,
                            loggedIn = cookies.hasLogin,
                            onRetry = { scope.launch { loadMore() } },
                            onLogin = { showLogin = true },
                        )
                    } else {
                        HomeTab(
                            items = items,
                            hasMore = hasMore,
                            loading = loading,
                            me = me,
                            followedIds = followedIds,
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
                            onFollow = { aweme ->
                                val author = aweme.author
                                followedIds = followedIds + author.uid
                                scope.launch {
                                    runCatching { repo.follow(author.uid, author.secUid, true) }
                                        .onFailure {
                                            followedIds = followedIds - author.uid
                                            toast(it.message ?: "关注失败")
                                        }
                                }
                            },
                            onShare = { shareFor = it },
                            onLogin = { showLogin = true },
                            onSearch = { showSearch = true },
                        )
                    }
                }

                AppTab.Friend -> PlaceholderTab(
                    title = "朋友",
                    hint = "关注的人发的新作品会出现在这里\n（需要先在网页端登录并关注作者）",
                )

                AppTab.Message -> PlaceholderTab(
                    title = "消息",
                    hint = "评论、点赞和粉丝通知正在接入\n目前请到官方客户端查看",
                )

                AppTab.Me -> MeTab(
                    repo = repo,
                    me = me,
                    onLogin = { showLogin = true },
                    onOpenComments = { commentsFor = it },
                )
            }
        }

        DouyinBottomBar(
            current = tab,
            onSelect = { tab = it },
            onPublish = { toast("发布功能还没接") },
        )
    }

    if (showSearch) {
        SearchScreen(
            repo = repo,
            onClose = { showSearch = false },
            onOpenComments = { commentsFor = it },
        )
    }

    if (showLogin) {
        LoginSheet(
            onDismiss = { showLogin = false },
            onFinished = {
                showLogin = false
                cookies.syncFromWebView()
                // 登录只补身份，不清空列表：清了会让竖向翻页跳回第一条
                scope.launch { refreshSelf() }
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

    shareFor?.let { aweme ->
        ShareSheet(
            onDismiss = { shareFor = null },
            onCopyLink = {
                val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("douyin", aweme.shareText))
                shareFor = null
                toast("链接已复制")
            },
            onDownload = {
                shareFor = null
                toast("开始下载…")
                scope.launch {
                    runCatching {
                        val url = CdnProbe.resolve(aweme.id, aweme.video)
                        downloader.download(url, aweme.desc.ifBlank { aweme.id })
                    }.onSuccess { toast(it) }.onFailure { toast(it.message ?: "下载失败") }
                }
            },
        )
    }
}

@Composable
private fun LoadingScreen(
    status: String,
    error: String?,
    loggedIn: Boolean,
    onRetry: () -> Unit,
    onLogin: () -> Unit,
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
            .background(Dy.Background)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(color = Dy.Accent, strokeWidth = 2.dp, modifier = Modifier.size(26.dp))
        Spacer(Modifier.height(18.dp))
        Text("正在接入抖音", color = Dy.Content, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(6.dp))
        Text(status, color = Dy.ContentSecondary, fontSize = 12.sp)
        error?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = Dy.Danger, fontSize = 12.sp)
        }
        Spacer(Modifier.height(16.dp))
        if (tail.isNotEmpty()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 190.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Dy.Surface)
                    .padding(12.dp),
            ) {
                Text(tail, color = Dy.ContentSecondary, fontSize = 10.sp, lineHeight = 14.sp)
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Pill(text = "重试", modifier = Modifier.clickable { onRetry() })
            if (!loggedIn) {
                Pill(text = "登录", modifier = Modifier.clickable { onLogin() })
            }
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
