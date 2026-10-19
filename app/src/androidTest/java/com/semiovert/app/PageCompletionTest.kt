package com.semiovert.app

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PageCompletionTest {
    @get:Rule(order=0) val preferences=PreviewPreferencesRule()
    @get:Rule(order=1) val app=createAndroidComposeRule<MainActivity>()
    private fun back() {Espresso.pressBack();app.waitForIdle()}
    private fun navigate(label: String) {
        app.onNodeWithContentDescription("打开页面导航").performClick()
        app.onNodeWithContentDescription("切换到"+label).performClick()
    }
    private fun enterDemo() {
        navigate("我的")
        app.onNodeWithText("登录",useUnmergedTree=true).performClick()
        app.onNodeWithText("填入示例资料").performScrollTo().performClick()
        app.onNodeWithTag("account-submit").performScrollTo().performClick()
        app.onNodeWithTag("profile-identity").assertIsDisplayed()
    }
    private fun settings() {app.onNodeWithContentDescription("设置").performClick()}

    @Test fun registrationProfileEditingAndCoverAreConnected() {
        navigate("我的")
        app.onNodeWithText("创建账号",useUnmergedTree=true).performClick()
        app.onNodeWithTag("account-submit").performScrollTo().performClick()
        app.onNodeWithText("请输入有效的示例邮箱").assertExists()
        app.onNodeWithText("填入示例资料").performScrollTo().performClick()
        app.onNodeWithTag("account-submit").performScrollTo().performClick()
        app.onNodeWithTag("profile-identity").performClick()
        app.onNodeWithTag("edit-name").performTextReplacement("本机演示读者")
        Espresso.closeSoftKeyboard()
        app.onNodeWithText("林间",useUnmergedTree=true).performScrollTo().performClick()
        app.onNodeWithText("保存资料").performScrollTo().performClick()
        app.onNodeWithText("本机演示读者").assertIsDisplayed()
        app.activityRule.scenario.recreate()
        app.onNodeWithText("本机演示读者").assertIsDisplayed()
        val saved=app.activity.getSharedPreferences("offline_preview",Context.MODE_PRIVATE)
        assertEquals("FOREST",saved.getString("cover",null))
        assertFalse(saved.all.keys.any {it.contains("password")||it.contains("email")||it.contains("token")})
    }

    @Test fun recoveryValidatesCodeAndEndsAtExplicitDemoResult() {
        navigate("我的")
        app.onNodeWithText("登录",useUnmergedTree=true).performClick()
        app.onNodeWithText("忘记密码").performScrollTo().performClick()
        app.onNodeWithTag("recovery-email").performTextInput("reader@example.invalid")
        Espresso.closeSoftKeyboard()
        app.onNodeWithText("获取演示验证码").performScrollTo().performClick()
        app.onNodeWithTag("recovery-code").performTextInput("000000")
        Espresso.closeSoftKeyboard()
        app.onNodeWithText("下一步").performScrollTo().performClick()
        app.onNodeWithText("请输入上方的演示验证码").assertExists()
        app.onNodeWithTag("recovery-code").performTextReplacement("123456")
        Espresso.closeSoftKeyboard()
        app.onNodeWithText("下一步").performScrollTo().performClick()
        app.onNodeWithTag("reset-password").performTextInput("demo-reset-123")
        app.onNodeWithTag("reset-confirmation").performTextInput("demo-reset-123")
        Espresso.closeSoftKeyboard()
        app.onNodeWithText("完成演示重置").performScrollTo().performClick()
        app.onNodeWithText("演示流程已完成").assertIsDisplayed()
        app.onNodeWithText("返回登录").performClick()
        app.onNodeWithTag("login-screen").assertIsDisplayed()
    }

    @Test fun recordsHaveConfirmationSummaryDiffAndCancelableMonthSelection() {
        enterDemo();settings()
        app.onNodeWithContentDescription("本地修改记录开关").assertIsOff().performClick()
        app.onNodeWithText("开启本地修改记录？").assertIsDisplayed()
        app.onNodeWithText("确定").performClick()
        app.onNodeWithText("查看记录").performScrollTo().performClick()
        app.onNodeWithTag("record-day-16").performScrollTo().performClick()
        app.onNodeWithTag("record-diff-forest").performScrollTo().performClick()
        app.onNodeWithTag("diff-sheet").assertIsDisplayed()
        app.onNodeWithText("文章 Diff").assertIsDisplayed()
        back()
        app.onNodeWithTag("record-detail").assertExists()
        app.onNodeWithTag("records-month").performScrollTo().performClick()
        app.onNodeWithText("9 月",useUnmergedTree=true).performClick()
        app.onNodeWithText("取消").performClick()
        app.onNodeWithText("2026.10").assertIsDisplayed()
        app.onNodeWithTag("records-month").performClick()
        app.onNodeWithText("9 月",useUnmergedTree=true).performClick()
        app.onNodeWithText("确定").performClick()
        app.onNodeWithText("2026.09").assertIsDisplayed()
        back()
        app.onNodeWithText("清除本地记录").performScrollTo().performClick()
        app.onNodeWithText("清除").performClick()
        app.onNodeWithText("恢复内置示例快照").assertExists()
    }

    @Test fun authorPagesAreReadOnlyAndReturnToSearch() {
        navigate("搜索")
        app.onNodeWithTag("search-input").performTextInput("街角")
        Espresso.closeSoftKeyboard()
        app.onNodeWithTag("main-circle").performClick()
        app.onNodeWithTag("author-zhou").performClick()
        app.onNodeWithTag("author-screen").assertIsDisplayed()
        app.onNodeWithContentDescription("设置").assertDoesNotExist()
        app.onNodeWithTag("profile-identity").assertDoesNotExist()
        app.onNodeWithTag("article-street").performScrollTo().performClick()
        app.onNodeWithTag("reader-screen").assertIsDisplayed()
        back();back()
        app.onNodeWithTag("search-input").assertTextContains("街角")
        app.onNodeWithTag("author-zhou").assertIsDisplayed()
    }

    @Test fun savedLeftPlacementAlsoMirrorsTheRadialMenu() {
        navigate("我的");settings()
        app.onNodeWithText("调整导航位置").performScrollTo().performClick()
        app.onNodeWithText("左下").performClick()
        app.onNodeWithText("保存位置").performScrollTo().performClick()
        back()
        assertTrue(app.onNodeWithTag("main-circle").getUnclippedBoundsInRoot().left.value<60f)
        val density=app.activity.resources.displayMetrics.density
        app.onNodeWithTag("main-circle").performTouchInput {down(center);advanceEventTime(800);moveTo(center+Offset(116f*density,-8f*density),delayMillis=200);up()}
        app.onNodeWithTag("feed-home").assertIsDisplayed()
        assertEquals(0f,app.activity.getSharedPreferences("offline_preview",Context.MODE_PRIVATE).getFloat("nav_x",1f),0f)
    }
}
