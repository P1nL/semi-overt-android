package com.semiovert.app.feature.profile

import androidx.compose.animation.*
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
import com.semiovert.app.core.data.*
import com.semiovert.app.core.designsystem.*
import com.semiovert.app.core.designsystem.wave.WaveArticleDeck
import com.semiovert.app.core.designsystem.wave.WaveDeckMath
import com.semiovert.app.core.model.*
import com.semiovert.app.feature.records.RecordCalendar
import com.semiovert.app.feature.records.RecordSectionNumber
import com.semiovert.app.feature.records.RecordSectionTitle
import com.semiovert.app.feature.account.AccountForm
import com.semiovert.app.navigation.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private data class ProfileRequest(val info: PublicProfile?, val pages: PagedRequest<Article>, val next: () -> Unit)

@Composable
private fun rememberProfileRequest(business: LiveBusiness, id: String, owner: Boolean): ProfileRequest {
    val revision = if(owner) business.writes.collectAsStateWithLifecycle().value else 0
    var info by remember(business, id, owner, revision) { mutableStateOf<PublicProfile?>(null) }
    val (pages, next) = rememberPaged(business, id, owner, revision, id = Article::id) { page ->
        val result = business.profile(id, page, owner)
        if(page==1)info=result
        result.articles
    }
    return ProfileRequest(info, pages, next)
}

private val profileHeaderHeight=64.dp
private val profileHeaderGutter=12.dp
private val profileSettingsSize=48.dp

@Composable
fun ProfileScreen(state: PreviewState,onNavigate: (Destination)->Unit,onRoute: (String)->Unit,onArticle: (String)->Unit,onEnterDemo: (String?)->Unit,
    entryKey: Int=0,onEntryReady: ()->Unit={}) {
    // Navigation holds progress at zero until this restored/new page has entered a frame.
    LaunchedEffect(entryKey) {withFrameNanos {};onEntryReady()}
    val motion=LocalMotionEnabled.current
    val business=LocalBusiness.current
    val account=business?.session?.collectAsStateWithLifecycle()?.value?.account
    val request=if(business!=null&&account!=null)rememberProfileRequest(business,account.id,true) else null
    // Inline authentication reveals the already-settled feed, not a second page entrance.
    var played by rememberSaveable {mutableStateOf(!state.signedIn)}
    val local=remember(state.signedIn){Animatable(if(played||!motion)1f else 0f)}
    LaunchedEffect(state.signedIn,motion){if(state.signedIn&&!played){local.animateTo(1f,tween(if(motion)SceneMotion.PROFILE_ENTER_MS else 0,easing=LinearEasing));played=true}}
    val progress=if(motion)minOf(local.value,LocalProfileEntryProgress.current) else 1f
    val top=WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val phase=LocalFlowPhase.current
    val settingsAlpha=when {
        !motion||phase!=FlowPhase.ENTER -> 1f
        state.signedIn -> SceneMotion.profileSettingsAlpha(progress)
        else -> SceneMotion.profileGuestSettingsAlpha(LocalIdentityArrival.current)
    }
    val list=rememberLazyListState()
    val cover=if(state.signedIn)rememberProfileCoverPhoto(state.profile.coverPhoto,state.profile.cover) else null
    val coverHeight=profileCoverHeight()
    val ltr=LocalLayoutDirection.current==LayoutDirection.Ltr
    val density=LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize().testTag("profile-scene")) {
        val coverSize=IntSize(constraints.maxWidth,with(density){coverHeight.roundToPx()})
        val settingsCenter=with(density) {
            val inset=(profileHeaderGutter+profileSettingsSize/2).toPx()
            Offset(if(ltr)coverSize.width-inset else inset,(top+profileHeaderHeight/2).toPx())
        }
        BrowseScene(Destination.PROFILE,"",onNavigate,header={
            Row(Modifier.fillMaxWidth().height(profileHeaderHeight).padding(horizontal=profileHeaderGutter),verticalAlignment=Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                ProfileSettingsButton(settingsAlpha,{onRoute(ScreenRoutes.SETTINGS)},cover,state.profile.coverPosition,
                    coverSize,settingsCenter)
            }
        }) {
            AnimatedContent(state.signedIn,modifier=Modifier.fillMaxSize(),label="profile-account",transitionSpec={
                ContentTransform(if(targetState)expandVertically(
                    tween(if(motion)ACCOUNT_SUCCESS_EXIT_MS else 0,easing=SoSceneEase),expandFrom=Alignment.Top) else EnterTransition.None,
                    if(targetState)slideOutVertically(tween(if(motion)ACCOUNT_SUCCESS_EXIT_MS else 0,easing=SoSceneEase)){it} else ExitTransition.None,
                    targetContentZIndex=if(targetState)-1f else 0f,sizeTransform=null)
            }) {signedIn ->
            if(!signedIn)BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).testTag("profile-guest")) {
            val exiting=LocalFlowPhase.current==FlowPhase.EXIT
            val slideDistance=(if(exiting)constraints.maxWidth else constraints.maxHeight).toFloat()
            val slide=Modifier.graphicsLayer {
                val offset=slideDistance*(1f-SoSceneEase.transform(progress))
                translationX=if(exiting)offset else 0f
                translationY=if(exiting)0f else offset
            }
            Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState())
                .padding(start=24.dp,end=24.dp,top=top+96.dp,bottom=128.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                // A fixed endpoint lets the shared avatar travel independently of the form.
                // After login, the incoming profile owns the identity while this sheet exits.
                val avatarModifier=Modifier.testTag("login-shared-avatar")
                if(state.signedIn)GuestAvatar(80,avatarModifier)
                else IdentityAvatar(avatarModifier,size=80)
                Spacer(Modifier.height(20.dp))
                Box(Modifier.fillMaxWidth().then(slide)) {
                AccountForm(onEnterDemo)
                }
            }
            }else ProfileFeed(state.profile,account?.id ?: if(business==null)"lin" else "",true,state.snapshots,state.snapshotsCleared,onArticle,
                {onRoute(ScreenRoutes.EDIT_PROFILE)},progress,list=list,coverImage=cover?.bitmap,remote=request)
            }
        }
    }
}

