package com.semiovert.app.core.designsystem.wave

import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.semiovert.app.core.designsystem.InterfaceDigitFont
import com.semiovert.app.core.designsystem.withInterfaceDigits
import com.semiovert.app.core.designsystem.motionMillis
import com.semiovert.app.core.designsystem.LocalBrowseAnnotationAlpha
import com.semiovert.app.core.designsystem.readingChromeFade
import kotlin.math.roundToInt

internal object DeckCounterLayout {
    const val SEARCH_HEADER_TOP=76f
    const val SEARCH_SCOPE_HEIGHT=48f
    // Both pages anchor directly to the search scope bar's bottom, with no extra gap.
    const val ROOT_TOP=SEARCH_HEADER_TOP+SEARCH_SCOPE_HEIGHT
    fun localTop(statusTop: Float,search: Boolean)=
        if(search)ROOT_TOP-SEARCH_HEADER_TOP-SEARCH_SCOPE_HEIGHT else statusTop+ROOT_TOP
}

@Stable private class CounterNumbers {
    var current by mutableIntStateOf(1)
    var total by mutableIntStateOf(1)
    var previousOwner: String?=null
    val slots=mutableStateListOf<CounterSlot>()
}

private data class CounterLine(val width: Dp,val height: Dp,val columnWidth: Dp,val topBearing: Dp,val dividerOffset: Dp,val style: TextStyle)
private fun TextStyle.dividerStyle()=copy(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.Black)
private fun Paint.visibleDigitBounds(value: String,bottom: Float?): RectF {
    val bounds=Rect()
    getTextBounds(value,0,value.length,bounds)
    val visible=RectF(bounds)
    if(bottom!=null&&bottom>visible.top&&bottom<visible.bottom) {
        val outline=Path()
        getTextPath(value,0,value.length,0f,0f,outline)
        val clip=Path().apply {addRect(visible.left,visible.top,visible.right,bottom,Path.Direction.CW)}
        if(outline.op(clip,Path.Op.INTERSECT)&&!outline.isEmpty)outline.computeBounds(visible,true)
    }
    return visible
}
@Stable private class CounterSlot(val page: String) {
    var origin by mutableStateOf<Offset?>(null)
    var line by mutableStateOf<CounterLine?>(null)
    var alpha by mutableFloatStateOf(1f)
}
private class CounterVisit(val owner: String?,val inherited: CounterSlot?)
private data class CounterMotion(val visit: CounterVisit,val holding: Boolean,val capacity: Int,val numbers: CounterNumbers,
    val current: Transition<Int>,val total: Transition<Int>)
private val LocalCounterMotion=compositionLocalOf<CounterMotion?> {null}
private fun String?.hasDeckCounter()=this=="category"||this=="search"

@Composable
fun DeckCounterHost(owner: String?,capacity: Int,navigating: Boolean=false,content: @Composable ()->Unit) {
    val numbers=remember {CounterNumbers()}
    val visit=remember(owner) {
        val previous=numbers.previousOwner
        val inherited=if(owner!=previous&&owner.hasDeckCounter()&&previous.hasDeckCounter())
            numbers.slots.lastOrNull {it.page==previous&&it.origin!=null} else null
        CounterVisit(owner,inherited)
    }
    SideEffect {numbers.previousOwner=owner}
    val current=updateTransition(numbers.current,label="deck-current")
    val total=updateTransition(numbers.total,label="deck-total")
    val motion=CounterMotion(visit,navigating&&visit.inherited!=null,capacity,numbers,current,total)
    var origin by remember {mutableStateOf(Offset.Zero)}
    Box(Modifier.fillMaxSize().onGloballyPositioned {origin=it.positionInRoot()}) {
        DeckCounterLayer(motion) {origin}
        CompositionLocalProvider(LocalCounterMotion provides motion,content=content)
    }
}

