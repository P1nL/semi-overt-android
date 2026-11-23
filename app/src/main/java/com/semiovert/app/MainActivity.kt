package com.semiovert.app

import android.os.Bundle
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.semiovert.app.core.data.PreviewViewModel
import com.semiovert.app.core.data.LocalBusiness
import com.semiovert.app.core.model.PreviewProfile
import com.semiovert.app.core.designsystem.SoTheme
import com.semiovert.app.core.designsystem.MotionEnvironment
import com.semiovert.app.core.designsystem.IdentityIntroHost
import com.semiovert.app.core.designsystem.AppMotionFrameRate
import com.semiovert.app.core.designsystem.ThemeRevealHost
import com.semiovert.app.core.designsystem.rememberProfilePhoto
import com.semiovert.app.navigation.AppNavigation

class MainActivity: ComponentActivity() {
    private var frameRateRegistration: AutoCloseable?=null

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_SemiOvert)
        super.onCreate(savedInstanceState)
        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.S)setTranslucent(false)
        enableEdgeToEdge()
        setContent {
            val model: PreviewViewModel=viewModel()
            val preferences by model.state.collectAsStateWithLifecycle()
            val business = (application as SemiOvertApplication).business
            val session = business?.session?.collectAsStateWithLifecycle()?.value
            val account = session?.account
            val historyRevision = business?.history?.changes?.collectAsStateWithLifecycle()?.value
            val snapshots = remember(business, account?.id, historyRevision) {
                account?.id?.let { business?.history?.enabled(it) } == true
            }
            LaunchedEffect(business) { business?.restore() }
            LaunchedEffect(business, account?.id) {
                if (business != null && account != null) {
                    try { business.updateCurrentProfile(account.id) }
                    catch (error: kotlinx.coroutines.CancellationException) { throw error }
                    catch (_: com.semiovert.app.core.data.ApiFailure) { }
                }
            }
            val state = if (business == null) preferences else preferences.copy(
                signedIn = account != null,
                profile = account?.profile ?: PreviewProfile(name = "", handle = "", bio = ""),
                snapshots = snapshots,
                snapshotsCleared = false,
            )
            val photoKey=state.profile.avatarPhoto.takeIf {state.signedIn}
            val photo=rememberProfilePhoto(photoKey)
            CompositionLocalProvider(LocalBusiness provides business) {
            MotionEnvironment {ThemeRevealHost {SoTheme(state.theme) {
                val light=MaterialTheme.colorScheme.surface.luminance()>.5f
                SideEffect {WindowCompat.getInsetsController(window,window.decorView).apply {isAppearanceLightNavigationBars=light}}
                Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background) {
                    IdentityIntroHost(state.signedIn,state.profile.avatar,photo=photo.bitmap,photoKey=photoKey) {
                        AppNavigation(state,model) {statusLight ->WindowCompat.getInsetsController(window,window.decorView).isAppearanceLightStatusBars=statusLight}
                    }
                }
            }}}
            }
        }
    }

    override fun onStart() {
        super.onStart()
        frameRateRegistration=AppMotionFrameRate.register(window)
    }

    override fun onStop() {
        frameRateRegistration?.close()
        frameRateRegistration=null
        super.onStop()
    }
}
