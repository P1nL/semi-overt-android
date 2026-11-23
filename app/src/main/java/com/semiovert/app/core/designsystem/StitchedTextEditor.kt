package com.semiovert.app.core.designsystem

import android.icu.text.BreakIterator
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.text.*
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

internal fun Modifier.stitchedFocusOnTap(
    source: MutableInteractionSource,
    requester: FocusRequester,
    keyboard: SoftwareKeyboardController?,
)=pointerInput(source,requester,keyboard) {
    detectTapGestures(
        onPress={position ->
            val press=PressInteraction.Press(position)
            source.emit(press)
            var completed=false
            try {
                if(tryAwaitRelease())source.emit(PressInteraction.Release(press))
                else source.emit(PressInteraction.Cancel(press))
                completed=true
            } finally {
                if(!completed)source.tryEmit(PressInteraction.Cancel(press))
            }
        },
        onTap={requester.requestFocus();keyboard?.show()},
    )
}

@Composable
internal fun StitchedTextEditor(
    value: TextFieldValue,
    onValueChange: (TextFieldValue)->Unit,
    source: MutableInteractionSource,
    focused: Boolean,
    textStyle: TextStyle,
    singleLine: Boolean,
    minLines: Int,
    keyboardOptions: KeyboardOptions,
    keyboardActions: KeyboardActions,
    visualTransformation: VisualTransformation,
    modifier: Modifier=Modifier,
) {
    val density=LocalDensity.current
    val motion=LocalMotionEnabled.current
    val scroll=rememberScrollState()
    val measurer=rememberTextMeasurer()
    var viewportWidth by remember {mutableIntStateOf(0)}
    val transformed=visualTransformation.filter(AnnotatedString(value.text))
    val measuredWidth=if(singleLine)measurer.measure(transformed.text+AnnotatedString(" "),style=textStyle,softWrap=false).size.width else 0
    BoxWithConstraints(Modifier.fillMaxWidth().clipToBounds().onSizeChanged {viewportWidth=it.width}) {
        val width=if(singleLine)maxOf(maxWidth,with(density){measuredWidth.toDp()}) else maxWidth
        // Measure the current value before drawing; onTextLayout can still belong to the previous edit.
        // A measurement-only space preserves the populated line's metrics while the real value stays empty.
        val layoutText=if(transformed.text.isEmpty())AnnotatedString(" ") else transformed.text
        val layout=measurer.measure(layoutText,style=textStyle,softWrap=!singleLine,
            maxLines=if(singleLine)1 else Int.MAX_VALUE,
            constraints=Constraints(maxWidth=with(density){width.roundToPx()}))
        LaunchedEffect(value.selection,layout,viewportWidth,singleLine,motion) {
            if(singleLine&&viewportWidth>0) {
                val cursor=layout.getCursorRect(transformed.offsetMapping.originalToTransformed(value.selection.end)
                    .coerceIn(0,layout.layoutInput.text.length))
                val target=when {
                    cursor.right>scroll.value+viewportWidth->cursor.right-viewportWidth+4f
                    cursor.left<scroll.value->cursor.left
                    else->scroll.value.toFloat()
                }.toInt().coerceAtLeast(0)
                if(motion)scroll.animateScrollTo(target,tween(160,easing=SoEase)) else scroll.scrollTo(target)
            }
        }
        Box(Modifier.fillMaxWidth().then(if(singleLine)Modifier.horizontalScroll(scroll) else Modifier)) {
            // Native editing owns selection and IME composition; the overlay owns only glyph motion and the caret.
            BasicTextField(value,onValueChange,modifier=modifier.width(width)
                    .heightIn(min=with(density){layout.size.height.toDp()}),
                singleLine=singleLine,minLines=minLines,
                textStyle=textStyle.copy(color=Color.Transparent),
                keyboardOptions=keyboardOptions,keyboardActions=keyboardActions,
                interactionSource=source,visualTransformation=visualTransformation,
                cursorBrush=SolidColor(Color.Transparent),
                decorationBox={editor ->
                    Box {
                        editor()
                        StitchedFallingText(layout,Modifier.matchParentSize())
                        StitchedCaret(layout,value,focused,Modifier.matchParentSize(),transformed.offsetMapping)
                    }
                })
        }
    }
}

private fun stitchedGraphemes(text: String): List<String> {
    val iterator=BreakIterator.getCharacterInstance()
    iterator.setText(text)
    return buildList {
        var start=iterator.first()
        var end=iterator.next()
        while(end!=BreakIterator.DONE) {
            add(text.substring(start,end))
            start=end;end=iterator.next()
        }
    }
}

