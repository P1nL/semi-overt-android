package com.semiovert.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.test.platform.app.InstrumentationRegistry
import android.app.Application
import com.semiovert.app.core.data.PreviewViewModel
import com.semiovert.app.core.model.PreviewState
import com.semiovert.app.core.designsystem.*
import com.semiovert.app.navigation.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class IdentityTransitionTest {
    @get:Rule val app=createComposeRule()

    /** Read the actual overlay pixels, not the destination's lookahead/semantics bounds. */
    private fun avatarBounds(image: ImageBitmap=app.onRoot().captureToImage(),symbol: Boolean=false): Rect {
        val pixels=image.toPixelMap()
        var left=pixels.width; var top=pixels.height; var right=-1; var bottom=-1
        for(y in 0 until pixels.height)for(x in 0 until pixels.width) {
            val c=pixels[x,y]
            val matches=if(symbol)c.green>.8f&&c.red<.2f&&c.blue<.2f else c.red>.8f&&c.blue>.8f&&c.green<.2f
            if(matches) {
                left=min(left,x);top=min(top,y);right=max(right,x);bottom=max(bottom,y)
            }
        }
        assertTrue("The avatar stays visible during the page fade",right>=left&&bottom>=top)
        return Rect(left.toFloat(),top.toFloat(),(right+1).toFloat(),(bottom+1).toFloat())
    }

    @Test fun eachHandoffUsesItsCurrentEndpointsAndDrawsOneContinuousAvatar() {
        lateinit var nav: NavHostController
        var handoff by mutableStateOf<PageHandoff?>(null)
        app.setContent {
            IdentityTransitionHost {
                nav=rememberNavController()
                NavHost(nav,Destination.HOME.route,
                    enterTransition={pageEnter(true)},exitTransition={pageExit(true)}) {
                    Destination.entries.forEach {page ->composable(page.route) {
                        SceneMotionContent(page,handoff) {
                            Box(Modifier.fillMaxSize().background(Color.White)) {
                                val alignment=if(page==Destination.HOME||page==Destination.CATEGORY)Alignment.TopEnd else Alignment.TopStart
                                val top=if(page==Destination.PROFILE)200.dp else 30.dp
                                val size=if(page==Destination.PROFILE)76.dp else 40.dp
                                Box(Modifier.align(alignment).padding(horizontal=24.dp).padding(top=top)) {
                                    Box(Modifier.identitySharedElement().size(size).background(Color.Magenta,CircleShape))
                                }
                            }
                        }
                    }}
                }
            }
        }
        app.waitForIdle()
        app.mainClock.autoAdvance=false
        try {
            var from=Destination.HOME
            for(to in listOf(Destination.PROFILE,Destination.SEARCH,Destination.PROFILE,Destination.HOME)) {
                val start=avatarBounds()
                val next=PageHandoff(from,to)
                app.runOnIdle {handoff=next;nav.navigate(to.route)}
                app.mainClock.advanceTimeBy(240)
                val mid=avatarBounds()
                app.mainClock.advanceTimeBy(SceneMotion.PRIMARY_ELEMENT_MS-240L-16)
                val arriving=avatarBounds()
                app.mainClock.advanceTimeBy(next.totalMillis-SceneMotion.PRIMARY_ELEMENT_MS+116L)
                val end=avatarBounds()
                assertTrue("Single avatar, not separated old/new copies: $from -> $to",mid.width<=max(start.width,end.width)+3&&mid.height<=max(start.height,end.height)+3)
                assertTrue("X follows this destination, not the previous origin: $from -> $to",mid.center.x in (min(start.center.x,end.center.x)-2)..(max(start.center.x,end.center.x)+2))
                assertTrue("Y stays on the current path: $from -> $to",mid.center.y in (min(start.center.y,end.center.y)-2)..(max(start.center.y,end.center.y)+2))
                assertTrue("No teleport to the final position: $from -> $to",abs(mid.center.y-end.center.y)>2f)
                assertTrue("No final snap when the overlay is removed: $from -> $to",abs(arriving.center.x-end.center.x)<3f&&abs(arriving.center.y-end.center.y)<3f)
                from=to
            }
        } finally {app.mainClock.autoAdvance=true}
    }

    @Test fun realHeaderButtonsDoNotLeaveOrPrepaintTheAvatarsCircularBacking() {
        val model=PreviewViewModel(InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application)
        app.setContent {
            SoTheme {
                CompositionLocalProvider(LocalIdentity provides remember {IdentityAnchor(true,0)},LocalMotionEnabled provides true) {
                    Surface(Modifier.fillMaxSize()) {AppNavigation(PreviewState(signedIn=true),model) {}}
                }
            }
        }
        app.waitForIdle()
        val source=app.onNodeWithContentDescription("打开个人页").fetchSemanticsNode().boundsInRoot.center
        app.mainClock.autoAdvance=false
        try {
            val searchAction=app.onNodeWithTag("main-circle").fetchSemanticsNode()
                .config[androidx.compose.ui.semantics.SemanticsActions.CustomActions].first {it.label=="前往搜索"}
            app.runOnUiThread {searchAction.action()}
            app.mainClock.advanceTimeBy(400)
            val departing=app.onRoot().captureToImage()
            app.mainClock.advanceTimeBy(250)
            val arriving=app.onRoot().captureToImage()
            app.mainClock.advanceTimeBy(1500)
            val target=app.onNodeWithContentDescription("打开页面导航").fetchSemanticsNode().boundsInRoot.center
            fun assertNoPurpleDisk(image: ImageBitmap,x: Int,y: Int) {
                val pixels=image.toPixelMap()
                for(dy in -4..4)for(dx in -4..4) {
                    val c=pixels[x+dx,y+dy]
                    assertFalse("Only the moving avatar may own the purple backing",c.blue-c.red>.08f&&c.blue-c.green>.15f)
                }
            }
            assertNoPurpleDisk(departing,source.x.toInt(),source.y.toInt())
            assertNoPurpleDisk(arriving,source.x.toInt(),source.y.toInt())
            assertNoPurpleDisk(arriving,target.x.toInt(),target.y.toInt())
        } finally {app.mainClock.autoAdvance=true}
    }

    @Test fun realAvatarSymbolAndBackingScaleTogetherInBothDirections() {
        lateinit var nav: NavHostController
        var handoff by mutableStateOf<PageHandoff?>(null)
        app.setContent {
            MaterialTheme(colorScheme=lightColorScheme(secondary=Color.Magenta,onSecondary=Color.Green)) {
                CompositionLocalProvider(LocalIdentity provides remember {IdentityAnchor(true,0)}) {
                    IdentityTransitionHost {
                        nav=rememberNavController()
                        NavHost(nav,Destination.HOME.route,
                            enterTransition={pageEnter(true)},exitTransition={pageExit(true)}) {
                            listOf(Destination.HOME,Destination.PROFILE).forEach {page ->composable(page.route) {
                                SceneMotionContent(page,handoff) {
                                    Box(Modifier.fillMaxSize().background(Color.White)) {
                                        val large=page==Destination.PROFILE
                                        Box(Modifier.align(if(large)Alignment.TopStart else Alignment.TopEnd)
                                            .padding(horizontal=24.dp).padding(top=if(large)200.dp else 30.dp)) {
                                            IdentityAvatar(size=if(large)76 else 40)
                                        }
                                    }
                                }
                            }}
                        }
                    }
                }
            }
        }
        app.waitForIdle()
        app.mainClock.autoAdvance=false
        try {
            var from=Destination.HOME
            for(to in listOf(Destination.PROFILE,Destination.HOME)) {
                val startImage=app.onRoot().captureToImage()
                val start=avatarBounds(startImage)
                val ratio=avatarBounds(startImage,symbol=true).width/start.width
                val next=PageHandoff(from,to)
                app.runOnIdle {handoff=next;nav.navigate(to.route)}
                var previousWidth=start.width
                for(sample in 1..3) {
                    app.mainClock.advanceTimeBy(250)
                    val image=app.onRoot().captureToImage()
                    val backing=avatarBounds(image)
                    val symbol=avatarBounds(image,symbol=true)
                    assertTrue("The backing must resize continuously: $from -> $to, sample=$sample, previous=$previousWidth, current=${backing.width}, start=${start.width}",if(to==Destination.PROFILE)backing.width>previousWidth+1f else backing.width<previousWidth-1f)
                    assertEquals("The inner symbol must scale with its circle, not jump to target size",ratio,symbol.width/backing.width,.035f)
                    previousWidth=backing.width
                }
                app.mainClock.advanceTimeBy(next.totalMillis-750L+100)
                val endImage=app.onRoot().captureToImage()
                assertEquals(ratio,avatarBounds(endImage,symbol=true).width/avatarBounds(endImage).width,.035f)
                from=to
            }
        } finally {app.mainClock.autoAdvance=true}
    }
}
