package com.semiovert.app

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.semiovert.app.core.data.PreviewViewModel
import com.semiovert.app.core.designsystem.SoTheme
import com.semiovert.app.core.model.NavigationShortcut
import com.semiovert.app.core.model.PreviewState
import com.semiovert.app.core.model.ReadingDuration
import com.semiovert.app.navigation.AppNavigation
import com.semiovert.app.navigation.Destination
import org.junit.Rule
import org.junit.Test

class CategoryShortcutTest {
    @get:Rule val app=createComposeRule()

    @Test fun pinnedCategoryOpensThePageThenCyclesReadingDuration() {
        val model=PreviewViewModel(InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application)
        val pinned=NavigationShortcut(Destination.CATEGORY.route,.15f,.72f)
        app.setContent {SoTheme {AppNavigation(PreviewState(shortcuts=listOf(pinned)),model) {}}}
        val shortcut=app.onNodeWithTag("shortcut-"+pinned.route)
        val label=app.onNodeWithTag("category-label")

        shortcut.performTouchInput {click()}
        label.assertTextEquals(ReadingDuration.QUICK.label)
        shortcut.performTouchInput {click()}
        label.assertTextEquals(ReadingDuration.MEDIUM.label)
        shortcut.performClick()
        label.assertTextEquals(ReadingDuration.LONG.label)
        shortcut.performTouchInput {click()}
        label.assertTextEquals(ReadingDuration.QUICK.label)
        app.onNodeWithTag("main-circle").assertContentDescriptionEquals("首页主圆")
    }
}
