package com.semiovert.app

import com.semiovert.app.feature.search.SearchSubmissionGate
import org.junit.Assert.*
import org.junit.Test

class SearchSubmissionGateTest {
    @Test fun explicitClearDoesNotWaitForCooldownButStillThrottlesTheNextSearch() {
        val gate=SearchSubmissionGate()
        assertEquals("林间",gate.begin("林间","",0))
        gate.finish()
        assertEquals("",gate.begin("","林间",600,explicitClear=true))
        gate.finish()
        assertNull(gate.begin("街角","",1200))
        assertEquals("街角",gate.begin("街角","",1800))
    }

    @Test fun explicitClearNeverInterruptsOrRepeatsAnAnimation() {
        val gate=SearchSubmissionGate()
        assertEquals("",gate.begin("","林间",0,explicitClear=true))
        repeat(20) {assertNull(gate.begin("","林间",it*100L,explicitClear=true))}
        gate.finish()
        assertNull(gate.begin("","",2500,explicitClear=true))
        assertFalse(gate.busy)
    }

    @Test fun whitespaceAndCaseVariantsDoNotReplayTheSameResults() {
        val gate=SearchSubmissionGate()
        assertNull(gate.begin("  ","",0))
        assertNull(gate.begin("  FOREST  ","forest",0))
        assertFalse(gate.busy)
        assertEquals("街角",gate.begin(" 街角 ","forest",0))
    }

    @Test fun repeatedSubmissionsStayLockedForTheWholeAnimationAndNeverQueue() {
        val gate=SearchSubmissionGate()
        assertEquals("林间",gate.begin("林间","",0))
        repeat(100) {assertNull(gate.begin("不同关键词$it","",it*100L))}
        gate.finish()
        assertFalse(gate.busy)
        assertEquals("街角",gate.begin("街角","林间",10000))
    }

    @Test fun minimumIntervalStillAppliesWhenAnimationIsSkippedOrCancelled() {
        val gate=SearchSubmissionGate()
        assertEquals("林间",gate.begin("林间","",0))
        gate.finish()
        assertNull(gate.begin("街角","林间",1199))
        assertEquals("街角",gate.begin("街角","林间",1200))
    }

    @Test fun scopeSwitchesCannotOverlapEachOtherOrASubmission() {
        val gate=SearchSubmissionGate()
        assertTrue(gate.switchScope(0))
        assertFalse(gate.switchScope(349))
        assertNull(gate.begin("林间","",349))
        assertEquals("林间",gate.begin("林间","",350))
        assertFalse(gate.switchScope(10000))
        gate.finish()
        assertTrue(gate.switchScope(10000))
    }

    @Test fun confirmedEmptyQueryClearsResultsOnlyOnce() {
        val gate=SearchSubmissionGate()
        assertEquals("",gate.begin("   ","林间",0))
        gate.finish()
        assertNull(gate.begin("","",1200))
    }
}
