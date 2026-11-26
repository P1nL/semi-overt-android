package com.semiovert.app.core.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter

internal fun Modifier.stitchedRevealClip()=drawWithContent {
    val halo=16.dp.toPx()
    clipRect(-halo,-halo,size.width+halo,size.height+halo) {this@drawWithContent.drawContent()}
}

// Native field styling adapted from the supplied Animorio component (animorio.com/en/license/).
@Composable
fun StitchedTextField(
    value: String,
    onValueChange: (String)->Unit,
    modifier: Modifier=Modifier,
    label: (@Composable ()->Unit)?=null,
    placeholder: (@Composable ()->Unit)?=null,
    singleLine: Boolean=false,
    minLines: Int=1,
    isError: Boolean=false,
    supportingText: (@Composable ()->Unit)?=null,
    trailingIcon: (@Composable ()->Unit)?=null,
    textStyle: TextStyle=MaterialTheme.typography.bodyLarge,
    keyboardOptions: KeyboardOptions=KeyboardOptions.Default,
    keyboardActions: KeyboardActions=KeyboardActions.Default,
    visualTransformation: VisualTransformation=VisualTransformation.None,
    interactionSource: MutableInteractionSource?=null,
) {
    val source=interactionSource?:remember {MutableInteractionSource()}
    val focused by source.collectIsFocusedAsState()
    val pressed by source.collectIsPressedAsState()
    val colors=MaterialTheme.colorScheme
    val requester=remember {FocusRequester()}
    val keyboard=LocalSoftwareKeyboardController.current
    var editing by remember {mutableStateOf(TextFieldValue(value,TextRange(value.length)))}
    val fieldValue=if(editing.text==value)editing else TextFieldValue(value,TextRange(value.length))
    Column(modifier.semantics(mergeDescendants=true) {}) {
        StitchedFieldFrame(focused,isError,label,trailingIcon,pressed=pressed,
            modifier=Modifier.stitchedFocusOnTap(source,requester,keyboard)) {
            Box(contentAlignment=if(singleLine)Alignment.CenterStart else Alignment.TopStart) {
                if(value.isEmpty()&&placeholder!=null) {
                    CompositionLocalProvider(LocalContentColor provides colors.onSurfaceVariant) {placeholder()}
                }
                StitchedTextEditor(fieldValue,{editing=it;if(it.text!=value)onValueChange(it.text)},
                    source,focused,textStyle,singleLine,minLines,keyboardOptions,keyboardActions,visualTransformation,
                    Modifier.fillMaxWidth().focusRequester(requester).semantics {if(isError)error("输入内容有误")})
            }
        }
        StitchedFieldSupport(isError,supportingText)
    }
}

@Composable
internal fun StitchedFieldFrame(
    focused: Boolean,
    isError: Boolean,
    label: (@Composable ()->Unit)?,
    trailingIcon: (@Composable ()->Unit)?,
    modifier: Modifier=Modifier,
    pressed: Boolean=false,
    content: @Composable ()->Unit,
) {
    val colors=MaterialTheme.colorScheme
    val motion=LocalMotionEnabled.current
    val focus by animateFloatAsState(if(focused)1f else 0f,
        if(motion)spring(dampingRatio=.7f,stiffness=350f) else tween(0),label="field-focus")
    val scale=remember {Animatable(1f)}
    val currentPressed by rememberUpdatedState(pressed)
    LaunchedEffect(motion) {
        if(!motion)scale.snapTo(1f)
        else snapshotFlow {currentPressed}.filter {it}.collectLatest {
            // Pointer-down starts the whole timeline; lifting a finger does not cancel the rebound.
            scale.animateTo(StitchedFieldMotion.PRESSED_SCALE,
                tween(StitchedFieldMotion.PRESS_MILLIS,easing=StitchedFieldMotion.pressEase))
            scale.animateTo(1f,tween(StitchedFieldMotion.RELEASE_MILLIS,easing=StitchedFieldMotion.releaseEase))
        }
    }
    val edge by animateColorAsState(
        when {isError->colors.error;focused->colors.primary;else->colors.outlineVariant},
        tween(motionMillis(250)),label="field-edge")
    val shape=RoundedCornerShape(14.dp)
    val strength=focus.coerceIn(0f,1f)
    Box(modifier.fillMaxWidth()) {
        // Keep native selection/handle coordinates fixed while only the field surface rebounds.
        Box(Modifier.matchParentSize()
            .graphicsLayer {scaleX=scale.value;scaleY=scale.value}
            .drawBehind {
                val spread=4.dp.toPx()*strength
                drawRoundRect(edge.copy(alpha=.1f*strength),Offset(-spread,-spread),
                    Size(size.width+spread*2,size.height+spread*2),CornerRadius(14.dp.toPx()+spread))
            }
            .shadow((1f+7f*strength).dp,shape,clip=false,
                ambientColor=colors.primary.copy(alpha=.3f),spotColor=colors.primary.copy(alpha=.22f))
            .background(colors.surface,shape)
            .border((1f+.5f*strength).dp,edge,shape))
        Row(
            Modifier.fillMaxWidth()
            .heightIn(min=if(label==null)56.dp else 60.dp)
            .padding(start=18.dp,end=if(trailingIcon==null)18.dp else 6.dp,
                top=if(trailingIcon==null)10.dp else 6.dp,bottom=if(trailingIcon==null)10.dp else 6.dp),
            verticalAlignment=Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                if(label!=null) {
                    CompositionLocalProvider(
                        LocalTextStyle provides MaterialTheme.typography.labelSmall,
                        LocalContentColor provides if(isError)colors.error else colors.onSurfaceVariant,
                    ) {label()}
                    Spacer(Modifier.height(2.dp))
                }
                content()
            }
            if(trailingIcon!=null) {
                Spacer(Modifier.width(6.dp))
                Box(Modifier.sizeIn(minWidth=48.dp,minHeight=48.dp),contentAlignment=Alignment.Center) {trailingIcon()}
            }
        }
    }
}

@Composable
internal fun StitchedFieldSupport(isError: Boolean,content: (@Composable ()->Unit)?) {
    if(content!=null) {
        Box(Modifier.fillMaxWidth().padding(start=4.dp,top=6.dp,end=4.dp),
            contentAlignment=if(isError)Alignment.Center else Alignment.TopStart) {
            val style=MaterialTheme.typography.bodySmall
            CompositionLocalProvider(
                LocalTextStyle provides if(isError)style.copy(textAlign=TextAlign.Center) else style,
                LocalContentColor provides if(isError)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            ) {content()}
        }
    }
}
