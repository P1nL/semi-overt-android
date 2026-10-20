package com.semiovert.app.feature.search

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.semiovert.app.core.data.SampleLibrary
import com.semiovert.app.core.designsystem.*
import com.semiovert.app.core.designsystem.wave.WaveArticleDeck
import com.semiovert.app.core.designsystem.wave.WaveDeckMath
import com.semiovert.app.core.model.SearchScope
import com.semiovert.app.navigation.*
import kotlinx.coroutines.launch

class SearchViewModel(private val savedState: SavedStateHandle) : ViewModel() {
    val query=savedState.getStateFlow("query","")
    val scope=savedState.getStateFlow("scope",SearchScope.ARTICLES.name)
    fun query(value: String) { savedState["query"]=value }
    fun toggleScope() { savedState["scope"]=if(scope.value==SearchScope.ARTICLES.name) SearchScope.AUTHORS.name else SearchScope.ARTICLES.name }
}

@Composable
fun SearchScreen(onNavigate: (Destination)->Unit,onArticle: (String)->Unit,onAuthor: (String)->Unit,model: SearchViewModel=viewModel()) {
    val query by model.query.collectAsStateWithLifecycle()
    val scopeName by model.scope.collectAsStateWithLifecycle()
    val searchScope=SearchScope.valueOf(scopeName)
    val motion=LocalMotionEnabled.current
    val headerProgress=if(motion)SceneMotion.searchHeader(1f-LocalFlowRoll.current) else 1f
    val density=LocalDensity.current.density
    val articleList=rememberLazyListState()
    val authorList=rememberLazyListState()
    val coroutine=rememberCoroutineScope()
    val keyboard=LocalSoftwareKeyboardController.current
    val articles=remember(query) {SampleLibrary.searchArticles(query)}
    var selectedArticle by rememberSaveable(query) {mutableIntStateOf(0)}
    val authors=remember(query) {SampleLibrary.searchAuthors(query)}
    val updateQuery: (String)->Unit = { model.query(it);coroutine.launch {articleList.scrollToItem(0);authorList.scrollToItem(0)} }
    BrowseScene(Destination.SEARCH,"搜索",onNavigate,onPrimary=model::toggleScope,header={openNavigation ->
        Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
            val sharedIdentity=LocalIdentityHandoffActive.current
            IconButton(onClick=openNavigation,modifier=Modifier.size(48.dp).graphicsLayer {translationX=if(sharedIdentity)0f else 56f*density*(1f-headerProgress)}.semantics {contentDescription="打开页面导航"}) {
                IdentityAvatar()
            }
            OutlinedTextField(value=query,onValueChange=updateQuery,modifier=Modifier.weight(1f).padding(start=8.dp).graphicsLayer {alpha=headerProgress;translationX=32f*density*(1f-headerProgress)}.testTag("search-input"),
                singleLine=true,label={Text("搜索本地示例")},textStyle=MaterialTheme.typography.bodyMedium,
                shape=CircleShape,keyboardOptions=KeyboardOptions(imeAction=ImeAction.Search),keyboardActions=KeyboardActions(onSearch={keyboard?.hide()}),
                trailingIcon={if(query.isNotEmpty()) IconButton(onClick={updateQuery("")}) {SoIcon(SoSymbol.CLOSE,"清空搜索")}})
        }
    }) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
        val referenceHeight=maxHeight
        val useWave=motion&&LocalDensity.current.fontScale<=1.2f&&referenceHeight>=440.dp&&referenceHeight>maxWidth
        val resultMotion=Modifier.graphicsLayer {alpha=headerProgress;translationY=32f*density*(1f-headerProgress)}
        Column(Modifier.fillMaxSize().padding(top=WindowInsets.statusBars.asPaddingValues().calculateTopPadding()+76.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal=20.dp).then(resultMotion),verticalAlignment=Alignment.CenterVertically) {
                SearchScope.entries.forEach { item ->
                    TextButton(onClick={if(searchScope!=item) model.toggleScope()},modifier=Modifier.semantics {selected=searchScope==item}) {
                        Text(item.label,style=MaterialTheme.typography.labelLarge,color=if(searchScope==item) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.weight(1f))
                val position=if(articles.isEmpty())0 else (if(useWave)selectedArticle else articleList.firstVisibleItemIndex).coerceAtMost(articles.lastIndex)+1
                Text(if(searchScope==SearchScope.ARTICLES)"$position / ${articles.size}" else "${authors.size} 条",
                    style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.testTag("search-position"))
            }
            AnimatedContent(targetState=searchScope,modifier=Modifier.weight(1f),label="search-scope",transitionSpec={
                val direction=if(targetState==SearchScope.AUTHORS)1 else -1
                (slideInHorizontally(tween(if(motion)300 else 0,easing=SoEase)) {it*direction} togetherWith slideOutHorizontally(tween(if(motion)300 else 0,easing=SoEase)) {-it*direction}).using(SizeTransform(clip=false))
            }) {visibleScope ->
            if(visibleScope==SearchScope.ARTICLES) BoxWithConstraints(Modifier.fillMaxSize().clipToBounds()) {
                if(articles.isNotEmpty()&&useWave)
                    WaveArticleDeck(articles,selectedArticle,{selectedArticle=it},onArticle,listTag="feed-search",deckTag="search-wave",referenceHeight=referenceHeight,
                        visibleLimit=WaveDeckMath.SEARCH_VISIBLE,showCounter=false,
                        frontTop=WaveDeckMath.restingFrontTop(maxWidth.value,maxHeight.value,referenceHeight.value).dp)
                else ArticleFeed(articles,articleList,"feed-search",onArticle,emptyMessage="没有找到相关文章",
                    emptyHint="换个关键词，或切换到作者搜索。",modifier=Modifier.fillMaxSize().then(resultMotion),categoryCards=true)
            }
            else if(authors.isEmpty()) Column(Modifier.fillMaxSize().padding(32.dp).then(resultMotion),verticalArrangement=Arrangement.Center) {
                Text("没有找到相关作者",style=MaterialTheme.typography.titleMedium)
                Text("试试搜索“林间”或“街角”。",style=MaterialTheme.typography.bodyMedium,modifier=Modifier.padding(top=8.dp))
            }
            else LazyColumn(state=authorList,modifier=Modifier.fillMaxSize().then(resultMotion).testTag("author-results"),contentPadding=PaddingValues(20.dp,20.dp,20.dp,128.dp),verticalArrangement=Arrangement.spacedBy(28.dp)) {
                items(authors,key={it.id}) {author ->
                    Row(Modifier.fillMaxWidth().clickable(onClickLabel="查看作者主页") {onAuthor(author.id)}.testTag("author-"+author.id),verticalAlignment=Alignment.Top) {
                        Surface(shape=CircleShape,color=MaterialTheme.colorScheme.secondaryContainer,modifier=Modifier.size(48.dp)) {Box(contentAlignment=Alignment.Center) {SoIcon(SoSymbol.PERSON,tint=MaterialTheme.colorScheme.onSecondaryContainer)}}
                        Column(Modifier.weight(1f).padding(start=16.dp)) {
                            Text(author.name,style=MaterialTheme.typography.titleMedium)
                            Text(author.bio,style=MaterialTheme.typography.bodyMedium,modifier=Modifier.padding(top=6.dp),color=MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("本地示例 · "+SampleLibrary.articles.count {it.author.id==author.id}+" 篇文章",style=MaterialTheme.typography.labelMedium,modifier=Modifier.padding(top=8.dp),color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            }
        }
        }
    }
}
