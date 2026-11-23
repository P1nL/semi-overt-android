package com.semiovert.app

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.net.Uri
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
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
    private fun input(tag: String)=app.onNode(
        hasSetTextAction() and (hasTestTag(tag) or hasAnyAncestor(hasTestTag(tag))),useUnmergedTree=true)
    private fun fillAccount(register: Boolean=false) {
        if(register)input("account-name").performScrollTo().performTextReplacement("本机读者")
        input("account-email").performScrollTo().performTextReplacement("reader@example.invalid")
        input("account-password").performScrollTo().performTextReplacement("reader-pass-123")
        if(register)input("account-confirmation").performScrollTo().performTextReplacement("reader-pass-123")
        Espresso.closeSoftKeyboard()
    }
    private fun enterDemo() {
        navigate("我的")
        fillAccount()
        app.onNodeWithTag("account-submit").performScrollTo().performClick()
        app.onNodeWithTag("profile-identity").assertIsDisplayed()
    }
    private fun settings() {app.onNodeWithContentDescription("设置").performClick()}
    private fun assertProfileCover(): SemanticsNodeInteraction {
        app.waitUntil(7_500) {
            app.onAllNodesWithTag("profile-cover-image",useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()
        }
        return app.onNodeWithTag("profile-cover-image",useUnmergedTree=true).assertIsDisplayed()
    }
    private fun coverTopColor(cover: SemanticsNodeInteraction): Int {
        val pixels=cover.captureToImage().toPixelMap()
        return pixels[pixels.width/4,pixels.height/40].toArgb()
    }
    private fun pickPhoto(target: SemanticsNodeInteraction,source: File) {
        val request=PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        val intent=ActivityResultContracts.PickVisualMedia().createIntent(app.activity,request)
        val filter=IntentFilter(requireNotNull(intent.action)).apply {
            intent.type?.let {addDataType(it)}
            intent.categories?.forEach {addCategory(it)}
        }
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val result=Instrumentation.ActivityResult(Activity.RESULT_OK,Intent().setData(Uri.fromFile(source)))
        val monitor=instrumentation.addMonitor(filter,result,true)
        try {
            target.performScrollTo().performTouchInput {click(center)}
            assertTrue(instrumentation.checkMonitorHit(monitor,1))
        } finally {instrumentation.removeMonitor(monitor)}
    }

    @Test fun inlineLoginSlidesDownOverTheSettledProfile() {
        navigate("我的")
        fillAccount()
        app.onNodeWithTag("account-submit").performScrollTo()
        val before=app.onNodeWithTag("profile-guest").getUnclippedBoundsInRoot()
        app.mainClock.autoAdvance=false
        try {
            app.onNodeWithTag("account-submit").performClick()
            app.mainClock.advanceTimeBy(400)
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
        app.onNodeWithText("进入登录页").assertDoesNotExist()
        app.onNodeWithContentDescription("返回").performClick()
        app.onNodeWithTag("settings-screen").assertDoesNotExist()
        app.onNodeWithTag("profile-guest").assertIsDisplayed()
        fillAccount()
        app.onNodeWithTag("account-submit").performScrollTo().performClick()
        app.onNodeWithTag("profile-identity").assertIsDisplayed()
        app.onNodeWithTag("login-screen").assertDoesNotExist()
        val saved=app.activity.getSharedPreferences("offline_preview",Context.MODE_PRIVATE)
        assertFalse(saved.all.keys.any {it.contains("password")||it.contains("email")||it.contains("token")})
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun registrationProfileEditingAndCustomPhotosAreConnected() {
        navigate("我的")
        app.onNodeWithText("没有账号？注册",useUnmergedTree=true).performClick()
        app.onNodeWithTag("account-submit").performScrollTo().performClick()
        app.onNodeWithText("邮箱格式有误").assertExists()
        fillAccount(register=true)
        app.onNodeWithTag("account-confirmation-visibility").assertDoesNotExist()
        app.onNodeWithTag("account-submit").performScrollTo().performClick()
        app.onNodeWithTag("profile-identity").performClick()
        input("edit-name").performTextReplacement("本机读者")
        Espresso.closeSoftKeyboard()
        val source=File.createTempFile("profile-test-",".png",app.activity.cacheDir)
        val image=Bitmap.createBitmap(768,768,Bitmap.Config.ARGB_8888)
        image.eraseColor(Color.BLUE)
        Canvas(image).drawRect(0f,0f,384f,768f,Paint().apply {
            shader=LinearGradient(0f,0f,0f,768f,Color.RED,Color.YELLOW,Shader.TileMode.CLAMP)
        })
        source.outputStream().use {assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,it))}
        image.recycle()
        val avatarButton=app.onNodeWithText("更换头像")
        val coverFrame=app.onNodeWithTag("edit-cover-frame")
        val preview=app.onNodeWithTag("edit-cover-preview",useUnmergedTree=true)
        val adjustedColor: Int
        try {
            pickPhoto(avatarButton,source)
            app.waitUntilAtLeastOneExists(hasTestTag("avatar-crop-frame"),7_500)
            app.onNodeWithText("取消").performClick()
            app.onNodeWithTag("avatar-crop-frame").assertDoesNotExist()
            pickPhoto(avatarButton,source)
            app.waitUntilAtLeastOneExists(hasTestTag("avatar-crop-frame"),7_500)
            val slider=app.onNodeWithTag("avatar-crop-zoom")
            val track=app.onNodeWithTag("avatar-crop-track",useUnmergedTree=true)
            fun nearStart(): Int {
                val pixels=track.captureToImage().toPixelMap()
                return pixels[pixels.width/50,pixels.height/2].toArgb()
            }
            val emptyStart=nearStart()
            slider.performSemanticsAction(SemanticsActions.SetProgress){it(1.08f)}
            assertNotEquals("A small movement immediately fills the start of the zoom track",emptyStart,nearStart())
            slider.performSemanticsAction(SemanticsActions.SetProgress){it(1f)}
            app.onNodeWithTag("avatar-crop-frame").performTouchInput {
                swipe(center,center+Offset(width*.24f,0f),450)
            }
            app.onNodeWithTag("avatar-crop-confirm").performClick()
            app.waitUntilDoesNotExist(hasTestTag("avatar-crop-dialog"),7_500)
            coverFrame.assertTextContains("上传封面")
            pickPhoto(coverFrame,source)
            app.waitUntil(7_500) {
                app.onAllNodesWithTag("edit-cover-preview",useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()
            }
            val initialColor=coverTopColor(preview)
            coverFrame.performTouchInput {swipe(center+Offset(0f,height*.30f),center-Offset(0f,height*.25f),500)}
            adjustedColor=coverTopColor(preview)
            assertTrue("Dragging the cover upward reveals rows further down the image",
                Color.green(adjustedColor)>Color.green(initialColor)+10)
            app.onNodeWithText("保存资料").performScrollTo().performClick()
        } finally {source.delete()}
        app.onNodeWithText("本机读者").assertIsDisplayed()
        app.activityRule.scenario.recreate()
        app.onNodeWithText("本机读者").assertIsDisplayed()
        val shownColor=coverTopColor(assertProfileCover())
        assertEquals("The cover has no tint over the top edge",0,Color.blue(shownColor))
        assertEquals("The profile uses the saved crop",Color.green(adjustedColor).toFloat(),Color.green(shownColor).toFloat(),8f)
        val saved=app.activity.getSharedPreferences("offline_preview",Context.MODE_PRIVATE)
        val media=app.activity.filesDir.resolve("profile-media")
        val avatar=requireNotNull(BitmapFactory.decodeFile(media.resolve(requireNotNull(saved.getString("avatar_photo",null))).path))
        try {
            assertEquals(512,avatar.width)
            assertEquals(512,avatar.height)
            assertTrue("Dragging the photo changes the exported crop",Color.red(avatar.getPixel(256,256))>200)
        } finally {avatar.recycle()}
        val cover=media.resolve(requireNotNull(saved.getString("cover_photo",null)))
        assertTrue(cover.isFile)
        assertNull(saved.getString("cover",null))
        val coverPosition=saved.getFloat("cover_position",.5f)
        assertTrue(coverPosition>.5f)
        app.onNodeWithTag("profile-identity").performClick()
        app.onNodeWithTag("edit-cover-remove").assertContentDescriptionEquals("移除封面").performScrollTo().performClick()
        coverFrame.assertTextContains("上传封面")
        back()
        app.onNodeWithText("放弃").performClick()
        assertTrue("Discarding a removal keeps the saved cover",cover.isFile)
        assertEquals(coverPosition,saved.getFloat("cover_position",.5f),0f)
        assertProfileCover()
        assertFalse(saved.all.keys.any {it.contains("password")||it.contains("email")||it.contains("token")})
    }

    @Test fun accountModesKeepTheirEditorsAndExpandFieldsInOrder() {
        navigate("我的")
        fillAccount()
        val emailId=app.onNodeWithTag("account-email").fetchSemanticsNode().id
        val editorId=app.onNodeWithTag("account-password").fetchSemanticsNode().id
        app.onNodeWithTag("account-register-toggle").performScrollTo().performClick()
        app.onNodeWithTag("account-name").assertExists()
        app.onNodeWithTag("account-confirmation").assertExists()
        app.onNodeWithTag("account-confirmation-visibility").assertDoesNotExist()
        app.onNodeWithTag("account-email").assertTextContains("reader@example.invalid")
        assertEquals(emailId,app.onNodeWithTag("account-email").fetchSemanticsNode().id)
        assertEquals(editorId,app.onNodeWithTag("account-password").fetchSemanticsNode().id)
        val email=app.onNodeWithTag("account-email").getUnclippedBoundsInRoot()
        val name=app.onNodeWithTag("account-name").getUnclippedBoundsInRoot()
        val confirmation=app.onNodeWithTag("account-confirmation").getUnclippedBoundsInRoot()
        val editor=app.onNodeWithTag("account-password").getUnclippedBoundsInRoot()
        assertTrue(email.bottom<=name.top)
        assertTrue(name.bottom<=editor.top)
        assertTrue(editor.bottom<=confirmation.top)
        app.onNodeWithTag("register-screen").assertDoesNotExist()
        app.onNodeWithTag("main-circle").assertIsDisplayed()
        back()
        app.onNodeWithTag("account-name").assertDoesNotExist()
        app.onNodeWithTag("account-confirmation").assertDoesNotExist()
        app.onNodeWithTag("account-recovery-toggle").performScrollTo().performClick()
        app.onNodeWithTag("recovery-code").assertExists()
        app.onNodeWithTag("account-confirmation").assertExists()
        app.onNodeWithTag("account-email").assertTextContains("reader@example.invalid")
        assertEquals(editorId,app.onNodeWithTag("account-password").fetchSemanticsNode().id)
        val code=app.onNodeWithTag("recovery-code").getUnclippedBoundsInRoot()
        val newSecret=app.onNodeWithTag("account-password").getUnclippedBoundsInRoot()
        val newConfirmation=app.onNodeWithTag("account-confirmation").getUnclippedBoundsInRoot()
        assertTrue(code.bottom<=newSecret.top)
        assertTrue(newSecret.bottom<=newConfirmation.top)
        app.onNodeWithTag("forgot-screen").assertDoesNotExist()
        app.onNodeWithTag("main-circle").assertIsDisplayed()
        app.onNodeWithContentDescription("返回").assertDoesNotExist()
    }

    @Test fun recoveryValidatesCodeAndCollapsesBackToLogin() {
        navigate("我的")
        app.onNodeWithText("忘记密码").performScrollTo().performClick()
        app.onNodeWithTag("account-email").performScrollTo().performTextInput("reader@example.invalid")
        Espresso.closeSoftKeyboard()
        app.onNodeWithTag("account-send-code").performScrollTo().performClick()
        app.onNodeWithTag("recovery-code").performScrollTo().performTextInput("000000")
        Espresso.closeSoftKeyboard()
        app.onNodeWithTag("account-password").performScrollTo().performTextInput("demo-reset-123")
        app.onNodeWithTag("account-confirmation").performScrollTo().performTextInput("demo-reset-123")
        app.onNodeWithTag("account-confirmation-visibility").assertDoesNotExist()
        Espresso.closeSoftKeyboard()
        app.onNodeWithText("重置密码").performScrollTo().performClick()
        app.onNodeWithText("验证码有误").assertExists()
        app.onNodeWithTag("recovery-code").performScrollTo().performTextReplacement("123456")
        Espresso.closeSoftKeyboard()
        Espresso.closeSoftKeyboard()
        app.onNodeWithText("重置密码").performScrollTo().performClick()
        app.onNodeWithTag("forgot-screen").assertDoesNotExist()
        app.onNodeWithTag("recovery-code").assertDoesNotExist()
        app.onNodeWithTag("account-confirmation").assertDoesNotExist()
        app.onNodeWithText("设置完成，请登录").assertExists()
        app.onNodeWithTag("login-screen").assertExists()
        app.onNodeWithTag("account-email").assertTextContains("reader@example.invalid")
        app.onNodeWithTag("main-circle").assertIsDisplayed()
    }

    @Test fun recordsHaveConfirmationSummaryDiffAndCancelableMonthSelection() {
        enterDemo();settings()
        app.onNodeWithContentDescription("本地修改记录开关").assertIsOff().performClick()
        app.onNodeWithText("开启修改记录？").assertIsDisplayed()
        app.onNodeWithText("确定").performClick()
        app.onNodeWithText("查看记录").assertDoesNotExist()
        app.onNodeWithText("页面状态").assertDoesNotExist()
        back()
        val feed=app.onNodeWithTag("my-profile-feed")
        fun reveal(tag: String): SemanticsNodeInteraction {
            feed.performScrollToNode(hasTestTag(tag))
            return app.onNodeWithTag(tag).performScrollTo()
        }
        reveal("records-expand").performClick()
        reveal("record-day-16").performClick()
        reveal("record-diff-forest").performClick()
        app.onNodeWithTag("diff-sheet").assertIsDisplayed()
        app.onNodeWithText("Diff").assertIsDisplayed()
        back()
        app.onNodeWithTag("record-detail").assertExists()
        reveal("records-month").performClick()
        val september=app.onNode(hasText("9") and hasAnyAncestor(hasTestTag("month-wheel-month")))
        september.performClick().assertIsSelected()
        app.onNodeWithTag("month-cancel").assertContentDescriptionEquals("取消").performClick()
        app.onNodeWithText("2026.10").assertIsDisplayed()
        app.onNodeWithTag("records-month").performClick()
        september.performClick().assertIsSelected()
        app.onNodeWithTag("month-apply").assertContentDescriptionEquals("确定").performClick()
        app.onNodeWithText("2026.09").assertIsDisplayed()
        settings()
        app.onNodeWithText("清除本地记录").performScrollTo().performClick()
        app.onNodeWithText("清除").performClick()
        app.onNodeWithText("恢复本地快照").assertExists()
    }

    @Test fun authorPagesAreReadOnlyAndReturnToSearch() {
        navigate("搜索")
        app.onNodeWithTag("search-input").performTextInput("街角");app.onNodeWithTag("search-input").performImeAction()
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
        app.onNodeWithContentDescription("保存位置").performClick()
        back()
        assertTrue(app.onNodeWithTag("main-circle").getUnclippedBoundsInRoot().left.value<60f)
        val density=app.activity.resources.displayMetrics.density
        app.onNodeWithTag("main-circle").performTouchInput {down(center);advanceEventTime(800);moveTo(center+Offset(116f*density,-8f*density),delayMillis=200);up()}
        app.onNodeWithTag("feed-home").assertIsDisplayed()
        assertEquals(0f,app.activity.getSharedPreferences("offline_preview",Context.MODE_PRIVATE).getFloat("nav_x",1f),0f)
    }
}
