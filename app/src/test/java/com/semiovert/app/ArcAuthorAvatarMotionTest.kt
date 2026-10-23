package com.semiovert.app

import com.semiovert.app.core.designsystem.arc.ArcAuthorAvatarMotion
import com.semiovert.app.core.designsystem.arc.ArcDeckMath
import com.semiovert.app.core.designsystem.arc.ArcAvatarRestGate
import org.junit.Assert.*
import org.junit.Test

class ArcAuthorAvatarMotionTest {
    @Test fun confirmsAShortStableWindowBeforeTheSpringHasFullyFinished() {
        val gate=ArcAvatarRestGate()
        assertFalse(gate.sample(.03f,"first",true,0))
        assertFalse(gate.sample(.035f,"first",true,119))
        assertTrue(gate.sample(.04f,"first",true,120))
    }

    @Test fun movementRestartsTheWindowInsteadOfReactingToInstantaneousSpeed() {
        val gate=ArcAvatarRestGate()
        assertFalse(gate.sample(0f,"first",true,0))
        assertFalse(gate.sample(.04f,"first",true,80))
        assertFalse(gate.sample(.045f,"first",true,160))
        assertTrue(gate.sample(.05f,"first",true,200))
    }

    @Test fun changingTheCentredArticleRestartsConfirmation() {
        val gate=ArcAvatarRestGate()
        assertFalse(gate.sample(.01f,"first",true,0))
        assertFalse(gate.sample(.01f,"latest",true,100))
        assertFalse(gate.sample(.01f,"latest",true,219))
        assertTrue(gate.sample(.01f,"latest",true,220))
    }

    @Test fun aBriefSlowdownBetweenSlotsCannotRevealAnIntermediateAuthor() {
        val gate=ArcAvatarRestGate()
        assertFalse(gate.sample(.48f,"first",true,0))
        assertFalse(gate.sample(.49f,"first",true,500))
        assertFalse(gate.sample(.98f,"latest",true,520))
        assertTrue(gate.sample(.99f,"latest",true,640))
    }

    @Test fun confirmedVisibilityStaysLatchedUntilANewGestureCreatesANewGate() {
        val gate=ArcAvatarRestGate()
        assertFalse(gate.sample(.02f,"first",true,0))
        assertTrue(gate.sample(.02f,"first",true,120))
        assertTrue(gate.sample(.085f,"first",true,180))
        assertFalse(ArcAvatarRestGate().sample(.02f,"first",true,200))
    }

    @Test fun restoredFractionalPositionsDoNotNeedToSnapToAnInteger() {
        val gate=ArcAvatarRestGate()
        assertFalse(gate.sample(.43f,"first",false,0))
        assertTrue(gate.sample(.43f,"first",false,120))
    }

    @Test fun expandedAvatarPrefersAboveAndKeepsTheCardsRightEdge() {
        val g=ArcDeckMath.geometry(400f,850f)
        val p=ArcAuthorAvatarMotion.expandedPlacement(g,220f,80f,760f)!!
        assertEquals(220f-ArcAuthorAvatarMotion.EXPANDED_GAP,p.top+p.diameter,.001f)
        assertEquals(ArcDeckMath.pulledLeft(g)+g.cardWidth,p.left+p.diameter,.001f)
        assertEquals(ArcAuthorAvatarMotion.placement(g).diameter,p.diameter,0f)
        assertTrue(p.left+p.hiddenOffset>g.width)
    }

    @Test fun expandedAvatarFallsBelowWhenTheHeaderLeavesNoUpperSpace() {
        val g=ArcDeckMath.geometry(400f,850f)
        val p=ArcAuthorAvatarMotion.expandedPlacement(g,80f,80f,760f)!!
        assertEquals(80f+g.cardHeight+ArcAuthorAvatarMotion.EXPANDED_GAP,p.top,.001f)
        assertTrue(p.top+p.diameter<=760f)
    }

    @Test fun shortViewportsDoNotForceTheAvatarOverTheHeaderOrNavigation() {
        val g=ArcDeckMath.geometry(400f,500f)
        assertNull(ArcAuthorAvatarMotion.expandedPlacement(g,80f,80f,80f+g.cardHeight+20f))
    }

    @Test fun restingCircleIsTangentAndHiddenCircleIsBeyondTheRightEdge() {
        val g=ArcDeckMath.geometry(400f,850f)
        val p=ArcAuthorAvatarMotion.placement(g)
        assertEquals(g.focusX+g.cardWidth/2f,p.left,.001f)
        assertEquals(g.focusY,p.top+p.diameter/2f,.001f)
        assertTrue(p.left+p.diameter<=g.width-12f)
        assertTrue(p.left+p.hiddenOffset>g.width)
    }

}