private fun TextLayoutResult.glyphBounds(start: Int,end: Int): Rect {
    var rect=getBoundingBox(start)
    for(offset in start+1 until end) {
        val next=getBoundingBox(offset)
        rect=Rect(minOf(rect.left,next.left),minOf(rect.top,next.top),
            maxOf(rect.right,next.right),maxOf(rect.bottom,next.bottom))
    }
    return rect
}

@Composable
internal fun StitchedFallingText(
    layout: TextLayoutResult,
    modifier: Modifier=Modifier,
    sourceLayout: TextLayoutResult?=null,
    revealProgress: Float=1f,
) {
    val motion=LocalMotionEnabled.current
    val ink=MaterialTheme.colorScheme.onSurface
    val tracker=remember {StitchedGlyphTracker()}
    val text=layout.layoutInput.text.text
    val glyphs=remember(text) {tracker.update(stitchedGraphemes(text))}
    val arrivals=glyphs.map {glyph ->
        key(glyph.id) {
            val arrival=remember {Animatable(if(motion&&glyph.entering)0f else 1f)}
            LaunchedEffect(motion) {
                if(motion)arrival.animateTo(1f,spring(dampingRatio=.65f,stiffness=400f))
                else arrival.snapTo(1f)
            }
            arrival
        }
    }
    Canvas(modifier) {
        if(revealProgress<=0f)return@Canvas
        val dropDistance=9.dp.toPx()
        val morphing=sourceLayout!=null&&revealProgress<1f
        val moving=glyphs.indices.filter {morphing||arrivals[it].value<.999f}
        val bounds=moving.associateWith {index ->
            glyphs[index].let {layout.glyphBounds(it.start,it.end)}
        }
        if(!morphing) {
            val cutouts=Path().apply {bounds.values.forEach {addRect(it)}}
            clipPath(cutouts,ClipOp.Difference) {drawText(layout,color=ink)}
        }
        moving.forEach {index ->
            val glyph=glyphs[index]
            val rect=bounds.getValue(index)
            val progress=arrivals[index].value
            val origin=sourceLayout?.glyphBounds(glyph.start,glyph.end)?.center?:rect.center
            val shift=(origin-rect.center)*(1f-revealProgress)
            val scale=(.8f+.2f*progress)*(.45f+.55f*revealProgress)
            withTransform({
                translate(shift.x,shift.y-dropDistance*(1f-progress))
                scale(scale,scale,rect.center)
            }) {
                clipRect(rect.left,rect.top,rect.right,rect.bottom) {
                    drawText(layout,color=ink,alpha=progress.coerceIn(0f,1f)*revealProgress)
                }
            }
        }
    }
}

@Composable
internal fun StitchedCaret(
    layout: TextLayoutResult?,
    value: TextFieldValue,
    focused: Boolean,
    modifier: Modifier=Modifier,
    mapping: OffsetMapping=OffsetMapping.Identity,
) {
    val motion=LocalMotionEnabled.current
    val ink=MaterialTheme.colorScheme.primary
    val offset=mapping.originalToTransformed(value.selection.end).coerceIn(0,layout?.layoutInput?.text?.length?:0)
    val target=layout?.getCursorRect(offset)?:Rect.Zero
    val position=remember {Animatable(target.topLeft,Offset.VectorConverter)}
    val blink=remember {Animatable(1f)}
    var placed by remember {mutableStateOf(false)}
    LaunchedEffect(target.topLeft,focused,motion) {
        if(!placed||!focused||!motion)position.snapTo(target.topLeft)
        else position.animateTo(target.topLeft,tween(160,easing=SoEase))
        placed=focused&&layout!=null
    }
    LaunchedEffect(value.text,value.selection,focused,motion) {
        blink.snapTo(if(focused&&value.selection.collapsed)1f else 0f)
        if(focused&&value.selection.collapsed&&motion)while(isActive) {
            delay(550);blink.snapTo(0f)
            delay(500);blink.snapTo(1f)
        }
    }
    Canvas(modifier) {
        if(focused&&value.selection.collapsed&&layout!=null) {
            drawLine(ink.copy(alpha=blink.value),position.value,
                position.value+Offset(0f,target.height),strokeWidth=1.5.dp.toPx(),cap=StrokeCap.Round)
        }
    }
}
