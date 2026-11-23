package com.semiovert.app.core.designsystem

import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import android.view.Window
import androidx.compose.runtime.MonotonicFrameClock

/** Uses real foreground frame demand; WebView canvas invalidations do not enter this clock. */
internal class MotionDemandFrameClock(private val delegate: MonotonicFrameClock): MonotonicFrameClock {
    override suspend fun <R> withFrameNanos(onFrame: (Long)->R): R {
        AppMotionFrameRate.activity()
        return delegate.withFrameNanos(onFrame)
    }
}

/** Activity and Dialog windows share one request, so a quiet window cannot lower an active one. */
internal object AppMotionFrameRate {
    private val handler=Handler(Looper.getMainLooper())
    private val motion=MotionFrameActivity()
    private val windows=linkedMapOf<Window,Registration>()
    private var idleCheckPending=false
    private val idleCheck=object: Runnable {
        override fun run() {
            idleCheckPending=false
            if(motion.releaseIfIdle(SystemClock.uptimeMillis()))applyRequests()
            else scheduleIdleCheck()
        }
    }

    fun register(window: Window): AutoCloseable {
        val registration=Registration(window)
        windows[window]=registration
        registration.install()
        activity()
        registration.applyRequest(motion.highRequested)
        return registration
    }

    fun activity() {
        if(windows.isEmpty())return
        val wasHigh=motion.highRequested
        motion.activity(SystemClock.uptimeMillis())
        if(!wasHigh)applyRequests()
        scheduleIdleCheck()
    }

    private fun touch(window: Window,down: Boolean) {
        val wasHigh=motion.highRequested
        motion.touch(window,down,SystemClock.uptimeMillis())
        if(!wasHigh)applyRequests()
        scheduleIdleCheck()
    }

    private fun scheduleIdleCheck() {
        if(idleCheckPending||!motion.highRequested)return
        val remaining=motion.remainingIdleMillis(SystemClock.uptimeMillis())?:return
        idleCheckPending=true
        handler.postDelayed(idleCheck,remaining)
    }

    private fun applyRequests() {
        windows.values.forEach {it.applyRequest(motion.highRequested)}
    }

    private class Registration(private val window: Window): AutoCloseable {
        private val originalCallback=window.callback
        private val callback=object: Window.Callback by originalCallback {
            override fun dispatchTouchEvent(event: MotionEvent): Boolean {
                when(event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> touch(window,true)
                    MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL -> touch(window,false)
                    MotionEvent.ACTION_MOVE -> activity()
                }
                return originalCallback.dispatchTouchEvent(event)
            }

            override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
                if(event.actionMasked==MotionEvent.ACTION_SCROLL)activity()
                return originalCallback.dispatchGenericMotionEvent(event)
            }

            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                activity()
                return originalCallback.dispatchKeyEvent(event)
            }
        }
        // Native WebView scrolling can outlast the finger; its decorative canvas does not scroll.
        private val scrollListener=ViewTreeObserver.OnScrollChangedListener {activity()}
        private var lastRequest: Float?=null

        fun install() {
            window.callback=callback
            window.decorView.viewTreeObserver.addOnScrollChangedListener(scrollListener)
        }

        @Suppress("DEPRECATION")
        fun applyRequest(high: Boolean) {
            val display=window.decorView.display?:window.windowManager?.defaultDisplay
            val rate=when {
                !high -> 0f
                Build.VERSION.SDK_INT>=34 -> MotionFramePolicy.TARGET_FPS
                display!=null -> {
                    val mode=display.mode
                    MotionFramePolicy.legacyRefreshRate(display.supportedModes.filter {
                        it.physicalWidth==mode.physicalWidth&&it.physicalHeight==mode.physicalHeight
                    }.map {it.refreshRate})
                }
                else -> 0f
            }
            if(lastRequest==rate)return
            lastRequest=rate
            window.attributes=window.attributes.apply {preferredRefreshRate=rate}
            if(Build.VERSION.SDK_INT>=35)window.decorView.setRequestedFrameRate(
                if(high)rate else View.REQUESTED_FRAME_RATE_CATEGORY_NO_PREFERENCE)
        }

        override fun close() {
            if(windows[window]!==this)return
            windows.remove(window)
            motion.forget(window)
            if(window.callback===callback)window.callback=originalCallback
            window.decorView.viewTreeObserver.removeOnScrollChangedListener(scrollListener)
            applyRequest(false)
            if(windows.isEmpty()) {
                handler.removeCallbacks(idleCheck)
                idleCheckPending=false
                motion.clear()
            } else scheduleIdleCheck()
        }
    }
}