@Composable
internal fun DeckCounter(current: Int,total: Int,page: String,modifier: Modifier=Modifier,categoryLabel: String?=null,
    compact: Boolean=false,categoryLabelFrame: State<String>?=null,counterOcclusionTop: Dp?=null) {
    val motion=LocalCounterMotion.current?:return
    val slot=remember(page) {CounterSlot(page)}
    val annotationAlpha=LocalBrowseAnnotationAlpha.current
    DisposableEffect(motion.numbers,slot) {
        motion.numbers.slots.add(slot)
        onDispose {motion.numbers.slots.remove(slot)}
    }
    SideEffect {
        slot.alpha=annotationAlpha
        if(motion.visit.owner==page) {
            motion.numbers.current=current
            motion.numbers.total=total
        }
    }
    val density=LocalDensity.current
    val statusTop=WindowInsets.statusBars.getTop(density)/density.density
    val labelHeight=maxOf(32.dp,with(density){24.sp.toDp()})
    val top=DeckCounterLayout.localTop(statusTop,page=="search").dp-
        if(categoryLabel!=null)labelHeight else 0.dp
    BoxWithConstraints(modifier.fillMaxWidth().padding(top=top)) {
        val measurer=rememberTextMeasurer()
        val base=TextStyle(fontFamily=InterfaceDigitFont,fontWeight=FontWeight.Bold,
            fontSize=100.sp,lineHeight=100.sp,fontFeatureSettings="tnum")
        // Reserve the same digit columns on both pages; 6 -> 11 never changes font height.
        val sample="8".repeat(maxOf(motion.capacity,current,total,1).toString().length)
        val number=measurer.measure(sample,base,softWrap=false)
        val slash=measurer.measure("/",base.dividerStyle(),softWrap=false)
        val widthRatio=with(density){maxWidth.toPx()}/(2*number.size.width+slash.size.width).coerceAtLeast(1)
        val ratio=if(compact)minOf(widthRatio,.52f/density.fontScale) else widthRatio
        val style=base.copy(fontSize=(100f*ratio).sp,lineHeight=(100f*ratio).sp)
        val columnWidth=with(density){(number.size.width*ratio).toDp()}
        val glyphs="0123456789"
        val layout=measurer.measure(glyphs,style,softWrap=false)
        val resolver=LocalFontFamilyResolver.current
        val typeface=resolver.resolve(style.fontFamily,FontWeight.Bold).value as Typeface
        val paint=remember(typeface,style,density) {
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.typeface=typeface
                textSize=with(density){style.fontSize.toPx()}
                fontFeatureSettings=style.fontFeatureSettings
            }
        }
        // The line box starts above the visible glyphs. Measure that bearing using the
        // same resolved font and baseline, rather than another fixed negative margin.
        val topBearing=remember(paint,layout.firstBaseline) {
            val bounds=Rect()
            paint.getTextBounds(glyphs,0,glyphs.length,bounds)
            with(density){(layout.firstBaseline+bounds.top).coerceAtLeast(0f).toDp()}
        }
        val divider=measurer.measure("/",style.dividerStyle(),softWrap=false,constraints=Constraints(maxHeight=layout.size.height))
        val dividerTypeface=resolver.resolve(FontFamily.SansSerif,FontWeight.Black).value as Typeface
        val dividerOffset=remember(paint,dividerTypeface,divider,layout.size.height,topBearing) {
            val bounds=Rect()
            Paint(paint).apply {this.typeface=dividerTypeface}.getTextBounds("/",0,1,bounds)
            val rowInset=Alignment.CenterVertically.align(divider.size.height,layout.size.height)
            topBearing-with(density){(rowInset+divider.firstBaseline+bounds.top).toDp()}
        }
        val height=with(density){layout.size.height.toDp()}
        val line=CounterLine(maxWidth,height,columnWidth,topBearing,dividerOffset,style)
        SideEffect {slot.line=line}
        Column(Modifier.fillMaxWidth()) {
            if(categoryLabel!=null) {
                val numerator=motion.current.targetState.toString()
                val denominator=motion.total.targetState.toString()
                val visibleBottom=counterOcclusionTop?.let {
                    with(density){it.toPx()-top.roundToPx()-labelHeight.roundToPx()+topBearing.roundToPx()}-layout.firstBaseline
                }
                val inkCenter=remember(paint,numerator,denominator,visibleBottom) {
                    val left=paint.visibleDigitBounds(numerator,visibleBottom)
                    val right=paint.visibleDigitBounds(denominator,visibleBottom)
                    (left.left-paint.measureText(numerator)+right.right)*.5f
                }
                val labelCenter by animateFloatAsState(inkCenter,tween(motionMillis(360)),label="category-label-center")
                Box(Modifier.fillMaxWidth().height(labelHeight).readingChromeFade().graphicsLayer {alpha=annotationAlpha},
                    contentAlignment=Alignment.Center) {
                    Text((categoryLabelFrame?.value?:categoryLabel).withInterfaceDigits(),
                        modifier=Modifier.absoluteOffset {IntOffset(labelCenter.roundToInt(),0)}.testTag("category-label")
                            .semantics {text=AnnotatedString(categoryLabel)},
                        style=MaterialTheme.typography.labelLarge.copy(fontSize=18.sp,lineHeight=24.sp),
                        fontWeight=FontWeight.Bold,color=counterInk(),textAlign=TextAlign.Center,maxLines=1,softWrap=false)
                }
            }
            Box(Modifier.fillMaxWidth().height(height).onGloballyPositioned {slot.origin=it.positionInRoot()})
        }
    }
}

