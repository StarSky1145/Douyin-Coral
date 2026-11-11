package com.lin0721.douyincoral.ui.me

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lin0721.douyincoral.core.data.DouyinRepo
import com.lin0721.douyincoral.core.model.Aweme
import com.lin0721.douyincoral.core.model.Profile
import com.lin0721.douyincoral.ui.formatCount
import com.lin0721.douyincoral.ui.theme.Dy

private enum class MeTabTitle(val label: String) { Posts("作品"), Liked("喜欢"), Favorites("收藏") }

/** 我：资料头 + 作品 / 喜欢 / 收藏 三栏网格。 */
@Composable
fun MeTab(
    repo: DouyinRepo,
    me: Profile?,
    onLogin: () -> Unit,
    onOpenComments: (Aweme) -> Unit,
) {
    var tab by remember { mutableStateOf(MeTabTitle.Posts) }
    var items by remember { mutableStateOf<List<Aweme>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }

    LaunchedEffect(tab, me?.secUid) {
        val profile = me ?: run { items = emptyList(); return@LaunchedEffect }
        loading = true
        items = runCatching {
            when (tab) {
                MeTabTitle.Posts -> repo.userPosts(profile.secUid).items
                MeTabTitle.Liked -> repo.liked().items
                MeTabTitle.Favorites -> repo.favorites().items
            }
        }.getOrDefault(emptyList())
        loading = false
    }

    Column(Modifier.fillMaxSize().background(Dy.Background).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("我", color = Dy.Content, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text("设置", color = Dy.ContentDim, fontSize = 13.sp)
        }

        if (me == null) {
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(72.dp).clip(CircleShape).background(Dy.SurfaceHigh))
                Spacer(Modifier.height(14.dp))
                Text("登录后查看个人主页", color = Dy.ContentSecondary, fontSize = 13.sp)
                Spacer(Modifier.height(16.dp))
                Text(
                    "登录",
                    color = Color.White,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Dy.Accent)
                        .clickable { onLogin() }
                        .padding(horizontal = 28.dp, vertical = 9.dp),
                )
            }
            return@Column
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = me.avatar,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(72.dp).clip(CircleShape).background(Dy.SurfaceHigh),
            )
            Spacer(Modifier.width(16.dp))
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceEvenly) {
                Stat(me.followingCount, "关注")
                Stat(me.followerCount, "粉丝")
                Stat(me.likedCount, "获赞")
            }
        }
        Spacer(Modifier.height(12.dp))
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text(me.nickname.ifBlank { "抖音用户" }, color = Dy.Content, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                "抖音号：${me.uniqueId.ifBlank { me.uid }}",
                color = Dy.ContentSecondary,
                fontSize = 12.sp,
            )
        }
        Spacer(Modifier.height(14.dp))

        Row(Modifier.fillMaxWidth()) {
            MeTabTitle.entries.forEach { entry ->
                val selected = entry == tab
                Column(
                    Modifier.weight(1f).clickable { tab = entry },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        entry.label,
                        color = if (selected) Dy.Content else Dy.ContentSecondary,
                        fontSize = 14.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                    Spacer(Modifier.height(6.dp))
                    Box(
                        Modifier
                            .width(18.dp)
                            .height(2.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (selected) Dy.Accent else Color.Transparent),
                    )
                }
            }
        }
        Spacer(Modifier.height(2.dp))

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Dy.ContentDim, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
            }
        } else if (items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("还没有内容", color = Dy.ContentSecondary, fontSize = 12.sp)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(1.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                items(items, key = { it.id }) { aweme ->
                    AsyncImage(
                        model = aweme.video.cover,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .aspectRatio(0.75f)
                            .background(Dy.SurfaceHigh)
                            .clickable { onOpenComments(aweme) },
                    )
                }
            }
        }
    }
}

@Composable
private fun Stat(value: Long, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(formatCount(value), color = Dy.Content, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(2.dp))
        Text(label, color = Dy.ContentSecondary, fontSize = 11.sp)
    }
}
