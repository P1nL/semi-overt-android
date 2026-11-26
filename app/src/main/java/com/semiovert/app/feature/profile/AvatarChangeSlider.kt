package com.semiovert.app.feature.profile

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.innerShadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.semiovert.app.core.designsystem.LocalMotionEnabled
import com.semiovert.app.core.designsystem.ProfileAvatar
import com.semiovert.app.core.designsystem.SoSceneEase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val avatarSliderLabel="更换头像"
private const val avatarThumbSize=66
private val avatarThumbInset=5.dp
private val avatarSliderHeight=avatarThumbSize.dp+avatarThumbInset*2
private val avatarTrailStart=Color(0xFF1D3C94)
private val avatarTrailEnd=Color(0xFF174BD6)
private val avatarCollapseEase=Easing {fraction ->
    val remaining=fraction-1f
    1f+remaining*remaining*(2.4f*remaining+1.4f)
}

@Composable
internal fun AvatarChangeSlider(
    variant: Int,photo: ImageBitmap?,enabled: Boolean,replacing: Boolean,onSlide: ()->Unit,onCollapsed: ()->Unit,
) {
    val motion=LocalMotionEnabled.current
    val scope=rememberCoroutineScope()
    val slide by rememberUpdatedState(onSlide)
    val ready by rememberUpdatedState(onCollapsed)
    val canConfirm by rememberUpdatedState(enabled&&!replacing)
    var offset by remember {mutableFloatStateOf(0f)}
    var dragging by remember {mutableStateOf(false)}
    var settle by remember {mutableStateOf<Job?>(null)}
    val transition=updateTransition(replacing,label="avatar-slider-shape")
    val collapse by transition.animateFloat(transitionSpec={
        if(motion)tween(if(targetState)550 else 400,easing=if(targetState)avatarCollapseEase else SoSceneEase) else snap()
    },label="avatar-slider-compact") {if(it)1f else 0f}
    val pressScale by animateFloatAsState(if(motion&&dragging).95f else 1f,
        if(!motion)snap() else if(dragging)tween(120) else spring(.60f,520f),label="avatar-slider-press")

    LaunchedEffect(replacing) {
        if(replacing) {
            settle?.cancel()
            dragging=false
            offset=0f
            snapshotFlow {transition.currentState&&!transition.isRunning}.first {it}
            ready()
        }
    }

    fun settleThumb(end: Float,velocity: Float,confirm: Boolean) {
        dragging=false
        settle?.cancel()
        if(!motion||(confirm&&offset>=end)) {
            offset=if(confirm&&canConfirm)end else 0f
            if(confirm&&canConfirm)slide()
            return
        }
        settle=scope.launch {
            val thumb=Animatable(offset,visibilityThreshold=.3f)
            if(confirm)thumb.updateBounds(upperBound=end)
            thumb.animateTo(if(confirm)end*1.25f else 0f,
                spring(if(confirm).68f else .53f,if(confirm)260f else 520f,visibilityThreshold=.3f),
                initialVelocity=if(confirm)velocity.coerceAtLeast(300f) else velocity.coerceAtMost(0f)) {
                offset=value
            }
            offset=if(confirm&&canConfirm)end else 0f
            if(confirm&&canConfirm)slide()
        }
    }

    val dark=MaterialTheme.colorScheme.surface.luminance()<.5f
    val density=LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth().padding(top=8.dp).height(avatarSliderHeight),
        contentAlignment=Alignment.Center) {
        val compact=collapse.coerceIn(0f,1f)
        val diameter=minOf(maxWidth,avatarSliderHeight)
        val width=lerp(maxWidth,diameter,collapse.coerceAtLeast(0f)).coerceAtLeast(diameter*.85f)
        val fullTravel=(maxWidth-avatarSliderHeight).value.coerceAtLeast(0f)
        val travel=(width-avatarSliderHeight).value.coerceAtLeast(0f)
        val interactive=enabled&&!replacing&&compact==0f
        fun travelProgress()=(offset/fullTravel.coerceAtLeast(1f)).coerceIn(0f,1f)
        Box(Modifier.width(width).fillMaxHeight().testTag("avatar-change-track")
            .semantics(mergeDescendants=true) {
                contentDescription=avatarSliderLabel
                role=Role.Button
                if(interactive)onClick(label=avatarSliderLabel) {settleThumb(fullTravel,900f,true);true}
                else disabled()
            }
            .onKeyEvent {event->
                val activate=event.key==Key.Enter||event.key==Key.Spacebar||event.key==Key.DirectionCenter
                if(interactive&&activate&&event.type==KeyEventType.KeyUp) {
                    settleThumb(fullTravel,900f,true);true
                } else false
            }
            .focusable(interactive)) {
            AvatarSliderGroove(Modifier.matchParentSize(),dark,compact) {if(replacing)1f else maxOf(compact,travelProgress())}
            if(compact<.5f)Box(Modifier.align(AbsoluteAlignment.CenterLeft)
                .absoluteOffset {IntOffset((avatarThumbInset+(if(replacing)travel else offset.coerceIn(0f,travel)).dp).roundToPx(),0)}
                .size(avatarThumbSize.dp)
                .pointerInput(interactive,fullTravel,motion,density) {
                    if(!interactive||fullTravel==0f)return@pointerInput
                    val tracker=VelocityTracker()
                    var dragPosition=0f
                    detectDragGestures(
                        orientationLock=Orientation.Horizontal,
                        onDragStart={down,_,_->
                            settle?.cancel()
                            dragging=true
                            dragPosition=if(offset<0f)offset*4f else offset
                            tracker.resetTracking()
                            tracker.addPointerInputChange(down)
                        },
                        onDragEnd={up->
                            tracker.addPointerInputChange(up)
                            val speed=tracker.calculateVelocity().x/density.density
                            val confirm=offset>=fullTravel*.9f||(offset>fullTravel*.25f&&speed>900f)
                            settleThumb(fullTravel,speed,confirm)
                        },
                        onDragCancel={settleThumb(fullTravel,0f,false)},
                    ) {change,delta->
                        tracker.addPointerInputChange(change)
                        dragPosition=(dragPosition+delta.x/density.density).coerceIn(if(motion)-72f else 0f,fullTravel)
                        offset=if(dragPosition<0f)dragPosition*.25f else dragPosition
                    }
                }
                .graphicsLayer {
                    val squash=if(motion&&!replacing)(-offset/60f).coerceIn(0f,.28f) else 0f
                    val scale=pressScale*(1f-compact*.4f)
                    transformOrigin=if(squash>0f)TransformOrigin(0f,.5f) else TransformOrigin.Center
                    alpha=1f-compact*2f
                    scaleX=scale*(1f-squash)
                    scaleY=scale*(1f+squash*.6f)
                }
                .dropShadow(CircleShape,Shadow(radius=5.dp,color=Color.Black.copy(alpha=if(dark).56f else .30f),
                    offset=DpOffset(0.dp,3.dp)))
                .border(1.dp,Brush.verticalGradient(.5f to Color.Transparent,
                    1f to Color.White.copy(alpha=if(dark).30f else .64f)),CircleShape)
                .testTag("edit-avatar-preview")) {
                ProfileAvatar(variant,size=avatarThumbSize,photo=photo,filterQuality=FilterQuality.Medium)
            }
        }
    }
}

