package com.semiovert.app.core.designsystem

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.*
import androidx.compose.ui.unit.*
import com.semiovert.app.R
import com.semiovert.app.core.model.Cover
import kotlin.math.roundToInt

@Composable
internal fun rememberProfileCover(photo: String?,fallback: Cover?): ImageBitmap? =
    rememberProfileCoverPhoto(photo,fallback).bitmap

@Composable
internal fun rememberProfileCoverPhoto(photo: String?,fallback: Cover?): ProfilePhoto {
    val original=rememberProfilePhoto(photo)
    if(original.bitmap!=null||fallback==null)return original
    val resources=LocalContext.current.resources
    val bitmap=remember(resources,fallback) {
        BitmapFactory.decodeResource(resources,if(fallback==Cover.FOREST)R.drawable.profile_cover_forest else R.drawable.profile_cover_architecture).asImageBitmap()
    }
    return original.copy(bitmap=bitmap)
}

@Composable
internal fun ProfileCoverImage(source: ImageBitmap,modifier: Modifier=Modifier,position: Float=.5f) {
    Canvas(modifier) {
        if(size.width>0f&&size.height>0f) {
            val target=IntSize(size.width.roundToInt(),size.height.roundToInt())
            val crop=source.profileCoverCrop(target,position)
            drawImage(source,srcOffset=IntOffset(crop.left,crop.top),srcSize=IntSize(crop.width,crop.height),
                dstSize=target,filterQuality=FilterQuality.High)
        }
    }
}

internal fun ImageBitmap.profileCoverCrop(target: IntSize,position: Float): IntRect {
    val ratio=target.width.toFloat()/target.height
    val sourceWidth=(height*ratio).roundToInt().coerceIn(1,width)
    val sourceHeight=(width/ratio).roundToInt().coerceIn(1,height)
    val left=(width-sourceWidth)/2
    val top=((height-sourceHeight)*position).roundToInt()
    return IntRect(left,top,left+sourceWidth,top+sourceHeight)
}

/** Web textEffect semantics: reserve the full layout, reveal characters, never reflow. */
@Composable
fun TypewriterText(text: String,progress: Float,style: TextStyle,modifier: Modifier=Modifier,color: Color=LocalContentColor.current) {
    val count=text.codePointCount(0,text.length)
    val visible=text.offsetByCodePoints(0,(count*progress.coerceIn(0f,1f)).roundToInt())
    val value=buildAnnotatedString {append(text.withInterfaceDigits());if(visible<text.length)addStyle(SpanStyle(color=Color.Transparent),visible,text.length)}
    Text(value,modifier=modifier,color=color,style=style)
}
