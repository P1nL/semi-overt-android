package com.semiovert.app.feature.states

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.unit.dp
import com.semiovert.app.core.designsystem.*

@Composable
fun ContentStatesScreen(onBack: ()->Unit) {
    var selected by rememberSaveable {mutableStateOf("加载")}
    PageFrame("页面状态预览",onBack,"content-states-screen",scroll=false) {
        LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {items(listOf("加载","空内容","请求失败","内容不可见","文章不存在","已恢复")) {state ->
            val spark=rememberSparkControl()
            FilterChip(selected=selected==state,onClick={selected=state},label={Text(state)},interactionSource=spark.source,modifier=spark.modifier)
        }}
        Crossfade(selected,modifier=Modifier.weight(1f).fillMaxWidth(),animationSpec=tween(motionMillis(SceneMotion.STATE_FADE_MS),easing=SoSceneEase),label="content-state") {state ->
        Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally) {
            SoIcon(when(state){"加载"->SoSymbol.CLOCK;"请求失败","内容不可见"->SoSymbol.CLOSE;"文章不存在"->SoSymbol.SEARCH;else->SoSymbol.BOOK},
                modifier=Modifier.size(40.dp),tint=MaterialTheme.colorScheme.primary)
            Text(when(state){"加载"->"正在加载";"空内容"->"这里暂时没有文章";"请求失败"->"暂时无法获取内容";"内容不可见"->"这篇文章暂不可见";"文章不存在"->"没有找到这篇文章";else->"内容预览已恢复"},style=MaterialTheme.typography.titleMedium,modifier=Modifier.padding(top=24.dp))
        }
        }
    }
}
