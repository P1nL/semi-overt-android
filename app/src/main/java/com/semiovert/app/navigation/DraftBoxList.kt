package com.semiovert.app.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.semiovert.app.core.designsystem.LocalMotionEnabled
import com.semiovert.app.core.designsystem.withInterfaceDigits
import com.semiovert.app.feature.editor.EditorDraft
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

internal object DraftRowSwipe {
    const val DELETE_WIDTH = 64f
    fun offset(raw: Float): Float {
        fun rubber(distance: Float) = distance * 32f * .55f / (32f + .55f * abs(distance))
        return when {
            raw > 0f -> rubber(raw)
            raw < -DELETE_WIDTH -> -DELETE_WIDTH + rubber(raw + DELETE_WIDTH)
            else -> raw
        }
    }
    fun reveals(offset: Float, velocity: Float) =
        if (abs(velocity) >= 110f) velocity < 0f else offset + velocity * .499f <= -DELETE_WIDTH / 2f
}

@Composable
internal fun DraftBoxList(
    drafts: List<EditorDraft>,
    background: Color,
    deletingKey: String?,
    onOpen: (EditorDraft) -> Unit,
    onDelete: (EditorDraft) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = rememberLazyListState()
    val motion = LocalMotionEnabled.current
    var swipeOwner by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(state.isScrollInProgress) { if (state.isScrollInProgress) swipeOwner = null }
    BackHandler(swipeOwner != null) { swipeOwner = null }
    Box(modifier) {
        LazyColumn(Modifier.fillMaxSize().testTag("draft-box-list"), state = state,
            contentPadding = PaddingValues(top = 2.dp, bottom = 2.dp)) {
            itemsIndexed(drafts, key = { index, draft -> draft.key.ifBlank { "draft-$index" } }) { index, draft ->
                val key = draft.key.ifBlank { "draft-$index" }
                Column(Modifier.animateItem(
                    fadeInSpec = if (motion) tween(160) else null,
                    placementSpec = if (motion) tween(240) else null,
                    fadeOutSpec = if (motion) tween(160) else null,
                )) {
                    DraftSwipeRow(draft, index, background, swipeOwner == key, deletingKey == key,
                        onClaim = { swipeOwner = key }, onClose = { swipeOwner = null },
                        onOpen = { onOpen(draft) }, onDelete = { onDelete(draft) })
                    if (index < drafts.lastIndex) HorizontalDivider(
                        Modifier.padding(horizontal = 24.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .6f))
                }
            }
        }
        DraftScrollThumb(state)
    }
}

@Composable
private fun DraftScrollThumb(state: LazyListState) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .4f)
    // Draw the thumb alone: the track is transparent and the list keeps the whole touch area.
    Canvas(Modifier.fillMaxSize().padding(end = 8.dp, top = 6.dp, bottom = 6.dp)
        .testTag("draft-box-scrollbar").clearAndSetSemantics {}) {
        val indicator = state.scrollIndicatorState ?: return@Canvas
        val content = indicator.contentSize.toFloat()
        val viewport = indicator.viewportSize.toFloat()
        if (content <= viewport || viewport <= 0f || content == Int.MAX_VALUE.toFloat()) return@Canvas
        val thumb = (size.height * viewport / content).coerceIn(24.dp.toPx(), size.height)
        val fraction = (indicator.scrollOffset / (content - viewport)).coerceIn(0f, 1f)
        val top = (size.height - thumb) * fraction
        drawLine(color, Offset(size.width - 1.dp.toPx(), top + 1.dp.toPx()),
            Offset(size.width - 1.dp.toPx(), top + thumb - 1.dp.toPx()), 2.dp.toPx(), StrokeCap.Round)
    }
}

