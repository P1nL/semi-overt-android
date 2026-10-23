package com.semiovert.app

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.semiovert.app.core.data.SampleLibrary
import com.semiovert.app.core.designsystem.arc.ArcArticleDeck
import com.semiovert.app.core.designsystem.arc.ArcAuthorAvatar
import com.semiovert.app.core.designsystem.arc.ArcDeckMath
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ArcExpandedAuthorAvatarTest {
    @get:Rule val app=createComposeRule()

    @Test fun browseStaysHiddenForTheGestureAndRegrabInterruptsTheFinalAuthorEntrance() {
        var author by mutableStateOf(SampleLibrary.authors[0])
        var position by mutableFloatStateOf(0f)
        var dragging by mutableStateOf(false)
        var settling by mutableStateOf(false)
        app.setContent {
            MaterialTheme {
                BoxWithConstraints(Modifier.fillMaxSize().clipToBounds()) {
                    ArcAuthorAvatar(author,"article-"+author.id,position,dragging,settling,true,
                        ArcDeckMath.geometry(maxWidth.value,maxHeight.value))
                }
            }
        }
        app.waitForIdle()
        app.mainClock.advanceTimeBy(600)
        app.waitForIdle()
        app.onNodeWithTag("home-browse-author-lin",useUnmergedTree=true).assertIsDisplayed()
        app.mainClock.autoAdvance=false
        app.runOnIdle {dragging=true;author=SampleLibrary.authors[1];position=.8f}
        app.mainClock.advanceTimeBy(400)
        app.waitForIdle()
        app.onNodeWithTag("home-browse-author-lin",useUnmergedTree=true).assertIsNotDisplayed()
        app.onNodeWithTag("home-browse-author-zhou",useUnmergedTree=true).assertDoesNotExist()
        app.onNodeWithContentDescription("林间来信的头像").assertDoesNotExist()
        // Even holding a slow/stationary drag must not bring the avatar back.
        app.runOnIdle {author=SampleLibrary.authors[2];position=1.95f}
        app.mainClock.advanceTimeBy(1000)
        app.waitForIdle()
        app.onNodeWithTag("home-author-rail").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,"已隐藏"))
        app.onNodeWithTag("home-browse-author-ye",useUnmergedTree=true).assertDoesNotExist()
        app.runOnIdle {dragging=false;settling=true;position=2.5f}
        app.mainClock.advanceTimeBy(300)
        app.waitForIdle()
        app.onNodeWithTag("home-author-rail").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,"已隐藏"))
        app.runOnIdle {position=2.03f}
        app.mainClock.advanceTimeBy(80)
        app.waitForIdle()
        app.onNodeWithTag("home-author-rail").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,"已隐藏"))
        app.mainClock.advanceTimeBy(100)
        app.waitForIdle()
        app.onNodeWithTag("home-author-rail").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,"已显示"))
        // Regrab before the 280ms entry finishes: reverse from the current position.
        app.runOnIdle {dragging=true;settling=false}
        app.mainClock.advanceTimeBy(200)
        app.waitForIdle()
        app.onNodeWithTag("home-browse-author-ye",useUnmergedTree=true).assertIsNotDisplayed()
        app.runOnIdle {dragging=false;settling=true;position=2.02f}
        app.mainClock.advanceTimeBy(600)
        app.waitForIdle()
        app.onNodeWithTag("home-browse-author-ye",useUnmergedTree=true).assertIsDisplayed().assertContentDescriptionEquals("纸页之间的头像")
        app.onNodeWithTag("home-browse-author-lin",useUnmergedTree=true).assertDoesNotExist()
        app.onNodeWithTag("home-browse-author-zhou",useUnmergedTree=true).assertDoesNotExist()
    }

    @Test fun clickedAuthorsAvatarWaitsForLiftAndLeavesWhenDragging() {
        app.setContent {
            var selected by remember {mutableIntStateOf(0)}
            MaterialTheme {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    ArcArticleDeck(SampleLibrary.articles,selected,{selected=it},{},80f,maxHeight.value-80f)
                }
            }
        }
        app.waitForIdle()
        app.mainClock.autoAdvance=false
        // Click a neighbour, not the centred article: the author must follow the click.
        app.onNodeWithTag("article-street").performTouchInput {click(Offset(width*.5f,height*.08f))}
        app.mainClock.advanceTimeBy(100)
        app.onNodeWithTag("home-expanded-author-street",useUnmergedTree=true).assertDoesNotExist()
        app.mainClock.advanceTimeBy(1500)
        app.waitForIdle()
        val avatar=app.onNodeWithTag("home-expanded-author-street",useUnmergedTree=true)
            .assertIsDisplayed().assertContentDescriptionEquals("街角观察员的头像").fetchSemanticsNode().boundsInRoot
        val card=app.onNodeWithTag("article-street").fetchSemanticsNode().boundsInRoot
        assertEquals("Right-aligned without shifting the card",card.right,avatar.right,2f)
        assertTrue("Prefer the upper blank area",avatar.bottom<card.top)
        app.onNodeWithContentDescription("林间来信的头像").assertDoesNotExist()

        app.onNodeWithTag("home-arc").performTouchInput {
            val start=Offset(width*.96f,height*.46f)
            swipe(start,start+Offset(0f,-height*.12f),600)
        }
        app.mainClock.advanceTimeBy(1500)
        app.waitForIdle()
        app.onNodeWithTag("home-expanded-author-street",useUnmergedTree=true).assertDoesNotExist()
        app.onAllNodes(hasContentDescription("的头像",substring=true)).assertCountEquals(1)
    }
}
