package com.semiovert.app.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.font.FontWeight
import com.semiovert.app.core.designsystem.*
import com.semiovert.app.core.designsystem.wave.mainCircleOverlay
import com.semiovert.app.core.designsystem.wave.MainCircleMorphIcon
import com.semiovert.app.core.designsystem.wave.mainCircleColorState
import com.semiovert.app.core.designsystem.wave.mainCircleIconState
import com.semiovert.app.core.designsystem.wave.mainCircleColors
import com.semiovert.app.core.designsystem.wave.isMainCircleDrawOwner
import com.semiovert.app.core.designsystem.wave.LocalShortcutFusionOverlay
import com.semiovert.app.core.model.NavigationShortcut
import androidx.compose.ui.util.lerp
import kotlin.math.roundToInt
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

private const val READER_CIRCLE_RIGHT_GAP_PX=20f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScene(
    destination: Destination, title: String, onNavigate: (Destination) -> Unit,
    onPrimary: (() -> Unit)? = null,
    header: (@Composable (() -> Unit) -> Unit)? = null,
    showHeader: Boolean = true,
    readerDock: State<Float>? = null,
    navigationEnabled: Boolean = true,
    onToggleTheme: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val readerMode=readerDock!=null
    var navigationOpen by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val expansion=LocalArticleExpansion.current
    val mainDestination=LocalMainCirclePage.current?:destination
    val circleKey=remember {Any()}
    val shortcutHost=LocalNavigationShortcutHost.current
    val shortcuts=LocalShortcuts.current
    val ownsCircle=isMainCircleDrawOwner(circleKey)
    val mainColors=mainCircleColors(circleKey,mainDestination.circleColors)
    val density=LocalDensity.current.density
    var sceneOrigin by remember {mutableStateOf(Offset.Zero)}
    var mainCenter by remember {mutableStateOf(Offset.Zero)}
    val drafts=remember {HomeDraftMenuState()}
    val homeDraftActions=!readerMode&&destination==Destination.HOME&&mainDestination==destination
        &&expansion?.active!=true&&LocalFlowPhase.current!=FlowPhase.EXIT&&!navigationOpen
        &&HomeDraftAction.entries.any {action->shortcuts.items.none {it.route==action.route}}
    LaunchedEffect(homeDraftActions) {if(!homeDraftActions)drafts.expanded=false}
    val navigate: (Destination) -> Unit = {
        if(navigationEnabled) {
            keyboard?.hide();navigationOpen=false;drafts.expanded=false;expansion?.forgetOrigins();onNavigate(it)
        }
    }
    SideEffect {
        if(ownsCircle&&shortcutHost!=null) {
            if(mainCenter!=Offset.Zero)shortcutHost.mainCenterInRoot=sceneOrigin+mainCenter*density
            shortcutHost.mainColors=mainDestination.circleColors
            shortcutHost.beforeNavigate={keyboard?.hide();navigationOpen=false;drafts.expanded=false;expansion?.forgetOrigins()}
            shortcutHost.onCategoryReselect=onPrimary.takeIf {!readerMode&&destination==Destination.CATEGORY}
        }
    }
    val homeAlpha=if(!readerMode&&destination==Destination.HOME&&expansion?.source?.origin==ArticleOrigin.HOME)
        SceneMotion.readingOpeningCardAlpha(expansion.progress,ArticleOrigin.HOME,expansion.returning) else 1f
    BoxWithConstraints(Modifier.fillMaxSize().onGloballyPositioned {sceneOrigin=it.positionInRoot()}) {
        Box(Modifier.fillMaxSize().graphicsLayer {alpha=homeAlpha}) {content()}
        if(showHeader)Box(Modifier.fillMaxWidth().align(Alignment.TopCenter).zIndex(2f).statusBarsPadding().readingChromeFade()) {
            if (header != null) header { navigate(Destination.PROFILE) }
            else Row(Modifier.fillMaxWidth().zIndex(if(destination==Destination.HOME)1f else 0f).heightIn(min=72.dp).padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically) {
                if(destination!=Destination.HOME&&destination!=Destination.CATEGORY)TextButton(onClick={navigationOpen=true},modifier=Modifier.semantics { contentDescription="打开页面导航" }) {
                    Text(title,style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    SoIcon(SoSymbol.CHEVRON,modifier=Modifier.padding(start=4.dp).size(16.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.weight(1f))
                Box(Modifier.size(56.dp).clickable(interactionSource=null,indication=null,role=Role.Button,
                    enabled=navigationEnabled,onClick={navigate(Destination.PROFILE)})
                    .semantics {contentDescription="打开个人页"},contentAlignment=Alignment.Center) {
                    // The avatar owns its circular backing; the button must not leave a disk behind.
                    val home=destination==Destination.HOME
                    IdentityAvatar(size=56,
                        guestArtworkFraction=if(home)HOME_GHOST_DRAW_FRACTION else GUEST_GHOST_DRAW_FRACTION,
                        supersampleGuestArtwork=home)
                }
            }
        }
        if(destination==Destination.HOME&&onToggleTheme!=null) {
            ThemeToggleButton(onToggleTheme,Modifier.align(Alignment.TopEnd).statusBarsPadding()
                .padding(top=76.dp,end=16.dp).readingChromeFade())
        }
        val mainPrimary: (() ->Unit)?=when {
            !readerMode&&mainDestination!=destination -> ({navigate(mainDestination)})
            homeDraftActions -> ({drafts.expanded=!drafts.expanded})
            else -> onPrimary
        }
        val draftJoins=if(LocalMotionEnabled.current)drafts.dropletJoins(density,LocalDraftBox.current?.active==true) else emptyMap()
        HomeDraftCircles(drafts,mainCenter,sceneOrigin,mainColors.background,draftJoins)
        MainCircle(mainDestination,navigate,mainPrimary,
            {drafts.expanded=false;navigationOpen=true},maxWidth,maxHeight,readerDock,!readerMode&&mainDestination!=destination,circleKey,sceneOrigin,mainCenter,{mainCenter=it},
            onNavigationStart={drafts.expanded=false},aboveDrafts=drafts.expanded||drafts.visible,
            homeDraftActions=homeDraftActions,homeDraftsExpanded=drafts.expanded,mainColors=mainColors,draftJoins=draftJoins.values)
    }
    if (navigationOpen) SoBottomSheet(onDismissRequest={navigationOpen=false}) {close ->
        Column(Modifier.fillMaxWidth().padding(start=24.dp,end=24.dp,bottom=28.dp).navigationBarsPadding()) {
            Text("去哪里看看",style=MaterialTheme.typography.headlineSmall.withInterfaceTypeface())
            Text("长按主圆可滑动选择",style=MaterialTheme.typography.bodySmall.withInterfaceTypeface(),
                color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=8.dp,bottom=18.dp))
            Destination.entries.forEach { item ->
                TextButton(onClick={close {navigate(item)}},colors=accentTextButtonColors(),
                    modifier=Modifier.fillMaxWidth().heightIn(min=56.dp).semantics { contentDescription="切换到"+item.label; selected=!readerMode&&item==destination }) {
                    SoIcon(item.symbol,tint=MaterialTheme.colorScheme.primary)
                    Text(item.label,Modifier.weight(1f).padding(start=16.dp),style=MaterialTheme.typography.titleMedium.withInterfaceTypeface())
                    if(!readerMode&&item==destination) Text("当前",style=MaterialTheme.typography.labelMedium.withInterfaceTypeface())
                }
            }
        }
    }
}

private data class RadialBubbleVisual(val item: Destination,val offset: Offset,val amount: Float,val scale: Float,
    val stretch: Float,val elasticStretch: Float,val colors: CircleColors,val iconAlpha: Float,val join: MenuDropJoin?)

@Composable
private fun BoxScope.MainCircle(destination: Destination,onNavigate: (Destination)->Unit,onPrimary: (() ->Unit)?,onAccessibleMenu: ()->Unit,
    availableWidth: Dp,availableHeight: Dp,readerDock: State<Float>?,returnToMainPage: Boolean,circleKey: Any,sceneOrigin: Offset,mainCenter: Offset,onCenter: (Offset)->Unit,
    onNavigationStart: ()->Unit,aboveDrafts: Boolean,homeDraftActions: Boolean,homeDraftsExpanded: Boolean,
    mainColors: CircleColors,draftJoins: Collection<MenuDropJoin>) {
    val readerMode=readerDock!=null
    var expanded by remember {mutableStateOf(false)}
    var closing by remember {mutableStateOf(false)}
    var highlighted by remember {mutableStateOf<Destination?>(null)}
    var pinReady by remember {mutableStateOf(false)}
    var pulledPosition by remember {mutableStateOf<Offset?>(null)}
    var pulledStretch by remember {mutableFloatStateOf(0f)}
    var detachOrigin by remember {mutableStateOf<Offset?>(null)}
    var detachStretch by remember {mutableFloatStateOf(0f)}
    val detachProgress=remember {Animatable(0f)}
    val scope=rememberCoroutineScope()
    var menuJob by remember {mutableStateOf<Job?>(null)}
    val shortcuts=LocalShortcuts.current
    val allChoices=remember(destination,readerMode) {Destination.entries.filter {readerMode||it!=destination}}
    val choices=RadialNavigationGesture.menuChoices(destination,readerMode,shortcuts.items)
    val drops=remember(choices) {List(choices.size) {MenuDropletState()}}
    val pullReturn=remember {MenuDropletState(1f)}
    val placement=LocalNavigationPlacement.current
    val motion=LocalMotionEnabled.current
    val pullShapes=choices.map {item ->key(item) {
        animateFloatAsState(if(motion&&highlighted==item&&!closing&&detachOrigin==null)pulledStretch else 0f,
            if(motion)RadialNavigationGesture.stretchSpring() else snap(),label="radial-liquid-pull")
    }}
    // Only the attached -> detached edge starts this clock, never a pointer-position change.
    LaunchedEffect(detachOrigin!=null,motion) {
        if(detachOrigin!=null)detachProgress.animateTo(1f,if(motion)RadialNavigationGesture.detachMotion() else snap())
        else detachProgress.snapTo(0f)
    }
    val expansion=LocalArticleExpansion.current
    val readerIcon=if(readerMode)1f else if(expansion?.active==true)SceneMotion.readingBody(
        expansion.contentProgress,expansion.returning,expansion.source!!.origin) else 0f
    val colorState=mainCircleColorState()
    val iconState=mainCircleIconState()
    val fusion=LocalShortcutFusionOverlay.current
    val slots=remember(placement.x,readerMode) {when {
        readerMode->listOf(Offset(-156f,-8f),Offset(-136f,-88f),Offset(-76f,-150f),Offset(0f,-174f))
        placement.x<.33f->listOf(Offset(116f,-8f),Offset(94f,-100f),Offset(0f,-142f))
        placement.x>.66f->listOf(Offset(-116f,-8f),Offset(-94f,-100f),Offset(0f,-142f))
        else->listOf(Offset(-110f,-72f),Offset(0f,-142f),Offset(110f,-72f))
    }}
    val offsets=choices.map {slots[allChoices.indexOf(it)]}
    val density=LocalDensity.current.density
    val bottomInset=WindowInsets.navigationBars.getBottom(LocalDensity.current)/density
    val viewport=remember(availableWidth,availableHeight,bottomInset) {ShortcutViewport(availableWidth.value,availableHeight.value,bottomInset)}
    val haptics=LocalHapticFeedback.current
    val currentNavigate by rememberUpdatedState(onNavigate)
    val currentPrimary by rememberUpdatedState(onPrimary)
    val currentNavigationStart by rememberUpdatedState(onNavigationStart)
    val latestMainCenter by rememberUpdatedState(mainCenter)
    val finish: (Destination?,Offset?)->Unit={target,pin ->
        if(!closing) {
            closing=true;pinReady=pin!=null;menuJob?.cancel()
            val picked=highlighted
            if(target!=null&&pin==null) {
                colorState?.beginMerge(circleKey,target.circleColors)
                iconState?.beginMerge(circleKey,target.symbol)
            }
            menuJob=scope.launch {
                coroutineScope {
                    // Finish an in-flight handoff without starting another delay on release.
                    if(pin!=null&&motion)launch {snapshotFlow {detachProgress.value}.first {it>=1f}}
                    choices.indices.reversed().filter {pin==null||choices[it]!=picked}.forEachIndexed {rank,index ->
                        launch {
                            if(choices[index]==picked)pullReturn.animateTo(false,rank,motion) {amount ->
                                if(target!=null){colorState?.merge(1f-amount);iconState?.merge(1f-amount)}
                            } else drops[index].animateTo(false,rank,motion)
                        }
                    }
                }
                drops.forEach {it.snapTo(0f)}
                if(target!=null&&pin==null){colorState?.merge(1f);iconState?.merge(1f)}
                expanded=false;closing=false;highlighted=null;pinReady=false;pulledPosition=null;pulledStretch=0f
                detachOrigin=null
                pullReturn.snapTo(1f)
                if(target!=null&&pin!=null)shortcuts.save(viewport.shortcut(target.route,latestMainCenter+pin))
                if(target!=null&&pin==null)currentNavigate(target)
            }
        }
    }
    val currentFinish by rememberUpdatedState(finish)
    val visuals=if(expanded||closing)choices.mapIndexed {index,item ->key(item) {
        val selected=highlighted==item&&pulledPosition!=null
        val held=selected&&(!closing||pinReady)
        val drop=if(selected)pullReturn else drops[index]
        val amount=if(held)1f else drop.travel.value.coerceAtLeast(0f)
        val targetOffset=if(selected)pulledPosition!!*amount else offsets[index]*amount
        val offset=detachOrigin?.takeIf {motion&&selected&&pinReady}?.let {origin ->
            androidx.compose.ui.geometry.lerp(origin,targetOffset,detachProgress.value)
        }?:targetOffset
        val hover by animateFloatAsState(if(selected&&!closing&&!pinReady)RadialNavigationGesture.SELECTED_SCALE else 1f,
            if(motion)RadialNavigationGesture.scaleSpring() else snap(),label="nav-hover")
        val scale=DropletMenuMotion.scale(amount)*hover
        val elasticStretch=when {
            !motion->0f
            selected&&detachOrigin!=null->RadialNavigationGesture.detachRecoil(detachStretch,detachProgress.value)
            else->pullShapes[index].value
        }
        val mergeStretch by animateFloatAsState(
            if(motion&&selected&&pinReady&&!closing)RadialNavigationGesture.mergeStretch(targetOffset.getDistance()) else 0f,
            if(motion)RadialNavigationGesture.stretchSpring() else snap(),label="radial-liquid-return")
        val stretch=elasticStretch+mergeStretch
        val targetColors=if(selected)item.circleColors else mainColors
        val background by animateColorAsState(targetColors.background,tween(if(motion)120 else 0),label="radial-background")
        val foreground by animateColorAsState(targetColors.foreground,tween(if(motion)120 else 0),label="radial-foreground")
        val join=if(motion&&!held)DropletMenuMotion.join(offset,scale,density,background,stretch,elasticStretch) else null
        RadialBubbleVisual(item,offset,amount,scale,stretch,elasticStretch,CircleColors(background,foreground),
            if(held)1f else drop.icon.value,join)
    }} else emptyList()
    val radialJoins=visuals.mapNotNull {it.join}
    val joinedSilhouettes=(radialJoins+draftJoins).map {it.silhouette}
    BackHandler(expanded) {finish(null,null)}
    Box(Modifier.align(AbsoluteAlignment.BottomRight).zIndex(if(aboveDrafts)4f else 0f).imePadding().navigationBarsPadding()
        .absolutePadding(right=20.dp,bottom=18.dp)
        .absoluteOffset {
            val dockProgress=readerDock?.value?:readerIcon
            val browseX=-(availableWidth-96.dp).coerceAtLeast(0.dp).toPx()*(1f-placement.x)
            val browseY=-140.dp.toPx()*placement.y
            IntOffset(lerp(browseX,20.dp.roundToPx()-READER_CIRCLE_RIGHT_GAP_PX,dockProgress).roundToInt(),
                lerp(browseY,18.dp.roundToPx()-48.dp.roundToPx().toFloat(),dockProgress).roundToInt())
        }.size(56.dp)
        .mainCircleOverlay(circleKey).onGloballyPositioned {
            onCenter((it.localToRoot(Offset(it.size.width/2f,it.size.height/2f))-sceneOrigin)/density)
        }) {
        MenuDropletBridges(Offset(28f,28f),mainColors.background,radialJoins,Modifier.matchParentSize())
        if(joinedSilhouettes.isNotEmpty()&&fusion?.joinedShadow!=true) {
            FusionShadow(DropletMenuMotion.coreCenter(density),joinedSilhouettes,tag="menu-droplet-shadow")
        }
        visuals.forEach {visual ->
            key(visual.item) {
                val item=visual.item
                val offset=visual.offset
                Column(Modifier.wrapContentSize(Alignment.TopStart,unbounded=true).offset(offset.x.dp,(offset.y+2f).dp).width(56.dp)
                    .testTag("radial-"+item.route),horizontalAlignment=Alignment.CenterHorizontally) {
                    NavigationBubble(item.symbol,visual.colors,visual.scale,visual.stretch,-offset,Modifier,
                        shadowElevation=if(visual.join==null)3.dp else 0.dp,elasticStretch=visual.elasticStretch,iconAlpha=visual.iconAlpha)
                    val labelAlpha by animateFloatAsState(if(highlighted==item&&!closing)1f else 0f,
                        tween(if(motion)120 else 0),label="nav-label")
                    Text(if(pinReady&&highlighted==item)"松手固定" else item.label,
                        style=MaterialTheme.typography.labelMedium.copy(fontFamily=NavigationLabelFont,fontWeight=FontWeight.Bold).withInterfaceTypeface(),
                        maxLines=1,softWrap=false,modifier=Modifier.wrapContentWidth(unbounded=true).padding(top=4.dp)
                            .graphicsLayer {alpha=labelAlpha*visual.amount.coerceIn(0f,1f)})
                }
            }
        }
        Surface(shape=CircleShape,color=mainColors.background,contentColor=mainColors.foreground,shadowElevation=0.dp,
            modifier=Modifier.size(56.dp).graphicsLayer {
                shape=CircleShape
                clip=false
                shadowElevation=if(fusion?.joinedShadow==true||joinedSilhouettes.isNotEmpty())0f else 6.dp.toPx()
            }.testTag("main-circle").semantics {
                role=Role.Button
                contentDescription=if(readerMode)"打开目录" else destination.label+"主圆"
                stateDescription=if(readerMode)"点按打开目录，长按打开页面导航" else if(returnToMainPage)"点按返回"+destination.label else when(destination) {
                    Destination.HOME->if(homeDraftActions)if(homeDraftsExpanded)"草稿操作已展开，点按收起" else "点按展开草稿操作，长按滑动切换页面" else "长按滑动切换页面"
                    Destination.CATEGORY->"点按切换阅读时长";Destination.SEARCH->"点按切换文章与作者";else->"长按滑动切换页面"
                }
                if(onPrimary!=null)onClick {currentPrimary?.invoke();true}
                onLongClick(label="打开可点击的页面导航") {onAccessibleMenu();true}
                customActions=choices.map {target ->CustomAccessibilityAction("前往"+target.label) {currentNavigate(target);true}}+
                    choices.map {target ->CustomAccessibilityAction("固定"+target.label+"快捷入口") {shortcuts.save(NavigationShortcut(target.route,.15f,.72f));true}}
            }.onKeyEvent {event ->
                if(currentPrimary!=null&&event.type==KeyEventType.KeyUp&&event.key in listOf(Key.Enter,Key.Spacebar,Key.DirectionCenter)){currentPrimary?.invoke();true} else false
            }.focusable().pointerInput(destination,readerMode,choices,density,placement,motion,closing,availableWidth,availableHeight,bottomInset) {
                awaitEachGesture {
                    val down=awaitFirstDown()
                    if(closing)return@awaitEachGesture
                    val longPress=awaitLongPressOrCancellation(down.id)
                    if(longPress==null) {
                        if(currentEvent.changes.any {it.id==down.id&&it.changedToUpIgnoreConsumed()})currentPrimary?.invoke()
                    } else {
                        currentNavigationStart()
                        if(choices.isEmpty()){onAccessibleMenu();return@awaitEachGesture}
                        expanded=true;menuJob?.cancel()
                        menuJob=scope.launch {
                            pullReturn.snapTo(1f)
                            coroutineScope {drops.forEachIndexed {index,item ->launch {
                                item.animateTo(true,index,motion)
                            }}}
                        }
                        haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        var acquired: Int?=null
                        var pullOrigin=Offset.Zero
                        var detached=false
                        var target: Destination?=null
                        var pin: Offset?=null
                        try {
                            val completed=drag(down.id) {change ->
                                val pointer=(change.position-Offset(size.width/2f,size.height/2f))/density
                                val index=if(detached)acquired else RadialNavigationGesture.target(pointer,offsets,acquired)
                                if(index!=null) {
                                    if(acquired!=index) {
                                        acquired=index;pullOrigin=pointer
                                        haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                    }
                                    if(!detached&&RadialNavigationGesture.canPin(pointer)) {
                                        detachStretch=pullShapes[index].value
                                        detachOrigin=pulledPosition?:offsets[index]
                                        detached=true
                                        haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                    }
                                    val desired=if(detached)pointer else RadialNavigationGesture.attachedCenter(pointer,pullOrigin,offsets[index])
                                    val center=viewport.constrain(latestMainCenter+desired)-latestMainCenter
                                    target=choices[index];highlighted=target;pulledPosition=center
                                    pinReady=detached
                                    if(!detached)pulledStretch=.8f*RadialNavigationGesture.pullProgress(pointer,pullOrigin,offsets[index])
                                    pin=if(detached)center else null
                                } else {
                                    acquired=null;target=null;pin=null
                                    highlighted=null;pulledPosition=null;pulledStretch=0f
                                }
                                change.consume()
                            }
                            val merge=pin?.let {RadialNavigationGesture.canMerge(it)}==true
                            val cancel=!completed||merge
                            currentFinish(if(cancel)null else target,if(cancel)null else pin)
                        } catch(cancelled: CancellationException) {currentFinish(null,null);throw cancelled}
                    }
                }
            }) {
            Box(contentAlignment=Alignment.Center) {
                MainCircleMorphIcon(circleKey,destination.symbol,readerIcon,readerMode||expansion?.active==true,tint=mainColors.foreground)
            }
        }
    }
}

@Composable
internal fun NavigationBubble(symbol: SoSymbol,colors: CircleColors,scale: Float,stretch: Float,towardMain: Offset,modifier: Modifier,
    shadowElevation: Dp=3.dp,elasticStretch: Float=stretch,iconAlpha: Float=1f) {
    NavigationBubble(colors,scale,stretch,towardMain,modifier,shadowElevation,elasticStretch,iconAlpha) {
        SoIcon(symbol,modifier=Modifier.size(NavigationCircleIconSize),tint=colors.foreground)
    }
}

@Composable
internal fun NavigationBubble(colors: CircleColors,scale: Float,stretch: Float,towardMain: Offset,modifier: Modifier,
    shadowElevation: Dp=3.dp,elasticStretch: Float=stretch,iconAlpha: Float=1f,icon: @Composable ()->Unit) {
    Surface(shape=LiquidCircleShape(towardMain,stretch,elasticStretch),color=colors.background,contentColor=colors.foreground,shadowElevation=shadowElevation,
        modifier=modifier.size(NavigationCircleSize).graphicsLayer {scaleX=scale;scaleY=scale}) {
        Box(Modifier.graphicsLayer {alpha=iconAlpha},contentAlignment=Alignment.Center) {icon()}
    }
}
