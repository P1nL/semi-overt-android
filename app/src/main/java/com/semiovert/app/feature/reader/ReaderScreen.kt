package com.semiovert.app.feature.reader

import androidx.compose.animation.*
import androidx.compose.animation.core.tween

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.semiovert.app.core.data.SampleLibrary
import com.semiovert.app.core.designsystem.*
import kotlinx.coroutines.launch

class ReaderViewModel(private val savedState: SavedStateHandle) : ViewModel() {
    val article=SampleLibrary.article(savedState.get<String>("articleId").orEmpty())
    val contentsOpen=savedState.getStateFlow("contentsOpen",false)
    fun contents(open: Boolean) {savedState["contentsOpen"]=open}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(onBack: ()->Unit,onAuthor: (String)->Unit,model: ReaderViewModel=viewModel()) {
    val article=model.article
    if(article==null) {
        Column(Modifier.fillMaxSize().padding(24.dp),verticalArrangement=Arrangement.Center) {
            Text("找不到这篇示例文章",style=MaterialTheme.typography.headlineSmall)
            TextButton(onClick=onBack) {Text("返回文章列表")}
        }
        return
    }
    val list=rememberLazyListState()
    val coroutine=rememberCoroutineScope()
    val contentsOpen by model.contentsOpen.collectAsStateWithLifecycle()
    val titleGone by remember {derivedStateOf {list.firstVisibleItemIndex>0}}
    val titleDuration=motionMillis(180)
    Box(Modifier.fillMaxSize().testTag("reader-screen")) {
        LazyColumn(state=list,modifier=Modifier.fillMaxSize().testTag("reader-content"),contentPadding=PaddingValues(start=24.dp,end=24.dp,top=18.dp,bottom=136.dp)) {
            item("title") {
                Text("本地示例",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary,modifier=Modifier.padding(bottom=12.dp).semantics {heading()})
                Text(article.title,style=MaterialTheme.typography.headlineLarge,modifier=Modifier.testTag("reader-title").semantics {heading()})
            }
            item("author") {
                Row(Modifier.clickable(onClickLabel="查看作者主页") {onAuthor(article.author.id)}.padding(top=18.dp,bottom=22.dp),verticalAlignment=Alignment.CenterVertically) {
                    SoIcon(SoSymbol.PERSON,tint=MaterialTheme.colorScheme.primary)
                    Column(Modifier.padding(start=10.dp)) {
                        Text(article.author.name,style=MaterialTheme.typography.labelLarge)
                        Text(article.date+" · 约 "+article.minutes+" 分钟",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
            }
            items(article.sections,key={it.id}) {section ->
                Column(Modifier.fillMaxWidth().padding(top=28.dp).testTag("section-"+section.id)) {
                    Text(section.title,style=MaterialTheme.typography.titleMedium,modifier=Modifier.padding(bottom=12.dp))
                    section.paragraphs.forEach {paragraph -> Text(paragraph,style=MaterialTheme.typography.bodyLarge,modifier=Modifier.padding(bottom=16.dp))}
                }
            }
        }
        Surface(shape=RoundedCornerShape(28.dp),color=MaterialTheme.colorScheme.tertiaryContainer,contentColor=MaterialTheme.colorScheme.onTertiaryContainer,
            modifier=Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal=20.dp,vertical=12.dp).fillMaxWidth()) {
            Row(Modifier.heightIn(min=56.dp).padding(start=18.dp,end=4.dp),verticalAlignment=Alignment.CenterVertically) {
                AnimatedContent(targetState=titleGone,modifier=Modifier.weight(1f),label="reader-bottom-title",transitionSpec={
                    (fadeIn(tween(titleDuration))+slideInVertically(tween(titleDuration,easing=SoEase)) {it/3}) togetherWith fadeOut(tween(titleDuration))
                }) {showTitle ->Text(if(showTitle)article.title else "阅读中 · 本地示例",style=MaterialTheme.typography.labelLarge,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.testTag("reader-bottom-title"))}
                IconButton(onClick={model.contents(true)},modifier=Modifier.size(48.dp)) {SoIcon(SoSymbol.CONTENTS,"打开目录",tint=MaterialTheme.colorScheme.onTertiaryContainer)}
            }
        }
    }
    if(contentsOpen) ModalBottomSheet(modifier=Modifier.preferredFrameRate(MotionFramePolicy.TARGET_FPS),onDismissRequest={model.contents(false)},sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(start=24.dp,end=24.dp,bottom=24.dp).testTag("reader-contents")) {
            Text("目录",style=MaterialTheme.typography.headlineSmall,modifier=Modifier.padding(bottom=16.dp))
            article.sections.forEachIndexed {index,section ->
                TextButton(onClick={model.contents(false);coroutine.launch {list.scrollToItem(index+2)}},modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) {
                    Text(section.title,Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium)
                    SoIcon(SoSymbol.ARROW,tint=MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
