package com.semiovert.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.semiovert.app.core.data.ProfileImages
import com.semiovert.app.core.designsystem.*
import com.semiovert.app.core.model.Cover
import com.semiovert.app.core.model.PreviewProfile
import com.semiovert.app.core.model.PreviewState
import com.semiovert.app.core.model.ThemeMode
import com.semiovert.app.feature.profile.ProfileScreen
import kotlin.math.abs
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Isolated profile layout: leave the preview account and its drafts intact. */
class ProfileCoverLayoutTest {
    @get:Rule val app=createComposeRule()

    @Test fun fullWidthCoverEndsAtAvatarEquatorAndTextFollowsBelow() {
        var cover by mutableStateOf<Cover?>(null)
        var edits=0
        app.setContent {
            SoTheme(ThemeMode.LIGHT) {
                CompositionLocalProvider(LocalMotionEnabled provides false,LocalIdentity provides remember {IdentityAnchor(true,0)}) {
                    ProfileScreen(PreviewState(signedIn=true,profile=PreviewProfile(cover=cover)),{},
                        {edits++},{},{})
                }
            }
        }
        fun assertGeometry() {
            val scene=app.onNodeWithTag("profile-scene").getUnclippedBoundsInRoot()
            val banner=app.onNodeWithTag("profile-cover",useUnmergedTree=true).getUnclippedBoundsInRoot()
            val avatar=app.onNodeWithTag("profile-shared-avatar",useUnmergedTree=true).getUnclippedBoundsInRoot()
            val name=app.onNode(hasText("林间来信") and hasAnyAncestor(hasTestTag("profile-info")),
                useUnmergedTree=true).getUnclippedBoundsInRoot()
            assertEquals(scene.top.value,banner.top.value,.5f)
            assertEquals(scene.left.value,banner.left.value,.5f)
            assertEquals(scene.right.value,banner.right.value,.5f)
            assertEquals("Cover edge bisects the avatar",banner.bottom.value,(avatar.top.value+avatar.bottom.value)/2,.5f)
            assertTrue("Profile text sits below the full avatar",name.top.value>=avatar.bottom.value+15f)
        }
        assertGeometry()
        app.runOnIdle {cover=Cover.FOREST}
        app.onNodeWithTag("profile-cover-image",useUnmergedTree=true).assertIsDisplayed()
        assertGeometry()
        app.onNodeWithTag("profile-identity").performClick()
        app.runOnIdle {assertEquals(1,edits)}
    }

    @Test fun coverEntersAndLeavesThroughTopWithAStableAvatarEndpoint() {
        var progress by mutableFloatStateOf(1f)
        var phase by mutableStateOf(FlowPhase.IDLE)
        app.setContent {
            SoTheme(ThemeMode.LIGHT) {
                CompositionLocalProvider(LocalMotionEnabled provides true,LocalProfileEntryProgress provides progress,
                    LocalFlowPhase provides phase,LocalIdentity provides remember {IdentityAnchor(true,0)}) {
                    ProfileScreen(PreviewState(signedIn=true,profile=PreviewProfile(cover=Cover.FOREST,coverPosition=.32f)),{},{},{},{})
                }
            }
        }
        val resting=app.onNodeWithTag("profile-cover",useUnmergedTree=true).getUnclippedBoundsInRoot()
        val avatar=app.onNodeWithTag("profile-shared-avatar",useUnmergedTree=true).getUnclippedBoundsInRoot()
        val image=app.onNodeWithTag("profile-cover-image",useUnmergedTree=true)
        val reference=image.captureToImage().toPixelMap()
        fun assertSharpCrop() {
            val moving=image.captureToImage().toPixelMap()
            assertEquals(reference.width,moving.width)
            assertTrue(moving.height in 1 until reference.height)
            val offset=reference.height-moving.height
            val difference=(1..8).flatMap {row->
                val sampleY=moving.height*row/9
                (4..7).map {column->
                    val sampleX=moving.width*column/10
                    abs(moving[sampleX,sampleY].luminance()-reference[sampleX,sampleY+offset].luminance())
                }
            }.average()
            assertTrue("Moving cover should use the same sharp crop as at rest: $difference",difference<.025)
        }
        fun assertHalfwayAbove() {
            val moving=app.onNodeWithTag("profile-cover",useUnmergedTree=true).getUnclippedBoundsInRoot()
            val endpoint=app.onNodeWithTag("profile-shared-avatar",useUnmergedTree=true).getUnclippedBoundsInRoot()
            assertEquals(resting.left.value,moving.left.value,.5f)
            assertEquals(resting.right.value,moving.right.value,.5f)
            assertEquals(resting.top.value-(resting.bottom.value-resting.top.value)/2,moving.top.value,.5f)
            assertEquals("Cover motion does not move the shared avatar endpoint",avatar.top.value,endpoint.top.value,.5f)
        }
        app.runOnIdle {phase=FlowPhase.ENTER;progress=SceneMotion.PROFILE_COVER_ENTER_MS/2f/SceneMotion.PROFILE_ENTER_MS}
        assertHalfwayAbove()
        app.runOnIdle {phase=FlowPhase.EXIT;progress=.5f}
        assertHalfwayAbove()
        app.runOnIdle {phase=FlowPhase.ENTER;progress=.36f}
        assertSharpCrop()
        app.runOnIdle {phase=FlowPhase.EXIT}
        assertSharpCrop()
        app.runOnIdle {progress=0f}
        val departed=app.onNodeWithTag("profile-cover",useUnmergedTree=true).getUnclippedBoundsInRoot()
        assertEquals("Cover clears the top edge",resting.top.value,departed.bottom.value,.5f)
    }