@Composable
private fun DraftSwipeRow(
    draft: EditorDraft,
    index: Int,
    background: Color,
    owner: Boolean,
    deleting: Boolean,
    onClaim: () -> Unit,
    onClose: () -> Unit,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    val density = LocalDensity.current.density
    val motion = LocalMotionEnabled.current
    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var rawOffset by remember { mutableFloatStateOf(0f) }
    var settling by remember { mutableStateOf<Job?>(null) }
    val offset = DraftRowSwipe.offset(rawOffset)
    fun settle(reveal: Boolean, velocity: Float = 0f) {
        settling?.cancel()
        settling = scope.launch {
            val target = if (reveal) -DraftRowSwipe.DELETE_WIDTH else 0f
            if (!motion) rawOffset = target
            else animate(rawOffset, target, initialVelocity = velocity,
                animationSpec = spring(dampingRatio = if (abs(velocity) >= 110f) .82f else 1f, stiffness = 676f)) { value, _ ->
                rawOffset = value
            }
        }
    }
    LaunchedEffect(owner) { if (!owner) settle(false) }
    val deleteExposed = owner && offset <= -32f
    val deleteColor = lerp(background, colors.error, .62f)
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(12.dp))) {
        Box(Modifier.matchParentSize().graphicsLayer {
            translationX = maxOf(0f, DraftRowSwipe.DELETE_WIDTH + offset) * density
            alpha = if (abs(offset) > .1f) 1f else 0f
        }.background(deleteColor)) {
            Box(Modifier.align(Alignment.CenterEnd).width(64.dp).fillMaxHeight()
                .testTag("draft-box-delete-$index")
                .then(if (!deleteExposed) Modifier.clearAndSetSemantics {} else Modifier)
                .clickable(enabled = deleteExposed && !deleting, role = Role.Button, onClickLabel = "删除${draft.displayTitle}",
                    interactionSource = null, indication = null, onClick = onDelete),
                contentAlignment = Alignment.Center) {
                if (deleting) CircularProgressIndicator(Modifier.size(20.dp), color = colors.onError, strokeWidth = 2.dp)
                else DraftTrashIcon(colors.onError)
            }
        }
        Column(Modifier.fillMaxWidth().graphicsLayer { translationX = offset * density }
            .background(background).testTag("draft-box-item-$index")
            .semantics {
                if(draft.deletable)customActions = listOf(CustomAccessibilityAction("删除草稿") { onDelete(); true })
            }
            .draggable(
                state = rememberDraggableState { delta -> rawOffset += delta / density },
                orientation = Orientation.Horizontal, enabled = !deleting&&draft.deletable,
                onDragStarted = { settling?.cancel(); onClaim() },
                onDragStopped = { velocity ->
                    val reveal = DraftRowSwipe.reveals(DraftRowSwipe.offset(rawOffset), velocity / density)
                    if (!reveal) onClose()
                    settle(reveal, velocity / density)
                },
            )
            .clickable(enabled = !deleting, role = Role.Button, onClickLabel = (if(draft.editable)"继续编辑" else "查看")+draft.displayTitle) {
                if (abs(offset) > 4f) { onClose(); settle(false) } else onOpen()
            }.padding(horizontal = 8.dp, vertical = 10.dp)) {
            Text(draft.displayTitle, maxLines = 2, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
            val preview = draft.reason ?: draft.preview
            if (preview.isNotBlank()) Text(preview, Modifier.padding(top = 4.dp),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            val metadata = listOfNotNull(draft.statusLabel, draft.modifiedLabel, "${draft.wordCount} 字").joinToString(" · ")
            Text(metadata.withInterfaceDigits(), Modifier.padding(top = 5.dp),
                style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun DraftTrashIcon(color: Color) {
    Canvas(Modifier.size(22.dp).semantics { contentDescription = "删除草稿" }) {
        val unit = size.width / 24f
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(color, Offset(x1 * unit, y1 * unit), Offset(x2 * unit, y2 * unit), 1.7f * unit, StrokeCap.Round)
        line(4f, 6f, 20f, 6f); line(9f, 3f, 15f, 3f)
        line(6f, 6f, 7f, 21f); line(7f, 21f, 17f, 21f); line(17f, 21f, 18f, 6f)
        line(10f, 10f, 10.5f, 17f); line(14f, 10f, 13.5f, 17f)
    }
}
