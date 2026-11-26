package com.semiovert.app.navigation

import android.os.Build
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import com.semiovert.app.core.designsystem.*
import com.semiovert.app.core.designsystem.wave.LocalShortcutFusionOverlay
import com.semiovert.app.core.designsystem.wave.ShortcutFusionOverlay
import com.semiovert.app.core.model.NavigationShortcut
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@Stable internal class NavigationShortcutHostState {
    var mainCenterInRoot by mutableStateOf(Offset.Zero)
    var mainColors by mutableStateOf(Destination.HOME.circleColors)
    var beforeNavigate: ()->Unit={}
    var onCategoryReselect: (() -> Unit)?=null
    var beginDraftDrag: (HomeDraftAction,Offset,Offset,Float)->DraftShortcutDrag?={_,_,_,_->null}
    var draftMenu: DockedDraftMenu?=null
}
internal class DraftShortcutDrag(val move: (Offset)->Unit,val finish: (Boolean)->Unit)
internal data class DockedDraftCircle(val action: HomeDraftAction,val centerInRoot: Offset,val stretch: State<Float>)
internal class DockedDraftMenu(val owner: Any,val items: List<DockedDraftCircle>,
    val press: (HomeDraftAction?)->Unit,val pull: (HomeDraftAction,Offset,Float)->Unit,val detach: (HomeDraftAction)->Unit,
    val tap: (HomeDraftAction)->Unit,val finish: (Boolean)->Unit)
internal val LocalNavigationShortcutHost=staticCompositionLocalOf<NavigationShortcutHostState?> {null}
private data class ShortcutEntry(val item: NavigationShortcut,val target: Destination?,val draft: HomeDraftAction?,val state: ShortcutDragState,
    val detachProgress: Animatable<Float,AnimationVector1D>?=null,val detachStretch: Float=0f) {
    val colors: CircleColors get()=target?.circleColors?:checkNotNull(draft).colors
    val label: String get()=target?.label?:checkNotNull(draft).label
}
private data class ShortcutVisual(val entry: ShortcutEntry,val hover: Float,val stretch: Float,val elasticStretch: Float,
    val radius: Float,val contactRadius: Float,val joined: Boolean,val neckProgress: Float)

/** Pointer coordinates stay at this fixed root; only the small draw layers move. */
@Composable
internal fun NavigationShortcutHost(destination: Destination?,onNavigate: (Destination)->Unit,enabled: Boolean,
    content: @Composable ()->Unit) {
    val host=remember {NavigationShortcutHostState()}
    val density=LocalDensity.current.density
    var origin by remember {mutableStateOf(Offset.Zero)}
    SharedTransitionLayout(Modifier.fillMaxSize()
        .then(if(LocalDraftBox.current?.active==true)Modifier.clearAndSetSemantics {} else Modifier)) {
        val fusion=remember(this) {ShortcutFusionOverlay(this)}
        CompositionLocalProvider(LocalNavigationShortcutHost provides host,LocalShortcutFusionOverlay provides fusion) {
            BoxWithConstraints(Modifier.fillMaxSize().onGloballyPositioned {origin=it.positionInRoot()}) {
                ShortcutLayer(destination,{host.beforeNavigate();onNavigate(it)},enabled,maxWidth,maxHeight,
                    (host.mainCenterInRoot-origin)/density,host.mainColors,fusion,host,origin,content)
            }
        }
    }
}