@Composable
private fun AvatarSliderGroove(modifier: Modifier,dark: Boolean,compact: Float,progress: ()->Float) {
    val rim=Brush.verticalGradient(.5f to Color.Transparent,
        1f to Color.White.copy(alpha=if(dark).22f else .85f))
    val well=Brush.verticalGradient(0f to Color.Black.copy(alpha=if(dark).30f else .10f),
        .46f to Color.Transparent,1f to Color.White.copy(alpha=if(dark).12f else .40f))
    val grooveDepth=((1f-compact)*2f).coerceAtMost(1f)
    Box(modifier.border(1.dp,rim,CircleShape).padding(2.dp)) {
        Box(Modifier.matchParentSize().clip(CircleShape).background(well)
            .innerShadow(CircleShape,Shadow(radius=7.dp,
                color=Color.Black.copy(alpha=(if(dark).72f else .27f)*grooveDepth),offset=DpOffset(0.dp,3.dp)))
            .drawBehind {
                val amount=progress()
                if(amount>0f) {
                    val width=size.minDimension+(size.width-size.minDimension)*amount
                    val trail=Brush.horizontalGradient(listOf(avatarTrailStart,avatarTrailEnd),endX=width)
                    val trailAlpha=(amount*4f).coerceAtMost(1f)*(1f-.14f*grooveDepth)
                    drawRoundRect(trail,size=Size(width,size.height),cornerRadius=CornerRadius(size.minDimension/2),
                        alpha=trailAlpha)
                }
            })
    }
}
