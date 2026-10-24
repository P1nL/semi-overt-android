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
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScene(
    destination: Destination, title: String, onNavigate: (Destination) -> Unit,
    onPrimary: (() -> Unit)? = null,
    header: (@Composable (() -> Unit) -> Unit)? = null,
    showHeader: Boolean = true,
    readerMode: Boolean = false,
    onToggleTheme: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    var navigationOpen by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val expansion=LocalArticleExpansion.current
    val mainDestination=LocalMainCirclePage.current?:destination
    val circleKey=remember {Any()}
    val shortcutHost=LocalNavigationShortcutHost.current
    val ownsCircle=isMainCircleDrawOwner(circleKey)
    val density=LocalDensity.current.density
    var sceneOrigin by remember {mutableStateOf(Offset.Zero)}
    var mainCenter by remember {mutableStateOf(Offset.Zero)}
    val navigate: (Destination) -> Unit = { keyboard?.hide();navigationOpen=false;expansion?.forgetOrigins();onNavigate(it) }
    SideEffect {
        if(ownsCircle&&shortcutHost!=null) {
            if(mainCenter!=Offset.Zero)shortcutHost.mainCenterInRoot=sceneOrigin+mainCenter*density
            shortcutHost.mainColors=mainDestination.circleColors
            shortcutHost.beforeNavigate={keyboard?.hide();navigationOpen=false;expansion?.forgetOrigins()}
        }
    }
    val homeAlpha=if(!readerMode&&destination==Destination.HOME&&expansion?.source?.origin==ArticleOrigin.HOME)
        1f-SceneMotion.homeReadingFade(expansion.progress) else 1f
    BoxWithConstraints(Modifier.fillMaxSize().onGloballyPositioned {sceneOrigin=it.positionInRoot()}) {
        Box(Modifier.fillMaxSize().graphicsLayer {alpha=homeAlpha}) {content()}
        if(showHeader)Box(Modifier.fillMaxWidth().align(Alignment.TopCenter).zIndex(2f).statusBarsPadding().readingChromeFade()) {
            if (header != null) header { navigationOpen=true }
            else Row(Modifier.fillMaxWidth().zIndex(if(destination==Destination.HOME)1f else 0f).heightIn(min=72.dp).padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically) {
                if(destination==Destination.CATEGORY) {
                    val duration=motionMillis(220)
                    val annotationAlpha=LocalBrowseAnnotationAlpha.current
                    AnimatedContent(title,modifier=Modifier.padding(horizontal=12.dp).graphicsLayer {alpha=annotationAlpha},label="category-label",transitionSpec={
                        (fadeIn(tween(duration))+slideInVertically(tween(duration)){it/3}) togetherWith
                            (fadeOut(tween(duration))+slideOutVertically(tween(duration)){-it/3})
                    }) {label->Text(label,style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.tertiary)}
                } else TextButton(onClick={navigationOpen=true},modifier=Modifier.semantics { contentDescription="打开页面导航" }) {
                    Text(title,style=MaterialTheme.typography.labelLarge,color=if(destination==Destination.CATEGORY) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant)
                    SoIcon(SoSymbol.CHEVRON,modifier=Modifier.padding(start=4.dp).size(16.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick={navigate(Destination.PROFILE)},modifier=Modifier.size(56.dp)) {
                    // The avatar owns its circular backing; the button must not leave a disk behind.
                    IdentityAvatar(Modifier.semantics {contentDescription="打开个人页"},size=56)
                }
            }
        }
        if(destination==Destination.HOME&&onToggleTheme!=null) {
            ThemeToggleButton(onToggleTheme,Modifier.align(Alignment.TopEnd).statusBarsPadding()
                .padding(top=76.dp,end=16.dp).readingChromeFade())
        }
        val primary=onPrimary?:{navigationOpen=true}
        val mainPrimary: ()->Unit={if(readerMode||mainDestination==destination)primary() else navigate(mainDestination)}
        MainCircle(mainDestination,navigate,mainPrimary,
            {navigationOpen=true},maxWidth,maxHeight,readerMode,!readerMode&&mainDestination!=destination,circleKey,sceneOrigin,mainCenter,{mainCenter=it})
    }
    if (navigationOpen) SoBottomSheet(onDismissRequest={navigationOpen=false}) {close ->
        Column(Modifier.fillMaxWidth().padding(start=24.dp,end=24.dp,bottom=28.dp).navigationBarsPadding()) {
            Text("去哪里看看",style=MaterialTheme.typography.headlineSmall)
            Text("本地示例 · 也可以长按主圆滑选",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=8.dp,bottom=18.dp))
            Destination.entries.forEach { item ->
                TextButton(onClick={close {navigate(item)}},modifier=Modifier.fillMaxWidth().heightIn(min=56.dp).semantics { contentDescription="切换到"+item.label; selected=!readerMode&&item==destination }) {
                    SoIcon(item.symbol,tint=MaterialTheme.colorScheme.primary)
                    Text(item.label,Modifier.weight(1f).padding(start=16.dp),style=MaterialTheme.typography.titleMedium)
                    if(!readerMode&&item==destination) Text("当前",style=MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun BoxScope.MainCircle(destination: Destination,onNavigate: (Destination)->Unit,onPrimary: ()->Unit,onAccessibleMenu: ()->Unit,
    availableWidth: Dp,availableHeight: Dp,readerMode: Boolean,returnToMainPage: Boolean,circleKey: Any,sceneOrigin: Offset,mainCenter: Offset,onCenter: (Offset)->Unit) {
    var expanded by remember {mutableStateOf(false)}
    var closing by remember {mutableStateOf(false)}
    var highlighted by remember {mutableStateOf<Destination?>(null)}
    var pinReady by remember {mutableStateOf(false)}
    var pulledPosition by remember {mutableStateOf<Offset?>(null)}
    var pulledStretch by remember {mutableFloatStateOf(0f)}
    var detachOrigin by remember {mutableStateOf(Offset.Zero)}
    val detachProgress=remember {Animatable(0f)}
    val scope=rememberCoroutineScope()
    var menuJob by remember {mutableStateOf<Job?>(null)}
    val shortcuts=LocalShortcuts.current
    val allChoices=remember(destination,readerMode) {Destination.entries.filter {readerMode||it!=destination}}
    val choices=RadialNavigationGesture.menuChoices(destination,readerMode,shortcuts.items)
    val progress=remember(choices) {List(choices.size) {Animatable(0f)}}
    val pullReturn=remember {Animatable(1f)}
    val placement=LocalNavigationPlacement.current
    val motion=LocalMotionEnabled.current
    // Only the attached -> detached edge starts this clock, never a pointer-position change.
    LaunchedEffect(pinReady,motion) {
        if(pinReady)detachProgress.animateTo(1f,if(motion)RadialNavigationGesture.detachMotion() else snap())
        else detachProgress.snapTo(0f)
    }
    val expansion=LocalArticleExpansion.current
    val readerIcon=if(readerMode)1f else if(expansion?.active==true)SceneMotion.readingBody(expansion.progress) else 0f
    val mainColors=mainCircleColors(circleKey,destination.circleColors)
    val colorState=mainCircleColorState()
    val iconState=mainCircleIconState()
    val fusion=LocalShortcutFusionOverlay.current
    val slots=remember(placement.x,readerMode) {when {
        readerMode&&placement.x<.33f->listOf(Offset(156f,-8f),Offset(136f,-88f),Offset(76f,-150f),Offset(0f,-174f))
        readerMode&&placement.x>.66f->listOf(Offset(-156f,-8f),Offset(-136f,-88f),Offset(-76f,-150f),Offset(0f,-174f))
        readerMode->listOf(Offset(-96f,-56f),Offset(-36f,-142f),Offset(36f,-142f),Offset(96f,-56f))
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
                    choices.indices.filter {choices[it]!=picked}.forEachIndexed {order,index ->launch {
                        if(motion)delay(order*35L)
                        progress[index].animateTo(0f,if(motion)SceneMotion.navigationSpring() else snap())
                    }}
                    if(picked!=null&&pin==null)launch {
                        val spec=if(!motion)snap() else if(target==null)RadialNavigationGesture.mergeMotion() else SceneMotion.navigationSpring()
                        pullReturn.animateTo(0f,spec) {
                            if(target!=null){colorState?.merge(1f-value);iconState?.merge(1f-value)}
                        }
                    }
                }
                progress.forEach {it.snapTo(0f)}
                if(target!=null&&pin==null){colorState?.merge(1f);iconState?.merge(1f)}
                expanded=false;closing=false;highlighted=null;pinReady=false;pulledPosition=null;pulledStretch=0f
                pullReturn.snapTo(1f)
                if(target!=null&&pin!=null)shortcuts.save(viewport.shortcut(target.route,latestMainCenter+pin))
                if(target!=null&&pin==null)currentNavigate(target)
            }
        }
    }
    val currentFinish by rememberUpdatedState(finish)
    BackHandler(expanded) {finish(null,null)}
    Box(Modifier.align(Alignment.BottomEnd).imePadding().navigationBarsPadding().padding(end=20.dp,bottom=18.dp)
        .offset(x=-(availableWidth-96.dp).coerceAtLeast(0.dp)*(1f-placement.x),y=-140.dp*placement.y).size(56.dp)
        .mainCircleOverlay(circleKey).onGloballyPositioned {
            onCenter((it.localToRoot(Offset(it.size.width/2f,it.size.height/2f))-sceneOrigin)/density)
        }) {
        choices.forEachIndexed {index,item ->
            val selected=highlighted==item&&pulledPosition!=null
            val amount=if(selected)if(closing&&!pinReady)pullReturn.value.coerceAtLeast(0f) else 1f else progress[index].value.coerceAtLeast(0f)
            val targetOffset=if(selected)pulledPosition!!*amount else offsets[index]*amount
            val offset=if(motion&&selected&&pinReady)
                androidx.compose.ui.geometry.lerp(detachOrigin,targetOffset,detachProgress.value) else targetOffset
            val hover by animateFloatAsState(if(selected&&!closing&&!pinReady)RadialNavigationGesture.SELECTED_SCALE else 1f,
                if(motion)RadialNavigationGesture.scaleSpring() else snap(),label="nav-hover")
            val scale=(.7f+.3f*amount)*hover
            val stretch by animateFloatAsState(if(motion&&selected&&!closing)pulledStretch else 0f,
                if(motion)RadialNavigationGesture.stretchSpring() else snap(),label="radial-liquid-pull")
            val targetColors=if(selected)item.circleColors else mainColors
            val background by animateColorAsState(targetColors.background,tween(if(motion)120 else 0),label="radial-background")
            val foreground by animateColorAsState(targetColors.foreground,tween(if(motion)120 else 0),label="radial-foreground")
            val colors=CircleColors(background,foreground)
            if(expanded||closing) {
                Column(Modifier.wrapContentSize(Alignment.TopStart,unbounded=true).offset(offset.x.dp,(offset.y+2f).dp).width(56.dp)
                    .testTag("radial-"+item.route),horizontalAlignment=Alignment.CenterHorizontally) {
                    NavigationBubble(item.symbol,colors,scale,stretch,-offset,Modifier)
                    val labelAlpha by animateFloatAsState(if(highlighted==item&&!closing)1f else 0f,
                        tween(if(motion)120 else 0),label="nav-label")
                    Text(if(pinReady&&highlighted==item)"松手固定" else item.label,
                        style=MaterialTheme.typography.labelMedium.copy(fontFamily=NavigationLabelFont,fontWeight=FontWeight.Bold),
                        maxLines=1,softWrap=false,modifier=Modifier.wrapContentWidth(unbounded=true).padding(top=4.dp)
                            .graphicsLayer {alpha=labelAlpha*amount.coerceIn(0f,1f)})
                }
            }
        }
        Surface(shape=CircleShape,color=mainColors.background,contentColor=mainColors.foreground,shadowElevation=0.dp,
            modifier=Modifier.size(56.dp).graphicsLayer {
                shape=CircleShape
                clip=false
                shadowElevation=if(fusion?.joinedShadow==true)0f else 6.dp.toPx()
            }.testTag("main-circle").semantics {
                role=Role.Button
                contentDescription=if(readerMode)"打开目录" else destination.label+"主圆"
                stateDescription=if(readerMode)"点按打开目录，长按打开页面导航" else if(returnToMainPage)"点按返回"+destination.label else when(destination) {
                    Destination.CATEGORY->"点按切换阅读时长";Destination.SEARCH->"点按切换文章与作者";else->"点按打开页面导航"
                }
                onClick {currentPrimary();true}
                onLongClick(label="打开可点击的页面导航") {onAccessibleMenu();true}
                customActions=choices.map {target ->CustomAccessibilityAction("前往"+target.label) {currentNavigate(target);true}}+
                    choices.map {target ->CustomAccessibilityAction("固定"+target.label+"快捷入口") {shortcuts.save(NavigationShortcut(target.route,.15f,.72f));true}}
            }.onKeyEvent {event ->
                if(event.type==KeyEventType.KeyUp&&event.key in listOf(Key.Enter,Key.Spacebar,Key.DirectionCenter)){currentPrimary();true} else false
            }.focusable().pointerInput(destination,readerMode,choices,density,placement,motion,closing,availableWidth,availableHeight,bottomInset) {
                awaitEachGesture {
                    val down=awaitFirstDown()
                    if(closing)return@awaitEachGesture
                    val longPress=awaitLongPressOrCancellation(down.id)
                    if(longPress==null) {
                        if(currentEvent.changes.any {it.id==down.id&&it.changedToUpIgnoreConsumed()})currentPrimary()
                    } else {
                        if(choices.isEmpty()){onAccessibleMenu();return@awaitEachGesture}
                        expanded=true;menuJob?.cancel()
                        menuJob=scope.launch {
                            pullReturn.snapTo(1f)
                            coroutineScope {progress.forEachIndexed {index,item ->launch {
                                if(motion)delay(index*40L)
                                item.animateTo(1f,if(motion)SceneMotion.navigationSpring() else snap())
                            }}}
                        }
                        haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        var acquired: Int?=null
                        var pullOrigin=Offset.Zero
                        var detached=false
                        var pulled=false
                        var target: Destination?=null
                        var pin: Offset?=null
                        try {
                            val completed=drag(down.id) {change ->
                                val pointer=(change.position-Offset(size.width/2f,size.height/2f))/density
                                val index=if(detached)acquired else RadialNavigationGesture.target(pointer,offsets,acquired)
                                if(index!=null) {
                                    if(acquired!=index) {
                                        acquired=index;pullOrigin=pointer;pulled=false
                                        haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                    }
                                    val tension=if(detached)0f else RadialNavigationGesture.pullProgress(pointer,pullOrigin,offsets[index])
                                    pulled=pulled||tension>0f
                                    if(!detached&&RadialNavigationGesture.canPin(pointer)) {
                                        detachOrigin=pulledPosition?:offsets[index]
                                        detached=true
                                        haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                    }
                                    val desired=if(detached)pointer else RadialNavigationGesture.attachedCenter(pointer,pullOrigin,offsets[index])
                                    val center=viewport.constrain(latestMainCenter+desired)-latestMainCenter
                                    target=choices[index];highlighted=target;pulledPosition=center
                                    pinReady=detached
                                    pulledStretch=if(detached)RadialNavigationGesture.mergeStretch(center.getDistance()) else .8f*tension
                                    pin=if(detached)center else null
                                } else {
                                    acquired=null;target=null;pin=null
                                    highlighted=null;pulledPosition=null;pulledStretch=0f
                                }
                                change.consume()
                            }
                            val merge=pin?.let {RadialNavigationGesture.canMerge(it)}==true
                            val cancel=!completed||merge||(!detached&&pulled)
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
    shadowElevation: Dp=3.dp) {
    Surface(shape=LiquidCircleShape(towardMain,stretch),color=colors.background,contentColor=colors.foreground,shadowElevation=shadowElevation,
        modifier=modifier.size(52.dp).graphicsLayer {scaleX=scale;scaleY=scale}) {
        Box(contentAlignment=Alignment.Center) {SoIcon(symbol,tint=colors.foreground)}
    }
}
