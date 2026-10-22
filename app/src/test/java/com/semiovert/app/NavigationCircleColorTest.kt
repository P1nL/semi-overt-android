package com.semiovert.app

import androidx.compose.ui.graphics.luminance
import com.semiovert.app.core.designsystem.MainCircleColorState
import com.semiovert.app.core.designsystem.MainCircleIconState
import com.semiovert.app.navigation.Destination
import com.semiovert.app.navigation.circleColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.*
import org.junit.Test

class NavigationCircleColorTest {
    @Test fun glyphAndColorShareTheReturnProgressBeforeTheNewPageExists() {
        val scope=CoroutineScope(Dispatchers.Unconfined)
        val colors=MainCircleColorState(scope)
        val icon=MainCircleIconState(scope)
        val owner=Any()
        val home=Destination.HOME;val search=Destination.SEARCH
        colors.showPage(owner,home.circleColors,false)
        icon.showPage(home.symbol,false,owner)
        colors.beginMerge(owner,search.circleColors);icon.beginMerge(owner,search.symbol)
        for(progress in listOf(0f,.25f,.7f,1f)) {
            colors.merge(progress);icon.merge(progress)
            colors.showPage(owner,home.circleColors,false)
            icon.showPage(home.symbol,false,owner)
            icon.showReading(home.symbol,1f,owner)
            assertEquals(home.circleColors.towards(search.circleColors,progress),colors.colors)
            assertEquals(progress,icon.progress,0f)
            assertEquals(search.symbol,icon.morph!!.target.symbol)
        }
        icon.showPage(search.symbol,true,Any())
        assertEquals(1f,icon.progress,0f)
    }
    @Test fun pageColorsAreDistinctAndIconsHaveContrast() {
        val palettes=Destination.entries.map {it.circleColors}
        assertEquals(4,palettes.map {it.background}.distinct().size)
        palettes.forEach {colors ->
            val a=colors.background.luminance()
            val b=colors.foreground.luminance()
            assertTrue("Navigation glyphs need at least 3:1 contrast",(maxOf(a,b)+.05f)/(minOf(a,b)+.05f)>=3f)
        }
    }

    @Test fun selectedCircleHandsOffColorWithoutAnOutgoingPageReset() {
        val state=MainCircleColorState(CoroutineScope(Dispatchers.Unconfined))
        val home=Destination.HOME.circleColors
        val search=Destination.SEARCH.circleColors
        val source=Any()
        state.showPage(source,home,false)
        state.beginMerge(source,search)
        state.merge(.5f)
        val middle=home.towards(search,.5f)
        assertEquals(middle,state.colors)
        state.showPage(source,home,false)
        assertEquals("The still-mounted source must not reset a committed merge",middle,state.colors)
        state.merge(1f)
        state.showPage(Any(),search,true)
        assertEquals("The new owner inherits the already reached target without a new animation",search,state.colors)
        state.showPage(Any(),home,false)
        assertEquals(home,state.colors)
    }

    @Test fun redirectedMergeStartsAtTheDisplayedColorAndClampsSpringOvershoot() {
        val state=MainCircleColorState(CoroutineScope(Dispatchers.Unconfined))
        val owner=Any()
        state.showPage(owner,Destination.HOME.circleColors,false)
        state.beginMerge(owner,Destination.CATEGORY.circleColors)
        state.merge(.4f)
        val visible=state.colors
        state.beginMerge(owner,Destination.PROFILE.circleColors)
        state.merge(0f)
        assertEquals(visible,state.colors)
        state.merge(1.1f)
        assertEquals(Destination.PROFILE.circleColors,state.colors)
    }
}
