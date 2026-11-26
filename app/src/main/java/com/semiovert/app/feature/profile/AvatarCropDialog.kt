package com.semiovert.app.feature.profile

import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.semiovert.app.core.designsystem.LocalMotionEnabled
import com.semiovert.app.core.designsystem.StatusText
import com.semiovert.app.core.designsystem.accentTextButtonColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

private val cropSaver = listSaver<AvatarCrop, Float>(
    save = { listOf(it.zoom, it.offset.x, it.offset.y) },
    restore = { AvatarCrop(it[0], Offset(it[1], it[2])) },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AvatarCropDialog(
    image: ImageBitmap,
    saving: Boolean,
    saveFailed: Boolean,
    onCancel: () -> Unit,
    onConfirm: (AvatarCrop) -> Unit,
) {
    val geometry = remember(image) { AvatarCropGeometry(image.width, image.height) }
    var crop by rememberSaveable(stateSaver = cropSaver) { mutableStateOf(AvatarCrop()) }
    var dragging by remember { mutableStateOf(false) }
    var settle by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val motion = LocalMotionEnabled.current
    val paint = remember { Paint(Paint.FILTER_BITMAP_FLAG) }
    val sliderColors = SliderDefaults.colors()

    fun settleCrop(velocity: Offset) {
        val constrained = geometry.clamp(crop)
        val legal = (constrained.offset - crop.offset).getDistance() < .0001f
        val target = if (motion && legal) geometry.clamp(crop.copy(offset = crop.offset + velocity * .12f)) else constrained
        if (!motion || target.offset == crop.offset) {
            crop = target
            return
        }
        settle?.cancel()
        settle = scope.launch {
            Animatable(crop.offset, Offset.VectorConverter).animateTo(target.offset,
                spring(dampingRatio = .88f, stiffness = 540f, visibilityThreshold = Offset(.001f, .001f))) {
                crop = crop.copy(offset = value)
            }
        }
    }

    fun nudge(by: Offset): Boolean {
        settle?.cancel()
        val next = geometry.clamp(crop.copy(offset = crop.offset + by))
        if (next == crop) return false
        crop = next
        return true
    }

    Dialog(onDismissRequest = { if (!saving) onCancel() },
        properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp),
            contentAlignment = Alignment.Center) {
            val side = minOf(maxWidth - 24.dp, (maxHeight - 156.dp).coerceAtLeast(80.dp), 360.dp)
            Surface(Modifier.widthIn(max = 408.dp).fillMaxWidth(),
                shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp).testTag("avatar-crop-dialog"),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                            TextButton(onClick = {
                                settle?.cancel()
                                crop = AvatarCrop()
                            }, enabled = !saving && crop != AvatarCrop(), colors = accentTextButtonColors()) { Text("重置") }
                        }
                        Text("头像", Modifier.weight(1f).semantics { heading() }, textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.titleMedium)
                        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                            TextButton(onClick = {
                                settle?.cancel()
                                crop = geometry.clamp(crop)
                                onConfirm(crop)
                            }, enabled = !saving, colors = accentTextButtonColors(), modifier = Modifier.testTag("avatar-crop-confirm")) { Text("完成") }
                        }
                    }
                    Canvas(Modifier.size(side).clip(RoundedCornerShape(16.dp)).background(Color.Black)
                        .testTag("avatar-crop-frame").semantics {
                            contentDescription = "头像裁剪"
                            if (!saving) customActions = listOf(
                                CustomAccessibilityAction("照片左移") { nudge(Offset(-.08f, 0f)) },
                                CustomAccessibilityAction("照片右移") { nudge(Offset(.08f, 0f)) },
                                CustomAccessibilityAction("照片上移") { nudge(Offset(0f, -.08f)) },
                                CustomAccessibilityAction("照片下移") { nudge(Offset(0f, .08f)) },
                            )
                        }.pointerInput(geometry, saving, motion) {
                            if (saving) return@pointerInput
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                val viewSide = size.width.toFloat().coerceAtLeast(1f)
                                val tracker = VelocityTracker().apply { addPosition(down.uptimeMillis, down.position) }
                                var raw = geometry.clamp(crop)
                                var pan = Offset.Zero
                                var scale = 1f
                                var multiplePointers = false
                                var started = false
                                var released = false
                                settle?.cancel()
                                try {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        if (event.changes.any { it.isConsumed }) break
                                        if (event.changes.count { it.pressed } > 1) multiplePointers = true
                                        event.changes.firstOrNull { it.id == down.id && it.pressed }?.let {
                                            tracker.addPosition(it.uptimeMillis, it.position)
                                        }
                                        if (event.changes.none { it.pressed }) {
                                            released = true
                                            break
                                        }
                                        val center = event.calculateCentroid(useCurrent = false)
                                        if (center == Offset.Unspecified) continue
                                        val movement = event.calculatePan()
                                        val zoom = event.calculateZoom()
                                        if (!started) {
                                            pan += movement
                                            scale *= zoom
                                            if (pan.getDistance() <= viewConfiguration.touchSlop &&
                                                abs(1f - scale) * viewSide / 2f <= viewConfiguration.touchSlop) continue
                                            started = true
                                            dragging = true
                                            raw = raw.transform(scale, center / viewSide - Offset(.5f, .5f), pan / viewSide)
                                        } else {
                                            raw = raw.transform(zoom, center / viewSide - Offset(.5f, .5f), movement / viewSide)
                                        }
                                        crop = geometry.resisted(raw)
                                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                                    }
                                } finally {
                                    dragging = false
                                    val speed = if (started && released && !multiplePointers) tracker.calculateVelocity() else null
                                    settleCrop(if (speed == null) Offset.Zero else Offset(speed.x, speed.y) / viewSide)
                                }
                            }
                        }) {
                        val viewSide = size.minDimension
                        val bounds = geometry.imageRect(crop)
                        drawIntoCanvas { canvas ->
                            canvas.nativeCanvas.drawBitmap(image.asAndroidBitmap(), null,
                                RectF(bounds.left * viewSide, bounds.top * viewSide, bounds.right * viewSide, bounds.bottom * viewSide), paint)
                        }
                        val radius = viewSide * AVATAR_OPENING / 2f
                        val opening = Rect(center, radius)
                        val circle = Path().apply { addOval(opening) }
                        clipPath(circle, ClipOp.Difference) { drawRect(Color.Black.copy(alpha = .58f)) }
                        if (dragging) clipPath(circle) {
                            val grid = Color.White.copy(alpha = .55f)
                            for (third in 1..2) {
                                val x = opening.left + opening.width * third / 3
                                val y = opening.top + opening.height * third / 3
                                drawLine(grid, Offset(x, opening.top), Offset(x, opening.bottom), 1.dp.toPx())
                                drawLine(grid, Offset(opening.left, y), Offset(opening.right, y), 1.dp.toPx())
                            }
                        }
                        drawCircle(Color.White, radius, style = Stroke((if (dragging) 2.dp else 1.dp).toPx()))
                    }
                    Slider(value = crop.zoom, onValueChange = { zoom ->
                        settle?.cancel()
                        crop = geometry.clamp(crop.transform(zoom / crop.zoom))
                    }, enabled = !saving, valueRange = 1f..AVATAR_MAX_ZOOM,
                        colors = sliderColors,
                        track = { slider ->
                            val rail = if (saving) sliderColors.disabledInactiveTrackColor else sliderColors.inactiveTrackColor
                            val fill = if (saving) sliderColors.disabledActiveTrackColor else sliderColors.activeTrackColor
                            Canvas(Modifier.fillMaxWidth().height(16.dp).clip(CircleShape).testTag("avatar-crop-track")) {
                                val filled = size.width * ((slider.value - 1f) / (AVATAR_MAX_ZOOM - 1f)).coerceIn(0f, 1f)
                                val start = if (layoutDirection == LayoutDirection.Rtl) size.width - filled else 0f
                                drawRect(rail)
                                drawRect(fill, topLeft = Offset(start, 0f), size = Size(filled, size.height))
                            }
                        },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
                            .testTag("avatar-crop-zoom").semantics { contentDescription = "缩放" })
                    if (saving) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (saveFailed) StatusText("无法保存头像", color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = onCancel, enabled = !saving, colors = accentTextButtonColors()) { Text("取消") }
                }
            }
        }
    }
}
