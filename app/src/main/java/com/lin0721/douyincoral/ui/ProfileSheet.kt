package com.lin0721.douyincoral.ui

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.lin0721.douyincoral.core.data.DouyinRepo
import com.lin0721.douyincoral.core.model.Aweme
import com.lin0721.douyincoral.core.model.Profile
import com.lin0721.douyincoral.ui.theme.Dy

@Composable
fun ProfileSheet(
    repo: DouyinRepo,
    secUid: String,
    nickname: String,
    onDismiss: () -> Unit,
    onOpenVideo: (Aweme) -> Unit,
) {
    var profile by remember { mutableStateOf<Profile?>(null) }
    var posts by remember { mutableStateOf<List<Aweme>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var cursor by remember { mutableStateOf(0L) }
    var hasMore by remember { mutableStateOf(true) }

    LaunchedEffect(secUid) {
        loading = true
        runCatching { repo.userProfile(secUid) }.onSuccess { profile = it }
            .onFailure { error = it.message }
        runCatching { repo.userPosts(secUid) }
            .onSuccess {
                posts = it.items
                cursor = it.cursor
                hasMore = it.hasMore
            }
            .onFailure { if (error == null) error = it.message }
        loading = false
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Dy.Background)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Rounded.ArrowBack,
                    contentDescription = "返回",
                    tint = Dy.Content,
                    modifier = Modifier.size(22.dp).clickable { onDismiss() },
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    profile?.nickname ?: nickname,
                    color = Dy.Content,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = profile?.avatar.orEmpty(),
                        contentDescription = null,
                        modifier = Modifier.size(64.dp).clip(CircleShape),
                    )
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(
                            profile?.nickname ?: nickname,
                            color = Dy.Content,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                            StatItem("作品", profile?.awemeCount ?: 0)
                            StatItem("粉丝", profile?.followerCount ?: 0)
                            StatItem("获赞", profile?.likedCount ?: 0)
                        }
                    }
                }
                profile?.signature?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        color = Dy.ContentSecondary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))

                if (loading && posts.isEmpty()) {
                    Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Dy.Accent, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                    }
                }
                error?.let {
                    Box(Modifier.fillMaxWidth().padding(18.dp)) {
                        Text(it, color = Dy.Danger, fontSize = 12.sp)
                    }
                }
                posts.chunked(3).forEach { rowItems ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        rowItems.forEach { post ->
                            Box(
                                Modifier
                                    .weight(1f)
                                    .aspectRatio(0.75f)
                                    .clip(RoundedCornerShape(2.dp))
                                    .clickable { onOpenVideo(post) },
                            ) {
                                AsyncImage(
                                    model = post.video.cover,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
                                )
                                Text(
                                    formatCount(post.diggCount),
                                    color = Dy.Content,
                                    fontSize = 10.sp,
                                    modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
                                )
                            }
                        }
                        repeat(3 - rowItems.size) { Spacer(Modifier.weight(1f)) }
                    }
                    Spacer(Modifier.height(2.dp))
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: Long) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("${formatCount(value)}", color = Dy.Content, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(4.dp))
        Text(label, color = Dy.ContentSecondary, fontSize = 11.sp)
    }
}
