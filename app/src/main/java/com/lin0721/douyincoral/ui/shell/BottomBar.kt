package com.lin0721.douyincoral.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.douyincoral.ui.theme.Dy

enum class AppTab(val label: String) {
    Home("首页"),
    Friend("朋友"),
    Message("消息"),
    Me("我"),
}

/** 抖音底栏：首页 / 朋友 / ➕ / 消息 / 我。 */
@Composable
fun DouyinBottomBar(
    current: AppTab,
    onSelect: (AppTab) -> Unit,
    onPublish: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().background(Color.Black).navigationBarsPadding()) {
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(Color(0xFF1F1F1F)))
        Row(
            Modifier.fillMaxWidth().height(52.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BottomItem(AppTab.Home, Icons.Rounded.Home, current, onSelect, Modifier.weight(1f))
            BottomItem(AppTab.Friend, Icons.Rounded.People, current, onSelect, Modifier.weight(1f))
            PublishButton(Modifier.weight(1f), onClick = onPublish)
            BottomItem(AppTab.Message, Icons.Rounded.Email, current, onSelect, Modifier.weight(1f))
            BottomItem(AppTab.Me, Icons.Rounded.Person, current, onSelect, Modifier.weight(1f))
        }
    }
}

@Composable
private fun BottomItem(
    tab: AppTab,
    icon: ImageVector,
    current: AppTab,
    onSelect: (AppTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selected = tab == current
    Column(
        modifier = modifier.clickable { onSelect(tab) },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = tab.label,
            tint = if (selected) Dy.Content else Dy.ContentDim,
            modifier = Modifier.size(23.dp),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = tab.label,
            color = if (selected) Dy.Content else Dy.ContentDim,
            fontSize = 10.sp,
        )
    }
}

@Composable
private fun PublishButton(modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(8.dp).height(16.dp).background(Color.White, RoundedCornerShape(2.dp)))
            Spacer(Modifier.width(3.dp))
            Box(
                Modifier
                    .size(width = 40.dp, height = 26.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(Color.White)
                    .clickable { onClick() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "发布", tint = Color.Black, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(3.dp))
            Box(Modifier.width(8.dp).height(16.dp).background(Color.White, RoundedCornerShape(2.dp)))
        }
    }
}