@Composable
private fun ProfileSettingsButton(fade: Float,onClick: ()->Unit,cover: ProfilePhoto?,position: Float,coverSize: IntSize,center: Offset) {
    val colors=MaterialTheme.colorScheme
    val iconSize=24.dp
    val step=with(LocalDensity.current){iconSize.toPx()/3f}
    val alpha=if(cover?.loaded==false)0f else fade
    val color=when {
        alpha<=0f -> Color.Unspecified
        cover==null -> colors.onBackground
        else -> remember(cover.bitmap,coverSize,position,center,step,colors.secondaryContainer) {
            val background=colors.secondaryContainer
            val image=cover.bitmap
            val luminance=if(image==null||coverSize.width==0||coverSize.height==0)background.luminance() else {
                val crop=image.profileCoverCrop(coverSize,position)
                val bitmap=image.asAndroidBitmap()
                var total=0f
                for(row in -1..1)for(column in -1..1) {
                    val sampleX=(crop.left+(center.x+column*step)/coverSize.width*crop.width).toInt()
                        .coerceIn(crop.left,crop.right-1)
                    val sampleY=(crop.top+(center.y+row*step)/coverSize.height*crop.height).toInt()
                        .coerceIn(crop.top,crop.bottom-1)
                    total+=Color(bitmap.getPixel(sampleX,sampleY)).compositeOver(background).luminance()
                }
                total/9f
            }
            if(luminance>.18f)Color.Black else Color.White
        }
    }
    IconButton(onClick=onClick,enabled=alpha>=1f,modifier=Modifier.size(profileSettingsSize)
        .testTag("profile-settings").semantics {contentDescription="设置"}) {
        Canvas(Modifier.size(iconSize)) {
            if(alpha>0f)drawSoIcon(SoSymbol.SETTINGS,color.copy(alpha=alpha))
        }
    }
}

