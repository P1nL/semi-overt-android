package com.semiovert.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.semiovert.app.core.data.SampleLibrary
import com.semiovert.app.core.designsystem.*
import com.semiovert.app.core.designsystem.wave.*
import com.semiovert.app.feature.reader.ArticleExpansionHost
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** One drawing regression for the two lift bugs; no screenshots are saved. */
@RunWith(AndroidJUnit4::class)
class ReadingOverlayTest {
    @get:Rule val app=createComposeRule()

    @Test fun primaryIconMorphSurvivesOwnerChangeAndReturnWithoutChangingShadow() {
        val category=mutableStateOf(false)
        var circle=Rect.Zero
        var density=1f
        app.setContent {
            MaterialTheme(colorScheme=lightColorScheme()) {
                val scale=LocalDensity.current.density
                SideEffect {density=scale}
                WaveDeckOverlayHost {
                    Box(Modifier.fillMaxSize().background(Color.White)) {
                        @Composable fun Circle(symbol: SoSymbol) {
                            val owner=androidx.compose.runtime.remember {Any()}
                            Surface(Modifier.align(Alignment.Center).size(56.dp).mainCircleOverlay(owner)
                                .onGloballyPositioned {circle=it.boundsInRoot()},shape=CircleShape,
                                color=MaterialTheme.colorScheme.primaryContainer,shadowElevation=6.dp) {
                                Box(contentAlignment=Alignment.Center) {MainCircleMorphIcon(owner,symbol,0f,false)}
                            }
                        }
                        key("home") {Circle(SoSymbol.HOME)}
                        if(category.value)key("category") {Circle(SoSymbol.CATEGORY)}
                    }
                }
            }
        }
        fun icon(): List<Float> {
            val image=app.onRoot().captureToImage().toPixelMap()
            val radius=(12*density).toInt()
            return (-radius until radius).flatMap {y->(-radius until radius).map {x->
                image[circle.center.x.toInt()+x,circle.center.y.toInt()+y].red
            }}
        }
        fun shadow()=app.onRoot().captureToImage().toPixelMap()[circle.center.x.toInt(),(circle.bottom+3*density).toInt()].red
        fun difference(a: List<Float>,b: List<Float>)=a.indices.map {kotlin.math.abs(a[it]-b[it])}.average()
        app.waitForIdle()
        val home=icon()
        val originalShadow=shadow()
        app.mainClock.autoAdvance=false
        try {
            app.runOnIdle {category.value=true}
            app.mainClock.advanceTimeBy(48)
            assertTrue("A new page must start from the old glyph, not jump to its own endpoint",difference(home,icon())<.02)
            app.mainClock.advanceTimeBy(200)
            val halfway=icon()
            app.mainClock.advanceTimeBy(400)
            val target=icon()
            assertTrue(difference(home,target)>.015)
            assertTrue(difference(halfway,home)>.005&&difference(halfway,target)>.005)
            assertEquals(originalShadow,shadow(),.012f)
            app.runOnIdle {category.value=false}
            app.mainClock.advanceTimeBy(200)
            val returning=icon()
            assertTrue(difference(returning,home)>.005&&difference(returning,target)>.005)
            app.mainClock.advanceTimeBy(400)
            assertTrue(difference(home,icon())<.005)
            assertEquals(originalShadow,shadow(),.012f)
        }finally{app.mainClock.autoAdvance=true;app.waitForIdle()}
    }

    @Test fun mainCircleHasOneStableShadowAcrossIconAndPageHandoffs() {
        val source=mutableStateOf(true)
        val reader=mutableStateOf(false)
        val progress=mutableStateOf(0f)
        var circle=Rect.Zero
        var density=1f
        app.setContent {
            MaterialTheme(colorScheme=lightColorScheme()) {
                val scale=LocalDensity.current.density
                SideEffect {density=scale}
                WaveDeckOverlayHost {
                    Box(Modifier.fillMaxSize().background(Color.White)) {
                        @Composable fun Circle() {
                            Surface(Modifier.align(Alignment.Center).size(56.dp).mainCircleOverlay()
                                .onGloballyPositioned {circle=it.boundsInRoot()},shape=CircleShape,
                                color=MaterialTheme.colorScheme.primaryContainer,shadowElevation=6.dp) {
                                Box(contentAlignment=Alignment.Center) {
                                    ReadingMorphIcon(SoSymbol.CATEGORY,progress.value)
                                }
                            }
                        }
                        if(source.value)key("source") {Circle()}
                        if(reader.value)key("reader") {Circle()}
                    }
                }
            }
        }
        fun shadow(): List<Float> {
            val image=app.onRoot().captureToImage().toPixelMap()
            return (2..10 step 2).flatMap {down->(-16..16 step 4).map {across->
                image[(circle.center.x+across*density).toInt(),(circle.bottom+down*density).toInt()].red
            }}
        }
        app.waitForIdle()
        val resting=shadow()
        assertTrue("Keep the existing shadow rather than removing it",resting.min()<.995f)
        fun assertStable(label: String) {
            val current=shadow()
            val delta=resting.indices.maxOf {kotlin.math.abs(resting[it]-current[it])}
            assertTrue("$label changed the shadow by $delta",delta<.012f)
        }
        app.runOnIdle {progress.value=.5f};app.waitForIdle()
        assertStable("Icon morph")
        app.runOnIdle {reader.value=true};app.waitForIdle()
        assertStable("Overlapping source and reader")
        app.runOnIdle {source.value=false;progress.value=1f};app.waitForIdle()
        assertStable("Completed handoff")
    }

