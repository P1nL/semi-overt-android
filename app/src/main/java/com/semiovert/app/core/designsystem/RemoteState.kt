package com.semiovert.app.core.designsystem

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
internal fun StatusText(
    message: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Text(message, modifier.fillMaxWidth(), style = style, color = color, textAlign = TextAlign.Center)
}

@Composable
internal fun EmptyContent(message: String, icon: SoSymbol, modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleMedium) {
    Column(modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally) {
        SoIcon(icon, modifier = Modifier.size(36.dp).testTag("empty-content-icon"), tint = MaterialTheme.colorScheme.primary)
        StatusText(message, Modifier.padding(top = 20.dp), style = style)
    }
}

@Composable
internal fun RemoteState(
    message: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    Column(modifier.padding(horizontal = 24.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        StatusText(message)
        if (action != null) TextButton(onClick = onAction, colors = accentTextButtonColors(), modifier = Modifier.testTag("remote-retry")) { Text(action) }
    }
}

@Composable
internal fun MoreResults(loading: Boolean, error: String?, onNext: () -> Unit, modifier: Modifier = Modifier,
    loadingMessage: String?=null) {
    Box(modifier.fillMaxWidth(),contentAlignment=Alignment.Center) {
        if(error!=null)RemoteState(error,action="重试",onAction=onNext)
        else if(!loading||loadingMessage==null)TextButton(onClick=onNext,enabled=!loading,colors=accentTextButtonColors(),modifier=Modifier.testTag("load-more")) {
            Text(if(loading)"加载中" else "加载更多")
        }
        if(loadingMessage!=null)ContentLoading(loading,loadingMessage)
    }
}
