package com.semiovert.app

import android.os.Bundle
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.preferredFrameRate
import androidx.compose.ui.graphics.luminance
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.lifecycleScope
import com.semiovert.app.core.data.PreviewViewModel
import com.semiovert.app.core.designsystem.SoTheme
import com.semiovert.app.core.designsystem.MotionEnvironment
import com.semiovert.app.core.designsystem.IdentityIntroHost
import com.semiovert.app.core.designsystem.MotionFramePolicy
import com.semiovert.app.core.designsystem.ThemeRevealHost
import com.semiovert.app.navigation.AppNavigation
import com.semiovert.app.feature.home.prepareHomeCovers
import kotlinx.coroutines.async

class MainActivity: ComponentActivity() {
    private var introCanStart by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Run cold-start data/image preparation during framework startup and the intro,
        // not after the identity animation. Recreation and reduced motion need no preload.
        val homeCovers=if(savedInstanceState==null&&Settings.Global.getFloat(contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)>0f)
            lifecycleScope.async {prepareHomeCovers(resources)} else null
        introCanStart=savedInstanceState!=null
        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.S) {
            splashScreen.setOnExitAnimationListener { splash ->
                // The app owns the intro; do not play a second exit animation over it.
                splash.remove()
                introCanStart=true
            }
        }
        enableEdgeToEdge()
        setContent {
            val model: PreviewViewModel=viewModel()
            val state by model.state.collectAsStateWithLifecycle()
            MotionEnvironment {ThemeRevealHost {SoTheme(state.theme) {
                val light=MaterialTheme.colorScheme.surface.luminance()>.5f
                SideEffect {WindowCompat.getInsetsController(window,window.decorView).apply {isAppearanceLightNavigationBars=light}}
                Surface(Modifier.fillMaxSize().preferredFrameRate(MotionFramePolicy.TARGET_FPS),color=MaterialTheme.colorScheme.background) {IdentityIntroHost(state.signedIn,state.profile.avatar,canStart=introCanStart,homeCovers=homeCovers) {AppNavigation(state,model) {statusLight ->WindowCompat.getInsetsController(window,window.decorView).isAppearanceLightStatusBars=statusLight}}}
            }}}
        }
    }

    override fun onEnterAnimationComplete() {
        super.onEnterAnimationComplete()
        // Also covers older Android versions and launches without a system splash.
        introCanStart=true
    }

    override fun onResume() {
        super.onResume()
        requestMotionFrameRate()
    }

    @Suppress("DEPRECATION")
    private fun requestMotionFrameRate() {
        val display=window.decorView.display ?: windowManager.defaultDisplay
        val current=display.mode
        val rates=display.supportedModes.filter {it.physicalWidth==current.physicalWidth&&it.physicalHeight==current.physicalHeight}.map {it.refreshRate}
        window.attributes=window.attributes.apply {
            preferredRefreshRate=if(Build.VERSION.SDK_INT>=34)MotionFramePolicy.TARGET_FPS else MotionFramePolicy.legacyRefreshRate(rates)
        }
    }
}
