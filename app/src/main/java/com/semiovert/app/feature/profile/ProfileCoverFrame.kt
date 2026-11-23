package com.semiovert.app.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.semiovert.app.core.designsystem.ProfileCoverImage
import com.semiovert.app.core.designsystem.SoIcon
import com.semiovert.app.core.designsystem.SoSymbol

@Composable
internal fun profileCoverHeight()=WindowInsets.statusBars.asPaddingValues().calculateTopPadding()+224.dp

@Composable
internal fun ProfileCoverFrame(
    image: ImageBitmap?,hasCover: Boolean,position: Float,enabled: Boolean,
    onPick: ()->Unit,onMove: (Float)->Boolean,onRemove: ()->Unit,
) {
    val density=LocalDensity.current
    val viewportWidth=with(density){LocalWindowInfo.current.containerSize.width.toDp()}
    val coverHeight=profileCoverHeight()
    val colors=MaterialTheme.colorScheme
    val shape=RoundedCornerShape(20.dp)
    BoxWithConstraints(Modifier.fillMaxWidth().padding(top=16.dp)) {
        val frameHeight=coverHeight*(maxWidth/viewportWidth.coerceAtLeast(maxWidth))
        val overflow=if(image==null)0f else with(density){
            (maxWidth.toPx()*image.height/image.width-frameHeight.toPx()).coerceAtLeast(0f)
        }
        val canDrag=enabled&&overflow>0f
        val drag=rememberDraggableState {pixels->if(overflow>0f)onMove(-pixels/overflow)}
        Box(Modifier.fillMaxWidth().height(frameHeight).clip(shape)
            .background(colors.surfaceVariant).border(1.dp,colors.outlineVariant,shape)) {
            Box(Modifier.matchParentSize().testTag("edit-cover-frame")
                .clickable(enabled=enabled,role=Role.Button,onClickLabel=if(hasCover)"更换封面" else "上传封面",onClick=onPick)
                .draggable(drag,Orientation.Vertical,enabled=canDrag)
                .semantics {
                    if(canDrag)customActions=listOf(
                        CustomAccessibilityAction("上移封面"){onMove(.1f)},
                        CustomAccessibilityAction("下移封面"){onMove(-.1f)},
                    )
                },contentAlignment=Alignment.Center) {
                if(image!=null)ProfileCoverImage(image,Modifier.fillMaxSize().testTag("edit-cover-preview"),position)
                else if(!hasCover)Text("上传封面",style=MaterialTheme.typography.labelLarge,color=colors.onSurfaceVariant)
            }
            if(hasCover)IconButton(onClick=onRemove,enabled=enabled,modifier=Modifier.align(Alignment.TopEnd)
                .testTag("edit-cover-remove")) {
                Box(Modifier.size(28.dp).background(colors.onSurface,CircleShape),contentAlignment=Alignment.Center) {
                    SoIcon(SoSymbol.CLOSE,"移除封面",Modifier.size(16.dp),tint=colors.surface)
                }
            }
        }
    }
}