@Composable
private fun ShortcutLayer(destination: Destination?,onNavigate: (Destination)->Unit,enabled: Boolean,
    width: Dp,height: Dp,mainCenter: Offset,mainColors: CircleColors,fusion: ShortcutFusionOverlay,
    host: NavigationShortcutHostState,origin: Offset,
    content: @Composable ()->Unit) {
    val actions=LocalShortcuts.current
    val draftAction=LocalHomeDraftAction.current
    val draftBox=LocalDraftBox.current
    val scope=rememberCoroutineScope()
    val motion=LocalMotionEnabled.current
    val density=LocalDensity.current.density
    val haptics=LocalHapticFeedback.current
    val inset=WindowInsets.navigationBars.getBottom(LocalDensity.current)/density
    val viewport=remember(width,height,inset) {ShortcutViewport(width.value,height.value,inset)}
    val states=remember {mutableMapOf<String,ShortcutDragState>()}
    var pendingDraft by remember {mutableStateOf<ShortcutEntry?>(null)}
    val entries=buildList {
        for(item in actions.items) {
            if(item.route==pendingDraft?.item?.route)continue
            val target=Destination.entries.find {it.route==item.route}
            val draft=HomeDraftAction.entries.find {it.route==item.route}
            if(target==null&&draft==null)continue
            add(ShortcutEntry(item,target,draft,states.getOrPut(item.route) {ShortcutDragState(viewport.center(item))}))
        }
        pendingDraft?.let {add(it)}
    }
    val visuals=entries.map {entry->key(entry.item.route) {shortcutVisual(entry,mainCenter)}}
    val fusionActive=enabled&&entries.any {it.state.dragging||it.state.merging}
    val joined=visuals.filter {it.joined}
    SideEffect {
        states.keys.retainAll(actions.items.map {it.route}.toSet())
        fusion.active=fusionActive
        fusion.joinedShadow=joined.isNotEmpty()
    }
    val currentEntries by rememberUpdatedState(entries)
    val currentActions by rememberUpdatedState(actions)
    val latestMain by rememberUpdatedState(mainCenter)
    val latestOrigin by rememberUpdatedState(origin)
    val canInteract by rememberUpdatedState(enabled)
    val activate: (ShortcutEntry)->Unit={entry->if(enabled) {
        val target=entry.target
        if(target!=null) {
            if(target!=destination)onNavigate(target)
            else if(target==Destination.CATEGORY)host.onCategoryReselect?.invoke()
        } else entry.draft?.let {draft->
            if(draft==HomeDraftAction.BOX&&draftBox!=null)draftBox.open(origin+entry.state.center*density)
            else host.draftMenu?.tap?.invoke(draft) ?: draftAction(draft)
        }
    }}
    val currentActivate by rememberUpdatedState(activate)
    LaunchedEffect(enabled) {if(!enabled)pendingDraft=null}
    LaunchedEffect(actions.items,pendingDraft) {
        val pending=pendingDraft
        // Keep one visual alive until the persisted shortcut has reached this composition.
        if(pending!=null&&!pending.state.dragging&&!pending.state.merging&&actions.items.any {it.route==pending.item.route})pendingDraft=null
    }
    SideEffect {
        host.beginDraftDrag={action,centerInRoot,pointerInRoot,initialStretch ->
            if(!enabled||pendingDraft!=null||actions.items.any {it.route==action.route})null else {
                val dragState=ShortcutDragState(viewport.constrain((centerInRoot-origin)/density))
                dragState.start((pointerInRoot-origin)/density)
                val detachOrigin=dragState.center
                var pointerTarget=detachOrigin
                val detachProgress=Animatable(if(motion)0f else 1f)
                val entry=ShortcutEntry(viewport.shortcut(action.route,dragState.center),null,action,dragState,detachProgress,initialStretch)
                pendingDraft=entry
                // Reuse the radial menu's one-shot handoff clock, not a new animation per pointer event.
                val detachJob=scope.launch {
                    detachProgress.animateTo(1f,if(motion)RadialNavigationGesture.detachMotion() else snap()) {
                        dragState.center=androidx.compose.ui.geometry.lerp(detachOrigin,pointerTarget,value)
                    }
                }
                var finished=false
                DraftShortcutDrag(
                    move={pointer->if(!finished&&pendingDraft===entry) {
                        dragState.moveTo((pointer-origin)/density,viewport)
                        pointerTarget=dragState.center
                        dragState.center=androidx.compose.ui.geometry.lerp(detachOrigin,pointerTarget,detachProgress.value)
                    }},
                    finish={cancelled ->
                        if(!finished&&pendingDraft===entry) {
                            finished=true
                            if(cancelled) {
                                detachJob.cancel()
                                dragState.release()
                                pendingDraft=null
                            } else scope.launch {
                                detachJob.join()
                                dragState.release()
                                if(RadialNavigationGesture.canMerge(dragState.center-latestMain)) {
                                    dragState.beginMerge()
                                    val from=dragState.center
                                    val to=latestMain
                                    animate(0f,1f,animationSpec=if(motion)RadialNavigationGesture.mergeMotion() else snap()) {value,_ ->
                                        dragState.absorb(from,to,value)
                                    }
                                    if(pendingDraft===entry)pendingDraft=null
                                } else currentActions.save(viewport.shortcut(action.route,dragState.center))
                            }
                        }
                    },
                )
            }
        }
    }
    Box(Modifier.fillMaxSize().pointerInput(viewport,density,motion) {
        awaitEachGesture {
            val down=awaitFirstDown(requireUnconsumed=false,pass=PointerEventPass.Initial)
            val entry=if(canInteract)currentEntries.lastOrNull {
                !it.state.merging&&(down.position/density-it.state.center).getDistance()<=RadialNavigationGesture.HIT_RADIUS
            } else null
            if(entry==null) {
                val menu=host.draftMenu?.takeIf {canInteract}
                val pointerInRoot=latestOrigin+down.position
                val item=menu?.items?.minByOrNull {(it.centerInRoot-pointerInRoot).getDistance()}
                    ?.takeIf {(it.centerInRoot-pointerInRoot).getDistance()<=RadialNavigationGesture.HIT_RADIUS*density}
                if(menu==null||item==null)return@awaitEachGesture
                // Keep the entire mouse press on this root when the docked visual is replaced.
                down.consume()
                awaitPointerEvent(PointerEventPass.Main)
                menu.press(item.action)
                var controller: DraftShortcutDrag?=null
                var completed=false
                try {
                    val held=awaitLongPressOrCancellation(down.id)
                    if(held==null) {
                        if(currentEvent.changes.any {it.id==down.id&&it.changedToUpIgnoreConsumed()})menu.tap(item.action)
                    } else {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        // A hold only highlights the docked circle; detach after intentional movement.
                        val dragStart=awaitTouchSlopOrCancellation(down.id) {change,_->change.consume()}
                        if(dragStart!=null) {
                            val currentItem=host.draftMenu?.takeIf {it.owner===menu.owner}
                                ?.items?.firstOrNull {it.action==item.action}?:item
                            val mainInRoot=latestOrigin+latestMain*density
                            val rest=(currentItem.centerInRoot-mainInRoot)/density
                            val pullOrigin=(latestOrigin+held.position-mainInRoot)/density
                            var attachedCenter=currentItem.centerInRoot
                            fun move(pointerInRoot: Offset) {
                                val pointer=(pointerInRoot-mainInRoot)/density
                                if(controller==null&&RadialNavigationGesture.canPin(pointer)) {
                                    controller=host.beginDraftDrag(item.action,attachedCenter,attachedCenter,currentItem.stretch.value)
                                    if(controller!=null)menu.detach(item.action)
                                }
                                val active=controller
                                if(active!=null)active.move(pointerInRoot) else {
                                    attachedCenter=mainInRoot+RadialNavigationGesture.attachedCenter(pointer,pullOrigin,rest)*density
                                    val attachedStretch=.8f*RadialNavigationGesture.pullProgress(pointer,pullOrigin,rest)
                                    menu.pull(item.action,attachedCenter,attachedStretch)
                                }
                            }
                            move(latestOrigin+dragStart.position)
                            val released=drag(down.id) {change ->
                                change.consume()
                                move(latestOrigin+change.position)
                            }
                            if(released)currentEvent.changes.firstOrNull {it.id==down.id}?.let {move(latestOrigin+it.position)}
                            controller?.finish(!released)
                            completed=released&&controller!=null
                        }
                    }
                } catch(cancelled: CancellationException) {
                    controller?.finish(true)
                    throw cancelled
                } finally {
                    menu.finish(completed)
                }
                return@awaitEachGesture
            }
            // Claim only shortcut hits; all other content keeps its original gestures.
            down.consume()
            awaitPointerEvent(PointerEventPass.Main)
            val held=awaitLongPressOrCancellation(down.id)
            if(held==null) {
                if(currentEvent.changes.any {it.id==down.id&&it.changedToUpIgnoreConsumed()})currentActivate(entry)
            } else {
                val state=entry.state
                state.start(down.position/density)
                state.moveTo(held.position/density,viewport)
                try {
                    val completed=drag(down.id) {change ->
                        change.consume()
                        state.moveTo(change.position/density,viewport)
                    }
                    if(!completed)state.cancel(viewport.center(entry.item)) else {
                        currentEvent.changes.firstOrNull {it.id==down.id}?.let {state.moveTo(it.position/density,viewport)}
                        state.release()
                        if(RadialNavigationGesture.canMerge(state.center-latestMain)) {
                            state.beginMerge()
                            val from=state.center
                            val to=latestMain
                            scope.launch {
                                animate(0f,1f,animationSpec=if(motion)RadialNavigationGesture.mergeMotion() else snap()) {value,_ ->
                                    state.absorb(from,to,value)
                                }
                                currentActions.remove(entry.item.route)
                            }
                        } else {
                            val saved=viewport.shortcut(entry.item.route,state.center)
                            state.center=viewport.center(saved)
                            currentActions.save(saved)
                        }
                    }
                } catch(cancelled: CancellationException) {
                    state.cancel(viewport.center(entry.item))
                    throw cancelled
                }
            }
        }
    }) {
        Box(Modifier.fillMaxSize()) {content()}
        if(joined.isNotEmpty()) {
            val main=mainCenter*density
            val parts=joined.map {visual->
                val small=visual.entry.state.center*density
                val radius=visual.radius*density
                LiquidShadowPart(small,radius,visual.stretch,visual.elasticStretch,LiquidCircleGeometry.bridge(main,28f*density,small,radius,
                    (28f+visual.contactRadius+ShortcutLiquidContact.HOLD_DISTANCE)*density,reveal=visual.neckProgress))
            }
            FusionShadow(main,parts)
        }
        for(visual in visuals)key(visual.entry.item.route) {
            val entry=visual.entry
            LaunchedEffect(entry.item.x,entry.item.y,viewport) {
                if(!entry.state.dragging&&!entry.state.merging)entry.state.center=viewport.center(entry.item)
            }
            val clickLabel=if(destination==Destination.CATEGORY&&entry.target==destination)"切换阅读时长" else null
            ShortcutCircle(visual,mainCenter,mainColors,enabled,clickLabel,{currentActivate(entry)},{actions.remove(entry.item.route)})
        }
    }
}

