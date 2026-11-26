package com.semiovert.app.feature.account

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.semiovert.app.core.designsystem.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Compose adaptation of the supplied Animorio animation; see animorio.com/en/license/.
private data class DotViewport(val bounds: Rect=Rect.Zero,val centers: List<Float> = emptyList())
private class DotMotion {
    val arrival=Animatable(0f)
    val shake=Animatable(0f)
}
private class StitchMotion {
    val length=Animatable(0f)
    val swing=Animatable(0f)
    val tug=Animatable(0f)
}

@Composable
internal fun StitchedPasswordField(
    label: String,
    value: String,
    onValueChange: (String)->Unit,
    error: String?=null,
    tag: String,
    modifier: Modifier=Modifier,
) {
    PasswordDotField(label,value,onValueChange,error,tag,modifier=modifier)
}

@Composable
internal fun StitchedPasswordPair(
    value: String,
    onValueChange: (String)->Unit,
    confirmation: String,
    onConfirmationChange: (String)->Unit,
    label: String,
    confirmationLabel: String,
    error: String?,
    confirmationError: String?,
    tag: String,
    confirmationTag: String,
    modifier: Modifier=Modifier,
    showConfirmation: Boolean=true,
) {
    val motion=LocalMotionEnabled.current
    val complete=showConfirmation&&value.isNotEmpty()&&value==confirmation
    val confirmationStatus=when {
        confirmation.isEmpty()->"再次输入密码"
        confirmation.indices.any {it<value.length&&confirmation[it]!=value[it]}->"密码不一致"
        confirmation.length>value.length->"长度超出"
        complete->"密码一致"
        else->"继续输入"
    }
    val colors=MaterialTheme.colorScheme
    var upper by remember {mutableStateOf(DotViewport())}
    var lower by remember {mutableStateOf(DotViewport())}
    var origin by remember {mutableFloatStateOf(0f)}
    var upperHidden by remember {mutableStateOf(true)}
    var lowerHidden by remember {mutableStateOf(true)}
    val stitches=(0 until if(showConfirmation)maxOf(value.length,confirmation.length) else 0).map {index ->
        key(index) {
            val stitch=remember {StitchMotion()}
            val a=value.getOrNull(index)
            val b=confirmation.getOrNull(index)
            LaunchedEffect(a,b,motion) {
                val length=when {a==null||b==null->0f;a==b->1f;else->.46f}
                if(!motion) {
                    stitch.length.snapTo(length);stitch.swing.snapTo(0f);stitch.tug.snapTo(0f)
                } else coroutineScope {
                    launch {
                        stitch.length.snapTo(0f)
                        delay(120)
                        stitch.length.animateTo(length,tween(320,easing=SoEase))
                    }
                    launch {
                        stitch.swing.snapTo(0f)
                        if(a!=null&&b!=null&&a!=b)stitch.swing.animateTo(0f,keyframes {
                            durationMillis=1100
                            0f at 0;26f at 180;-14f at 380;7f at 580;-3f at 800;0f at 1100
                        })
                    }
                    launch {
                        stitch.tug.snapTo(0f)
                        if(a!=null&&a==b) {
                            delay(160)
                            stitch.tug.animateTo(1f,tween(100))
                            stitch.tug.animateTo(0f,spring(dampingRatio=.45f,stiffness=200f))
                        }
                    }
                }
            }
            stitch
        }
    }
    Column(modifier.fillMaxWidth().onGloballyPositioned {origin=it.boundsInRoot().left}) {
        // The primary editor stays mounted while confirmation expands below it.
        PasswordDotField(label,value,onValueChange,error,tag,
            stitches=stitches,onViewport={upper=it},onHiddenChange={upperHidden=it})
        AnimatedVisibility(showConfirmation,modifier=Modifier.stitchedRevealClip(),
            enter=expandVertically(tween(if(motion)360 else 0,easing=SoEase),expandFrom=Alignment.Top,clip=false)+
                fadeIn(tween(if(motion)180 else 0)),
            exit=shrinkVertically(tween(if(motion)280 else 0,easing=SoEase),shrinkTowards=Alignment.Top,clip=false)+
                fadeOut(tween(if(motion)200 else 0))) {
        Column {
        Box(Modifier.fillMaxWidth().height(18.dp).clipToBounds()) {
            stitches.forEachIndexed {index,stitch ->
                key(index) {
                    val ink by animateColorAsState(
                        if(complete)colors.primary else colors.outlineVariant,
                        tween(if(motion)250 else 0,delayMillis=if(motion&&complete)index*35 else 0),
                        label="stitch-color")
                    val pulse=remember {Animatable(1f)}
                    LaunchedEffect(complete,motion) {
                        pulse.snapTo(1f)
                        if(complete&&motion) {
                            delay(index*35L)
                            pulse.animateTo(2f,tween(100))
                            pulse.animateTo(1f,tween(400,easing=SoEase))
                        }
                    }
                    Canvas(Modifier.fillMaxSize()) {
                        val x1=upper.centers.getOrNull(index)
                        val x2=lower.centers.getOrNull(index)
                        if(upperHidden&&lowerHidden&&x1!=null&&x2!=null&&
                            x1 in upper.bounds.left..upper.bounds.right&&x2 in lower.bounds.left..lower.bounds.right) {
                            val length=stitch.length.value
                            val sway=kotlin.math.sin(Math.toRadians(stitch.swing.value.toDouble())).toFloat()*size.height*length
                            drawLine(ink,Offset(x1-origin,0f),
                                Offset(x1-origin+(x2-x1)*length+sway,size.height*length),
                                strokeWidth=1.dp.toPx()*pulse.value,cap=StrokeCap.Round)
                        }
                    }
                }
            }
        }
        PasswordDotField(confirmationLabel,confirmation,onConfirmationChange,confirmationError,confirmationTag,
            reference=value,stitches=stitches,complete=complete,onViewport={lower=it},onHiddenChange={lowerHidden=it},
            labelVisible=showConfirmation,animateLabelOnEnter=true,statusText=confirmationStatus)
        }
        }
    }
}