    @Test fun settingsFadesAfterTheCoverAndUsesItsVisibleCrop() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val photo=checkNotNull(runBlocking {ProfileImages.save(context) {
            Bitmap.createBitmap(400,1000,Bitmap.Config.ARGB_8888).apply {
                eraseColor(android.graphics.Color.BLACK)
                Canvas(this).drawRect(0f,500f,width.toFloat(),height.toFloat(),Paint().apply {color=android.graphics.Color.WHITE})
            }
        }})
        try {
            var progress by mutableFloatStateOf((SceneMotion.PROFILE_COVER_ENTER_MS-32f)/SceneMotion.PROFILE_ENTER_MS)
            var position by mutableFloatStateOf(0f)
            app.setContent {
                SoTheme(ThemeMode.LIGHT) {
                    Surface(Modifier.fillMaxWidth().height(380.dp),color=Color(0xff808080)) {
                        CompositionLocalProvider(LocalMotionEnabled provides true,LocalProfileEntryProgress provides progress,
                            LocalFlowPhase provides FlowPhase.ENTER,LocalIdentity provides remember {IdentityAnchor(true,0)}) {
                            ProfileScreen(PreviewState(signedIn=true,profile=PreviewProfile(coverPhoto=photo,coverPosition=position)),
                                {},{},{},{})
                        }
                    }
                }
            }
            app.waitUntil(5_000) {
                app.onAllNodesWithTag("profile-cover-image",useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()
            }
            val settings=app.onNodeWithTag("profile-settings")
            fun extremes(): Pair<Float,Float> {
                val pixels=settings.captureToImage().toPixelMap()
                var darkest=1f
                var brightest=0f
                for(row in 0 until pixels.height)for(column in 0 until pixels.width) {
                    val red=pixels[column,row].red
                    darkest=minOf(darkest,red)
                    brightest=maxOf(brightest,red)
                }
                return darkest to brightest
            }
            settings.assertIsNotEnabled()
            assertTrue("The gear does not draw during cover travel",extremes().second<.05f)
            app.runOnIdle {progress=(SceneMotion.PROFILE_COVER_ENTER_MS+SceneMotion.PROFILE_ENTER_MS)/2f/SceneMotion.PROFILE_ENTER_MS}
            assertTrue("A partial fade follows the cover landing",extremes().second in .25f.. .8f)
            app.runOnIdle {progress=1f}
            settings.assertIsEnabled()
            assertTrue("White gear on the dark crop",extremes().second>.9f)

            val feed=app.onNodeWithTag("my-profile-feed")
            feed.performScrollToIndex(1)
            app.onNodeWithTag("records-expand").performClick()
            feed.performScrollToIndex(1)
            app.onNodeWithTag("profile-cover",useUnmergedTree=true).assertIsNotDisplayed()
            assertTrue("The white gear stays white after the cover scrolls out",extremes().second>.9f)

            feed.performScrollToIndex(0)
            app.runOnIdle {progress=0f;position=1f}
            settings.assertIsNotEnabled()
            app.runOnIdle {progress=1f}
            val (darkest,brightest)=extremes()
            assertTrue("Black gear on the bright crop",darkest<.1f&&brightest>.9f)
        } finally {
            ProfileImages.delete(context,photo)
        }
    }
}
