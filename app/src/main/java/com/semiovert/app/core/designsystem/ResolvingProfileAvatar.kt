package com.semiovert.app.core.designsystem

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun ResolvingProfileAvatar(variant: Int,size: Int,photo: ImageBitmap?,photoKey: String?,loaded: Boolean) {
    val motion=LocalMotionEnabled.current
    var waiting by remember(photoKey) {mutableStateOf(!loaded)}
    val clarity=remember(photoKey) {Animatable(if(loaded)1f else 0f)}
    val frames=if(motion&&waiting&&photo!=null)key(photo) {
        produceState<AvatarPixelFrames?>(null,photo) {
            value=withContext(Dispatchers.Default) {AvatarPixelFrames(photo)}
        }.value
    } else null
    val frame by remember(frames,clarity) {derivedStateOf {frames?.frame(clarity.value)}}
    LaunchedEffect(loaded,frames,motion) {
        when {
            !loaded->{waiting=true;clarity.snapTo(0f)}
            !motion||photo==null->{waiting=false;clarity.snapTo(1f)}
            frames!=null->{
                clarity.animateTo(1f,tween(AvatarPixelMotion.DURATION_MILLIS,easing=LinearEasing))
                waiting=false
            }
        }
    }
    when {
        !loaded||(motion&&waiting&&photo!=null&&frames==null)->{
            val background=MaterialTheme.colorScheme.secondary
            val ink=MaterialTheme.colorScheme.onSecondary
            Canvas(Modifier.size(size.dp).testTag("identity-avatar-coarse")
                .semantics {stateDescription="头像加载中"}) {
                drawCircle(background)
                val cell=this.size.minDimension/8f
                drawRect(ink,Offset(3f*cell,2f*cell),Size(2f*cell,2f*cell))
                drawRect(ink,Offset(2f*cell,5f*cell),Size(4f*cell,cell))
                drawRect(ink,Offset(2f*cell,6f*cell),Size(4f*cell,cell))
            }
        }
        frames!=null->ProfileAvatar(variant,size,photo=frame,filterQuality=FilterQuality.None)
        else->ProfileAvatar(variant,size,photo=photo)
    }
}
