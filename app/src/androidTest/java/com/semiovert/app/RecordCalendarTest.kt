package com.semiovert.app

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
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
import com.semiovert.app.core.model.ThemeMode
import com.semiovert.app.feature.records.RecordCalendar
import java.time.LocalDate
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

    @OptIn(ExperimentalTestApi::class)
    @Test fun recordTiersFillLargerCellsInsideTheSameCalendarBounds() {
        val words=mutableStateOf(emptyMap<LocalDate,Int>())
        fun date(day: Int)=LocalDate.of(2026,10,day)
        app.setContent {
            SoTheme(ThemeMode.LIGHT) {
                Column { RecordCalendar("lin",owner=true,snapshots=false,cleared=false,initialExpanded=true,calendar=words.value) }
            }
        }
        val panel=app.onNodeWithTag("record-calendar-grid").getUnclippedBoundsInRoot()
        fun cell(day: Int)=app.onNodeWithTag("record-day-$day")
        fun width(day: Int)=cell(day).getUnclippedBoundsInRoot().let {(it.right-it.left).value}
        fun area(day: Int)=cell(day).getUnclippedBoundsInRoot().let {(it.right-it.left).value*(it.bottom-it.top).value}
        val zeroWidth=width(9)
        assertEquals(zeroWidth,width(5),1f)
        assertEquals(area(5),area(12),65f)
        app.runOnIdle {words.value=mapOf(date(5) to 299,date(6) to 300,date(7) to 800)}
        assertEquals(panel,app.onNodeWithTag("record-calendar-grid").getUnclippedBoundsInRoot())
        assertTrue(width(9)<zeroWidth)
        assertTrue(area(7)>area(6)&&area(6)>area(5)&&area(5)>area(12))
        for((day,fill) in listOf(5 to Color(0xFFBF0FFF),6 to Color(0xFFCBFF49),7 to Color(0xFF12BFBF))) {
            val pixels=cell(day).captureToImage().toPixelMap()
            assertEquals(fill,pixels[pixels.width/2,pixels.height/8])
        }
        cell(7).performClick().assertIsSelected()
        app.onNodeWithText("10月7日·800字").assertIsDisplayed()
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
