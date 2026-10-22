package com.semiovert.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.semiovert.app.core.designsystem.SoTheme
import com.semiovert.app.navigation.SwipeBackPage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SwipeBackPageTest {
    @get:Rule val app=createComposeRule()
    @Test fun middleSwipeReturnsButVerticalScrollAndTextInputKeepTheirGestures() {
        var returns=0
        app.setContent {
            SoTheme {
                SwipeBackPage(true,{returns++}) {
                    Column(Modifier.fillMaxSize().padding(24.dp).testTag("swipe-page-content")) {
                        var value by remember {mutableStateOf("输入框中的手势不触发页面返回")}
                        OutlinedTextField(value,{value=it},Modifier.fillMaxWidth().testTag("swipe-input"))
                    }
                }
            }
        }
        app.onNodeWithTag("swipe-input").performTouchInput {swipe(Offset(width*.3f,height*.5f),Offset(width*.9f,height*.5f),400)}
        app.runOnIdle {assertEquals(0,returns)}
        app.onNodeWithTag("swipe-page-content").performTouchInput {swipe(Offset(width*.4f,height*.4f),Offset(width*.4f,height*.7f),400)}
        app.runOnIdle {assertEquals(0,returns)}
        app.onNodeWithTag("swipe-page-content").performTouchInput {swipe(Offset(width*.3f,height*.5f),Offset(width*.9f,height*.5f),400)}
        app.runOnIdle {assertEquals(1,returns)}
    }
}
