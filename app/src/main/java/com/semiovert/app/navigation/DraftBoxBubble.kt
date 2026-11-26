package com.semiovert.app.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.semiovert.app.core.designsystem.LocalMotionEnabled
import com.semiovert.app.core.designsystem.SoEase
import com.semiovert.app.core.designsystem.SoIcon
import com.semiovert.app.core.designsystem.SoSymbol
import com.semiovert.app.core.designsystem.ContentLoading
import com.semiovert.app.core.designsystem.RemoteState
import com.semiovert.app.core.designsystem.StatusText
import com.semiovert.app.core.designsystem.withInterfaceDigits
import com.semiovert.app.feature.editor.EditorDraft
import kotlin.math.roundToInt

@Stable
internal class DraftBoxState {
    var source by mutableStateOf<Offset?>(null)
        private set
    var expanded by mutableStateOf(false)
    val active get() = source != null

    fun open(centerInRoot: Offset) {
        if (active) return
        source = centerInRoot
        expanded = true
    }

    fun finish() { source = null }
}

internal val LocalDraftBox = staticCompositionLocalOf<DraftBoxState?> { null }

private class BubbleClip(private val bounds: Rect, private val radius: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density) =
        Outline.Rounded(RoundRect(bounds, CornerRadius(radius)))
}

/**
 * Only the outline changes size. The list is laid out once at its final width so
 * characters keep their size and line breaks while the button becomes a bubble.
 */
