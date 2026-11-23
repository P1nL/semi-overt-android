package com.semiovert.app

import com.semiovert.app.core.designsystem.scrambleTextFrame
import org.junit.Assert.*
import org.junit.Test

class ScrambleTextTest {
    @Test fun transitionsStartAndFinishWithTheRealCopy() {
        assertEquals("Login",scrambleTextFrame("Login","Register",0f))
        assertEquals("Register",scrambleTextFrame("Login","Register",1f))
    }

    @Test fun intermediateFramesUseNoiseBeforeSettlingLeftToRight() {
        val middle=scrambleTextFrame("\u767b\u5f55","\u6ce8\u518c",.3f)
        assertNotEquals("\u767b\u5f55",middle)
        assertNotEquals("\u6ce8\u518c",middle)
        assertTrue(scrambleTextFrame("\u767b\u5f55","\u6ce8\u518c",.8f).startsWith("\u6ce8"))
    }

    @Test fun appearingAndDisappearingCopyCanReachEmpty() {
        assertEquals("",scrambleTextFrame("","Label",0f))
        assertTrue(scrambleTextFrame("","Label",.3f).isNotBlank())
        assertTrue(scrambleTextFrame("Label","",.3f).isNotBlank())
        assertEquals("",scrambleTextFrame("Label","",1f))
    }

    @Test fun unchangedCopyNeverScrambles() {
        for(progress in listOf(0f,.25f,.5f,1f)) {
            assertEquals("Login",scrambleTextFrame("Login","Login",progress))
            assertEquals("",scrambleTextFrame("","",progress))
        }
    }

    @Test fun interruptedChangesStartAtTheCurrentVisibleFrame() {
        val interrupted=scrambleTextFrame("Login","Register",.4f)
        assertEquals(interrupted,scrambleTextFrame(interrupted,"Recovery",0f))
        assertEquals("Recovery",scrambleTextFrame(interrupted,"Recovery",1f))
    }
}
