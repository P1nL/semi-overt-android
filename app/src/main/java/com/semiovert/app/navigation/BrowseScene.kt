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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
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
import com.semiovert.app.core.model.NavigationShortcut
import kotlinx.coroutines.*
import kotlin.math.roundToInt

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
    val navigate: (Destination) -> Unit = { keyboard?.hide();navigationOpen=false;expansion?.forgetOrigins();onNavigate(it) }
    val homeAlpha=if(!readerMode&&destination==Destination.HOME&&expansion?.source?.origin==ArticleOrigin.HOME)
        1f-SceneMotion.homeReadingFade(expansion.progress) else 1f
    BoxWithConstraints(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().graphicsLayer {alpha=homeAlpha}) {content()}
        if(showHeader)Box(Modifier.fillMaxWidth().align(Alignment.TopCenter).zIndex(2f).statusBarsPadding().readingChromeFade()) {
            if (header != null) header { navigationOpen=true }
            else Row(Modifier.fillMaxWidth().zIndex(if(destination==Destination.HOME)1f else 0f).heightIn(min=72.dp).padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically) {
                if(destination==Destination.CATEGORY) {
                    val duration=motionMillis(220)
                    AnimatedContent(title,modifier=Modifier.padding(horizontal=12.dp),label="category-label",transitionSpec={
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
        MainCircle(destination,navigate,onPrimary ?: {navigationOpen=true}, {navigationOpen=true},maxWidth,maxHeight,readerMode)
        ShortcutCircles(destination,navigate,maxWidth,maxHeight,readerMode)
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
private fun BoxScope.MainCircle(destination: Destination,onNavigate: (Destination)->Unit,onPrimary: ()->Unit,onAccessibleMenu: ()->Unit,availableWidth: Dp,availableHeight: Dp,readerMode: Boolean) {
    var expanded by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    var highlighted by remember { mutableStateOf<Destination?>(null) }
    var pinReady by remember {mutableStateOf(false)}
    var pulledPosition by remember {mutableStateOf<Offset?>(null)}
    val scope=rememberCoroutineScope()
    var menuJob by remember {mutableStateOf<Job?>(null)}
    // The reader is not one of the four primary pages: its origin is still a valid destination.
    val choices = remember(destination,readerMode) { Destination.entries.filter {readerMode||it!=destination} }
    val progress=remember(destination,readerMode) {List(choices.size) {Animatable(0f)}}
    val placement=LocalNavigationPlacement.current
    val shortcuts=LocalShortcuts.current
    val motion=LocalMotionEnabled.current
    val expansion=LocalArticleExpansion.current
    // Only the inner icon follows reading progress; the circular surface stays opaque and stationary.
    val readerIcon=if(readerMode)1f else if(expansion?.active==true)SceneMotion.readingBody(expansion.progress) else 0f
    val circleKey=remember {Any()}
    val mainColors=mainCircleColors(circleKey,destination.circleColors)
    val colorState=mainCircleColorState()
    val iconState=mainCircleIconState()
    val offsets=remember(placement.x,readerMode) {when {
        readerMode&&placement.x<.33f->listOf(Offset(156f,-8f),Offset(136f,-88f),Offset(76f,-150f),Offset(0f,-174f))
        readerMode&&placement.x>.66f->listOf(Offset(-156f,-8f),Offset(-136f,-88f),Offset(-76f,-150f),Offset(0f,-174f))
        readerMode->listOf(Offset(-96f,-56f),Offset(-36f,-142f),Offset(36f,-142f),Offset(96f,-56f))
        placement.x<.33f->listOf(Offset(116f,-8f),Offset(94f,-100f),Offset(0f,-142f))
        placement.x>.66f->listOf(Offset(-116f,-8f),Offset(-94f,-100f),Offset(0f,-142f))
        else->listOf(Offset(-110f,-72f),Offset(0f,-142f),Offset(110f,-72f))
    }}
    val density=LocalDensity.current.density
    val bottomInset=WindowInsets.navigationBars.getBottom(LocalDensity.current)/density
    val haptics=LocalHapticFeedback.current
    val currentNavigate by rememberUpdatedState(onNavigate)
    val currentPrimary by rememberUpdatedState(onPrimary)
    var mainCenter by remember {mutableStateOf(Offset.Zero)}
    val finish: (Destination?,Offset?)->Unit={target,pin ->
        if(!closing) {
            closing=true
            menuJob?.cancel()
            if(target!=null&&pin==null) {
                colorState?.beginMerge(circleKey,target.circleColors)
                iconState?.beginMerge(circleKey,target.symbol)
            }
            menuJob=scope.launch {
                val others=choices.indices.filter {choices[it]!=target}
                val returning=others+if(pin==null&&target!=null)listOf(choices.indexOf(target)) else emptyList()
                coroutineScope {returning.forEachIndexed {order,index ->launch {
                    if(motion)kotlinx.coroutines.delay(order*35L)
                    val start=progress[index].value.coerceAtLeast(.001f)
                    progress[index].animateTo(0f,if(motion)SceneMotion.navigationSpring() else snap()) {
                        if(target==choices[index]&&pin==null) {
                            val merged=1f-value/start
                            colorState?.merge(merged);iconState?.merge(merged)
                        }
                    }
                }}}
                if(target!=null) {
                    if(pin!=null) shortcuts.save(NavigationShortcut(target.route,((mainCenter.x/density+pin.x-42f)/(availableWidth.value-72f)).coerceIn(0f,1f),((mainCenter.y/density+pin.y-42f)/(availableHeight.value-128f-bottomInset)).coerceIn(0f,1f)))
                }
                progress.forEach {it.snapTo(0f)}
                if(target!=null&&pin==null) {colorState?.merge(1f);iconState?.merge(1f)}
                expanded=false;closing=false;highlighted=null;pinReady=false;pulledPosition=null
                if(target!=null&&pin==null)currentNavigate(target)
            }
        }
    }
    BackHandler(expanded) {finish(null,null)}
    Box(Modifier.align(Alignment.BottomEnd).imePadding().navigationBarsPadding().padding(end=20.dp,bottom=18.dp).offset(x=-(availableWidth-96.dp).coerceAtLeast(0.dp)*(1f-placement.x),y=-140.dp*placement.y).size(56.dp).mainCircleOverlay(circleKey).onGloballyPositioned {mainCenter=it.positionInParent()+Offset(it.size.width/2f,it.size.height/2f)}) {
        choices.forEachIndexed { index,item ->
            val amount=progress[index].value.coerceAtLeast(0f)
            val offset=(if(highlighted==item)pulledPosition?:offsets[index] else offsets[index])*amount
            val hover by animateFloatAsState(if(highlighted==item)1.14f else 1f,
                if(motion)spring(dampingRatio=.55f,stiffness=420f,visibilityThreshold=.001f) else snap(),label="nav-hover")
            val scale=(.7f+.3f*amount)*hover
            val smallCenter=offset+Offset(28f,26f)
            val radius=26f*scale
            val separation=if(motion)LiquidCircleGeometry.materialProgress((smallCenter-Offset(28f,28f)).getDistance(),28f,radius) else 1f
            val selectedColors=animatedCircleColors(if(highlighted==item)item.circleColors else mainColors,"radial-"+item.route)
            val liquidColors=mainColors.towards(selectedColors,separation)
            if(expanded||closing) {
                if(motion)Canvas(Modifier.matchParentSize().zIndex(1f)) {
                    val neck=LiquidCircleGeometry.bridge(Offset(28f,28f)*density,28f*density,smallCenter*density,radius*density)
                    if(neck!=null) {
                        // Paint over just the shadow inside the neck, never over the fixed body/icon.
                        val body=Path().apply {addOval(Rect(0f,0f,size.width,size.height))}
                        clipPath(body,ClipOp.Difference) {drawLiquidBridge(neck,mainColors.background)}
                    }
                }
                Column(Modifier.wrapContentSize(Alignment.TopStart,unbounded=true).offset(offset.x.dp,offset.y.dp).width(56.dp).graphicsLayer {
                    // The bridge and disk must share a center, independent of the label's height.
                    transformOrigin=TransformOrigin(.5f,26.dp.toPx()/size.height)
                    scaleX=scale;scaleY=scale
                }.testTag("radial-"+item.route),horizontalAlignment=Alignment.CenterHorizontally) {
                Surface(shape=CircleShape,color=liquidColors.background,contentColor=liquidColors.foreground,
                    modifier=Modifier.size(52.dp)) {
                    Box(Modifier.graphicsLayer {alpha=separation},contentAlignment=Alignment.Center) { SoIcon(item.symbol,tint=liquidColors.foreground) }
                }
                val labelAlpha by animateFloatAsState(if(highlighted==item)1f else 0f,tween(if(motion)120 else 0),label="nav-label")
                Text(if(pinReady&&highlighted==item)"松手固定" else item.label,
                    style=MaterialTheme.typography.labelMedium.copy(fontFamily=NavigationLabelFont,fontWeight=FontWeight.Bold),
                    maxLines=1,softWrap=false,modifier=Modifier.wrapContentWidth(unbounded=true).padding(top=4.dp).graphicsLayer {alpha=labelAlpha*separation})
                }
            }
        }
        Surface(shape=CircleShape,color=mainColors.background,contentColor=mainColors.foreground,shadowElevation=6.dp,
            modifier=Modifier.size(56.dp).testTag("main-circle").semantics {
                role=Role.Button
                contentDescription=if(readerMode)"打开目录" else destination.label+"主圆"
                stateDescription=if(readerMode)"点按打开目录，长按打开页面导航" else when(destination) { Destination.CATEGORY->"点按切换阅读时长";Destination.SEARCH->"点按切换文章与作者";else->"点按打开页面导航" }
                onClick { currentPrimary();true }
                onLongClick(label="打开可点击的页面导航") { onAccessibleMenu();true }
                customActions=choices.map { target -> CustomAccessibilityAction("前往"+target.label) { currentNavigate(target);true } }+choices.map {target ->CustomAccessibilityAction("固定"+target.label+"快捷入口") {shortcuts.save(NavigationShortcut(target.route,.15f,.72f));true}}
            }.onKeyEvent { event ->
                if(event.type==KeyEventType.KeyUp && event.key in listOf(Key.Enter,Key.Spacebar,Key.DirectionCenter)) { currentPrimary();true } else false
            }.focusable().pointerInput(destination,readerMode,density,placement,motion,closing) {
                awaitEachGesture {
                    val down=awaitFirstDown()
                    if(closing)return@awaitEachGesture
                    val longPress=awaitLongPressOrCancellation(down.id)
                    if(longPress==null) {
                        if(currentEvent.changes.any {it.id==down.id && it.changedToUpIgnoreConsumed()}) currentPrimary()
                    } else {
                        expanded=true
                        menuJob?.cancel()
                        menuJob=scope.launch {coroutineScope {progress.forEachIndexed {index,item ->launch {
                            if(motion)kotlinx.coroutines.delay(index*40L)
                            item.animateTo(1f,if(motion)SceneMotion.navigationSpring() else snap())
                        }}}}
                        haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        var target: Destination?=null
                        var pin: Offset?=null
                        try {
                            val completed=drag(down.id) { change ->
                                val position=(change.position-Offset(size.width/2f,size.height/2f))/density
                                val hit=choices.indices.firstOrNull { (position-offsets[it]).getDistance()<=34f }
                                val pull=target?.let {choices.indexOf(it)}?.takeIf {it>=0}?.let {index ->val direction=offsets[index];position.getDistance()>direction.getDistance()+64f && (position.x*direction.x+position.y*direction.y)/(position.getDistance()*direction.getDistance())>.92f}==true
                                target=if(pull)target else hit?.let {choices[it]}
                                pin=if(pull)position else null
                                pinReady=pull
                                pulledPosition=if(pull)position else null
                                if(highlighted!=target&&target!=null)haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                highlighted=target
                                change.consume()
                            }
                            finish(if(completed)target else null,if(completed)pin else null)
                        } catch(cancelled: CancellationException) {finish(null,null);throw cancelled}
                    }
                }
            }) {
            Box(contentAlignment=Alignment.Center) {
                MainCircleMorphIcon(circleKey,destination.symbol,readerIcon,readerMode||expansion?.active==true,
                    tint=mainColors.foreground)
            }
        }
    }
}

@Composable
private fun BoxScope.ShortcutCircles(destination: Destination,onNavigate: (Destination)->Unit,width: Dp,height: Dp,readerMode: Boolean) {
    val actions=LocalShortcuts.current
    val scope=rememberCoroutineScope()
    val motion=LocalMotionEnabled.current
    val placement=LocalNavigationPlacement.current
    val density=LocalDensity.current.density
    val inset=WindowInsets.navigationBars.getBottom(LocalDensity.current)/density
    val usableWidth=(width-72.dp).coerceAtLeast(1.dp)
    val usableHeight=(height-128.dp-inset.dp).coerceAtLeast(1.dp)
    actions.items.forEach {item ->
        val target=Destination.entries.find {it.route==item.route}?:return@forEach
        var drag by remember(item) {mutableStateOf(Offset.Zero)}
        val x=16.dp+usableWidth*item.x+drag.x.dp
        val y=16.dp+usableHeight*item.y+drag.y.dp
        var reconnect by remember {mutableStateOf(false)}
        val colors=animatedCircleColors(if(reconnect)destination.circleColors else target.circleColors,"shortcut-"+target.route)
        Surface(shape=CircleShape,color=colors.background,contentColor=colors.foreground,shadowElevation=3.dp,
            modifier=Modifier.offset(x.coerceIn(8.dp,width-60.dp),y.coerceIn(8.dp,height-80.dp-inset.dp)).size(52.dp).readingChromeFade().testTag("shortcut-"+target.route)
                .semantics {contentDescription=target.label+"快捷入口";stateDescription=if(reconnect)"松手并回主圆" else "长按拖动，拖回主圆可收起";customActions=listOf(CustomAccessibilityAction("收回快捷入口") {actions.remove(item.route);true})}
                .pointerInput(item,placement,width,height) {
                    detectDragGesturesAfterLongPress(onDragCancel={drag=Offset.Zero;reconnect=false},onDragEnd={
                        if(reconnect) {
                            val from=drag
                            val to=Offset(width.value-48f-(width.value-96f)*(1f-placement.x)-(42f+usableWidth.value*item.x),height.value-inset-46f-140f*placement.y-(42f+usableHeight.value*item.y))
                            scope.launch {animate(0f,1f,animationSpec=tween(if(motion)180 else 0,easing=SoEase)) {value,_ ->drag=androidx.compose.ui.geometry.lerp(from,to,value)};actions.remove(item.route)}
                        } else {actions.save(item.copy(x=(item.x+drag.x/usableWidth.value).coerceIn(0f,1f),y=(item.y+drag.y/usableHeight.value).coerceIn(0f,1f)));drag=Offset.Zero;reconnect=false}
                    }) {change,amount ->
                        change.consume();drag+=amount/density
                        val center=Offset(42f+usableWidth.value*item.x+drag.x,42f+usableHeight.value*item.y+drag.y)
                        val main=Offset(width.value-48f-(width.value-96f)*(1f-placement.x),height.value-inset-46f-140f*placement.y)
                        reconnect=(center-main).getDistance()<52f
                    }
                }.clickable {if(readerMode||target!=destination)onNavigate(target)}) {
            Box(contentAlignment=Alignment.Center) {SoIcon(target.symbol,tint=colors.foreground)}
        }
    }
}
