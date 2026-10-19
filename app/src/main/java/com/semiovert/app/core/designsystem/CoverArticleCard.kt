package com.semiovert.app.core.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.semiovert.app.R
import com.semiovert.app.core.model.*

/** The existing full-cover card, shared by the home arc and category wave. */
@Composable
fun CoverArticleCard(article: Article,selected: Boolean,modifier: Modifier,coverTag: String,topAligned: Boolean=false,squareBottom: Boolean=false,shortBottomBlur: Boolean=false) {
    val cover=article.cover ?: if(article.tone==CardTone.MINT)Cover.FOREST else Cover.ARCHITECTURE
    val image=when {
        shortBottomBlur&&cover==Cover.FOREST->R.drawable.category_cover_forest
        shortBottomBlur->R.drawable.category_cover_architecture
        cover==Cover.FOREST->R.drawable.home_cover_forest
        else->R.drawable.home_cover_architecture
    }
    val lightCoverCopy=topAligned&&cover==Cover.FOREST
    val colors=MaterialTheme.colorScheme
    val ink=if(lightCoverCopy) {
        if(colors.surface.luminance()<colors.onSurface.luminance())colors.surface else colors.onSurface
    } else Color.White
    Surface(modifier=modifier,color=colors.surfaceVariant,contentColor=ink,
        shape=RoundedCornerShape(topStart=18.dp,topEnd=18.dp,
            bottomStart=if(squareBottom)0.dp else 18.dp,bottomEnd=if(squareBottom)0.dp else 18.dp),
        border=BorderStroke(if(selected)1.dp else .5.dp,Color.White.copy(alpha=if(selected).28f else .12f)),shadowElevation=2.dp) {
        Box(Modifier.fillMaxSize()) {
            // Offline cover assets contain actual progressive bottom blur, not a runtime scrim.
            Image(painterResource(image),contentDescription=null,contentScale=ContentScale.Crop,
                modifier=Modifier.fillMaxSize().testTag(coverTag))
            Column(Modifier.align(if(topAligned)Alignment.TopStart else Alignment.BottomStart).fillMaxWidth().padding(18.dp)) {
                Text(article.title,style=MaterialTheme.typography.titleLarge.copy(
                    fontFamily=FontFamily.SansSerif,fontSize=23.sp,lineHeight=29.sp,fontWeight=FontWeight.SemiBold,
                    shadow=if(lightCoverCopy)Shadow.None else Shadow(Color.Black.copy(alpha=.22f),Offset(0f,1f),4f)),
                    maxLines=3,overflow=TextOverflow.Ellipsis)
                Text(article.author.name+" · 约 "+article.minutes+" 分钟",
                    style=MaterialTheme.typography.bodySmall.copy(fontSize=13.sp,lineHeight=18.sp),
                    maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.padding(top=5.dp))
                Text(article.date,style=MaterialTheme.typography.bodySmall.copy(fontSize=13.sp,lineHeight=18.sp),maxLines=1)
            }
        }
    }
}
