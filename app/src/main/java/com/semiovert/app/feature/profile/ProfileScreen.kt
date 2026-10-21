package com.semiovert.app.feature.profile

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import com.semiovert.app.core.data.SampleLibrary
import com.semiovert.app.core.designsystem.*
import com.semiovert.app.core.designsystem.wave.WaveArticleDeck
import com.semiovert.app.core.designsystem.wave.WaveDeckMath
import com.semiovert.app.core.model.*
import com.semiovert.app.feature.records.RecordCalendar
import com.semiovert.app.navigation.*

@Composable
fun ProfileScreen(state: PreviewState,onTheme: ()->Unit,onNavigate: (Destination)->Unit,onRoute: (String)->Unit,onArticle: (String)->Unit,
    entryKey: Int=0,onEntryReady: ()->Unit={}) {
    // Navigation holds progress at zero until this restored/new page has entered a frame.
    LaunchedEffect(entryKey) {withFrameNanos {};onEntryReady()}
    val motion=LocalMotionEnabled.current
    var played by rememberSaveable(state.signedIn){mutableStateOf(!state.signedIn)}
    val local=remember(state.signedIn){Animatable(if(played||!motion)1f else 0f)}
    LaunchedEffect(state.signedIn,motion){if(state.signedIn&&!played){local.animateTo(1f,tween(if(motion)SceneMotion.PROFILE_ENTER_MS else 0,easing=LinearEasing));played=true}}
    val progress=if(motion)minOf(local.value,LocalProfileEntryProgress.current) else 1f
    val top=WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    BoxWithConstraints(Modifier.fillMaxSize().testTag("profile-scene")) {
        BrowseScene(Destination.PROFILE,"",onNavigate,header={
            Row(Modifier.fillMaxWidth().heightIn(min=64.dp).padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                // This button is static, underneath the departing header avatar.
                IconButton(onClick={onRoute(ScreenRoutes.SETTINGS)},modifier=Modifier.semantics {contentDescription="设置"}){SoIcon(SoSymbol.SETTINGS)}
            }
        }) {
            if(!state.signedIn)Column(Modifier.fillMaxSize().testTag("profile-guest")
                .graphicsLayer {translationX=size.width*(1f-SoSceneEase.transform(SceneMotion.profileSegment(progress,0f,1000f)))}.verticalScroll(rememberScrollState())
                .padding(start=24.dp,end=24.dp,top=top+112.dp,bottom=128.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                IdentityAvatar(size=80,registerAnchor=false)
                Text("尚未登录",style=MaterialTheme.typography.headlineSmall,modifier=Modifier.padding(top=20.dp))
                Text("先读一篇，再慢慢认识这里。",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=8.dp,bottom=24.dp))
                Button(onClick={onRoute(ScreenRoutes.LOGIN)},modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)){Text("登录")}
                FilledTonalButton(onClick={onRoute(ScreenRoutes.REGISTER)},modifier=Modifier.fillMaxWidth().padding(top=10.dp).heightIn(min=52.dp)){Text("创建账号")}
                HorizontalDivider(Modifier.padding(top=38.dp,bottom=12.dp),color=MaterialTheme.colorScheme.outlineVariant)
                TextButton(onClick=onTheme,modifier=Modifier.fillMaxWidth().heightIn(min=56.dp)) {
                    SoIcon(SoSymbol.APPEARANCE);Text("外观",Modifier.weight(1f).padding(start=12.dp));Text(state.theme.label)
                }
                Text("离线体验",style=MaterialTheme.typography.titleMedium,modifier=Modifier.fillMaxWidth().padding(top=26.dp))
                Text("文章、作者、账号表单和记录均为本地演示。不会登录真实账号或上传输入。",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.fillMaxWidth().padding(top=8.dp))
            }else ProfileFeed(state.profile,"lin",true,state.snapshots,state.snapshotsCleared,onArticle,
                {onRoute(ScreenRoutes.EDIT_PROFILE)},progress)
        }
    }
}

