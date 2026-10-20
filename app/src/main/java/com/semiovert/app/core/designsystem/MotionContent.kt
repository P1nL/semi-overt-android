package com.semiovert.app.core.designsystem

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.*
import androidx.compose.ui.unit.*
import com.semiovert.app.R
import com.semiovert.app.core.model.Cover
import kotlin.math.roundToInt

/** Shared by the opening identity and profile cover, rather than two similar effects. */
object PixelReveal {
    fun tileVisible(index: Int,progress: Float)=progress>0f&&((index*37%64)/64f)<=progress
    fun sample(progress: Float,count: Int)=(progress*count).toInt().coerceIn(0,(count-1).coerceAtLeast(0))
}

@Composable
fun PixelCover(cover: Cover,progress: Float,modifier: Modifier=Modifier) {
    val resources=LocalContext.current.resources
    val source=remember(cover){BitmapFactory.decodeResource(resources,if(cover==Cover.FOREST)R.drawable.home_cover_forest else R.drawable.home_cover_architecture)}
    val coarse=remember(source){listOf(8,12,20,32,64,128).map {Bitmap.createScaledBitmap(source,it,it,false).asImageBitmap()}}
    val full=remember(source){source.asImageBitmap()}
    Canvas(modifier) {
        if(progress>0f) {
            val tiles=Path()
            for(row in 0..7)for(column in 0..7)if(PixelReveal.tileVisible(row*8+column,progress))
                tiles.addRect(Rect(size.width*column/8,size.height*row/8,size.width*(column+1)/8,size.height*(row+1)/8))
            clipPath(tiles) {
                val image=if(progress>=1f)full else coarse[PixelReveal.sample(progress,coarse.size)]
                val ratio=size.width/size.height
                val sourceW=if(ratio>1f)image.width else (image.height*ratio).roundToInt().coerceAtLeast(1)
                val sourceH=if(ratio>1f)(image.width/ratio).roundToInt().coerceAtLeast(1) else image.height
                drawImage(image,srcOffset=IntOffset((image.width-sourceW)/2,(image.height-sourceH)/2),srcSize=IntSize(sourceW,sourceH),
                    dstSize=IntSize(size.width.roundToInt(),size.height.roundToInt()),filterQuality=if(progress>=1f)FilterQuality.High else FilterQuality.None)
            }
        }
    }
}

/** Web textEffect semantics: reserve the full layout, reveal characters, never reflow. */
@Composable
fun TypewriterText(text: String,progress: Float,style: TextStyle,modifier: Modifier=Modifier,color: Color=LocalContentColor.current) {
    val count=text.codePointCount(0,text.length)
    val visible=text.offsetByCodePoints(0,(count*progress.coerceIn(0f,1f)).roundToInt())
    val value=buildAnnotatedString {append(text);if(visible<text.length)addStyle(SpanStyle(color=Color.Transparent),visible,text.length)}
    Text(value,modifier=modifier,color=color,style=style)
}
