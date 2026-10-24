package com.semiovert.app.navigation

import androidx.compose.ui.geometry.Offset
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import com.semiovert.app.core.model.NavigationShortcut

internal object RadialNavigationGesture {
    const val DETACH_DISTANCE=216f
    const val MERGE_DISTANCE=75f
    const val PULL_DEAD_ZONE=20f
    const val MERGE_DEFORM_DISTANCE=84f
    const val SELECTED_SCALE=1.14f
    const val DETACH_MILLIS=120
    const val NECK_APPEAR_MILLIS=180
    const val HIT_RADIUS=34f
    fun scaleSpring()=spring<Float>(dampingRatio=.55f,stiffness=420f,visibilityThreshold=.001f)
    fun stretchSpring()=spring<Float>(dampingRatio=.52f,stiffness=260f,visibilityThreshold=.001f)
    fun detachMotion()=tween<Float>(durationMillis=DETACH_MILLIS,easing=FastOutSlowInEasing)
    fun neckAppearMotion()=tween<Float>(durationMillis=NECK_APPEAR_MILLIS,easing=FastOutSlowInEasing)
    fun mergeMotion()=spring<Float>(dampingRatio=1f,stiffness=180f,visibilityThreshold=.001f)

    // Whole-circle hits take priority while browsing; only an outward pull retains a target in empty space.
    fun target(position: Offset,offsets: List<Offset>,previous: Int?): Int? {
        val hit=offsets.indices.minByOrNull {(position-offsets[it]).getDistance()}
            ?.takeIf {(position-offsets[it]).getDistance()<=HIT_RADIUS}
        return hit?:previous?.takeIf {position.getDistance()>=offsets[it].getDistance()}
    }

    fun canPin(position: Offset)=position.getDistance()>DETACH_DISTANCE
    fun canMerge(position: Offset)=position.getDistance()<=MERGE_DISTANCE

    fun pullProgress(position: Offset,origin: Offset,rest: Offset=origin): Float {
        val start=maxOf(origin.getDistance(),rest.getDistance())
        val travel=position.getDistance()-start-PULL_DEAD_ZONE
        val range=(DETACH_DISTANCE-start-PULL_DEAD_ZONE).coerceAtLeast(1f)
        return (travel/range).coerceIn(0f,1f)
    }

    fun attachedCenter(position: Offset,origin: Offset,rest: Offset): Offset =
        rest+(position-origin)*(.22f*pullProgress(position,origin,rest))

    fun mergeStretch(distance: Float): Float =
        .5f*((MERGE_DEFORM_DISTANCE-distance)/(MERGE_DEFORM_DISTANCE-28f)).coerceIn(0f,1f)

    fun menuChoices(destination: Destination,readerMode: Boolean,shortcuts: List<NavigationShortcut>): List<Destination> {
        return Destination.entries.filter { (readerMode||it!=destination)&&shortcuts.none {shortcut->shortcut.route==it.route} }
    }
}

/** Every rendered, dragged and persisted shortcut uses this same scene-local centre. */
internal class ShortcutViewport(width: Float,height: Float,bottomInset: Float) {
    private val minimum=Offset(34f,34f)
    private val maximum=Offset((width-34f).coerceAtLeast(34f),(height-bottomInset-34f).coerceAtLeast(34f))
    private val span=Offset((maximum.x-minimum.x).coerceAtLeast(1f),(maximum.y-minimum.y).coerceAtLeast(1f))

    fun constrain(center: Offset)=Offset(center.x.coerceIn(minimum.x,maximum.x),center.y.coerceIn(minimum.y,maximum.y))
    fun center(shortcut: NavigationShortcut)=constrain(minimum+Offset(shortcut.x*span.x,shortcut.y*span.y))
    fun shortcut(route: String,center: Offset): NavigationShortcut {
        val point=constrain(center)-minimum
        return NavigationShortcut(route,point.x/span.x,point.y/span.y)
    }
}

/** A cancelled pointer must never undo an already-started absorption. */
@Stable internal class ShortcutDragState(initialCenter: Offset) {
    var center by mutableStateOf(initialCenter)
    var dragging by mutableStateOf(false);private set
    var merging by mutableStateOf(false);private set
    var absorption by mutableFloatStateOf(1f);private set
    private var grabOffset=Offset.Zero

    fun start(pointer: Offset=center) {if(!merging){grabOffset=pointer-center;dragging=true}}
    fun moveTo(pointer: Offset,viewport: ShortcutViewport) {if(dragging&&!merging)center=viewport.constrain(pointer-grabOffset)}
    fun cancel(saved: Offset) {if(!merging){center=saved;dragging=false}}
    fun release() {dragging=false}
    fun beginMerge() {dragging=false;merging=true}
    fun absorb(from: Offset,to: Offset,progress: Float) {
        val amount=progress.coerceIn(0f,1f)
        center=androidx.compose.ui.geometry.lerp(from,to,amount)
        absorption=1f-amount
    }
}

/** Contact creates a neck; proximity alone cannot create one before the circles touch. */
@Stable internal class ShortcutLiquidContact {
    var connected by mutableStateOf(false);private set
    fun next(distance: Float,mainRadius: Float,smallRadius: Float,active: Boolean): Boolean {
        val tangent=mainRadius+smallRadius
        return active&&smallRadius>.01f&&if(connected)distance<tangent+HOLD_DISTANCE else distance<=tangent
    }
    fun update(distance: Float,mainRadius: Float,smallRadius: Float,active: Boolean) {
        connected=next(distance,mainRadius,smallRadius,active)
    }
    companion object {const val HOLD_DISTANCE=18f}
}

internal fun mainCirclePageAfter(current: Destination,target: Destination,shortcut: Boolean)=if(shortcut)current else target
