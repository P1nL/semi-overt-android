package com.semiovert.app.core.designsystem

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.platform.WindowRecomposerPolicy
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.compose.ui.platform.createLifecycleAwareWindowRecomposer

/** Keep authored timing while still honoring the system's Remove animations setting. */
internal class AppMotionDurationScale(systemScale: Float): MotionDurationScale {
    override var scaleFactor by mutableFloatStateOf(if(systemScale>0f)1f else 0f)
        private set

    fun updateSystemScale(systemScale: Float) {
        scaleFactor=if(systemScale>0f)1f else 0f
    }
}

@OptIn(InternalComposeUiApi::class)
internal fun installAppMotionPolicy(context: Context) {
    val resolver=context.applicationContext.contentResolver
    fun systemScale()=Settings.Global.getFloat(resolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)
    val motionScale=AppMotionDurationScale(systemScale())
    // This observer and factory live for the application process, never an Activity.
    resolver.registerContentObserver(
        Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),false,
        object: ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {motionScale.updateSystemScale(systemScale())}
        },
    )
    motionScale.updateSystemScale(systemScale())
    // Observe Compose frame demand while retaining its lifecycle, VSync and authored timing.
    WindowRecomposerPolicy.setFactory {view ->
        val frameClock=checkNotNull(AndroidUiDispatcher.CurrentThread[MonotonicFrameClock])
        view.createLifecycleAwareWindowRecomposer(coroutineContext=motionScale+MotionDemandFrameClock(frameClock))
    }
}
