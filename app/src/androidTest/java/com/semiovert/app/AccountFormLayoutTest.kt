package com.semiovert.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.semiovert.app.core.designsystem.LocalMotionEnabled
import com.semiovert.app.core.designsystem.SoTheme
import com.semiovert.app.core.model.ThemeMode
import com.semiovert.app.feature.account.AccountForm
import com.semiovert.app.feature.account.AccountMode
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccountFormLayoutTest {
    @get:Rule val app=createComposeRule()

    private fun form(mode: AccountMode) {
        app.setContent {
            SoTheme(ThemeMode.LIGHT) {
                CompositionLocalProvider(LocalMotionEnabled provides false) {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
                        AccountForm({},initialMode=mode)
                    }
                }
            }
        }
    }

    @Test fun registrationKeepsOnlyTheBottomLoginEntry() {
        form(AccountMode.REGISTER)
        app.onNodeWithTag("account-recovery-toggle").assertDoesNotExist()
        app.onNodeWithText("返回登录").assertDoesNotExist()
        app.onAllNodesWithText("已有账号？登录").assertCountEquals(1)
        app.onNodeWithTag("account-register-toggle").performScrollTo().performClick()
        app.onNodeWithTag("account-confirmation").assertDoesNotExist()
        app.onNodeWithTag("account-recovery-toggle").assertExists()
    }

    @Test fun recoveryHasSideBySideRegisterAndLoginBelowTheSubmitButton() {
        form(AccountMode.RECOVERY)
        app.onNodeWithTag("account-recovery-toggle").assertDoesNotExist()
        app.onNodeWithText("返回登录").assertDoesNotExist()
        app.onNodeWithText("注册账号").assertDoesNotExist()
        val register=app.onNodeWithTag("account-register-toggle").assertTextContains("注册").getUnclippedBoundsInRoot()
        val login=app.onNodeWithTag("account-login-toggle").assertTextContains("登录").getUnclippedBoundsInRoot()
        val submit=app.onNodeWithTag("account-submit").getUnclippedBoundsInRoot()
        assertTrue(register.top>=submit.bottom)
        assertTrue(login.top==register.top)
        assertTrue(register.right<login.left)
        app.onNodeWithTag("account-login-toggle").performClick()
        app.onNodeWithTag("recovery-code").assertDoesNotExist()
        app.onNodeWithTag("account-recovery-toggle").performClick()
        app.onNodeWithTag("account-register-toggle").performClick()
        app.onNodeWithTag("account-name").assertExists()
        app.onNodeWithTag("account-recovery-toggle").assertDoesNotExist()
    }

    @Test fun confirmationStatusReplacesTheLabelWithoutABelowFieldDuplicate() {
        form(AccountMode.REGISTER)
        val password="demo-pass-123"
        app.onNodeWithTag("account-password").performTextReplacement(password)
        val confirmation=app.onNodeWithTag("account-confirmation")
        for((value,status) in listOf(
            "" to "再次输入密码",
            password.take(4) to "继续输入",
            "wrong" to "密码不一致",
            "$password!" to "长度超出",
            password to "密码一致",
        )) {
            confirmation.performTextReplacement(value)
            Espresso.closeSoftKeyboard()
            app.onAllNodesWithText(status,useUnmergedTree=true).assertCountEquals(1)
            confirmation.assertContentDescriptionEquals("确认密码")
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,status))
            val label=app.onNodeWithTag("account-confirmation-label",useUnmergedTree=true).getUnclippedBoundsInRoot()
            val input=confirmation.getUnclippedBoundsInRoot()
            assertTrue("The status belongs in the field label, above the editable text",label.bottom<=input.top)
        }
        confirmation.performTextReplacement("wrong")
        Espresso.closeSoftKeyboard()
        app.onNodeWithTag("account-submit").performScrollTo().performClick()
        app.onNodeWithText("密码不一致",useUnmergedTree=true).assertExists()
        app.onNodeWithText("两次密码不一致").assertDoesNotExist()
    }
}
