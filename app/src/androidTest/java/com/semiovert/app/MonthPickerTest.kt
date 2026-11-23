package com.semiovert.app

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.semiovert.app.core.designsystem.SoTheme
import com.semiovert.app.feature.records.RecordCalendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MonthPickerTest {
    @get:Rule val app = createComposeRule()

    private fun openCalendar() = app.setContent {
        SoTheme {
            Column { RecordCalendar("lin", owner = true, snapshots = false, cleared = false, initialExpanded = true) }
        }
    }

    private fun option(tag: String, value: String) =
        app.onNode(hasText(value) and hasAnyAncestor(hasTestTag(tag)))

    private fun assertCentered(tag: String, value: String) {
        val wheel = app.onNodeWithTag(tag)
        val wheelCenter = wheel.fetchSemanticsNode().boundsInRoot.center.y
        val optionCenter = option(tag, value).assertIsSelected().fetchSemanticsNode().boundsInRoot.center.y
        assertEquals(wheelCenter, optionCenter, 2f)
    }

    @Test fun wheelCentersItsSelectionAndAppliesOnlyOnConfirm() {
        openCalendar()
        val calendarMonth = app.onNodeWithTag("records-month")
        val yearWheel = "month-wheel-year"
        val monthWheel = "month-wheel-month"
        val cancel = app.onNodeWithTag("month-cancel")
        val apply = app.onNodeWithTag("month-apply")
        app.onNodeWithText("本地修改记录").assertDoesNotExist()
        calendarMonth.performClick()
        app.onNodeWithText("选择年月").assertDoesNotExist()
        cancel.assertContentDescriptionEquals("取消")
        apply.assertContentDescriptionEquals("确定")
        assertTrue(cancel.getUnclippedBoundsInRoot().left > apply.getUnclippedBoundsInRoot().right)
        assertCentered(yearWheel, "2026")
        assertCentered(monthWheel, "10")

        option(monthWheel, "9").performClick()
        assertCentered(monthWheel, "9")
        cancel.performClick()
        calendarMonth.assertTextEquals("2026.10").performClick()
        assertCentered(monthWheel, "10")

        app.onNodeWithTag(yearWheel).performScrollToIndex(0)
        assertCentered(yearWheel, "2021")
        app.onNodeWithTag(monthWheel).performTouchInput {
            swipe(center, center + Offset(0f, height * .26f), durationMillis = 500)
        }
        assertCentered(monthWheel, "9")
        apply.performClick()
        calendarMonth.assertTextEquals("2021.09")
    }

    @Test fun aMonthWheelFlickCoastsBeyondTheDraggedRowThenSnaps() {
        openCalendar()
        app.onNodeWithTag("records-month").performClick()
        val monthWheel = "month-wheel-month"
        app.onNodeWithTag(monthWheel).performTouchInput {
            swipeWithVelocity(center, center + Offset(0f, height * .15f), endVelocity = height * 4f, durationMillis = 72)
        }
        app.waitForIdle()
        val chosen = app.onNode(isSelected() and hasAnyAncestor(hasTestTag(monthWheel)))
            .fetchSemanticsNode().config[SemanticsProperties.Text].single().text
        assertTrue("The flick should travel farther than a one-row drag: $chosen", chosen.toInt() in 1..8)
        assertCentered(monthWheel, chosen)
    }

    @Test fun monthSheetSettlesAndClosesFasterThanStandardDrawers() {
        openCalendar()
        val panel = app.onNodeWithTag("drawer-panel")
        app.mainClock.autoAdvance = false
        try {
            app.onNodeWithTag("records-month").performClick()
            app.mainClock.advanceTimeBy(112)
            val entering = panel.getUnclippedBoundsInRoot().top.value
            app.mainClock.advanceTimeBy(208)
            val resting = panel.getUnclippedBoundsInRoot().top.value
            assertTrue(entering > resting)
            app.mainClock.advanceTimeBy(96)
            assertEquals(resting, panel.getUnclippedBoundsInRoot().top.value, .5f)

            app.onNodeWithTag("month-cancel").performClick()
            app.mainClock.advanceTimeBy(96)
            assertTrue(panel.getUnclippedBoundsInRoot().top.value > resting)
            app.mainClock.advanceTimeBy(176)
            panel.assertDoesNotExist()
        } finally {
            app.mainClock.autoAdvance = true
        }
    }
}