@Composable
private fun counterInk()=if(MaterialTheme.colorScheme.background.luminance()>.5f)Color.Black else Color.White

@Composable
private fun DeckCounterLayer(motion: CounterMotion,rootOrigin: ()->Offset) {
    val held=motion.visit.inherited?.takeIf {motion.holding}
    val slots=motion.numbers.slots
    val slot=held?:slots.lastOrNull {it.page==motion.visit.owner&&it.origin!=null}
        ?:slots.lastOrNull {it.origin!=null}?:return
    val line=slot.line?:return
    val duration=motionMillis(360)
    val ink=counterInk()
    Box(Modifier.absoluteOffset {
        val position=(slot.origin?:Offset.Zero)-rootOrigin()
        IntOffset(position.x.roundToInt(),position.y.roundToInt())
    }.size(line.width,line.height).readingChromeFade().graphicsLayer {alpha=if(held!=null)1f else slot.alpha}
        .clipToBounds().testTag("deck-counter").clearAndSetSemantics {
            contentDescription="第 ${motion.numbers.current} 篇，共 ${motion.numbers.total} 篇"
        }) {
        Row(Modifier.fillMaxWidth().offset(y=-line.topBearing),
            verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.Center) {
            @Composable fun Number(transition: Transition<Int>,wrap: Boolean,alignment: TextAlign) {
                transition.AnimatedContent(modifier=Modifier.width(line.columnWidth).height(line.height),contentAlignment=Alignment.Center,
                    transitionSpec={
                        val forward=if(wrap&&initialState==motion.numbers.total&&targetState==1)true
                            else if(wrap&&initialState==1&&targetState==motion.numbers.total)false else targetState>initialState
                        val direction=if(forward)1 else -1
                        (slideInVertically(tween(duration)){it*direction}+fadeIn(tween(duration))) togetherWith
                            (slideOutVertically(tween(duration)){-it*direction}+fadeOut(tween(duration)))
                    }) {value->Text(value.toString(),modifier=Modifier.fillMaxWidth(),style=line.style,
                        textAlign=alignment,color=ink,maxLines=1,softWrap=false)}
            }
            Number(motion.current,true,TextAlign.Right)
            Text("/",modifier=Modifier.offset(y=line.dividerOffset),style=line.style.dividerStyle(),color=ink,maxLines=1,softWrap=false)
            Number(motion.total,false,TextAlign.Left)
        }
    }
}