@Composable
private fun shortcutVisual(entry: ShortcutEntry,mainCenter: Offset): ShortcutVisual {
    val state=entry.state
    val motion=LocalMotionEnabled.current
    val contact=remember {ShortcutLiquidContact()}
    val active=state.dragging||state.merging
    val distance=(state.center-mainCenter).getDistance()
    val hover by animateFloatAsState(if(state.dragging)RadialNavigationGesture.SELECTED_SCALE else 1f,
        if(motion)RadialNavigationGesture.scaleSpring() else snap(),label="shortcut-hold")
    val detach=entry.detachProgress?.value?:1f
    val elasticStretch=if(motion&&active)RadialNavigationGesture.detachRecoil(entry.detachStretch,detach) else 0f
    val mergeStretch by animateFloatAsState(if(motion&&active)RadialNavigationGesture.mergeStretch(distance)*detach else 0f,
        if(motion)RadialNavigationGesture.stretchSpring() else snap(),label="shortcut-liquid-return")
    val stretch=elasticStretch+mergeStretch
    val smallRadius=NavigationCircleSize.value/2f*hover*state.absorption
    val contactRadius=LiquidCircleGeometry.facingRadius(smallRadius,stretch)
    val joined=contact.next(distance,28f,contactRadius,motion&&active)
    val neckProgress by animateFloatAsState(if(joined)1f else 0f,
        if(joined)RadialNavigationGesture.neckAppearMotion() else snap(),label="shortcut-neck-appear")
    SideEffect {contact.update(distance,28f,contactRadius,motion&&active)}
    return ShortcutVisual(entry,hover,stretch,elasticStretch,smallRadius,contactRadius,joined,neckProgress)
}

