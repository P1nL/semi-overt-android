package com.semiovert.app.core.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle

internal fun scrambleTextFrame(from: String,to: String,progress: Float): String {
    if(from==to)return to
    if(progress<=0f)return from
    if(progress>=1f)return to
    val noise="<>/\\|{}[]#%01"
    val count=maxOf(from.length,to.length)
    val frame=(progress*20).toInt()
    return buildString {
        repeat(count) {index ->
            val local=progress*1.6f-index.toFloat()/count*.6f
            append(when {
                local<=0f->from.getOrNull(index)?:' '
                local>=1f->to.getOrNull(index)?:' '
                else->noise[(index*7+frame*3)%noise.length]
            })
        }
    }.trimEnd()
}

@Composable
internal fun rememberScrambleText(text: String,animateOnEnter: Boolean=false): State<String> {
    val motion=LocalMotionEnabled.current
    val rendered=remember {mutableStateOf(if(animateOnEnter&&motion)"" else text)}
    LaunchedEffect(text,motion) {
        val from=rendered.value
        if(motion&&from!=text) {
            val start=withFrameNanos {it}
            var progress=0f
            while(progress<1f) {
                progress=((withFrameNanos {it}-start)/280_000_000f).coerceIn(0f,1f)
                rendered.value=scrambleTextFrame(from,text,progress)
            }
        }
        rendered.value=text
    }
    return if(motion)rendered else rememberUpdatedState(text)
}

@Composable
internal fun ScrambleText(
    text: String,
    modifier: Modifier=Modifier,
    style: TextStyle=LocalTextStyle.current,
    color: Color=LocalContentColor.current,
    animateOnEnter: Boolean=false,
) {
    val rendered by rememberScrambleText(text,animateOnEnter)
    // Keep layout and accessibility anchored to real copy, never the temporary noise.
    Box(modifier.clearAndSetSemantics {this.text=AnnotatedString(text)}) {
        Text(if(text.length>=rendered.length)text else rendered,
            modifier=Modifier.clearAndSetSemantics {},style=style,color=Color.Transparent,maxLines=1,softWrap=false)
        Text(rendered,modifier=Modifier.clearAndSetSemantics {},style=style,color=color,maxLines=1,softWrap=false)
    }
}