@Composable
private fun ProfileFeed(profile: PreviewProfile,authorId: String,owner: Boolean,snapshots: Boolean,cleared: Boolean,onArticle: (String)->Unit,
    onEdit: ()->Unit={},progress: Float=1f,authorArrival: Int=0,list: LazyListState=rememberLazyListState(),
    coverImage: ImageBitmap?=rememberProfileCover(profile.coverPhoto,profile.cover),remote: ProfileRequest?=null) {
    val articles=remote?.pages?.items ?: if(LocalBusiness.current==null)remember(authorId){SampleLibrary.articles.filter {it.author.id==authorId}}
        else emptyList()
    var handledArrival by rememberSaveable(authorId) {mutableIntStateOf(0)}
    LaunchedEffect(authorArrival) {
        if(!owner&&authorArrival!=handledArrival) {
            list.scrollToItem(0);handledArrival=authorArrival
        }
    }
    var selected by rememberSaveable(authorId){mutableIntStateOf(0)}
    val articleList=rememberLazyListState()
    val motion=LocalMotionEnabled.current
    val expansion=LocalArticleExpansion.current
    val exiting=LocalFlowPhase.current==FlowPhase.EXIT
    val density=LocalDensity.current
    val coverHeight=profileCoverHeight()
    val avatarSize=76.dp
    val panel=if(exiting)1f-SoSceneEase.transform(1f-progress)
        else SoSceneEase.transform(progress)
    val coverPanel=if(owner)SceneMotion.profileCoverProgress(progress,exiting) else panel
    fun segment(start: Float,end: Float)=SceneMotion.profileSegment(progress,start,end,exiting)
    val identity: @Composable ()->Unit = {
            BoxWithConstraints(Modifier.fillMaxWidth().readingChromeFade()
                .then(if(owner)Modifier.clickable(onClickLabel="编辑资料",onClick=onEdit).testTag("profile-identity") else Modifier)) {
                val distance=with(density){maxWidth.toPx()}
                Surface(color=MaterialTheme.colorScheme.secondaryContainer,
                    modifier=Modifier.fillMaxWidth().height(coverHeight).graphicsLayer {translationY=-size.height*(1f-coverPanel)}
                        .testTag("profile-cover")) {
                    if(coverImage!=null)ProfileCoverImage(coverImage,Modifier.fillMaxSize().testTag("profile-cover-image"),profile.coverPosition)
                }
                // The avatar remains a stable shared-element endpoint while the cover moves.
                // Its center and the cover's lower edge share the same layout coordinate.
                Column(Modifier.fillMaxWidth().padding(start=40.dp,end=40.dp,top=coverHeight-avatarSize/2)) {
                    Box(Modifier.size(avatarSize)) {
                        if(owner)IdentityAvatar(Modifier.testTag("profile-shared-avatar"),size=76)
                        else AuthorAvatar(authorId,size=76)
                    }
                    Column(Modifier.fillMaxWidth().padding(top=16.dp).graphicsLayer {translationX=distance*(1f-panel)}
                        .testTag("profile-info")) {
                        TypewriterText(profile.name,segment(100f,450f),MaterialTheme.typography.headlineSmall,
                            color=MaterialTheme.colorScheme.onSurface)
                        TypewriterText("@"+profile.handle,segment(120f,480f),MaterialTheme.typography.labelMedium,
                            Modifier.padding(top=4.dp),MaterialTheme.colorScheme.onSurfaceVariant)
                        if(profile.bio.isNotBlank())TypewriterText(profile.bio,segment(140f,540f),
                            MaterialTheme.typography.bodyMedium,Modifier.padding(top=12.dp),MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
    }
    val records: @Composable ()->Unit = {
        Column(Modifier.padding(horizontal=20.dp).readingChromeFade()){
            val error=remote?.pages?.error
            if(remote!=null&&remote.info==null)RemoteState(error ?: "正在加载创作记录…",
                action=error?.let {"重新加载"},onAction=remote.next)
            else RecordCalendar(authorId,owner,snapshots,cleared,textProgress=segment(150f,500f),topPadding=0.dp,
                calendar=remote?.info?.calendar)
        }
    }
    val heading: @Composable ()->Unit = {
        val trailing=ButtonDefaults.TextButtonContentPadding.calculateEndPadding(LocalLayoutDirection.current)
        Row(Modifier.fillMaxWidth().heightIn(min=48.dp).padding(horizontal=20.dp).graphicsLayer {alpha=segment(150f,400f)}
            .readingChromeFade(),verticalAlignment=Alignment.CenterVertically) {
            RecordSectionTitle("公开文章",Modifier.testTag("profile-articles-heading"))
            Spacer(Modifier.weight(1f))
            val position=if(articles.isEmpty())0 else (if(motion&&density.fontScale<=1.2f)selected else articleList.firstVisibleItemIndex).coerceAtMost(articles.lastIndex)+1
            RecordSectionNumber("$position / ${remote?.pages?.total ?: articles.size}",Modifier.padding(end=trailing).testTag("profile-position"))
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val referenceHeight=maxHeight
        // Records own their full height. The page stops at the deck's cropped viewport,
        // not at the full card height or its extra off-screen elastic reserve.
        val deckHeight=WaveDeckMath.profileViewportHeight(maxWidth.value,referenceHeight.value,articles.size,density.fontScale).dp
        LazyColumn(state=list,userScrollEnabled=expansion?.active!=true,modifier=Modifier.fillMaxSize().testTag(if(owner)"my-profile-feed" else "author-profile-feed"),
            verticalArrangement=Arrangement.spacedBy(16.dp)) {
            item("identity"){identity()}
            item("records"){records()}
            item("articles-heading"){heading()}
            if(remote==null||remote.info!=null)item("articles-deck") {
            Box(Modifier.fillMaxWidth().height(deckHeight).clipToBounds()) {
            if(articles.isNotEmpty()) {
            val hidden=1f-progress
            CompositionLocalProvider(LocalFlowRoll provides hidden,LocalFlowPhase provides if(hidden>0f)if(exiting)FlowPhase.EXIT else FlowPhase.ENTER else FlowPhase.IDLE) {
                if(motion&&density.fontScale<=1.2f)WaveArticleDeck(articles,selected.coerceIn(0,articles.lastIndex),{selected=it},onArticle,
                    Modifier.fillMaxSize(),listTag=if(owner)"profile-articles" else "author-articles",deckTag=if(owner)"profile-wave" else "author-wave",frontTop=76.dp,referenceHeight=referenceHeight,
                    visibleLimit=WaveDeckMath.PROFILE_VISIBLE,showCounter=false,
                    sceneDurationMillis=if(exiting)SceneMotion.PROFILE_EXIT_MS else SceneMotion.PROFILE_ENTER_MS,sceneStaggerMillis=0,
                    consumePageDrag={delta -> -list.dispatchRawDelta(-delta)})
                else ArticleFeed(articles,articleList,if(owner)"profile-articles" else "author-articles",onArticle,
                    modifier=Modifier.fillMaxSize(),categoryCards=true)
            }
            }else Text("暂时没有公开文章",Modifier.padding(24.dp),style=MaterialTheme.typography.bodyMedium)
            }
            }
            if(remote!=null&&articles.isNotEmpty()&&remote.pages.hasNext)item("more-profile-articles") {
                MoreResults(remote.pages.loading,remote.pages.error,remote.next,Modifier.padding(horizontal=20.dp))
            }
        }
    }
}

@Composable
fun AuthorScreen(authorId: String,onBack: ()->Unit,onArticle: (String)->Unit,onNavigate: (Destination)->Unit,avatarArrival: Int=0) {
    val business=LocalBusiness.current
    if(business!=null) {
        val request=rememberProfileRequest(business,authorId,false)
        val info=request.info
        if(info==null) {
            PageFrame("作者主页",onBack,"author-unavailable") {
                val error=request.pages.error
                RemoteState(error ?: "正在加载作者主页…",action=error?.let {"重新加载"},onAction=request.next)
            }
        } else Box(Modifier.fillMaxSize().testTag("author-screen")) {
            BrowseScene(Destination.PROFILE,"",onNavigate,showHeader=false) {
                ProfileFeed(info.account.profile,info.account.id,false,false,false,onArticle,
                    progress=LocalProfileEntryProgress.current,authorArrival=avatarArrival,remote=request)
            }
        }
        return
    }
    val author=SampleLibrary.authors.find {it.id==authorId}
    if(author==null){PageFrame("作者主页",onBack,"author-missing-screen"){Text("未找到作者")};return}
    val cover=if(author.id=="zhou")Cover.ARCHITECTURE else null
    Box(Modifier.fillMaxSize().testTag("author-screen")) {
        BrowseScene(Destination.PROFILE,"",onNavigate,showHeader=false) {
            ProfileFeed(PreviewProfile(author.name,"sample_"+author.id,author.bio,cover=cover),author.id,false,false,false,onArticle,
                progress=LocalProfileEntryProgress.current,authorArrival=avatarArrival)
        }
    }
}