/** All units are pixels; leave a real shadow outset instead of recording a tight circle-sized box. */
@Composable
internal fun BoxScope.FusionShadow(main: Offset,parts: List<LiquidShadowPart>,tag: String="shortcut-fusion-shadow") {
    val density=LocalDensity.current.density
    val path=liquidFusionOutline(main,28f*density,parts)
    val bounds=path.getBounds().inflate(16f*density)
    path.translate(-bounds.topLeft)
    val shape=object: Shape {
        override fun createOutline(size: Size,layoutDirection: LayoutDirection,density: Density)=Outline.Generic(path)
    }
    val shadow=if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.Q)Modifier.shadow(6.dp,shape,clip=false)
        else Modifier.dropShadow(shape,Shadow(radius=6.dp,color=Color.Black.copy(alpha=.22f),offset=DpOffset(0.dp,3.dp)))
    Box(Modifier.offset {IntOffset(bounds.left.roundToInt(),bounds.top.roundToInt())}
        .wrapContentSize(AbsoluteAlignment.TopLeft,unbounded=true)
        .size((bounds.width/density).dp,(bounds.height/density).dp).zIndex(.25f)
        .drawWithContent {clipPath(path,ClipOp.Difference) {this@drawWithContent.drawContent()}}
        .then(shadow).testTag(tag))
}

