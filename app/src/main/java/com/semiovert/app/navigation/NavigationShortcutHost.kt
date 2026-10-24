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
}
internal val LocalNavigationShortcutHost=staticCompositionLocalOf<NavigationShortcutHostState?> {null}
private data class ShortcutEntry(val item: NavigationShortcut,val target: Destination,val state: ShortcutDragState)
private data class ShortcutVisual(val entry: ShortcutEntry,val hover: Float,val stretch: Float,
    val radius: Float,val contactRadius: Float,val joined: Boolean,val neckProgress: Float)

/** Pointer coordinates stay at this fixed root; only the small draw layers move. */
@Composable
internal fun NavigationShortcutHost(destination: Destination?,onNavigate: (Destination)->Unit,enabled: Boolean,
    content: @Composable ()->Unit) {
    val host=remember {NavigationShortcutHostState()}
    val density=LocalDensity.current.density
    var origin by remember {mutableStateOf(Offset.Zero)}
    SharedTransitionLayout(Modifier.fillMaxSize()) {
        val fusion=remember(this) {ShortcutFusionOverlay(this)}
        CompositionLocalProvider(LocalNavigationShortcutHost provides host,LocalShortcutFusionOverlay provides fusion) {
            BoxWithConstraints(Modifier.fillMaxSize().onGloballyPositioned {origin=it.positionInRoot()}) {
                ShortcutLayer(destination,{host.beforeNavigate();onNavigate(it)},enabled,maxWidth,maxHeight,
                    (host.mainCenterInRoot-origin)/density,host.mainColors,fusion,content)
            }
        }
    }
}

@Composable
private fun ShortcutLayer(destination: Destination?,onNavigate: (Destination)->Unit,enabled: Boolean,
    width: Dp,height: Dp,mainCenter: Offset,mainColors: CircleColors,fusion: ShortcutFusionOverlay,
    content: @Composable ()->Unit) {
    val actions=LocalShortcuts.current
    val scope=rememberCoroutineScope()
    val motion=LocalMotionEnabled.current
    val density=LocalDensity.current.density
    val inset=WindowInsets.navigationBars.getBottom(LocalDensity.current)/density
    val viewport=remember(width,height,inset) {ShortcutViewport(width.value,height.value,inset)}
    val states=remember {mutableMapOf<String,ShortcutDragState>()}
    val entries=buildList {
        for(item in actions.items) {
            val target=Destination.entries.find {it.route==item.route}?:continue
            add(ShortcutEntry(item,target,states.getOrPut(item.route) {ShortcutDragState(viewport.center(item))}))
        }
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
    val canInteract by rememberUpdatedState(enabled)
    val activate: (Destination)->Unit={if(enabled&&it!=destination)onNavigate(it)}
    val currentActivate by rememberUpdatedState(activate)
    Box(Modifier.fillMaxSize().pointerInput(viewport,density,motion) {
        awaitEachGesture {
            val down=awaitFirstDown(requireUnconsumed=false,pass=PointerEventPass.Initial)
            val entry=if(canInteract)currentEntries.lastOrNull {
                !it.state.merging&&(down.position/density-it.state.center).getDistance()<=RadialNavigationGesture.HIT_RADIUS
            } else null
            if(entry==null)return@awaitEachGesture
            // Claim only shortcut hits; all other content keeps its original gestures.
            down.consume()
            awaitPointerEvent(PointerEventPass.Main)
            val held=awaitLongPressOrCancellation(down.id)
            if(held==null) {
                if(currentEvent.changes.any {it.id==down.id&&it.changedToUpIgnoreConsumed()})currentActivate(entry.target)
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
                LiquidShadowPart(small,radius,visual.stretch,LiquidCircleGeometry.bridge(main,28f*density,small,radius,
                    (28f+visual.contactRadius+ShortcutLiquidContact.HOLD_DISTANCE)*density,reveal=visual.neckProgress))
            }
            FusionShadow(main,parts)
        }
        for(visual in visuals)key(visual.entry.item.route) {
            val entry=visual.entry
            LaunchedEffect(entry.item.x,entry.item.y,viewport) {
                if(!entry.state.dragging&&!entry.state.merging)entry.state.center=viewport.center(entry.item)
            }
            ShortcutCircle(visual,mainCenter,mainColors,enabled,{currentActivate(entry.target)},{actions.remove(entry.item.route)})
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
    val stretch by animateFloatAsState(if(motion&&active)RadialNavigationGesture.mergeStretch(distance) else 0f,
        if(motion)RadialNavigationGesture.stretchSpring() else snap(),label="shortcut-liquid-pull")
    val smallRadius=26f*hover*state.absorption
    val contactRadius=LiquidCircleGeometry.facingRadius(smallRadius,stretch)
    val joined=contact.next(distance,28f,contactRadius,motion&&active)
    val neckProgress by animateFloatAsState(if(joined)1f else 0f,
        if(joined)RadialNavigationGesture.neckAppearMotion() else snap(),label="shortcut-neck-appear")
    SideEffect {contact.update(distance,28f,contactRadius,motion&&active)}
    return ShortcutVisual(entry,hover,stretch,smallRadius,contactRadius,joined,neckProgress)
}

/** All units are pixels; leave a real shadow outset instead of recording a tight circle-sized box. */
@Composable
private fun BoxScope.FusionShadow(main: Offset,parts: List<LiquidShadowPart>) {
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
        .size((bounds.width/density).dp,(bounds.height/density).dp).zIndex(.25f)
        .drawWithContent {clipPath(path,ClipOp.Difference) {this@drawWithContent.drawContent()}}
        .then(shadow).testTag("shortcut-fusion-shadow"))
}

@Composable
private fun BoxScope.ShortcutCircle(visual: ShortcutVisual,mainCenter: Offset,mainColors: CircleColors,enabled: Boolean,
    onClick: ()->Unit,onRemove: ()->Unit) {
    val entry=visual.entry
    val state=entry.state
    val target=entry.target
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
                if(bridge!=null)drawLiquidBridge(bridge,Brush.linearGradient(listOf(mainColors.background,target.circleColors.background),main,small))
            }
        }
    }
    Box(Modifier.size(52.dp).zIndex(1f).graphicsLayer {
        translationX=(state.center.x-26f)*density
        translationY=(state.center.y-26f)*density
        alpha=state.absorption
    }.testTag("shortcut-"+target.route).semantics {
        contentDescription=target.label+"快捷入口"
        stateDescription=if(active&&distance<=RadialNavigationGesture.MERGE_DISTANCE)"松手并回主圆" else "长按拖动，拖回主圆可收起"
        customActions=listOf(CustomAccessibilityAction("收回快捷入口") {onRemove();true})
    }.clickable(interactionSource=null,indication=null,enabled=enabled&&!state.merging,onClick=onClick),
        contentAlignment=Alignment.Center) {
        NavigationBubble(target.symbol,target.circleColors,visual.hover*state.absorption,visual.stretch,mainCenter-state.center,Modifier,
            shadowElevation=if(visual.joined)0.dp else 3.dp)
    }
}
