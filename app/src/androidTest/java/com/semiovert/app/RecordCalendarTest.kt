package com.semiovert.app

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.semiovert.app.core.designsystem.SoTheme
import com.semiovert.app.feature.records.RecordCalendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecordCalendarTest {
    @get:Rule val app = createComposeRule()

    private fun showCalendar(snapshots: Boolean) = app.setContent {
        SoTheme {
            Column { RecordCalendar("lin", owner = true, snapshots = snapshots, cleared = false, initialExpanded = true) }
        }
    }

    @Test fun dailySummaryShowsDateAndCountWithoutSecondaryCopy() {
        showCalendar(snapshots = false)
        app.onNodeWithTag("record-day-16").performClick()
        app.onNodeWithText("10月16日·360字").assertIsDisplayed()
        app.onNodeWithText("每日汇总", substring = true).assertDoesNotExist()
        app.onNodeWithText("无快照时", substring = true).assertDoesNotExist()

        app.onNodeWithTag("record-day-29").performClick()
        app.onNodeWithText("10月29日·0字").assertIsDisplayed()
    }

    @Test fun expandedCalendarUsesLargerWeekdaysDigitsAndSummary() {
        showCalendar(snapshots = false)
        fun layout(text: String) = mutableListOf<TextLayoutResult>().also { results ->
            app.onNodeWithText(text,useUnmergedTree=true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        }.single().layoutInput
        val emphasizedFont=FontFamily(Font(R.font.yufanxinyu_digits_extrabold,weight=FontWeight.ExtraBold))
        fun digitRuns(text: String) = layout(text).text.spanStyles.filter {it.item.fontFamily==emphasizedFont}
        assertEquals(15.sp,layout("一").style.fontSize)
        assertTrue(digitRuns("一").isEmpty())
        assertEquals(22.sp,layout("16").style.fontSize)
        assertEquals(FontWeight.ExtraBold,digitRuns("16").single().item.fontWeight)
        app.onNodeWithTag("record-day-16").performClick()
        val summary="10月16日·360字"
        assertEquals(22.sp,layout(summary).style.fontSize)
        val digits=digitRuns(summary)
        assertEquals(listOf("10","16","360"),digits.map {summary.substring(it.start,it.end)})
        assertTrue(digits.all {it.item.fontSize==26.sp&&it.item.fontWeight==FontWeight.ExtraBold})
    }

    @Test fun snapshotOpensCompactDiffWithSeparateExcerpts() {
        showCalendar(snapshots = true)
        app.onNodeWithTag("record-day-16").performClick()
        app.onNodeWithText("10月16日·360字").assertIsDisplayed()
        app.onNodeWithTag("record-diff-forest").performClick()

        val title = app.onNodeWithText("Diff").assertIsDisplayed().getUnclippedBoundsInRoot()
        val panel = app.onNodeWithTag("drawer-panel").getUnclippedBoundsInRoot()
        assertTrue(title.top - panel.top < 40.dp)
        app.onNodeWithTag("diff-removed-quote").assertIsDisplayed().assertTextEquals("让内容被更多人看到。")
        app.onNodeWithTag("diff-added-quote").assertIsDisplayed().assertTextEquals("让内容被更多人看到，也让每一次表达被认真对待。")
    }
}