    @Test fun counterGlyphsStartAtScopeBottomOnBothPages() {
        val page=mutableStateOf("search")
        var density=1f
        var statusTop=0f
        app.setContent {
            MaterialTheme(colorScheme=lightColorScheme(onSurfaceVariant=Color.Black)) {
                val scale=LocalDensity.current
                val inset=WindowInsets.statusBars.getTop(scale)/scale.density
                SideEffect {density=scale.density;statusTop=inset}
                DeckCounterHost(page.value,11) {
                    Box(Modifier.fillMaxSize().background(Color.White)) {
                        DeckCounter(1,11,page.value,Modifier.width(240.dp))
                    }
                }
            }
        }
        fun firstInk(): Int {
            val image=app.onRoot().captureToImage().toPixelMap()
            return (0 until image.height).first {y->
                (0 until (240*density).toInt()).any {x->image[x,y].red<.5f}
            }
        }
        app.waitForIdle()
        assertEquals("Search glyphs must start at the results container's top",0f,firstInk().toFloat(),2f*density)
        app.runOnIdle {page.value="category"}
        app.waitForIdle()
        assertEquals("Category must use the same screen anchor as the search scope bottom",
            (statusTop+DeckCounterLayout.ROOT_TOP)*density,firstInk().toFloat(),2f*density)
    }

    @Test fun rearLiftKeepsForegroundVisibleAndFadesPositionedNumbers() {
        lateinit var expansion: ArticleExpansionState
        var density=1f
        app.setContent {
            MaterialTheme(colorScheme=lightColorScheme(background=Color.White,onSurfaceVariant=Color.Black)) {
                DeckCounterHost("search",3) {
                    WaveDeckOverlayHost {
                        ArticleExpansionHost {
                            val state=LocalArticleExpansion.current!!
                            val scale=LocalDensity.current.density
                            SideEffect {expansion=state;density=scale}
                            Box(Modifier.fillMaxSize().background(Color.White)) {
                                DeckCounter(1,3,"search",Modifier.width(240.dp))
                                // A front card must still cover the rear reading copy, even though
                                // that copy is owned by the full-screen reading host.
                                Box(Modifier.offset(y=330.dp).size(240.dp,280.dp)
                                    .readingStackOverlay(3f) {state.active}.background(Color.Red))
                            }
                        }
                    }
                }
            }
        }
        fun rect(x: Float,y: Float,w: Float,h: Float)=Rect(Offset(x*density,y*density),Size(w*density,h*density))
        fun pixels()=app.onRoot().captureToImage().toPixelMap()
        fun ink(): Float {
            val image=pixels()
            var sum=0f
            for(y in (20*density).toInt() until (130*density).toInt())
                for(x in 0 until (240*density).toInt())sum+=1f-image[x,y].red
            return sum
        }
        fun frontIsVisible() {
            val pixel=pixels()[(100*density).toInt(),(400*density).toInt()]
            assertEquals("The expanding rear card covered the foreground",Color.Red,pixel)
        }
        app.waitForIdle()
        val restingInk=ink()
        assertTrue(restingInk>100f)
        app.runOnIdle {
            expansion.begin(ArticleSource(SampleLibrary.articles.first(),rect(0f,270f,240f,360f),
                rect(18f,304f,204f,87f),rect(18f,395f,60f,18f),rect(18f,413f,130f,18f),
                rect(88f,395f,100f,18f),Color.White,ArticleOrigin.STACK,1f)) {}
            expansion.drag(.4f)
        }
        app.waitForIdle()
        val fadedInk=ink()
        assertTrue("Numbers must fade during the lift, not at navigation",fadedInk>0f&&fadedInk<restingInk*.8f)
        frontIsVisible()
        app.runOnIdle {expansion.drag(.8f)}
        app.waitForIdle()
        assertTrue("Numbers should be gone before container expansion",ink()<restingInk*.01f)
        frontIsVisible()
        app.runOnIdle {expansion.cancel()}
        app.waitForIdle()
        assertEquals(restingInk,ink(),restingInk*.01f)
        frontIsVisible()
    }
}