@Composable
private fun PasswordDotField(
    label: String,
    value: String,
    onValueChange: (String)->Unit,
    error: String?,
    tag: String,
    modifier: Modifier=Modifier,
    reference: String?=null,
    stitches: List<StitchMotion> = emptyList(),
    complete: Boolean=false,
    onViewport: (DotViewport)->Unit={},
    onHiddenChange: (Boolean)->Unit={},
    labelVisible: Boolean=true,
    animateLabelOnEnter: Boolean=false,
    statusText: String?=null,
) {
    var visible by remember {mutableStateOf(false)}
    val source=remember {MutableInteractionSource()}
    val focused by source.collectIsFocusedAsState()
    val pressed by source.collectIsPressedAsState()
    val focusRequester=remember {FocusRequester()}
    val keyboard=LocalSoftwareKeyboardController.current
    var editing by remember {mutableStateOf(TextFieldValue(value,TextRange(value.length)))}
    val fieldValue=if(editing.text==value)editing else TextFieldValue(value,TextRange(value.length))
    SideEffect {onHiddenChange(!visible)}
    val trailing: (@Composable ()->Unit)?=if(reference==null||complete) {{
        Row(verticalAlignment=Alignment.CenterVertically) {
            if(complete)MatchTick(value.length)
            if(reference==null)PasswordToggleButton(visible,{visible=it},Modifier.testTag("$tag-visibility"))
        }
    }} else null
    Column(modifier.fillMaxWidth()) {
        StitchedFieldFrame(focused,error!=null,label={
            Box(Modifier.testTag("$tag-label").semantics {
                if(statusText!=null)liveRegion=LiveRegionMode.Polite
            }) {
                ScrambleText(if(labelVisible)statusText?:label else "",animateOnEnter=animateLabelOnEnter,
                    color=if(complete)MaterialTheme.colorScheme.accentInk else LocalContentColor.current)
            }
        },trailingIcon=trailing,pressed=pressed,
            modifier=Modifier.stitchedFocusOnTap(source,focusRequester,keyboard)) {
            DotEditor(fieldValue,{editing=it;if(it.text!=value)onValueChange(it.text)},reference,stitches,visible,focused,
                Modifier.focusRequester(focusRequester).testTag(tag)
                    .semantics {
                        contentDescription=label
                        if(statusText!=null)stateDescription=statusText
                        if(error!=null)this.error(error)
                    },
                source,onViewport)
        }
        StitchedFieldSupport(error!=null,if(error!=null&&statusText==null) {{Text(error.withInterfaceDigits())}} else null)
    }
}