@Composable
private fun BoxScope.ShortcutCircle(visual: ShortcutVisual,mainCenter: Offset,mainColors: CircleColors,enabled: Boolean,
    onClickLabel: String?,onClick: ()->Unit,onRemove: ()->Unit) {
    val entry=visual.entry
    val state=entry.state
    val hidden=entry.draft==HomeDraftAction.BOX&&LocalDraftBox.current?.active==true
    val density=LocalDensity.current.density
    val motion=LocalMotionEnabled.current
    val active=state.dragging||state.merging
    val distance=(state.center-mainCenter).getDistance()
    val smallRadius=visual.radius
    val contactRadius=visual.contactRadius
    if(motion&&active&&distance<28f+contactRadius+ShortcutLiquidContact.HOLD_DISTANCE) {
        val origin=Offset(minOf(mainCenter.x,state.center.x)-48f,minOf(mainCenter.y,state.center.y)-48f)
        val bounds=Offset(abs(mainCenter.x-state.center.x)+96f,abs(mainCenter.y-state.center.y)+96f)
        Canvas(Modifier.offset {IntOffset((origin.x*density).roundToInt(),(origin.y*density).roundToInt())}
            .size(bounds.x.dp,bounds.y.dp).zIndex(.5f).graphicsLayer {alpha=state.absorption}) {
            if(visual.joined) {
                val main=(mainCenter-origin)*density
                val small=(state.center-origin)*density
                val bridge=LiquidCircleGeometry.bridge(main,28f*density,small,smallRadius*density,
                    (28f+contactRadius+ShortcutLiquidContact.HOLD_DISTANCE)*density,reveal=visual.neckProgress)
                if(bridge!=null)drawLiquidBridge(bridge,Brush.linearGradient(listOf(mainColors.background,entry.colors.background),main,small))
            }
        }
    }
    Box(Modifier.size(NavigationCircleSize).zIndex(1f).graphicsLayer {
        translationX=(state.center.x-NavigationCircleSize.value/2f)*density
        translationY=(state.center.y-NavigationCircleSize.value/2f)*density
        alpha=if(hidden)0f else state.absorption
    }.testTag("shortcut-"+entry.item.route).semantics {
        contentDescription=entry.label+"快捷入口"
        stateDescription=if(active&&distance<=RadialNavigationGesture.MERGE_DISTANCE)"松手并回主圆" else "长按拖动，拖回主圆可收起"
        customActions=listOf(CustomAccessibilityAction("收回快捷入口") {onRemove();true})
    }.clickable(interactionSource=null,indication=null,enabled=enabled&&!state.merging,onClickLabel=onClickLabel,onClick=onClick),
        contentAlignment=Alignment.Center) {
        NavigationBubble(entry.colors,visual.hover*state.absorption,visual.stretch,mainCenter-state.center,Modifier,
            shadowElevation=if(visual.joined)0.dp else 3.dp,elasticStretch=visual.elasticStretch) {
            val page=entry.target
            if(page!=null)SoIcon(page.symbol,modifier=Modifier.size(NavigationCircleIconSize),tint=entry.colors.foreground)
            else HomeDraftIcon(checkNotNull(entry.draft))
        }
    }
}