@Composable
internal fun DraftBoxBubble(
    state: DraftBoxState,
    drafts: List<EditorDraft>,
    loading: Boolean,
    failed: Boolean,
    onLoad: () -> Unit,
    onOpenDraft: (EditorDraft?) -> Unit,
    deletingKey: String? = null,
    onDeleteDraft: (EditorDraft) -> Unit = {},
    message: String? = null,
    onRetry: () -> Unit = onLoad,
) {
    val source = state.source ?: return
    val motion = LocalMotionEnabled.current
    val density = LocalDensity.current
    val colors = MaterialTheme.colorScheme
    val progress = remember(source) { Animatable(if (motion) 0f else 1f) }
    val focus = remember { FocusRequester() }
    var openAfterClosing by remember(source) { mutableStateOf(false) }
    var pendingDraft by remember(source) { mutableStateOf<EditorDraft?>(null) }
    val currentOpen by rememberUpdatedState(onOpenDraft)
    val dismiss = { openAfterClosing = false; state.expanded = false }
    LaunchedEffect(source) { onLoad(); focus.requestFocus() }
    LaunchedEffect(state.expanded, motion) {
        val target = if (state.expanded) 1f else 0f
        if (motion) progress.animateTo(target, tween(
            ((if (state.expanded) 480 else 440) * kotlin.math.abs(target - progress.value)).roundToInt().coerceAtLeast(1),
            easing = SoEase,
        )) else progress.snapTo(target)
        if (!state.expanded) {
            state.finish()
            if (openAfterClosing) currentOpen(pendingDraft)
        }
    }
    BackHandler { dismiss() }
    BoxWithConstraints(Modifier.fillMaxSize().testTag("draft-box-overlay")
        .semantics { isTraversalGroup = true; paneTitle = "草稿箱" }
        .focusRequester(focus).focusable()) {
        val scale = density.density
        val margin = 16f * scale
        val topInset = WindowInsets.safeDrawing.getTop(density).toFloat() + margin
        val bottomInset = WindowInsets.safeDrawing.getBottom(density).toFloat() + margin
        val screenWidth = constraints.maxWidth.toFloat()
        val screenHeight = constraints.maxHeight.toFloat()
        val width = minOf(392f * scale, screenWidth - margin * 2f)
        val height = minOf(420f * scale, screenHeight - topInset - bottomInset)
        val left = (source.x - width * source.x / screenWidth)
            .coerceIn(margin, screenWidth - margin - width)
        val top = (source.y + 26f * scale - height)
            .coerceIn(topInset, screenHeight - bottomInset - height)
        val target = Rect(left, top, left + width, top + height)
        val closed = Rect(source - Offset(26f, 26f) * scale, Size(52f, 52f) * scale)
        val amount = progress.value
        val bounds = androidx.compose.ui.geometry.lerp(closed, target, amount)
        val radius = (26f + 10f * amount) * scale
        val fullyOpen = amount >= 1f
        val contentAlpha = if (fullyOpen) 1f else ((amount - .97f) / .03f).coerceIn(0f, 1f)
        val tint = HomeDraftAction.BOX.colors
        val bubbleColor = lerp(tint.background, lerp(colors.surface, tint.background, .10f), amount)
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .18f * amount))
            .testTag("draft-box-dismiss")
            .clickable(interactionSource = null, indication = null, onClickLabel = "收起草稿箱", onClick = dismiss))
        Box(Modifier.offset((bounds.left / scale).dp, (bounds.top / scale).dp)
            .size((bounds.width / scale).dp, (bounds.height / scale).dp)
            .graphicsLayer {
                shape = RoundedCornerShape((radius / scale).dp)
                clip = true
                shadowElevation = (3f + 13f * amount) * scale
            }
            .background(bubbleColor).testTag("draft-box-bubble")
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(amount, 0f..1f) })
        Box(Modifier.fillMaxSize().graphicsLayer { shape = BubbleClip(bounds, radius); clip = true }) {
            Box(Modifier.offset((left / scale).dp, (top / scale).dp)
                .size((width / scale).dp, (height / scale).dp)
                .clickable(interactionSource = null, indication = null) {}
                .clearAndSetSemantics {})
            Column(Modifier.offset((left / scale).dp, (top / scale).dp)
                .size((width / scale).dp, (height / scale).dp)
                .graphicsLayer { alpha = contentAlpha }
                .then(if (!fullyOpen) Modifier.clearAndSetSemantics {} else Modifier)) {
                Row(Modifier.fillMaxWidth().padding(start = 24.dp, top = 4.dp, end = 12.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.width(36.dp))
                    Text((if (loading || drafts.isEmpty()) "草稿箱" else "${drafts.size}篇草稿").withInterfaceDigits(), Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = colors.onSurface)
                    IconButton(onClick = dismiss, modifier = Modifier.testTag("draft-box-close")) {
                        SoIcon(SoSymbol.CLOSE, "收起草稿箱")
                    }
                }
                HorizontalDivider(Modifier.padding(horizontal = 24.dp), color = colors.outlineVariant)
                if(!loading&&!failed&&message!=null) RemoteState(message,action="重试",onAction=onRetry)
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when {
                        loading -> Unit
                        failed -> RemoteState(message ?: "草稿读取失败", Modifier.align(Alignment.Center), "重试", onRetry)
                        drafts.isEmpty() -> Column(Modifier.fillMaxSize().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            IconButton(onClick = { openAfterClosing = true; state.expanded = false },
                                modifier = Modifier.size(56.dp).testTag("draft-box-create")) {
                                Icon(painterResource(HomeDraftAction.CREATE.icon), "创建草稿",
                                    Modifier.size(36.dp), tint = colors.onSurfaceVariant)
                            }
                            StatusText("暂无草稿", Modifier.padding(top = 16.dp),
                                style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                        }
                        else -> DraftBoxList(drafts, bubbleColor, deletingKey, onOpen = { draft ->
                            pendingDraft = draft
                            openAfterClosing = true
                            state.expanded = false
                        }, onDelete = onDeleteDraft, modifier = Modifier.fillMaxSize())
                    }
                    ContentLoading(loading&&state.expanded,"正在读取草稿箱",Modifier.align(Alignment.Center))
                }
                Spacer(Modifier.height(16.dp))
            }
            val iconCenter = androidx.compose.ui.geometry.lerp(source, Offset(left + 36f * scale, top + 28f * scale), amount)
            Icon(painterResource(HomeDraftAction.BOX.icon), null,
                Modifier.offset((iconCenter.x / scale - 12f).dp, (iconCenter.y / scale - 12f).dp).size(24.dp),
                tint = lerp(tint.foreground, colors.onSurface, amount))
        }
    }
}