@Composable
private fun DotEditor(
    value: TextFieldValue,
    onValueChange: (TextFieldValue)->Unit,
    reference: String?,
    stitches: List<StitchMotion>,
    visible: Boolean,
    focused: Boolean,
    modifier: Modifier,
    source: MutableInteractionSource,
    onViewport: (DotViewport)->Unit,
) {
    val motion=LocalMotionEnabled.current
    val colors=MaterialTheme.colorScheme
    val density=LocalDensity.current
    val scroll=rememberScrollState()
    var layout by remember {mutableStateOf<TextLayoutResult?>(null)}
    var viewport by remember {mutableStateOf(Rect.Zero)}
    var retainedCount by remember {mutableIntStateOf(value.text.length)}
    val count=maxOf(retainedCount,value.text.length)
    SideEffect {retainedCount=count}
    LaunchedEffect(value.text.length,motion) {
        if(count>value.text.length&&motion)delay(220)
        retainedCount=value.text.length
    }
    val style=MaterialTheme.typography.bodyMedium.copy(fontFamily=FontFamily.Monospace,
        fontSize=16.sp,letterSpacing=4.sp,color=Color.Transparent,textDirection=TextDirection.Ltr)
    val measurer=rememberTextMeasurer()
    val metrics=measurer.measure("\u2022".repeat(count+1),style=style,softWrap=false)
    val plainStyle=MaterialTheme.typography.bodyLarge.copy(color=colors.onSurface,textDirection=TextDirection.Ltr)
    val plainLayout=measurer.measure(value.text,style=plainStyle,softWrap=false)
    val displayStyle=if(visible)plainStyle else style
    val displayWidth=maxOf(metrics.size.width,measurer.measure(value.text+" ",style=plainStyle,softWrap=false).size.width)
    val displayHeight=maxOf(metrics.size.height,plainLayout.size.height)
    val reveal by animateFloatAsState(if(visible)1f else 0f,
        tween(if(motion)280 else 0,easing=SoEase),label="field-reveal")
    val dots=(0 until count).map {index ->
        key(index) {
            val dot=remember {DotMotion()}
            val character=value.text.getOrNull(index)
            val mismatch=character!=null&&reference!=null&&character!=reference.getOrNull(index)
            LaunchedEffect(character,mismatch,motion) {
                if(!motion) {
                    dot.arrival.snapTo(if(character==null)0f else 1f);dot.shake.snapTo(0f)
                } else coroutineScope {
                    launch {
                        if(character==null)dot.arrival.animateTo(0f,tween(220))
                        else {
                            dot.arrival.snapTo(0f)
                            dot.arrival.animateTo(1f,spring(dampingRatio=.65f,stiffness=400f))
                        }
                    }
                    launch {
                        dot.shake.snapTo(0f)
                        if(mismatch)dot.shake.animateTo(0f,keyframes {
                            durationMillis=550
                            0f at 0;2.4f at 150;-2f at 220;1.2f at 290;0f at 550
                        })
                    }
                }
            }
            dot
        }
    }
    LaunchedEffect(value.selection,layout,viewport.width,visible) {
        layout?.let {
            val cursor=it.getCursorRect(value.selection.end.coerceIn(0,value.text.length))
            val width=viewport.width
            if(width>0f) {
                val target=when {
                    cursor.right>scroll.value+width->cursor.right-width+4f
                    cursor.left<scroll.value->cursor.left
                    else->scroll.value.toFloat()
                }
                if(motion)scroll.animateScrollTo(target.toInt().coerceAtLeast(0),tween(160,easing=SoEase))
                else scroll.scrollTo(target.toInt().coerceAtLeast(0))
            }
        }
    }
    SideEffect {
        onViewport(DotViewport(viewport,(0 until value.text.length).map {
            viewport.left+metrics.getBoundingBox(it).center.x-scroll.value
        }))
    }
    BoxWithConstraints(Modifier.fillMaxWidth().clipToBounds().onGloballyPositioned {viewport=it.boundsInRoot()}) {
        val editorWidth=maxOf(maxWidth,with(density){displayWidth.toDp()})
        Box(Modifier.fillMaxWidth().horizontalScroll(scroll)) {
            BasicTextField(value,onValueChange,
                modifier=modifier.width(editorWidth).heightIn(min=with(density){displayHeight.toDp()}),
                singleLine=true,textStyle=displayStyle.copy(color=Color.Transparent),
                keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password),
                interactionSource=source,visualTransformation=if(visible)VisualTransformation.None else PasswordVisualTransformation(),
                cursorBrush=SolidColor(Color.Transparent),onTextLayout={layout=it},
                decorationBox={editor ->
                    Box {
                        editor()
                        Canvas(Modifier.matchParentSize()) {
                            if(reveal<1f)dots.forEachIndexed {index,dot ->
                                val arrival=dot.arrival.value
                                val bounds=metrics.getBoundingBox(index)
                                val target=if(index<value.text.length)plainLayout.getBoundingBox(index).center else bounds.center
                                val center=bounds.center+(target-bounds.center)*reveal
                                val mismatch=reference!=null&&value.text.getOrNull(index)!=reference.getOrNull(index)
                                val tug=stitches.getOrNull(index)?.tug?.value?:0f
                                val x=center.x+dot.shake.value*1.dp.toPx()*(1f-reveal)
                                val y=center.y-9f*(1f-arrival)*1.dp.toPx()+
                                    tug*(if(reference==null)1.2f else -1.4f)*1.dp.toPx()*(1f-reveal)
                                val radius=3.5.dp.toPx()*(.4f+.6f*arrival).coerceAtLeast(0f)*(1f-.55f*reveal)
                                val ink=colors.onSurface.copy(alpha=arrival.coerceIn(0f,1f)*(1f-reveal))
                                if(mismatch)drawCircle(ink,radius,Offset(x,y),style=Stroke(1.4.dp.toPx()))
                                else drawCircle(ink,radius,Offset(x,y))
                            }
                        }
                        StitchedFallingText(plainLayout,Modifier.matchParentSize(),metrics,reveal)
                        StitchedCaret(layout,value,focused,Modifier.matchParentSize())
                    }
                })
        }
    }
}

@Composable
private fun MatchTick(length: Int) {
    val motion=LocalMotionEnabled.current
    val progress=remember {Animatable(0f)}
    val ink=MaterialTheme.colorScheme.primary
    LaunchedEffect(motion) {
        if(motion) {
            delay(length*35L+50)
            progress.animateTo(1f,tween(340,easing=SoEase))
        } else progress.snapTo(1f)
    }
    Canvas(Modifier.size(24.dp).clearAndSetSemantics {}) {
        val path=Path().apply {
            moveTo(size.width*6.5f/24,size.height*12.6f/24)
            lineTo(size.width*10.4f/24,size.height*16.4f/24)
            lineTo(size.width*17.6f/24,size.height*8.4f/24)
        }
        val measure=PathMeasure().apply {setPath(path,false)}
        val segment=Path()
        measure.getSegment(0f,measure.length*progress.value,segment,true)
        drawPath(segment,ink,style=Stroke(2.dp.toPx(),cap=StrokeCap.Round,join=StrokeJoin.Round))
    }
}
