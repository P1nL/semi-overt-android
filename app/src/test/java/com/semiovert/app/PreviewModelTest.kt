package com.semiovert.app

import com.semiovert.app.core.data.DemoRecords
import com.semiovert.app.core.model.*
import org.junit.Assert.*
import org.junit.Test

class PreviewModelTest {
    @Test fun demoEmailValidationDoesNotAcceptBlankOrMalformedInput() {
        assertNotNull(PreviewValidation.email(""))
        assertNotNull(PreviewValidation.email("not an email"))
        assertNull(PreviewValidation.email("reader@example.invalid"))
        assertNull(PreviewValidation.email(" reader@example.invalid "))
    }
    @Test fun namesAndHandlesHaveClearConstraints() {
        assertNotNull(PreviewValidation.name(" "))
        assertNull(PreviewValidation.name("林间读者"))
        assertNotNull(PreviewValidation.handle("含有空格 "))
        assertNull(PreviewValidation.handle("offline_reader"))
    }
    @Test fun demoPasswordRequiresEightCharacters() {
        assertNotNull(PreviewValidation.password("short"))
        assertNull(PreviewValidation.password("demo-only-123"))
    }
    @Test fun snapshotsAreOffByDefaultAndProfileHasNoCover() {
        val state=PreviewState()
        assertFalse(state.signedIn)
        assertFalse(state.snapshots)
        assertNull(state.profile.cover)
        assertEquals(NavigationPlacement(1f,0f),state.placement)
    }
    @Test fun fixtureDiffCountsMatchReplacedTextNotDailySummary() {
        val entry=DemoRecords.entries.first()
        assertEquals(entry.after.length,entry.added)
        assertEquals(entry.before.length,entry.removed)
        assertNotEquals(entry.summaryWords,entry.added)
    }
    @Test fun authorRecordViewsOnlyContainThatAuthorsArticles() {
        val source=com.semiovert.app.core.data.SampleLibrary
        assertTrue(DemoRecords.forAuthor("lin").all {source.article(it.articleId)?.author?.id=="lin"})
        assertTrue(DemoRecords.forAuthor("no-author").isEmpty())
    }
}
