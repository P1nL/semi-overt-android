package com.semiovert.app

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
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
        app.onNodeWithTag("main-circle").performSemanticsAction(SemanticsActions.OnLongClick){it()}
        app.onNodeWithContentDescription("切换到"+label).performClick()
    }
    private fun enterDemo() {
        navigate("我的")
        app.onNodeWithText("填入示例资料").performScrollTo().performClick()
        app.onNodeWithTag("account-submit").performScrollTo().performClick()
        app.onNodeWithTag("profile-identity").assertIsDisplayed()
    }
    private fun settings() {app.onNodeWithContentDescription("设置").performClick()}

    @Test fun inlineLoginSlidesDownOverTheSettledProfile() {
        navigate("我的")
        app.onNodeWithText("填入示例资料").performScrollTo().performClick()
        app.onNodeWithTag("account-submit").performScrollTo()
        val before=app.onNodeWithTag("profile-guest").getUnclippedBoundsInRoot()
        app.mainClock.autoAdvance=false
        try {
            app.onNodeWithTag("account-submit").performClick()
            app.mainClock.advanceTimeBy(240)
            val leaving=app.onNodeWithTag("profile-guest").getUnclippedBoundsInRoot()
            val identity=app.onNodeWithTag("profile-identity").getUnclippedBoundsInRoot()
            assertTrue("Login content exits downward",leaving.top.value>before.top.value+1f)
            assertEquals("Login does not exit horizontally",before.left.value,leaving.left.value,.5f)
            app.mainClock.advanceTimeBy(800)
            app.onNodeWithTag("profile-guest").assertDoesNotExist()
            val settled=app.onNodeWithTag("profile-identity").getUnclippedBoundsInRoot()
            assertEquals("Profile is already at rest behind login",settled.left.value,identity.left.value,.5f)
            assertEquals(settled.top.value,identity.top.value,.5f)
        } finally {app.mainClock.autoAdvance=true;app.waitForIdle()}
    }

    @Test fun passwordEyeTogglesVisibilityWithoutChangingTheValue() {
        navigate("我的")
        val field=app.onNodeWithTag("account-password")
        val eye=app.onNodeWithTag("account-password-visibility")
        fun renderedPassword(): String {
            val layouts=mutableListOf<TextLayoutResult>()
            field.performSemanticsAction(SemanticsActions.GetTextLayoutResult){it(layouts)}
            return layouts.single().layoutInput.text.text
        }
        field.performTextInput("demo-eye-123")
        Espresso.closeSoftKeyboard()
        eye.performScrollTo().assertContentDescriptionEquals("显示密码").assertIsOff()
        val hidden=renderedPassword()
        assertEquals("demo-eye-123".length,hidden.length)
        assertTrue(hidden.all {it=='\u2022'})
        eye.performClick().assertContentDescriptionEquals("隐藏密码").assertIsOn()
        assertEquals("demo-eye-123",renderedPassword())
        eye.performClick().assertContentDescriptionEquals("显示密码").assertIsOff()
        assertEquals(hidden,renderedPassword())
    }

    @Test fun guestOwnsLoginAndSettingsReturnsToThatSameProfile() {
        navigate("我的")
        app.onNodeWithTag("profile-guest").assertIsDisplayed()
        app.onNodeWithTag("login-screen").assertExists()
        app.onNodeWithText("尚未登录").assertDoesNotExist()
        app.onNodeWithContentDescription("返回").assertDoesNotExist()
        settings()
        app.onNodeWithText("进入登录页").performScrollTo().performClick()
        app.onNodeWithTag("settings-screen").assertDoesNotExist()
        app.onNodeWithTag("profile-guest").assertIsDisplayed()
        app.onNodeWithText("填入示例资料").performScrollTo().performClick()
        app.onNodeWithTag("account-submit").performScrollTo().performClick()
        app.onNodeWithTag("profile-identity").assertIsDisplayed()
        app.onNodeWithTag("login-screen").assertDoesNotExist()
        val saved=app.activity.getSharedPreferences("offline_preview",Context.MODE_PRIVATE)
        assertFalse(saved.all.keys.any {it.contains("password")||it.contains("email")||it.contains("token")})
    }

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
        app.onNodeWithText("忘记密码").performScrollTo().performClick()
        app.onNodeWithTag("recovery-email").performTextInput("reader@example.invalid")
        Espresso.closeSoftKeyboard()
        app.onNodeWithText("获取演示验证码").performScrollTo().performClick()
        app.onNodeWithTag("recovery-code").performTextInput("000000")
        Espresso.closeSoftKeyboard()
        app.onNodeWithTag("reset-password").performTextInput("demo-reset-123")
        app.onNodeWithTag("reset-confirmation").performTextInput("demo-reset-123")
        Espresso.closeSoftKeyboard()
        app.onNodeWithText("完成演示重置").performScrollTo().performClick()
        app.onNodeWithText("请输入上方的演示验证码").assertExists()
        app.onNodeWithTag("recovery-code").performTextReplacement("123456")
        Espresso.closeSoftKeyboard()
        Espresso.closeSoftKeyboard()
        app.onNodeWithText("完成演示重置").performScrollTo().performClick()
        app.onNodeWithTag("forgot-screen").assertIsDisplayed()
        app.onNodeWithTag("reset-screen").assertDoesNotExist()
        app.onNodeWithText("演示重置已完成").assertIsDisplayed()
        app.onNodeWithText("返回个人页").performClick()
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
        app.onNodeWithTag("main-circle").assertIsDisplayed()
        app.onNodeWithText("作者主页").assertDoesNotExist()
        app.onNodeWithTag("author-profile-feed").performScrollToNode(hasTestTag("author-articles"))
        app.onNodeWithTag("author-wave").assertIsDisplayed()
        app.onNodeWithTag("article-street").performClick()
        app.onNodeWithTag("reader-screen").assertIsDisplayed()
        back();back()
        app.onNodeWithTag("search-input").assertTextContains("街角")
        app.onNodeWithTag("author-zhou").assertIsDisplayed()
    }

    @Test fun savedLeftPlacementAlsoMirrorsTheRadialMenu() {
        navigate("我的");settings()
        app.onNodeWithText("调整导航位置").performScrollTo().performClick()
        app.onNodeWithTag("position-preview").performTouchInput {
            down(center);moveBy(Offset(-1000f,0f),delayMillis=400);up()
        }
        app.onNodeWithText("保存位置").performClick()
        back()
        assertTrue(app.onNodeWithTag("main-circle").getUnclippedBoundsInRoot().left.value<60f)
        val density=app.activity.resources.displayMetrics.density
        app.onNodeWithTag("main-circle").performTouchInput {down(center);advanceEventTime(800);moveTo(center+Offset(116f*density,-8f*density),delayMillis=200);up()}
        app.onNodeWithTag("feed-home").assertIsDisplayed()
        assertEquals(0f,app.activity.getSharedPreferences("offline_preview",Context.MODE_PRIVATE).getFloat("nav_x",1f),0f)
    }
}
