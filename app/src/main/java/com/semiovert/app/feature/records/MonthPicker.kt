package com.semiovert.app.feature.records

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.semiovert.app.core.designsystem.*
import java.time.YearMonth
import kotlin.math.abs
import kotlinx.coroutines.launch

private val pickerYears = 2021..2031
private val pickerMonths = 1..12
private const val WHEEL_FLING_MULTIPLIER = 1.2f

@Composable
internal fun MonthPicker(initial: YearMonth, onApply: (YearMonth)->Unit, onDismiss: ()->Unit) {
    val years = rememberLazyListState(initial.year - pickerYears.first)
    val months = rememberLazyListState(initial.monthValue - pickerMonths.first)
    SoBottomSheet(
        onDismissRequest = onDismiss,
        enterMillis = 280,
        exitMillis = 220,
        dimBackground = false,
        dragHandleHeight = 20.dp,
        scrollContent = false,
    ) { close ->
        val colors = MaterialTheme.colorScheme
        val wheelStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 26.sp, lineHeight = 36.sp)
        val rowHeight = with(LocalDensity.current) {
            maxOf(48.dp, wheelStyle.lineHeight.toDp() + 12.dp)
        }
        val actionSize = 48.dp
        val actionSpacing = ButtonDefaults.MinWidth - actionSize
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 20.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(end = actionSpacing / 2),
                horizontalArrangement = Arrangement.spacedBy(actionSpacing, Alignment.End),
            ) {
                val applySpark = rememberSparkControl()
                val cancelSpark = rememberSparkControl()
                FilledIconButton(
                    onClick = {
                        val selection = YearMonth.of(
                            pickerYears.first + years.centeredIndex(),
                            pickerMonths.first + months.centeredIndex(),
                        )
                        close { onApply(selection) }
                    },
                    modifier = applySpark.modifier.size(actionSize).testTag("month-apply"),
                    interactionSource = applySpark.source,
                    shape = CircleShape,
                ) { SoIcon(SoSymbol.CHECK, "确定", tint = LocalContentColor.current) }
                OutlinedIconButton(
                    onClick = { close {} },
                    modifier = cancelSpark.modifier.size(actionSize).testTag("month-cancel"),
                    interactionSource = cancelSpark.source,
                    shape = CircleShape,
                ) { SoIcon(SoSymbol.CLOSE, "取消", tint = LocalContentColor.current) }
            }
            BoxWithConstraints(
                Modifier.fillMaxWidth().height(rowHeight * 5).wheelEdgeFade(colors.surfaceContainerLow, rowHeight)
            ) {
                val centerPadding = ((maxHeight - rowHeight) / 2).coerceAtLeast(0.dp)
                Box(Modifier.align(Alignment.Center).fillMaxWidth().height(rowHeight)
                    .background(colors.primaryContainer, RoundedCornerShape(14.dp)))
                Row(Modifier.fillMaxSize()) {
                    MonthWheel(pickerYears, "年份", years, rowHeight, centerPadding, wheelStyle,
                        Modifier.weight(1f).fillMaxHeight().testTag("month-wheel-year"))
                    MonthWheel(pickerMonths, "月份", months, rowHeight, centerPadding, wheelStyle,
                        Modifier.weight(1f).fillMaxHeight().testTag("month-wheel-month"))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MonthWheel(
    values: IntRange,
    label: String,
    state: LazyListState,
    rowHeight: Dp,
    centerPadding: Dp,
    numberStyle: TextStyle,
    modifier: Modifier,
) {
    val scope = rememberCoroutineScope()
    val motion = LocalMotionEnabled.current
    val selectedIndex by remember(state) { derivedStateOf(structuralEqualityPolicy()) { state.centeredIndex() } }
    val blurRadius = with(LocalDensity.current) { 2.dp.toPx() }
    val edgeBlur = remember(blurRadius) { BlurEffect(blurRadius, blurRadius, TileMode.Decal) }
    val colors = MaterialTheme.colorScheme
    val snap = rememberSnapFlingBehavior(state, snapPosition = SnapPosition.Center)
    val inertialSnap = remember(snap) {
        object : FlingBehavior {
            override suspend fun ScrollScope.performFling(initialVelocity: Float): Float =
                with(snap) { performFling(initialVelocity * WHEEL_FLING_MULTIPLIER) } / WHEEL_FLING_MULTIPLIER
        }
    }
    LazyColumn(
        modifier = modifier.selectableGroup().semantics { contentDescription = label },
        state = state,
        contentPadding = PaddingValues(vertical = centerPadding),
        flingBehavior = if(motion) inertialSnap else snap,
    ) {
        items(values.count()) { index ->
            val isCentered = selectedIndex == index
            val spark = rememberSparkControl()
            Box(
                Modifier.fillMaxWidth().height(rowHeight).then(spark.modifier).selectable(
                    selected = isCentered,
                    interactionSource = spark.source,
                    indication = if(LocalSparkFeedbackEnabled.current) LocalIndication.current else null,
                    role = Role.RadioButton,
                    onClick = {
                        scope.launch {
                            if(motion) state.animateScrollToItem(index) else state.scrollToItem(index)
                        }
                    },
                ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    (values.first + index).toString().withInterfaceDigits(),
                    modifier = Modifier.graphicsLayer {
                        val layout = state.layoutInfo
                        val item = layout.visibleItemsInfo.firstOrNull { it.index == index }
                        renderEffect = if(item != null && abs(item.centerOffset - layout.viewportCenter) >
                            maxOf(item.size / 2f, layout.viewportSize.height / 2f - item.size)
                        ) edgeBlur else null
                    },
                    style = numberStyle,
                    color = if(isCentered) colors.onPrimaryContainer else colors.onSurfaceVariant,
                    fontWeight = if(isCentered) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                )
            }
        }
    }
}

private val LazyListItemInfo.centerOffset: Float get() = offset + size / 2f
private val LazyListLayoutInfo.viewportCenter: Float get() = (viewportStartOffset + viewportEndOffset) / 2f

private fun LazyListState.centeredIndex(): Int {
    val layout = layoutInfo
    return layout.visibleItemsInfo.minByOrNull { abs(it.centerOffset - layout.viewportCenter) }?.index
        ?: firstVisibleItemIndex
}

private fun Modifier.wheelEdgeFade(surface: Color, rowHeight: Dp) = drawWithCache {
    val fadeHeight = minOf(rowHeight.toPx() * 1.2f, (size.height - rowHeight.toPx()) / 2f)
    if(fadeHeight <= 0f) {
        onDrawWithContent { drawContent() }
    } else {
        val clear = surface.copy(alpha = 0f)
        val topFade = Brush.verticalGradient(listOf(surface, clear), endY = fadeHeight)
        val bottomFade = Brush.verticalGradient(listOf(clear, surface), startY = size.height - fadeHeight, endY = size.height)
        onDrawWithContent {
            drawContent()
            val edgeSize = Size(size.width, fadeHeight)
            drawRect(topFade, size = edgeSize)
            drawRect(bottomFade, topLeft = Offset(0f, size.height - fadeHeight), size = edgeSize)
        }
    }
}
