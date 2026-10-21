package com.semiovert.app.core.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.preferredFrameRate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.nestedscroll.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** All dismiss paths retain the sheet until the shared 400ms exit finishes. */
typealias DrawerClose = (afterClose: ()->Unit)->Unit

@Composable
fun SoBottomSheet(onDismissRequest: ()->Unit,content: @Composable ColumnScope.(DrawerClose)->Unit) {
    val duration=motionMillis(SceneMotion.DRAWER_MS)
    val progress=remember {Animatable(0f)}
    val scope=rememberCoroutineScope()
    val density=LocalDensity.current.density
    val currentDismiss by rememberUpdatedState(onDismissRequest)
    var closing by remember {mutableStateOf(false)}
    var drag by remember {mutableFloatStateOf(0f)}
    var sheetHeight by remember {mutableFloatStateOf(1f)}
    var motionJob by remember {mutableStateOf<Job?>(null)}
    val close: DrawerClose={after->
        if(!closing) {
            closing=true;motionJob?.cancel()
            motionJob=scope.launch {
                progress.snapTo((progress.value-drag/sheetHeight).coerceIn(0f,1f));drag=0f
                progress.animateTo(0f,tween(duration,easing=SoSceneEase))
                currentDismiss();after()
            }
        }
    }
    val settle: (Float)->Unit={velocity->
        if(!closing) {
        if(drag>48f*density||(drag>0f&&velocity>900f*density))close {} else {
            motionJob?.cancel();motionJob=scope.launch {
                progress.snapTo((progress.value-drag/sheetHeight).coerceIn(0f,1f));drag=0f
                progress.animateTo(1f,tween(duration,easing=SoSceneEase))
            }
        }
        }
    }
    val nestedScroll=remember(duration,closing,density) {object: NestedScrollConnection {
        override fun onPreScroll(available: Offset,source: NestedScrollSource): Offset {
            if(closing||source!=NestedScrollSource.UserInput||drag<=0f||available.y>=0f)return Offset.Zero
            val consumed=maxOf(available.y,-drag);drag+=consumed;return Offset(0f,consumed)
        }
        override fun onPostScroll(consumed: Offset,available: Offset,source: NestedScrollSource): Offset {
            if(closing||source!=NestedScrollSource.UserInput||available.y<=0f)return Offset.Zero
            val pulled=minOf(available.y,sheetHeight-drag);drag+=pulled;return Offset(0f,pulled)
        }
        override suspend fun onPreFling(available: Velocity): Velocity {
            if(closing||drag<=0f)return Velocity.Zero
            settle(available.y);return Velocity(0f,available.y)
        }
    }}
    LaunchedEffect(Unit) {motionJob=scope.launch {progress.animateTo(1f,tween(duration,easing=SoSceneEase))}}
    Dialog(onDismissRequest={close {}},properties=DialogProperties(usePlatformDefaultWidth=false,
        decorFitsSystemWindows=false,dismissOnClickOutside=false)) {
        Box(Modifier.fillMaxSize().preferredFrameRate(MotionFramePolicy.TARGET_FPS).testTag("drawer-root")) {
            Box(Modifier.fillMaxSize().graphicsLayer {alpha=(progress.value-drag/sheetHeight).coerceIn(0f,1f)}
                .background(Color.Black.copy(alpha=.32f))
                .clickable(remember {MutableInteractionSource()},indication=null,onClickLabel="关闭抽屉") {close {}})
            BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top+WindowInsetsSides.Horizontal)).imePadding()) {
                Surface(Modifier.align(Alignment.BottomCenter).fillMaxWidth().heightIn(max=maxHeight)
                    .nestedScroll(nestedScroll)
                    .onSizeChanged {sheetHeight=it.height.toFloat().coerceAtLeast(1f)}
                    .graphicsLayer {translationY=size.height*(1f-progress.value)+drag}
                    .testTag("drawer-panel").semantics {paneTitle="抽屉";dismiss {close {};true}},
                    shape=RoundedCornerShape(topStart=28.dp,topEnd=28.dp),color=MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
                        Box(Modifier.fillMaxWidth().height(40.dp).semantics {contentDescription="下拉关闭抽屉";dismiss {close {};true}}
                            .pointerInput(closing,duration) {
                                if(!closing)detectVerticalDragGestures(
                                    onDragEnd={settle(0f)},
                                    onDragCancel={settle(0f)},
                                    onVerticalDrag={change,amount->change.consume();drag=(drag+amount).coerceIn(0f,sheetHeight)})
                            },contentAlignment=Alignment.Center) {
                            Box(Modifier.size(32.dp,4.dp).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=.4f),RoundedCornerShape(2.dp)))
                        }
                        Column(Modifier.weight(1f,fill=false).verticalScroll(rememberScrollState())) {content(close)}
                    }
                }
            }
        }
    }
}
