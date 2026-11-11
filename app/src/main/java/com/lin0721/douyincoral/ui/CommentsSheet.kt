package com.lin0721.douyincoral.ui

import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Close
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lin0721.douyincoral.core.data.DouyinRepo
import com.lin0721.douyincoral.core.model.Aweme
import com.lin0721.douyincoral.core.model.Comment
import com.lin0721.douyincoral.ui.theme.Dy
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentsSheet(
    repo: DouyinRepo,
    aweme: Aweme,
    onDismiss: () -> Unit,
    onToast: (String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var comments by remember { mutableStateOf<List<Comment>>(emptyList()) }
    var cursor by remember { mutableStateOf(0L) }
    var hasMore by remember { mutableStateOf(true) }
    var loading by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }

    suspend fun load(reset: Boolean = false) {
        if (loading) return
        loading = true
        runCatching { repo.comments(aweme.id, if (reset) 0L else cursor) }
            .onSuccess { page ->
                comments = if (reset) page.items else comments + page.items
                cursor = page.cursor
                hasMore = page.hasMore
            }
            .onFailure { onToast(it.message ?: "评论加载失败") }
        loading = false
    }

    LaunchedEffect(aweme.id) { load(reset = true) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Dy.Surface,
        contentColor = Dy.Content,
        dragHandle = {
            Box(Modifier.fillMaxWidth().padding(10.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(width = 36.dp, height = 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Dy.SurfaceHigh),
                )
            }
        },
    ) {
        Column(Modifier.fillMaxWidth().imePadding().navigationBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${formatCount(aweme.commentCount)} 条评论",
                    color = Dy.Content,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.weight(1f))
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = "关闭",
                    tint = Dy.ContentSecondary,
                    modifier = Modifier.size(18.dp).clickable { onDismiss() },
                )
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.heightIn(max = 460.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 18.dp, end = 18.dp, top = 8.dp, bottom = 12.dp,
                ),
            ) {
                items(comments, key = { it.cid }) { comment ->
                    Row(Modifier.fillMaxWidth()) {
                        AsyncImage(
                            model = comment.avatar,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp).clip(CircleShape),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(comment.nickname, color = Dy.ContentDim, fontSize = 12.sp)
                            Spacer(Modifier.height(3.dp))
                            Text(comment.text, color = Dy.Content, fontSize = 14.sp, lineHeight = 20.sp)
                            Spacer(Modifier.height(5.dp))
                            Text(
                                listOf(relativeTime(comment.createTime), "回复").joinToString(" · "),
                                color = Dy.ContentSecondary,
                                fontSize = 11.sp,
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Rounded.Favorite,
                                contentDescription = "点赞",
                                tint = Dy.ContentSecondary,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(formatCount(comment.diggCount), color = Dy.ContentSecondary, fontSize = 11.sp)
                        }
                    }
                }
                if (hasMore) {
                    item {
                        LaunchedEffect(comments.size) { load() }
                        Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                            if (loading) {
                                CircularProgressIndicator(
                                    color = Dy.ContentSecondary,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = { Text("说点什么…", color = Dy.ContentSecondary, fontSize = 13.sp) },
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(18.dp)),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Dy.SurfaceHigh,
                        unfocusedContainerColor = Dy.SurfaceHigh,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = Dy.Accent,
                        focusedTextColor = Dy.Content,
                        unfocusedTextColor = Dy.Content,
                    ),
                    maxLines = 3,
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        val text = input.trim()
                        if (text.isEmpty() || sending) return@IconButton
                        sending = true
                        scope.launch {
                            runCatching { repo.publishComment(aweme.id, text) }
                                .onSuccess {
                                    input = ""
                                    onToast("已发送")
                                    load(reset = true)
                                }
                                .onFailure { onToast(it.message ?: "发送失败") }
                            sending = false
                        }
                    },
                ) {
                    Icon(Icons.Rounded.Send, contentDescription = "发送", tint = Dy.Accent)
                }
            }
        }
    }
}
