package com.semiovert.app

import com.semiovert.app.core.designsystem.StitchedGlyphTracker
import org.junit.Assert.*
import org.junit.Test

class StitchedGlyphTrackerTest {
    @Test fun initialTextIsAlreadyVisible() {
        val glyphs=StitchedGlyphTracker().update(listOf("a","b","c"))
        assertTrue(glyphs.none {it.entering})
        assertEquals(listOf(0,1,2),glyphs.map {it.start})
    }

    @Test fun typingOnlyAddsAnAnimationForTheNewGlyph() {
        val tracker=StitchedGlyphTracker()
        tracker.update(emptyList())
        val first=tracker.update(listOf("a"))
        val next=tracker.update(listOf("a","b"))
        assertTrue(first.single().entering)
        assertEquals(first.single().id,next.first().id)
        assertNotEquals(first.single().id,next.last().id)
        assertTrue(next.last().entering)
    }

    @Test fun backspacePreservesEverySurvivingGlyph() {
        val tracker=StitchedGlyphTracker()
        val before=tracker.update(listOf("a","b","c"))
        val after=tracker.update(listOf("a","b"))
        assertEquals(before.take(2),after)
        assertTrue(after.none {it.entering})
    }

    @Test fun deletingInTheMiddleMovesTheSuffixWithoutRestartingIt() {
        val tracker=StitchedGlyphTracker()
        val before=tracker.update(listOf("a","b","c","d"))
        val after=tracker.update(listOf("a","d"))
        assertEquals(listOf(before[0].id,before[3].id),after.map {it.id})
        assertEquals(1,after.last().start)
        assertTrue(after.none {it.entering})
    }

    @Test fun insertingInTheMiddlePreservesBothSides() {
        val tracker=StitchedGlyphTracker()
        val before=tracker.update(listOf("a","d"))
        val after=tracker.update(listOf("a","b","c","d"))
        assertEquals(before.first().id,after.first().id)
        assertEquals(before.last().id,after.last().id)
        assertEquals(listOf(false,true,true,false),after.map {it.entering})
    }

    @Test fun replacementPreservesTheSuffixAndGraphemeOffsets() {
        val tracker=StitchedGlyphTracker()
        val before=tracker.update(listOf("a","b","z"))
        val after=tracker.update(listOf("a","\uD83D\uDE00","e\u0301","z"))
        assertEquals(before.first().id,after.first().id)
        assertEquals(before.last().id,after.last().id)
        assertEquals(listOf(0,1,3,5),after.map {it.start})
        assertEquals(listOf(1,3,5,6),after.map {it.end})
        assertEquals(listOf(false,true,true,false),after.map {it.entering})
    }

    @Test fun retypingAfterClearingGetsANewArrival() {
        val tracker=StitchedGlyphTracker()
        val before=tracker.update(listOf("a"))
        tracker.update(emptyList())
        val after=tracker.update(listOf("a"))
        assertNotEquals(before.single().id,after.single().id)
        assertTrue(after.single().entering)
    }
}
