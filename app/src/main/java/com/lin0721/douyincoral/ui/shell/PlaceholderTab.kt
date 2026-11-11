package com.lin0721.douyincoral.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.People
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.douyincoral.ui.theme.Dy

/** 还没接数据的页签：说清楚这里将来是什么，而不是留白。 */
@Composable
fun PlaceholderTab(
    title: String,
    hint: String,
    icon: ImageVector = Icons.Rounded.People,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Dy.Background)
            .statusBarsPadding()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = Dy.ContentSecondary, modifier = Modifier.size(44.dp))
        Spacer(Modifier.height(14.dp))
        Text(title, color = Dy.Content, fontSize = 16.sp)
        Spacer(Modifier.height(8.dp))
        Text(hint, color = Dy.ContentSecondary, fontSize = 12.sp, textAlign = TextAlign.Center)
    }
}
