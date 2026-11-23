package com.semiovert.app.core.designsystem

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
internal fun RemoteState(
    message: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    Column(modifier.padding(horizontal = 24.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (action != null) TextButton(onClick = onAction, modifier = Modifier.testTag("remote-retry")) { Text(action) }
    }
}

@Composable
internal fun MoreResults(loading: Boolean, error: String?, onNext: () -> Unit, modifier: Modifier = Modifier) {
    if (error != null) RemoteState(error, modifier, "重试加载", onNext)
    else TextButton(onClick = onNext, enabled = !loading, modifier = modifier.testTag("load-more")) {
        Text(if (loading) "正在加载…" else "加载更多")
    }
}
