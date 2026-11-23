package com.semiovert.app.core.designsystem

/** Foreground motion requests high refresh; quiet windows return control to Android. */
object MotionFramePolicy {
    const val TARGET_FPS=120f
    const val IDLE_DELAY_MS=400L

    // Before API 34, preferredRefreshRate must name a supported rate at this resolution.
    fun legacyRefreshRate(supported: List<Float>): Float =
        supported.filter {it<=TARGET_FPS+.5f}.maxOrNull() ?: supported.minOrNull() ?: 0f
}

/** Tracks input across Activity/Dialog windows using uptime, independently of animation time. */
internal class MotionFrameActivity {
    var highRequested=false
        private set
    private var lastActivityMs=0L
    private val touching=mutableSetOf<Any>()

    fun activity(nowMs: Long) {
        lastActivityMs=nowMs
        highRequested=true
    }

    fun touch(window: Any,down: Boolean,nowMs: Long) {
        if(down)touching.add(window) else touching.remove(window)
        activity(nowMs)
    }

    fun forget(window: Any) {touching.remove(window)}

    fun remainingIdleMillis(nowMs: Long): Long? =
        if(touching.isNotEmpty())null else (lastActivityMs+MotionFramePolicy.IDLE_DELAY_MS-nowMs).coerceAtLeast(0)

    fun releaseIfIdle(nowMs: Long): Boolean {
        if(!highRequested||remainingIdleMillis(nowMs)!=0L)return false
        highRequested=false
        return true
    }

    fun clear() {
        touching.clear()
        highRequested=false
    }
}
