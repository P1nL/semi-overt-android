package com.semiovert.app.core.designsystem.wave

import android.graphics.Paint
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.semiovert.app.core.designsystem.motionMillis
import com.semiovert.app.core.designsystem.readingChromeFade

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
}
private data class CounterMotion(val owner: String?,val capacity: Int,val numbers: CounterNumbers,
    val current: Transition<Int>,val total: Transition<Int>)
private val LocalCounterMotion=compositionLocalOf<CounterMotion?> {null}

/** Keep the two number transitions alive across routes and category SaveableStateProvider keys. */
@Composable
fun DeckCounterHost(owner: String?,capacity: Int,content: @Composable ()->Unit) {
    val numbers=remember {CounterNumbers()}
    val current=updateTransition(numbers.current,label="deck-current")
    val total=updateTransition(numbers.total,label="deck-total")
    CompositionLocalProvider(LocalCounterMotion provides CounterMotion(owner,capacity,numbers,current,total),content=content)
}

@Composable
internal fun DeckCounter(current: Int,total: Int,page: String,modifier: Modifier=Modifier) {
    val motion=LocalCounterMotion.current?:return
    SideEffect {
        // The departing route must not overwrite the arriving route's counter.
        if(motion.owner==page){motion.numbers.current=current;motion.numbers.total=total}
    }
    val density=LocalDensity.current
    val statusTop=WindowInsets.statusBars.getTop(density)/density.density
    BoxWithConstraints(modifier.fillMaxWidth().testTag("deck-counter").clearAndSetSemantics {
        contentDescription="第 ${motion.numbers.current} 篇，共 ${motion.numbers.total} 篇"
    }) {
        val measurer=rememberTextMeasurer()
        val base=TextStyle(fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.Black,
            fontSize=100.sp,lineHeight=100.sp,fontFeatureSettings="tnum")
        // Reserve the same digit columns on both pages; 6 -> 11 never changes font height.
        val sample="8".repeat(motion.capacity.coerceAtLeast(1).toString().length)
        val number=measurer.measure(sample,base,softWrap=false)
        val slash=measurer.measure("/",base,softWrap=false)
        val ratio=with(density){maxWidth.toPx()}/(2*number.size.width+slash.size.width).coerceAtLeast(1)
        val style=base.copy(fontSize=(100f*ratio).sp,lineHeight=(100f*ratio).sp)
        val columnWidth=with(density){(number.size.width*ratio).toDp()}
        val glyphs="0123456789/"
        val layout=measurer.measure(glyphs,style,softWrap=false)
        val typeface=LocalFontFamilyResolver.current.resolve(FontFamily.SansSerif,FontWeight.Black).value as Typeface
        // The line box starts above the visible glyphs. Measure that bearing using the
        // same resolved font and baseline, rather than another fixed negative margin.
        val topBearing=remember(typeface,style,density,layout.firstBaseline) {
            val bounds=android.graphics.Rect()
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.typeface=typeface;textSize=with(density){style.fontSize.toPx()}
            }.getTextBounds(glyphs,0,glyphs.length,bounds)
            with(density){(layout.firstBaseline+bounds.top).coerceAtLeast(0f).toDp()}
        }
        val height=with(density){layout.size.height.toDp()}
        val duration=motionMillis(360)
        // Clip at the scope bar's bottom so rolling digits cannot rise into its buttons.
        Box(Modifier.fillMaxWidth().offset(y=DeckCounterLayout.localTop(statusTop,page=="search").dp)
            .height(height).clipToBounds().readingChromeFade()) {
        Row(Modifier.fillMaxWidth().offset(y=-topBearing),
            verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.Center) {
            @Composable fun Number(transition: Transition<Int>,wrap: Boolean,alignment: TextAlign) {
                transition.AnimatedContent(modifier=Modifier.width(columnWidth).height(height),contentAlignment=Alignment.Center,
                    transitionSpec={
                        val forward=if(wrap&&initialState==motion.numbers.total&&targetState==1)true
                            else if(wrap&&initialState==1&&targetState==motion.numbers.total)false else targetState>initialState
                        val direction=if(forward)1 else -1
                        (slideInVertically(tween(duration)){it*direction}+fadeIn(tween(duration))) togetherWith
                            (slideOutVertically(tween(duration)){-it*direction}+fadeOut(tween(duration)))
                    }) {value->Text(value.toString(),modifier=Modifier.fillMaxWidth(),style=style,
                        textAlign=alignment,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1,softWrap=false)}
            }
            Number(motion.current,true,TextAlign.Right)
            Text("/",style=style,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1,softWrap=false)
            Number(motion.total,false,TextAlign.Left)
        }
        }
    }
}