@Composable
private fun ProfileFeed(profile: PreviewProfile,authorId: String,owner: Boolean,snapshots: Boolean,cleared: Boolean,onArticle: (String)->Unit,
    onEdit: ()->Unit={},progress: Float=1f) {
    val articles=remember(authorId){SampleLibrary.articles.filter {it.author.id==authorId}}
    val list=rememberLazyListState()
    var selected by rememberSaveable(authorId){mutableIntStateOf(0)}
    val articleList=rememberLazyListState()
    val motion=LocalMotionEnabled.current
    val expansion=LocalArticleExpansion.current
    val exiting=LocalFlowPhase.current==FlowPhase.EXIT
    val density=LocalDensity.current
    val top=WindowInsets.statusBars.asPaddingValues().calculateTopPadding()+80.dp
    val panel=SoSceneEase.transform(SceneMotion.profileSegment(progress,0f,1000f))
    val cover=SceneMotion.profileSegment(progress,120f,900f)
    val coverInk=if(profile.cover!=null)Color.White else MaterialTheme.colorScheme.onSecondaryContainer
    val identity: @Composable ()->Unit = {
            BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal=20.dp).height(281.dp).readingChromeFade()) {
                val distance=with(density){maxWidth.toPx()+40.dp.toPx()}
                Surface(shape=RoundedCornerShape(24.dp),color=MaterialTheme.colorScheme.secondaryContainer,
                    modifier=Modifier.fillMaxSize().graphicsLayer {translationX=distance*(1f-panel)}
                        .then(if(owner)Modifier.clickable(onClickLabel="编辑资料",onClick=onEdit).testTag("profile-identity") else Modifier)) {
                    Box(Modifier.fillMaxSize()) {
                        profile.cover?.let {PixelCover(it,cover,Modifier.fillMaxSize().testTag("profile-pixel-cover"))}
                        if(profile.cover!=null)Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent,Color.Black.copy(alpha=.72f)))))
                        Column(Modifier.fillMaxSize().padding(20.dp)) {
                            Spacer(Modifier.size(76.dp))
                            TypewriterText(profile.name,SceneMotion.profileSegment(progress,220f,820f),MaterialTheme.typography.headlineSmall,Modifier.padding(top=16.dp),coverInk)
                            TypewriterText("@"+profile.handle,SceneMotion.profileSegment(progress,280f,880f),MaterialTheme.typography.labelMedium,Modifier.padding(top=4.dp),coverInk)
                            if(profile.bio.isNotBlank())TypewriterText(profile.bio,SceneMotion.profileSegment(progress,340f,940f),MaterialTheme.typography.bodyMedium,Modifier.padding(top=12.dp),coverInk)
                        }
                    }
                }
                Box(Modifier.align(Alignment.TopStart).padding(20.dp).size(76.dp)) {
                    if(owner)IdentityAvatar(Modifier.testTag("profile-shared-avatar"),size=76)
                    else ProfileAvatar(profile.avatar,76)
                }
            }
    }
    val records: @Composable ()->Unit = {
        Column(Modifier.padding(horizontal=20.dp).readingChromeFade()){RecordCalendar(authorId,owner,snapshots,cleared,textProgress=SceneMotion.profileSegment(progress,400f,1000f))}
    }
    val heading: @Composable ()->Unit = {
        Row(Modifier.fillMaxWidth().padding(horizontal=24.dp).graphicsLayer {alpha=SceneMotion.profileSegment(progress,1000f,1500f)}.readingChromeFade(),verticalAlignment=Alignment.CenterVertically) {
            Text(if(owner)"公开文章（示例）" else "公开文章 · ${articles.size}（示例）",style=MaterialTheme.typography.labelLarge,
                modifier=Modifier.testTag("profile-articles-heading"))
            if(owner) {
                val position=if(articles.isEmpty())0 else (if(motion&&density.fontScale<=1.2f)selected else articleList.firstVisibleItemIndex).coerceAtMost(articles.lastIndex)+1
                Text("$position / ${articles.size}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier=Modifier.padding(start=16.dp).testTag("profile-position"))
            }
        }
    }
    if(owner)BoxWithConstraints(Modifier.fillMaxSize()) {
        val referenceHeight=maxHeight
        // Records own their full height. The page stops at the deck's cropped viewport,
        // not at the full card height or its extra off-screen elastic reserve.
        val deckHeight=WaveDeckMath.profileViewportHeight(maxWidth.value,referenceHeight.value,articles.size).dp
        LazyColumn(state=list,userScrollEnabled=expansion?.active!=true,modifier=Modifier.fillMaxSize().testTag("my-profile-feed"),
            contentPadding=PaddingValues(top=top),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            item("identity"){identity()}
            item("records"){records()}
            item("articles-heading"){heading()}
            item("articles-deck") {
            Box(Modifier.fillMaxWidth().height(deckHeight).clipToBounds()) {
            if(articles.isNotEmpty()) {
            val hidden=1f-SceneMotion.profileSegment(progress,320f,1500f)
            CompositionLocalProvider(LocalFlowRoll provides hidden,LocalFlowPhase provides if(hidden>0f)if(exiting)FlowPhase.EXIT else FlowPhase.ENTER else FlowPhase.IDLE) {
                if(motion&&density.fontScale<=1.2f)WaveArticleDeck(articles,selected,{selected=it},onArticle,
                    Modifier.fillMaxSize(),listTag="profile-articles",deckTag="profile-wave",frontTop=76.dp,referenceHeight=referenceHeight,
                    visibleLimit=WaveDeckMath.PROFILE_VISIBLE,showCounter=false,
                    consumePageDrag={delta -> -list.dispatchRawDelta(-delta)})
                else ArticleFeed(articles,articleList,"profile-articles",onArticle,
                    modifier=Modifier.fillMaxSize(),categoryCards=true)
            }
            }else Text("暂时没有公开文章",Modifier.padding(24.dp),style=MaterialTheme.typography.bodyMedium)
            }
            }
        }
    }else LazyColumn(state=list,userScrollEnabled=expansion?.active!=true,modifier=Modifier.fillMaxSize().testTag("author-profile-feed"),
        contentPadding=PaddingValues(top=top,bottom=128.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item("identity"){identity()}
        item("records"){records()}
        item("articles-heading"){heading()}
        itemsIndexed(articles,key={_,it->it.id}) {index,article->CoverArticleCard(article,true,Modifier.padding(horizontal=20.dp).fillMaxWidth().aspectRatio(.8f).testTag("article-"+article.id),
            "wave-cover-"+article.id,topAligned=true,squareBottom=true,shortBottomBlur=true,onClick={onArticle(article.id)},exitRank=index)}
        if(articles.isEmpty())item("empty-articles"){Text("暂时没有公开文章",Modifier.padding(24.dp),style=MaterialTheme.typography.bodyMedium)}
    }
}

@Composable
fun AuthorScreen(authorId: String,onBack: ()->Unit,onArticle: (String)->Unit) {
    val author=SampleLibrary.authors.find {it.id==authorId}
    if(author==null){PageFrame("作者主页",onBack,"author-missing-screen"){Text("没有找到这位示例作者")};return}
    val cover=if(author.id=="zhou")Cover.ARCHITECTURE else null
    Box(Modifier.fillMaxSize().testTag("author-screen")) {
        ProfileFeed(PreviewProfile(author.name,"sample_"+author.id,author.bio,cover=cover),author.id,false,false,false,onArticle)
        Row(Modifier.fillMaxWidth().statusBarsPadding().heightIn(min=64.dp).padding(horizontal=12.dp).readingChromeFade(),verticalAlignment=Alignment.CenterVertically) {
            IconButton(onClick=onBack){SoIcon(SoSymbol.ARROW,"返回",Modifier.graphicsLayer {rotationZ=180f})}
            Text("作者主页",style=MaterialTheme.typography.labelLarge)
        }
    }
}
