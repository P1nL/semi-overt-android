package com.semiovert.app

import androidx.compose.ui.geometry.Offset
import com.semiovert.app.navigation.SwipeBackGesture
import org.junit.Assert.*
import org.junit.Test

class SwipeBackGestureTest {
    @Test fun onlyPredominantlyRightwardMotionCanAcquireBack() {
        assertTrue(SwipeBackGesture.acceptsDirection(Offset(40f,10f)))
        assertFalse(SwipeBackGesture.acceptsDirection(Offset(-40f,0f)))
        assertFalse(SwipeBackGesture.acceptsDirection(Offset(10f,40f)))
    }
    @Test fun deliberateTravelOrAShortRightwardFlingCommitsButTinySwipesDoNot() {
        assertTrue(SwipeBackGesture.commits(100f,0f,411f,1f))
        assertTrue(SwipeBackGesture.commits(40f,1000f,411f,1f))
        assertFalse(SwipeBackGesture.commits(20f,1000f,411f,1f))
        assertFalse(SwipeBackGesture.commits(40f,-1000f,411f,1f))
    }
}
