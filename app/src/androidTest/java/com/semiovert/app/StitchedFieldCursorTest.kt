package com.semiovert.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.semiovert.app.core.designsystem.LocalMotionEnabled
import com.semiovert.app.core.designsystem.SoTheme
import com.semiovert.app.core.model.ThemeMode
import com.semiovert.app.feature.account.AccountForm
import com.semiovert.app.feature.account.AccountMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StitchedFieldCursorTest {
    @get:Rule val app = createComposeRule()

    private fun form() {
        app.setContent {
            SoTheme(ThemeMode.LIGHT) {
                CompositionLocalProvider(LocalMotionEnabled provides true) {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
                        AccountForm({}, initialMode = AccountMode.REGISTER)
                    }
                }
            }
        }
    }

    @Test fun populatedTextEditorKeepsItsNativeCursorAnchorStationaryDuringRetap() {
        form()
        val field = app.onNode(
            hasSetTextAction() and hasAnyAncestor(hasTestTag("account-email")),
            useUnmergedTree = true,
        )
        checkRetap(field, "reader@example.com")
    }

    @Test fun populatedPasswordAndConfirmationKeepTheirNativeCursorAnchorsStationary() {
        form()
        for (tag in listOf("account-password", "account-confirmation")) {
            checkRetap(app.onNodeWithTag(tag), "cursor-check")
        }
    }

    private fun checkRetap(field: SemanticsNodeInteraction, value: String) {
        field.performScrollTo().performTextReplacement(value)
        field.performTouchInput { click(center) }
        app.waitForIdle()
        app.onAllNodes(isPopup(), useUnmergedTree = true).assertCountEquals(1)
        val anchor = field.fetchSemanticsNode().boundsInRoot
        app.mainClock.autoAdvance = false
        try {
            field.performTouchInput { down(center) }
            for (elapsed in listOf(40L, 60L, 120L)) {
                app.mainClock.advanceTimeBy(elapsed)
                val actual = field.fetchSemanticsNode().boundsInRoot
                assertEquals("Editor left must not follow the surface scale", anchor.left, actual.left, .1f)
                assertEquals("Editor top must not follow the surface scale", anchor.top, actual.top, .1f)
                assertEquals(anchor.right, actual.right, .1f)
                assertEquals(anchor.bottom, actual.bottom, .1f)
            }
            field.performTouchInput { up() }
            app.mainClock.advanceTimeBy(32)
            app.onAllNodes(isPopup(), useUnmergedTree = true).assertCountEquals(1)
            app.mainClock.advanceTimeBy(700)
            app.onAllNodes(isPopup(), useUnmergedTree = true).assertCountEquals(1)
        } finally {
            app.mainClock.autoAdvance = true
        }
    }
}
